package com.brouken.player.together;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs one watch-together session for the life of a screen: owns the {@link Relay}, drives the
 * {@link SyncEngine} on a ticker, and applies what it decides through {@link Host}.
 *
 * <p>Deliberately anchored to the screen rather than to the player, which is torn down and rebuilt
 * far more often than a viewer would guess — a quality switch, a return from settings and six
 * distinct error-recovery paths all do it, and a session keyed to the player instance would die
 * with each one.
 *
 * <p>The room protocol is the Lampa {@code lparty.js} plugin's, so a viewer in Lampa's own web
 * player and a viewer here can share one room. That brings back the one thing our own design did
 * without — an owner — but only for what their client needs it for: answering a newcomer with a
 * snapshot, and being allowed to change what the room plays. Play, pause and seek stay open to
 * everyone.
 *
 * <p>Relay callbacks arrive on a network thread and are posted onto the main thread before they
 * touch anything, so the engine only ever runs on one thread and needs no locking.
 */
public final class TogetherManager implements Relay.Listener {

    /** What somebody else did, with the wire's own spelling for it left behind in LpartyCodec. */
    public enum Act {
        PAUSED, RESUMED, SEEKED, LEFT
    }

    /** Everything the session needs from the screen. All calls happen on the main thread. */
    public interface Host {
        /** False while there is no player or no media — the session idles rather than sampling. */
        boolean ready();

        /** True while the viewer is dragging the timebar; sampling waits for them to let go. */
        boolean scrubbing();

        long positionMs();

        /** Playback intent, not whether pixels are moving. */
        boolean playWhenReady();

        float speed();

        boolean buffering();

        /** Whether our own media has run out, which is not the viewer pausing. */
        boolean ended();

        /** How long our own media lasts, or 0 when that is not known yet. */
        long durationMs();

        /** What is playing right now, or null when nothing is. Only compared with what the room says
         *  it plays. */
        String playingUri();

        /** How much playback is buffered ahead of where we are, which is what a hold waits for. */
        long bufferedAheadMs();

        void applyPlay(boolean play);

        void applySeek(long positionMs);

        void applySpeed(float speed);

        /** The badge should be redrawn: members, connection or who we are waiting for changed. */
        void onRoomChanged();

        /**
         * Somebody else touched the controls and this room followed them.
         *
         * @param nick what they call themselves, or empty if they never said
         */
        void onRoomAction(String nick, Act act);

        /**
         * Everything this screen was launched with: the media plus the whole launch payload —
         * playlist, posters, ids, subtitle lists, headers. Null when there is nothing to describe.
         * Our own players exchange this; a Lampa peer gets the thin card below instead.
         */
        JSONObject sessionDescription();

        /**
         * The thin card a Lampa peer understands, with neutral keys: {@code url}, {@code title},
         * {@code poster}, {@code tmdb}, {@code source}, {@code type} ({@code movie}/{@code tv}).
         */
        JSONObject roomCard();

        /** Play what the room plays. */
        void openSession(JSONObject session);

        /** Nobody was on the channel, so there was no room to join. */
        void onJoinFailed();

        /** The room has finished waiting for everybody's buffer and is going on. */
        void onHoldLifted();
    }

    /** A stall shorter than this is not worth telling the room about. */
    private static final long BUFFER_ARM_MS = 1_000;
    /** How long a joining member waits for the owner's snapshot before giving up. Theirs is 6s. */
    private static final long JOIN_TIMEOUT_MS = 6_000;
    /** Breath taken before deciding a vacated room's new owner, so every member is reading the same
     *  roster rather than its own stale copy. Theirs is the same half second. */
    private static final long OWNER_ELECTION_MS = 500;

    // The join hold, their 1.2.0 addition, with their numbers. A member arriving mid-film has nothing
    // buffered, so instead of dragging it along the room stops where it is until everybody can go on.
    /** Longest the owner waits for the room before going on regardless. */
    private static final long HOLD_MAX_MS = 15_000;
    /** Buffer ahead of the hold position that counts as ready. */
    private static final long HOLD_BUFFER_MS = 6_000;
    /** Less than that will do if the buffer has plainly stopped growing — a line that will never reach
     *  six seconds should not keep everybody waiting for the full timeout. */
    private static final long HOLD_MIN_BUFFER_MS = 2_000;
    private static final long HOLD_STALL_MS = 3_000;
    /** A hello from somebody we heard from this recently is a reconnect, not an arrival, and calling a
     *  hold for it would stop the room every time anybody's signal wavered. */
    private static final long RECONNECT_HELLO_MS = 60_000;

    /**
     * Our id in the room, as the other client expects it: stable while the app runs, and what
     * ownership is compared by. Not the relay's connection id, which changes on every reconnect.
     */
    private static final String PID = newPid();

    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Replaced outright on entering a room rather than reset field by field: almost everything it holds
     *  is about one particular room — whose report the room follows, what position it agreed on, who is
     *  stalling — and carrying any of it into the next one made a fresh room pause itself and jump to the
     *  previous room's position. A new instance cannot forget a field. */
    private SyncEngine engine = new SyncEngine();
    /** id → what they call themselves. Only read to name a departure, and dropped as it is named. */
    private final Map<String, String> nicks = new HashMap<>();
    /** Relay id → application id, so a departure the relay reports can be matched to the owner. */
    private final Map<String, String> pidByUid = new HashMap<>();
    private final Runnable ownerElection = this::electOwner;
    /** pid → when we last heard a hello from them, to tell an arrival from a reconnect. */
    private final Map<String, Long> helloSeenAt = new HashMap<>();

    // --- The join hold. Owned by the owner; a member only answers when its buffer is full enough. ---
    private boolean holdActive;
    private long holdPositionMs;
    /** True while it is our hold to lift, i.e. we are the owner who called it. */
    private boolean holdOurs;
    /** Whether playback was running when the hold began, so lifting it puts back what was there rather
     *  than starting a room somebody had deliberately paused. Their client always resumes, which turns
     *  "somebody joined" into "the film started" for a room that was sitting still on purpose. */
    private boolean holdWasPlaying;
    private final Set<String> holdWaiting = new HashSet<>();
    private boolean holdReadySent;
    private long holdBufferSeenMs;
    private long holdBufferAt;
    private final Runnable holdExpiry = this::holdTimedOut;

    private Room room;
    private Relay relay;
    /** Answers "who" in the shared lobby for as long as the room lasts, so it can be found without
     *  the code being passed around by hand. */
    private final Lobby.Publisher publisher = new Lobby.Publisher(this::ad);
    private String nick = "";
    /** What the room calls itself, as the plugin shows it beside the code. Named on creation, and
     *  learned from the owner's snapshot when joining. */
    private String roomName = "";
    /** What the room plays. Replaced only by the owner, and only through a media-change frame. */
    private JSONObject session;
    /** Whose word is final on what the room plays. Ours when we created it. */
    private String owner;

    private boolean joining;
    private boolean mediaApplied;
    private boolean running;
    private boolean connected;
    /** Set once the channel has answered, and cleared only by entering another room. */
    private boolean everConnected;
    private int peers = 1;
    private String waitingFor;
    private long bufferingSince;

    public TogetherManager(final Host host) {
        this.host = host;
    }

    /**
     * Create a room around what this screen is playing. We become its owner, which is what lets a
     * Lampa peer join at all — their client waits for a snapshot from the owner and leaves if none
     * arrives.
     */
    public void create(final String code, final String password, final boolean listed,
                       final String roomName, final JSONObject session, final String nick) {
        enter(code, password, nick);
        this.roomName = roomName;
        this.session = session;
        assignOwner(PID);
        this.mediaApplied = true;
        // Only a room of our own can be listed, and only if asked: joining never advertises, and a
        // private room never opens the lobby socket at all rather than sitting in it saying nothing.
        if (listed) {
            publisher.start();
        }
        host.onRoomChanged();
    }

    /**
     * Enter someone else's room. What to play arrives with the owner's snapshot.
     *
     * @param password the room's password, or empty. A wrong one is indistinguishable from a room
     *                 that does not exist, because it lands us in a different channel.
     */
    public void join(final String code, final String password, final String nick) {
        enter(code, password, nick);
        this.joining = true;
        host.onRoomChanged();
    }

    private void enter(final String code, final String password, final String nick) {
        leave();
        this.room = new Room(code, password);
        this.nick = nick;
        this.roomName = "";
        this.session = null;
        assignOwner(null);
        this.joining = false;
        this.mediaApplied = false;
        this.peers = 1;
        this.waitingFor = null;
        this.connected = false;
        this.everConnected = false;
        this.bufferingSince = 0;
        // The nick goes to the relay as well as into our frames: the other client names a departure
        // from the relay's own leave event (its bye frame carries nothing), so without this it can only
        // say that somebody left.
        this.relay = new Relay(room.channel(), nick, this);
        engine = new SyncEngine();
        engine.bind(PID, nick);
        relay.open();
        running = true;
        handler.postDelayed(ticker, SyncEngine.TICK_MS);
    }

    public void leave() {
        // Hand the player back the speed the viewer chose. While a gap is being closed the player is a
        // few percent off that, and leaving the room mid-correction used to leave the trim behind — where
        // the speed menu shows it and savePlayer writes it down as a preference.
        if (running && engine.trimmed()) {
            host.applySpeed(engine.userSpeed());
        }
        running = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(joinTimeout);
        publisher.stop();
        if (relay != null) {
            relay.send(LpartyCodec.bye(PID, nick));
            relay.close();
            relay = null;
        }
        room = null;
        session = null;
        assignOwner(null);
        joining = false;
        connected = false;
        waitingFor = null;
        nicks.clear();
        pidByUid.clear();
        helloSeenAt.clear();
        clearHold();
        handler.removeCallbacks(ownerElection);
        host.onRoomChanged();
    }

    /** Stop sampling while the screen is away; the socket stays up so the room stays whole. */
    public void suspend() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    /** Sample again, re-anchoring first: the gap was not something the viewer did. */
    public void resume() {
        if (room == null || running) {
            return;
        }
        engine.reprime();
        // And stay quiet while the screen comes back, which re-anchoring alone did not do. Restoring
        // playback flips the intent from stopped to playing, and one tick later that read as the viewer
        // pressing play: it went out as a command carrying the position we had been left behind at, and
        // it made us the room's reference — after which the room's own reports were ignored as coming
        // from somebody who had not acted, and we stayed behind until a real press by somebody else
        // handed the reference back. Silence puts the drift correction in charge instead, which is what
        // catches us up; the window closes itself early the moment we are in step.
        engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
        running = true;
        handler.postDelayed(ticker, SyncEngine.TICK_MS);
    }

    public boolean isActive() {
        return room != null;
    }

    public String code() {
        return room == null ? null : room.code();
    }

    /** The room's name, or empty when nobody has said one. */
    public String roomName() {
        return roomName == null ? "" : roomName;
    }

    /** The media the room is on, for the screen to check it is still playing the right thing. */
    public String url() {
        return session == null ? null : session.optString("uri", null);
    }

    /** The shareable {@code lparty://...} link, or null when not in a room. */
    public String invite() {
        return room == null ? null : room.invite();
    }

    public int peers() {
        return peers;
    }

    /** Every change of owner goes through here: the engine needs to know too, because the owner is also
     *  whose position the room takes on trust. Six separate assignments were six chances to forget. */
    private void assignOwner(final String ownerPid) {
        this.owner = ownerPid;
        engine.setOwner(ownerPid);
    }

    /** True when what the room plays is ours to change. */
    public boolean isOwner() {
        return PID.equals(owner);
    }

    /** The speed the viewer chose, which is not the one on the player while a gap is being closed. */
    public float userSpeed() {
        return engine.userSpeed();
    }

    public boolean connected() {
        return connected;
    }

    /** Whether this room has ever been reached — what separates "connecting" from "reconnecting". */
    public boolean everConnected() {
        return everConnected;
    }

    public String waitingFor() {
        return waitingFor;
    }

    /** Where the room is, for a player about to be built to start there rather than land somewhere else
     *  and be pulled across. -1 while the room has not said. */
    public long roomPositionMs() {
        return engine.roomPositionMs(SystemClock.elapsedRealtime());
    }

    /** Tell the room it is watching something else now. Only the owner may. */
    public void changeMedia(final JSONObject session) {
        if (relay == null || !PID.equals(owner) || session == null) {
            return;
        }
        this.session = session;
        relay.send(LpartyCodec.url(PID, session.optString("uri", null), cardTitle()));
        relay.send(LpartyCodec.session(PID, session));
        // Confirmed by us saying so: what the room plays is the owner's word, and this is it.
        stepped(true);
    }

    /**
     * Our own playlist has stepped ahead of the room's word for what it plays — a member reaching the end
     * of an episode before the owner announces the next one, which at a boundary is a coin toss.
     *
     * @param session what we are playing now, so a room that later falls to us is described by the episode
     *                we are on rather than the one we left
     */
    public void mediaStepped(final JSONObject session) {
        if (session != null) {
            this.session = session;
        }
        stepped(false);
    }

    /**
     * Our own playback has moved to other media. Only the owner announces that; everybody has to stop
     * describing the old file, which is what this does.
     *
     * <p>The player is about to be somewhere else entirely. Without the reprime that move was diffed as
     * a seek, and every episode change popped "somebody jumped" on every screen; without forgetting the
     * room's position, it was corrected back to where the previous episode ended.
     */
    private void stepped(final boolean confirmed) {
        final long now = SystemClock.elapsedRealtime();
        engine.mediaChanged(confirmed, now);
        engine.reprime();
        engine.settle(now + SyncEngine.SETTLE_MS);
    }

    /**
     * What we advertise in the lobby, or null while there is nothing worth advertising. Only the
     * owner answers, as in their client: a room described by each of its members in turn is the same
     * room listed several times over, and several times the traffic for it.
     */
    private JSONObject ad() {
        final JSONObject card = room == null || !isOwner() ? null : host.roomCard();
        if (card == null || card.optString("url", "").isEmpty()) {
            return null;
        }
        try {
            return new JSONObject()
                    .put("id", room.code())
                    .put("name", roomName().isEmpty() ? room.code() : roomName())
                    .put("title", card.optString("title", ""))
                    .put("poster", card.optString("poster", ""))
                    .put("owner", nick)
                    .put("members", peers)
                    .put("pwd", room.hasPassword() ? 1 : 0)
                    .put("tmdb", card.optInt("tmdb"))
                    .put("type", card.optString("type", "movie"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Rooms the lobby knew about, best-attended first. */
    public interface Rooms {
        void onRooms(java.util.List<JSONObject> rooms);
    }

    /** Ask the lobby which rooms are open right now. The answer arrives on the main thread. */
    public static void discover(final Rooms callback) {
        Lobby.discover(new Handler(Looper.getMainLooper()), callback::onRooms);
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            tick();
            handler.postDelayed(this, SyncEngine.TICK_MS);
        }
    };

    private final Runnable joinTimeout = new Runnable() {
        @Override
        public void run() {
            if (!joining) {
                return;
            }
            joining = false;
            leave();
            host.onJoinFailed();
        }
    };

    private void tick() {
        final long now = SystemClock.elapsedRealtime();

        if (relay == null || joining || !host.ready()) {
            // Nothing to sample. Whatever happens while the player is gone is not the viewer's
            // doing, so the baseline is dropped rather than diffed against on the way back — and
            // the first seconds of the player that replaces it stay silent, because a player
            // starting up sets playWhenReady itself and would be heard as somebody pressing play.
            engine.reprime();
            engine.settle(now + SyncEngine.SETTLE_MS);
            // Who the room was waiting for is only ever recomputed inside a tick, so a wait that was
            // current when the media went away (a fatal error, an empty state) was never cleared and the
            // float sat over the error screen until the room was left.
            if (waitingFor != null) {
                waitingFor = null;
                host.onRoomChanged();
            }
            return;
        }
        // While the room is held, playback belongs to the hold: the pause is not the viewer's, and the
        // correction would fight the position everybody is filling a buffer at. Same silent early-out as
        // buffering, plus the housekeeping that answers the hold or lifts it.
        if (holdActive) {
            engine.reprime();
            engine.settle(now + SyncEngine.SETTLE_MS);
            if (waitingFor != null) {
                waitingFor = null;
                host.onRoomChanged();
            }
            holdTick();
            return;
        }
        // Mid-gesture the position is noise; note that this does NOT re-anchor, so the settled
        // position the viewer landed on is picked up as their action on the very next tick.
        if (host.scrubbing()) {
            return;
        }

        updateBuffering(now);

        // A refilling player is not a reason to stop listening to the viewer. It used to be: this
        // re-anchored and fell silent, on the grounds that a buffering position means nothing. That is
        // true of a player still loading its first frames — and false of the far more common case, a
        // seek, which is *how* buffering starts. Re-anchoring erased the very jump the room needed to
        // hear, the silence made sure it was never sent, and the incoming reports kept arriving — so the
        // viewer's seek was undone by the room a moment later, every time, and nothing they did to the
        // timebar ever reached anybody. The load case is already covered twice over: by the ready()
        // check above, and by the silent window applyMedia opens.
        //
        // What buffering does mean is that this is no moment to jump anywhere ourselves, which is the
        // engine's business and is told to it here.
        engine.setBuffering(host.buffering());
        engine.setEnded(host.ended());
        engine.setDuration(host.durationMs());

        final SyncEngine.Action action =
                engine.tick(now, host.positionMs(), host.playWhenReady(), host.speed());


        for (JSONObject frame : action.out) {
            relay.send(frame);
        }
        if (action.seekToMs >= 0) {
            host.applySeek(action.seekToMs);
        }
        if (action.play != null) {
            host.applyPlay(action.play);
        }
        if (action.speed != null) {
            host.applySpeed(action.speed);
        }
        if (!same(waitingFor, action.waitingFor)) {
            waitingFor = action.waitingFor;
            host.onRoomChanged();
        }
    }

    private void updateBuffering(final long now) {
        if (host.buffering()) {
            if (bufferingSince == 0) {
                bufferingSince = now;
            } else if (now - bufferingSince > BUFFER_ARM_MS) {
                send(engine.localBuffering(true, now));
            }
        } else {
            bufferingSince = 0;
            send(engine.localBuffering(false, now));
        }
    }

    private void send(final JSONObject frame) {
        if (frame != null && relay != null) {
            relay.send(frame);
        }
    }

    /** Answer a newcomer. Only the owner does this, and their client accepts it only from the
     *  owner — the rich payload goes first so our own players prefer it over the thin card. */
    private void answerNewcomer() {
        if (!PID.equals(owner)) {
            return;
        }
        send(LpartyCodec.session(PID, freshSession()));
        final JSONObject card = host.roomCard();
        if (card == null) {
            return;
        }
        send(LpartyCodec.state(PID, roomName(), owner,
                card.optString("url", null), card.optString("title", ""),
                card.optString("poster", ""), card.optInt("tmdb"),
                card.optString("source", ""), card.optString("type", "movie"),
                // Not straight from the player: it reports zero while it is being rebuilt — a quality
                // switch, a return from settings, any of the error-recovery paths — and a snapshot is the
                // one frame a joining member believes without question. The last position actually seen
                // on it is right in every one of those windows and identical outside them.
                engine.playing(), host.ready() ? host.positionMs() : engine.position()));
    }

    // --- The join hold -------------------------------------------------------------------------

    /** True while the room is stopped to let everybody fill a buffer, which is not a pause anybody asked
     *  for and so is neither broadcast nor undone by the engine. */
    public boolean holding() {
        return holdActive;
    }

    /**
     * Stop the room where it is and wait for {@code newcomer} — and for our own buffer — before going on.
     * The owner's job: their client only accepts a hold from the owner, and a room with two people calling
     * holds would stop and start at cross purposes.
     */
    private void startHold(final String newcomer) {
        if (relay == null || !PID.equals(owner) || !host.ready()) {
            return;
        }
        if (!holdActive) {
            holdActive = true;
            holdOurs = true;
            holdWasPlaying = host.playWhenReady();
            holdPositionMs = host.positionMs();
            holdWaiting.clear();
            holdReadySent = false;
            resetHoldProgress();
            host.applyPlay(false);
            host.onRoomChanged();
        }
        if (newcomer != null && !newcomer.isEmpty() && !newcomer.equals(PID)) {
            holdWaiting.add(newcomer);
        }
        send(LpartyCodec.hold(PID, holdPositionMs));
        handler.removeCallbacks(holdExpiry);
        handler.postDelayed(holdExpiry, HOLD_MAX_MS);
    }

    /** Somebody else's room stopped for a newcomer: stand where they say and start filling. */
    private void applyHold(final long positionMs) {
        if (!holdActive) {
            holdActive = true;
            holdOurs = false;
            holdWasPlaying = host.playWhenReady();
        }
        holdPositionMs = positionMs;
        holdReadySent = false;
        resetHoldProgress();
        host.applyPlay(false);
        // A second off is not worth a jump: the buffer we are about to fill is the one at our own
        // position, and it would be thrown away for nothing.
        if (Math.abs(host.positionMs() - positionMs) > 1_000) {
            host.applySeek(positionMs);
        }
        host.onRoomChanged();
        // Their client gives a member no timeout at all, so an owner that dies mid-hold leaves everybody
        // standing for ever. Ours lets go by itself, a little after the owner's own deadline would have.
        handler.removeCallbacks(holdExpiry);
        handler.postDelayed(holdExpiry, HOLD_MAX_MS + OWNER_ELECTION_MS * 4);
    }

    /** A member says its buffer is full enough. Only the owner is counting. */
    private void markReady(final String memberPid) {
        if (!holdActive || !holdOurs) {
            return;
        }
        holdWaiting.remove(memberPid);
        checkHoldDone();
    }

    /** Lift the hold once nobody is outstanding and our own buffer is there too. */
    private void checkHoldDone() {
        if (!holdActive || !holdOurs || !holdWaiting.isEmpty() || !bufferReady()) {
            return;
        }
        send(LpartyCodec.go(PID, holdPositionMs, holdWasPlaying));
        releaseHold(holdPositionMs, holdWasPlaying);
    }

    /** The deadline: go on with whoever is ready. Also the member-side escape from a dead owner. */
    private void holdTimedOut() {
        if (!holdActive) {
            return;
        }
        if (holdOurs) {
            send(LpartyCodec.go(PID, holdPositionMs, holdWasPlaying));
        }
        // Nobody is going to say what to come out doing, so the state the hold began in is the best there
        // is: for a member of a room that was running, that is playing, and the next report corrects it.
        releaseHold(holdPositionMs, holdWasPlaying);
    }

    private void releaseHold(final long positionMs, final boolean play) {
        if (!holdActive) {
            return;
        }
        final boolean wasOurs = holdOurs;
        holdActive = false;
        holdOurs = false;
        holdWaiting.clear();
        holdReadySent = false;
        handler.removeCallbacks(holdExpiry);
        if (Math.abs(host.positionMs() - positionMs) > 500) {
            host.applySeek(positionMs);
        }
        // Everybody starts again at once, on the owner's word rather than on their own reading of the
        // room. A member used to restore nothing and wait for the next report to tell it what the room
        // was doing — but nobody speaks while a hold is up, and the owner falls silent for another three
        // seconds after lifting one, so the room ran on without whoever had just joined it. By the time a
        // heartbeat arrived they were seconds behind, and catching up meant a jump: a second load of the
        // buffer the hold had just spent all that time filling.
        //
        // What to restore is the owner's, not ours: it is the only member whose player says what the room
        // means, which is what keeps a room somebody had deliberately paused from starting itself because
        // a newcomer arrived (their client always resumes here, and does start it).
        host.applyPlay(play);
        // And the same state to the engine, for a member: its idea of where the room is dates from before
        // the hold and has been carrying itself forward at playing speed throughout — so the first tick
        // after this either reads a standstill of several seconds as drift and jumps it, or, if the
        // snapshot caught the owner already paused for the hold, pauses us straight back again.
        if (!wasOurs) {
            engine.seed(positionMs, play, SystemClock.elapsedRealtime());
        }
        // None of the above was the viewer's doing, and the engine has not sampled the player since the
        // hold began — so re-anchor and stay quiet, or the restored play state goes out as a decision.
        engine.reprime();
        engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
        host.onHoldLifted();
        host.onRoomChanged();
    }

    /** Housekeeping on the ticker while a hold is up: the owner watches for everyone, a member answers. */
    private void holdTick() {
        if (holdOurs) {
            checkHoldDone();
            return;
        }
        if (!holdReadySent && bufferReady()) {
            holdReadySent = true;
            send(LpartyCodec.ready(PID));
        }
    }

    /**
     * Enough buffered at the hold position to go on. Their reading of it, minus the part that only makes
     * sense for an HTML video element: six seconds ahead, or a full buffer, or — because a line that will
     * never reach six seconds should not hold everybody to the timeout — anything above two seconds that
     * has plainly stopped growing.
     *
     * <p>ponytail: no equivalent of their loader nudge (a hairline seek to restart a stalled fill). Media3
     * refills on its own; if a stalled fill ever turns out to need poking, that is where it goes.
     */
    private boolean bufferReady() {
        final long ahead = host.bufferedAheadMs();
        final long now = SystemClock.elapsedRealtime();
        if (ahead > holdBufferSeenMs + 250) {
            holdBufferSeenMs = ahead;
            holdBufferAt = now;
        }
        if (ahead >= HOLD_BUFFER_MS) {
            return true;
        }
        return ahead >= HOLD_MIN_BUFFER_MS && now - holdBufferAt > HOLD_STALL_MS;
    }

    private void resetHoldProgress() {
        holdBufferSeenMs = -1;
        holdBufferAt = SystemClock.elapsedRealtime();
    }

    private void clearHold() {
        holdActive = false;
        holdOurs = false;
        holdWasPlaying = false;
        holdWaiting.clear();
        holdReadySent = false;
        handler.removeCallbacks(holdExpiry);
    }

    /**
     * The session to announce. Re-read from the screen when it still points at the room's media, so
     * a member arriving an hour in gets a start position near where everyone actually is rather than
     * where the room began — but never re-read across a change of media.
     */
    private JSONObject freshSession() {
        if (session == null) {
            return null;
        }
        final JSONObject fresh = host.sessionDescription();
        if (fresh != null && session.optString("uri", "").equals(fresh.optString("uri", null))) {
            session = fresh;
        }
        return session;
    }

    private String cardTitle() {
        final JSONObject card = host.roomCard();
        return card == null ? "" : card.optString("title", "");
    }

    /** Take on the room's media, from either shape, the first time we learn it. */
    private void applyMedia(final JSONObject candidate) {
        if (candidate == null) {
            return;
        }
        session = candidate;
        if (joining) {
            joining = false;
            handler.removeCallbacks(joinTimeout);
        }
        if (!mediaApplied) {
            mediaApplied = true;
            // Opening media builds a player synchronously, so no tick may ever observe the old one
            // gone — the silent window has to be started from here or it never opens on the very
            // path that needs it most.
            engine.settle(SystemClock.elapsedRealtime() + SyncEngine.SETTLE_MS);
            host.openSession(candidate);
        }
        host.onRoomChanged();
    }

    /** All a Lampa peer ever tells us about media: an address, something to call it, and — from a room
     *  snapshot, though not from a bare media change — a poster. */
    private static JSONObject thinSession(final String url, final String title, final String poster) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            final JSONObject session = new JSONObject().put("uri", url);
            if (title != null && !title.isEmpty()) {
                session.put("title", title);
            }
            if (poster != null && !poster.isEmpty()) {
                session.put("poster", poster);
            }
            return session;
        } catch (Exception e) {
            return null;
        }
    }

    // --- Relay.Listener: network thread, so everything hops to the main thread first. ---

    @Override
    public void onFrame(final JSONObject frame) {
        onFrame(frame, "");
    }

    @Override
    public void onFrame(final JSONObject frame, final String senderUid) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            final String type = LpartyCodec.type(frame);
            final String framePid = LpartyCodec.pid(frame);

            // Remember what each id calls itself, from whichever frame says so first — a greeting, an
            // action, a stall. A departure is the one event that has to be named after the member is
            // already gone, and a Lampa peer's own goodbye carries an id and nothing else.
            final String frameNick = LpartyCodec.nick(frame);
            if (!frameNick.isEmpty()) {
                nicks.put(framePid, frameNick);
            }
            // Which relay id belongs to which member. The relay names a departure by its own id only,
            // and whether that was the owner is the question the whole handover turns on.
            if (!senderUid.isEmpty() && !framePid.isEmpty()) {
                pidByUid.put(senderUid, framePid);
            }

            if (LpartyCodec.T_HELLO.equals(type)) {
                relay.send(LpartyCodec.me(PID, nick));
                answerNewcomer();
                final Long seen = helloSeenAt.put(framePid, SystemClock.elapsedRealtime());
                final boolean reconnected = seen != null
                        && SystemClock.elapsedRealtime() - seen < RECONNECT_HELLO_MS;
                if (!reconnected) {
                    startHold(framePid);
                }
                return;
            }
            if (LpartyCodec.T_HOLD.equals(type)) {
                // The owner's to call, nobody else's — otherwise any member could stop the room. Accepted
                // while the owner is still unknown, exactly as theirs is: their client calls the hold
                // before it answers with the snapshot that names the owner, so on the very join this is
                // for, the sender cannot be checked yet and refusing it would miss every one.
                if (owner == null || framePid.equals(owner)) {
                    applyHold(LpartyCodec.positionMs(frame));
                }
                return;
            }
            if (LpartyCodec.T_READY.equals(type)) {
                markReady(framePid);
                return;
            }
            if (LpartyCodec.T_GO.equals(type)) {
                if (owner == null || framePid.equals(owner)) {
                    releaseHold(LpartyCodec.positionMs(frame), LpartyCodec.goPlaying(frame));
                }
                return;
            }
            if (LpartyCodec.T_SESSION.equals(type)) {
                applyMedia(LpartyCodec.sessionOf(frame));
                return;
            }
            if (LpartyCodec.T_STATE.equals(type)) {
                if (owner == null) {
                    assignOwner(LpartyCodec.stateOwner(frame));
                }
                if (roomName.isEmpty()) {
                    roomName = LpartyCodec.stateRoomName(frame);
                }
                // The snapshot carries where the room is, which is worth having before the first
                // heartbeat: the player then loads straight into position instead of starting at
                // zero and being pulled forward a moment later.
                //
                // Only while joining, exactly as their client gates it. The relay fans every frame to
                // the whole channel, so an owner answering one newcomer re-anchored everybody — and it
                // bypassed the (seq, pid) ordering entirely, because a snapshot carries no counter. One
                // member's connection hiccuping was enough to throw the room back to wherever the owner's
                // player happened to be, which during a rebuild is zero.
                if (joining) {
                    engine.seed(LpartyCodec.positionMs(frame), LpartyCodec.playing(frame),
                            SystemClock.elapsedRealtime());
                }
                // Only as a fallback: a snapshot from a Lampa peer carries a bare URL, so it must
                // not overwrite the full launch payload one of our own players may have sent.
                if (!mediaApplied) {
                    applyMedia(thinSession(LpartyCodec.stateUrl(frame),
                            LpartyCodec.stateTitle(frame), LpartyCodec.statePoster(frame)));
                }
                return;
            }
            if (LpartyCodec.T_URL.equals(type)) {
                // Their contract: what the room plays is the owner's to change, nobody else's.
                if (framePid == null || !framePid.equals(owner)) {
                    return;
                }
                final JSONObject candidate = thinSession(LpartyCodec.stateUrl(frame),
                        LpartyCodec.stateTitle(frame), null);
                if (candidate == null) {
                    return;
                }
                // Nothing to open when it names the episode we are already on. A member with the room's
                // playlist steps through it on its own, and usually before this frame — which has a relay
                // hop to make — so all the owner is doing here is agreeing: applyMedia takes the title and
                // leaves the player alone, and the agreement is what lets us follow the room's positions
                // again. Reopening would restart what is playing.
                if (candidate.optString("uri", "").equals(host.playingUri())) {
                    engine.mediaConfirmed();
                } else {
                    stepped(true);
                    mediaApplied = false;
                }
                applyMedia(candidate);
                return;
            }
            if (LpartyCodec.T_HOST.equals(type)) {
                assignOwner(framePid);
                host.onRoomChanged();
                return;
            }
            if (LpartyCodec.T_BYE.equals(type) || LpartyCodec.T_ME.equals(type)) {
                // Nothing to do but learn the name, which happened above. The departure itself is
                // announced from the relay's own leave event, which every exit produces — this frame
                // is only sent on a deliberate one, and the other client sends none at all when its
                // player simply closes.
                return;
            }
            // Nothing comes back to its own sender on this relay, so an action heard here is always
            // somebody else's — and the engine's answer is what the room actually made of it.
            final Act act = engine.onFrame(frame, SystemClock.elapsedRealtime());
            if (act != null) {
                host.onRoomAction(LpartyCodec.nick(frame), act);
            }
        });
    }

    @Override
    public void onReady(final int total) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            peers = total;
            connected = true;
            everConnected = true;
            if (joining) {
                if (total <= 1) {
                    // Nobody is on the channel, so there is no room behind this code. Saying so
                    // beats sitting silently on an empty socket.
                    joining = false;
                    leave();
                    host.onJoinFailed();
                    return;
                }
                relay.send(LpartyCodec.hello(PID, nick));
                handler.removeCallbacks(joinTimeout);
                handler.postDelayed(joinTimeout, JOIN_TIMEOUT_MS);
            } else {
                // Back on the channel after a drop, which is a new socket and a new relay id for every
                // member — so introduce ourselves again exactly as their client does. Everyone answers
                // with their own id, which is what refills the relay-id → member map that a handover
                // reads; without it an owner leaving shortly after any hiccup went unnoticed and the
                // room was left with nobody able to answer a newcomer. An owner also re-offers its
                // snapshot, for anyone who was trying to get in while we were away.
                relay.send(LpartyCodec.hello(PID, nick));
                answerNewcomer();
            }
            host.onRoomChanged();
        });
    }

    @Override
    public void onPeers(final int total) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            peers = total;
            host.onRoomChanged();
        });
    }

    @Override
    public void onLeave(final String uid, final String alias, final int total) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            peers = total;
            final String pid = pidByUid.remove(uid);
            engine.forget(pid);
            // Somebody we were holding the room for is gone and will never say they are ready — stop
            // counting them, or the room waits out the whole timeout for nobody.
            if (pid != null) {
                helloSeenAt.remove(pid);
                markReady(pid);
            }
            // The relay's own name for them first: it is the one that arrives on every exit, including
            // the other client's silent one. What they called themselves in a frame is the fallback,
            // for a member who was on the channel before we ever heard a word from them.
            final String who = alias != null && !alias.isEmpty() ? alias : nicks.remove(pid);
            host.onRoomAction(who, Act.LEFT);
            host.onRoomChanged();
            // The owner leaving is the one departure the room has to do something about. Their client
            // waits half a second before deciding, and so do we: the check has to run after everyone's
            // roster has caught up, or two members read a stale list and both claim the room.
            if (pid != null && pid.equals(owner)) {
                handler.removeCallbacks(ownerElection);
                handler.postDelayed(ownerElection, OWNER_ELECTION_MS);
            }
        });
    }

    /**
     * Take a vacated room over, or leave it to whoever the same arithmetic picks elsewhere: the
     * remaining relay ids in ascending order, and the lowest one is the new owner. Their client's rule,
     * kept byte for byte — with our own we would either both claim the room or neither would, and a
     * room with no owner answers no newcomer and can never change what it plays.
     *
     * <p>Deliberately not the application id, tempting as it is: a mixed room only agrees if both ends
     * sort the same list, and the other client sorts the relay's.
     */
    private void electOwner() {
        // Not while still waiting to be let in: an owner is what answers a newcomer with a snapshot, and
        // we have nothing to answer with. That room is already lost to us and joinTimeout says so.
        if (relay == null || room == null || joining || ownerPresent()) {
            return;
        }
        final List<String> uids = relay.memberUids();
        if (uids.isEmpty() || !uids.get(0).equals(relay.uid())) {
            return;
        }
        assignOwner(PID);
        relay.send(LpartyCodec.host(PID, nick));
        // Not the lobby, though their client re-advertises here: listing a room is this device's own
        // choice (togetherPublic, off by default) and inheriting the role is not a decision to publish.
        host.onRoomChanged();
    }

    /** Whether whoever owns the room is still on the channel. */
    private boolean ownerPresent() {
        if (owner == null) {
            return false;
        }
        if (PID.equals(owner)) {
            return true;
        }
        for (String uid : relay.memberUids()) {
            if (owner.equals(pidByUid.get(uid))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onConnected(final boolean up) {
        handler.post(() -> {
            if (relay == null) {
                return;
            }
            connected = up;
            if (up) {
                everConnected = true;
            }
            if (!up) {
                // Every member gets a new relay id on the next socket, so the map from those ids to
                // members is worthless the moment this one dies — and keeping it was worse than losing
                // it: a handover read stale entries and concluded the owner was still there.
                pidByUid.clear();
            }
            host.onRoomChanged();
        });
    }

    /** Eight lowercase hex characters, the shape the other client uses for its own id. */
    private static String newPid() {
        final byte[] bytes = new byte[4];
        new SecureRandom().nextBytes(bytes);
        final StringBuilder sb = new StringBuilder(8);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean same(final String a, final String b) {
        return a == null ? b == null : a.equals(b);
    }
}
