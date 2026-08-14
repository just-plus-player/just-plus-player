package com.brouken.player.together;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps one player in step with the rest of the room. Pure logic — no Android, no player, no
 * network: it is fed the local playback state on a tick and answers with what to do about it.
 *
 * <p><b>Outgoing state is diffed, not intercepted.</b> Two thirds of the calls to play/pause/seekTo
 * in the player are internal machinery (the pause on the way to the background, the pause while the
 * timebar is scrubbed, the restore after a rebuild, six error-recovery paths), and Media3 reports
 * them with the same "user request" reason as a real tap. So instead of instrumenting call sites,
 * each tick extrapolates where playback should have got to on its own and treats any discrepancy as
 * something the viewer did. A swipe-seek that fires a seek per scroll step, a double tap, a D-pad
 * scrub and a PiP button all arrive here as one position change.
 *
 * <p><b>There is no feedback loop to guard against.</b> Having applied a decision, the engine
 * records it as the new baseline, so the change it just caused is never mistaken for a local
 * action. That removes the suppress counters — and their expiry timers, and both of their failure
 * modes — that this kind of code usually grows.
 *
 * <p><b>Everyone is equal.</b> Commands carry a logical counter and the sender's id; a frame is
 * applied only if it beats the last one applied on {@code (seq, uid)}. Two people seeking at the
 * same moment converge on the higher id in one exchange, with no host role to lose or hand over.
 *
 * <p>Not thread-safe by design: every entry point is called from the one thread that owns the
 * player.
 */
public final class SyncEngine {

    /** How often the host samples the player. Fine enough to coalesce a swipe, cheap enough to run
     *  for a whole film. */
    public static final long TICK_MS = 250;
    /** After this long the room stops waiting for whoever is buffering and plays on. */
    public static final long BUFFER_WAIT_MAX_MS = 15_000;

    /** Position broadcast while nothing else is happening — also what keeps the socket warm. */
    private static final long HEARTBEAT_MS = 3_000;
    /** Position discrepancy that means the viewer did something rather than time passing. */
    private static final long LOCAL_ACTION_MS = 700;
    /** Below this, being out of step is not worth touching playback for. */
    private static final long DRIFT_DEADBAND_MS = 300;
    /** Above this, nudging the rate cannot close the gap in reasonable time — jump instead. */
    private static final long DRIFT_HARD_SEEK_MS = 1_500;
    /** Rate trim per second of drift. Proportional rather than a flat nudge: a flat 5% closes a
     *  1.4s gap in nearly half a minute, which reads as "not syncing" rather than as catching up.
     *  Media3 keeps pitch across a rate change, so none of this is audible. */
    private static final float CORRECTION_GAIN = 0.1f;
    /** Ceiling on that trim, so a wild target cannot run playback away. */
    private static final float CORRECTION_MAX = 0.1f;
    /** With nothing heard from the room for this long, stop correcting: the target would otherwise
     *  keep extrapolating forward for ever and we would chase a position nobody is at. */
    private static final long TARGET_STALE_MS = 15_000;
    private static final float SPEED_EPSILON = 0.001f;
    /** How long after the player is (re)built nothing is said out loud. Their client calls the same
     *  thing an initial sync lock and gives it the same three seconds. */
    public static final long SETTLE_MS = 3_000;
    /** Floor between two hard seeks. Theirs, added in 1.3.0 for the same reason: on a line that cannot
     *  hold the pace, every tick finds the same gap and asks for the same jump — four times a second,
     *  each one restarting the buffer that made the gap. */
    private static final long HARD_SEEK_COOLDOWN_MS = 3_000;
    /** How long our own command outranks anything the room says back. Theirs, and for the same reason:
     *  the room needs a moment to hear a seek and act on it, and every report already in flight still
     *  describes the position we just left. Applying those undoes the seek — and the undo is a position
     *  change like any other, so it goes back out as a fresh command and takes everybody with it. */
    private static final long LOCAL_COMMAND_MS = 2_000;
    /** A move in the room's own position that neither drift nor a stall can account for, and so is
     *  somebody seeking — whether or not the frame carrying it says a word about it. Their client
     *  announces at most one seek every two seconds and swallows the rest, so a viewer scrubbing about
     *  moves the whole room in silence; all that arrives is a heartbeat from nowhere near where the room
     *  should be by now. Well above the hard-seek threshold on purpose: this is not a correction, it is
     *  what to tell the viewer about.
     *  ponytail: a stall of more than this on a peer that reports none — which is every Lampa peer — reads
     *  as a jump as well. The position is the only thing such a peer gives us to read. */
    private static final long JUMP_NOTICE_MS = 5_000;
    /** Ceiling on how far past the room a jump may aim. Beyond this the line is not going to hold the
     *  pace anyway, and leading further would only overshoot into a second correction. */
    private static final long SEEK_LEAD_MAX_MS = 3_000;
    /** How far apart two reports must be before the distance between them says anything about whether
     *  their sender is moving. Under one heartbeat of theirs. */
    private static final long REPORT_GAP_MS = 1_500;
    /** How near our own duration a reported position counts as that sender's file running out rather
     *  than a place in it. Their end-of-episode pause carries the duration to the millisecond. */
    private static final long END_SLACK_MS = 1_000;
    /** Longest a step of our own waits for the room to say it is on the same media. Past this the room is
     *  plainly not following, and its positions are the best — the only — thing left to go on. */
    private static final long MEDIA_CONFIRM_MS = 10_000;

    /** What a tick decided. The host applies it verbatim and does nothing else. */
    public static final class Action {
        /** Frames to broadcast; usually none or one. */
        public final List<JSONObject> out = new ArrayList<>(2);
        /** Seek target, or -1 for none. */
        public long seekToMs = -1;
        /** Playback intent to set, or null to leave it alone. */
        public Boolean play;
        /** Playback speed to set, or null to leave it alone. */
        public Float speed;
        /** Who the room is waiting for, or null when it is waiting for nobody. */
        public String waitingFor;
    }

    private String pid = "";
    private String nick = "";
    /** Whose heartbeats the room follows. Their 1.3.0 made this the owner rather than whoever acted
     *  last: with several people watching, "last to act" points somewhere different on each client, so
     *  two halves of a mixed room chase two different positions and the gap between them only grows. */
    private String ownerPid = "";

    private long seq;
    private long appliedSeq = -1;
    /** Whose report the room is currently following. Heartbeats are taken from this member only. */
    private String appliedPid = "";

    // The baseline: what we last left on the player, and when.
    private boolean primed;
    private long lastPosMs;
    private boolean lastPlay;
    private float lastSpeed = 1f;
    private long lastAt;

    // The room's agreed state, carried forward between frames by local elapsed time. No wall clock
    // is involved anywhere, so two devices whose clocks disagree still agree on this.
    private boolean targetKnown;
    private long lastFrameAt;
    private long targetPosMs;
    private boolean targetPlay;
    private float targetSpeed = 1f;
    private long targetAt;

    /** The speed the viewer picked, which is what the room syncs. The drift correction multiplies
     *  it and never replaces it, so nudging never leaks into what other people see. */
    private float userSpeed = 1f;
    private long lastSentAt;
    /** Until this moment we follow the room but say nothing. A player that is starting up moves on
     *  its own — it reports position zero and then flips itself from paused to playing — and taking
     *  either for a decision by the viewer makes the whole room jump to the beginning. */
    private long settleUntil;

    /** The room's playback intent, which is not the same as the player's state: while the room holds
     *  for someone who is buffering, playback is stopped but nobody has asked for a pause. Only this
     *  is ever broadcast — putting the held pause on the wire would make one member's stall look like
     *  a decision, and the room would talk itself into staying stopped. */
    private boolean intentPlay;

    /** Someone else's stall, with its own deadline. Per-entry rather than one shared timer: a
     *  member who drops off mid-stall never sends the frame that clears it, and a stale entry left
     *  behind would otherwise eat everybody's next wait. */
    private static final class Stall {
        final String nick;
        final long at;

        Stall(final String nick, final long at) {
            this.nick = nick;
            this.at = at;
        }
    }

    private final Map<String, Stall> stalls = new LinkedHashMap<>();
    /** What the room has been told about our own stall — throttled, and not the same thing as whether we
     *  are stalling right now. */
    private boolean localBuffering;
    /** Whether the player is refilling at this very moment. Only stops us correcting: a seek is how
     *  buffering usually starts, so the position that comes with it is the viewer's and has to be heard.
     *  Set every tick, unlike the reported stall above. */
    private boolean buffering;
    /** Whether our own media has run out. Nothing about that is a decision by the viewer, so while it
     *  lasts we follow the room and say nothing at all — see {@link #setEnded}. */
    private boolean ended;
    /** Whether we are still opening media the room has just changed to, with nothing played yet — see
     *  {@link #mediaChanged}. */
    private boolean arriving;
    /** How long our own media lasts, or 0 when that is not known — a live stream, or a player still
     *  loading. Only used to recognise a report of a position we have no media for. */
    private long durationMs;

    // The last report accepted, to read the sender's own progress off two of them. See onFrame.
    private String reportPid = "";
    private long reportPosMs;
    private long reportAt;
    /** When that sender was first seen not moving, so the wait it opens is measured from then. */
    private long stallSince;
    /** Until when a position from the room means nothing to us, because we have stepped to media it has
     *  not confirmed it is on — see {@link #mediaChanged}. Zero when there is nothing to wait for. */
    private long unconfirmedUntil;
    private long lastHardSeekAt;
    /** What the last jump cost, from asking for it to the picture being back past where it asked for.
     *  Measured rather than assumed: a seek costs a segment fetch and a decode from the last keyframe on
     *  one line and next to nothing on another, and the same stream is not the same cost twice running.
     *  Zero to begin with, so the first jump of a session behaves as it always did and pays for the
     *  measurement. */
    private long seekLeadMs;
    private long jumpedAt;
    private long jumpTargetMs;
    /** When the viewer last had their way. Until this is old, the room is heard but not obeyed. Starts
     *  one window in the past, not at Long.MIN_VALUE: the subtraction against a monotonic clock overflows
     *  from there and comes out negative, which reads as "just now" and made this deaf to the whole room
     *  until the viewer happened to touch something. */
    private long localCommandAt = -LOCAL_COMMAND_MS;
    /** The paused target already jumped to, so it is jumped to once and not chased. No position, so
     *  the first landing always happens. */
    private long landedOnMs = Long.MIN_VALUE;

    /** Identify us. The id is our own, not the relay's: it is what the other client stamps on every
     *  frame and compares to decide who owns the room. */
    public void bind(final String pid, final String nick) {
        this.pid = pid;
        this.nick = nick;
    }

    /** Who owns the room. Only read to keep an owner from taking anybody's word for its own position. */
    public void setOwner(final String ownerPid) {
        this.ownerPid = ownerPid == null ? "" : ownerPid;
    }

    /** Whether the player is refilling. Told on every tick, and only to hold the corrections back. */
    public void setBuffering(final boolean value) {
        buffering = value;
    }

    /**
     * Whether our own media has run out. Told on every tick, and it silences us: playback stopping and
     * the position freezing at the duration are the file ending, not the viewer pausing — but they are
     * the same diff, so they went out as a real pause carrying the last frame's timecode. Everybody
     * else stopped on it, including the peer that had just moved on to the next episode; and having
     * spoken we became the room's reference, after which the owner's heartbeats were refused and only
     * somebody pressing a button could unstick us.
     *
     * <p>Said nothing, the room plays on and the media change we are waiting for arrives as usual.
     */
    public void setEnded(final boolean value) {
        ended = value;
    }

    /** How long our own media lasts, or 0 while that is not known. Told on every tick. */
    public void setDuration(final long value) {
        durationMs = value > 0 ? value : 0;
    }

    /**
     * The room is watching other media now, so forget where it was: that position belongs to the file
     * it has left, and nothing about the new one is known until somebody reports from inside it.
     *
     * <p>Kept on, the old timecode was handed to the player opening the new episode, which came up past
     * its own end — a hundred percent of a film nobody was watching, with that position written down as
     * where to resume it. And it was not merely reported: a settle silences what we say, not what we
     * correct, so the drift correction hard-seeked the new episode back to where the old one had ended.
     *
     * <p>Forgotten rather than assumed to be zero, because whoever moved the room does not necessarily
     * start at the beginning — an episode picked by hand resumes where that viewer left it — and the
     * first heartbeat from the new file says where everybody actually is.
     *
     * @param confirmed whether the room is known to be on this media — true when the room is what moved
     *                  us, false when our own playlist stepped ahead of the room's word for it, in which
     *                  case positions are ignored until that word arrives (see {@link #mediaConfirmed})
     *                  or the wait runs out
     */
    public void mediaChanged(final boolean confirmed, final long now) {
        targetKnown = false;
        landedOnMs = Long.MIN_VALUE;
        arriving = true;
        stallSince = 0;
        reportPid = "";
        unconfirmedUntil = confirmed ? 0 : now + MEDIA_CONFIRM_MS;
        // The cooldown belongs to the file we have left. Kept, a jump made at the end of the old episode
        // spent the arrival in the new one waiting it out — which is the whole of the gap this closes.
        lastHardSeekAt = 0;
    }

    /** The room has named the media we are on, so its positions mean something again. */
    public void mediaConfirmed() {
        unconfirmedUntil = 0;
    }

    /** The speed the viewer picked, as opposed to the nudged one currently on the player. Anything
     *  user-facing — the speed menu, the value persisted on exit — wants this one. */
    public float userSpeed() {
        return userSpeed;
    }

    /** Whether the speed currently on the player is a correction of ours rather than the viewer's own. */
    public boolean trimmed() {
        return Math.abs(lastSpeed - userSpeed) > SPEED_EPSILON;
    }

    /** The last position we actually saw on the player — what to say about ourselves while there is no
     *  player to ask, which is every window in which one is being rebuilt. */
    public long position() {
        return lastPosMs;
    }

    /** Where the room is right now, or -1 while that is not known yet. Carried forward from the last
     *  report by elapsed time, so it is still right for a player that takes a moment to come up. */
    public long roomPositionMs(final long now) {
        return targetKnown ? Math.max(0, target(now)) : -1;
    }

    /**
     * Take the room's position from the snapshot handed to a joining member, before any heartbeat
     * has arrived. Without it a member joining an hour in starts at zero and is yanked forward by a
     * hard seek a moment later; with it the target is already right while the player is still
     * loading, and it keeps advancing on its own until playback catches up to it.
     */
    /** Follow the room but stay silent until {@code until}. */
    public void settle(final long until) {
        if (until > settleUntil) {
            settleUntil = until;
        }
    }

    public void seed(final long posMs, final boolean playing, final long now) {
        lastFrameAt = now;
        setTarget(posMs, playing, userSpeed, now);
    }

    /** Drop the baseline so the next tick re-anchors instead of reading the gap as a user action.
     *  Called after any break in sampling that the viewer did not cause — coming back from the
     *  background, or a player rebuild. */
    public void reprime() {
        primed = false;
        // landedOnMs deliberately survives: the seek that lands on a paused room puts the player into
        // buffering, and buffering is one of the things that reprimes — so clearing it here let the
        // landing erase its own guard and ask for the same frame again, once a second, for ever. What
        // makes a landing owed again is the room naming a different position, which the guard compares
        // against; a break in sampling cannot move a target that is standing still.
    }

    /**
     * Sample the player and decide.
     *
     * @param now     a monotonic clock in ms (elapsed realtime), only ever compared with itself
     * @param posMs   current playback position
     * @param play    current playback intent (playWhenReady, not isPlaying)
     * @param speed   the speed the player currently reports
     */
    public Action tick(final long now, final long posMs, final boolean play, final float speed) {
        final Action action = new Action();

        if (!primed) {
            primed = true;
            // Adopt the player's speed as the viewer's only when it is not the one we put there. A
            // correction in progress reprimes on every buffering tick, and taking our own trim for their
            // choice walked userSpeed a few percent further each time — into the speed menu, and out to
            // Prefs as a preference they never expressed.
            if (Math.abs(speed - lastSpeed) > SPEED_EPSILON) {
                userSpeed = speed;
            }
            intentPlay = play;
            // The baseline is what the player actually has, trim included — recording our own idea of the
            // speed instead would read as a speed change by the viewer on the very next tick.
            setLast(posMs, play, speed, now);
            return action;
        }

        // 1. Did the viewer do something? Either the intent flipped, or the speed is not the one we
        //    put there, or playback is not where simply letting it run would have left it.
        final long expected = lastPosMs + (lastPlay ? Math.round((now - lastAt) * lastSpeed) : 0);
        final boolean speedChanged = Math.abs(speed - lastSpeed) > SPEED_EPSILON;
        final boolean userAction = !ended && (speedChanged || play != lastPlay
                || Math.abs(posMs - expected) > LOCAL_ACTION_MS);

        if (speedChanged) {
            userSpeed = speed;
        }

        final boolean settling = now < settleUntil;
        // Read once, and before anything is said: it decides both what to do and whether to speak. While
        // the room holds for somebody, our own playback is stopped where it stands — and a heartbeat from
        // there says "playing" (the held pause is deliberately never put on the wire) at a position that
        // does not move, which is exactly what the reading in onFrame calls a stall. Two of our own players
        // waiting for a third therefore accused each other of stalling and traded fifteen-second waits for
        // ever. Nothing is lost by the silence: a member that is waiting has a buffering frame out already.
        final String waiting = waitingFor(now);

        if (userAction && !settling) {
            seq++;
            appliedSeq = seq;
            appliedPid = pid;
            // Which of the three things they announce this was. A bare speed change is none of
            // them, so it goes out silently rather than mislabelled as a seek.
            final String verb = play != lastPlay
                    ? (play ? LpartyCodec.V_RESUMED : LpartyCodec.V_PAUSED)
                    : (Math.abs(posMs - expected) > LOCAL_ACTION_MS ? LpartyCodec.V_SEEKED : null);
            intentPlay = play;
            localCommandAt = now;
            setTarget(posMs, play, userSpeed, now);
            // Always an action, verb or not: what makes this frame an instruction is that the viewer did
            // it, not whether there is a word for it. Sent as a heartbeat, a bare speed change was thrown
            // away by everyone whose reference was somebody else — the room agreed on a rate one member
            // alone was playing, silently, for as long as nobody pressed anything. Their client guards its
            // notice on the verb being present, so a verbless action is applied there and not announced.
            add(action, LpartyCodec.transport(LpartyCodec.T_ACT,
                    pid, seq, posMs, intentPlay, userSpeed, verb, nick));
            lastSentAt = now;
        } else {
            if (targetKnown) {
                intentPlay = targetPlay;
            }
            // Nor a heartbeat once our media has run out: offered as the room's position, the last frame
            // of a finished file drags whoever is following us onto the end credits. Nor while the room is
            // held for somebody, for the reason given above.
            if (!settling && !ended && waiting == null && now - lastSentAt >= HEARTBEAT_MS) {
                add(action, LpartyCodec.transport(LpartyCodec.T_SYNC,
                        pid, seq, posMs, intentPlay, userSpeed, null, nick));
                lastSentAt = now;
            }
        }

        // What the jump we asked for cost, learnt the moment playback passes the position it asked for.
        // Not read off the buffering flag: a seek that lands in what is already buffered never raises it,
        // and one that does raise it does so a tick or two after being asked for.
        if (jumpedAt != 0 && posMs > jumpTargetMs) {
            seekLeadMs = Math.min(SEEK_LEAD_MAX_MS, now - jumpedAt);
            jumpedAt = 0;
        }

        // 2. A room we have stopped hearing from is not a room to follow: the target would keep
        //    extrapolating on its own and we would chase a position nobody is at.
        if (targetKnown && now - lastFrameAt > TARGET_STALE_MS) {
            targetKnown = false;
        }

        // Arrival is over the moment playback is actually running, whether or not it took a jump to get
        // there: from then on a refilling player is a stall to be waited out, not an empty one to aim.
        if (!buffering) {
            arriving = false;
        }

        // 3. Follow the room's intent, minus whoever we are all waiting for.
        action.waitingFor = waiting;
        boolean wantPlay = intentPlay && waiting == null;

        // 4. Close whatever gap is left. Below the deadband nothing happens; a real gap is walked
        //    off by trimming the rate in proportion to it; a gap too wide to walk off is jumped.
        float wantSpeed = userSpeed;
        if (targetKnown && wantPlay) {
            final long drift = posMs - target(now);
            // A jump is for a gap too wide to walk off, and only when jumping can achieve something:
            // not while our own buffer is refilling (the seek restarts the fill, which stalls us
            // again, which widens the gap — the loop that keeps a slow line permanently out), and not
            // on top of the last one.
            // Once our media has run out, only backwards: being ahead of the room is worth seeking back
            // into, so the last seconds are watched together and everybody reaches the end at once. The
            // room being ahead of our own end is not — there is nothing there to play, and aiming at it
            // would re-buffer the final frame every time the cooldown allowed.
            // Arriving in media the room has just changed to is the one case a refilling player should
            // still jump: nothing has played yet, so the buffer being filled at the beginning is worth
            // nothing, and there is no fill to restart. Waiting for it cost the whole of it — a device
            // that opens the new episode slower than the rest (a web player on a laptop is quicker at
            // this than any phone) filled a buffer at zero, played from there, and only then jumped,
            // paying for a second fill and showing the seam to everybody.
            if (Math.abs(drift) > DRIFT_HARD_SEEK_MS && (!ended || drift > 0)
                    && (arriving || (!buffering && !localBuffering)) && jumpAllowed(now)) {
                lastHardSeekAt = now;
                // One jump is what arriving is worth; from here on the guard above applies as usual.
                arriving = false;
                // Past where the room is, by what the last jump cost to make. A seek on a network
                // stream is not free: the picture comes back a second or two later and the room has
                // moved on by exactly that, so landing where the room *was* leaves the same gap again
                // — which is read as drift and jumped at again, once the cooldown allows. That loop is
                // what makes catching up look like stuttering rather than syncing.
                action.seekToMs = Math.max(0, target(now) + seekLeadMs);
                jumpedAt = now;
                jumpTargetMs = action.seekToMs;
            } else if (Math.abs(drift) > DRIFT_DEADBAND_MS) {
                float trim = (drift / 1000f) * CORRECTION_GAIN;
                trim = Math.max(-CORRECTION_MAX, Math.min(CORRECTION_MAX, trim));
                // Subtracted, not scaled: the gap then closes at a constant `trim` seconds per
                // second whatever speed the viewer chose, which is what makes it predictable.
                //
                // This is also the answer for a gap too wide to walk off that cannot be jumped at yet:
                // the trim used to sit in the other arm of the same test, so a member waiting out the
                // cooldown, or refilling, did nothing at all about being seconds behind — it stood
                // there out of step until another jump was allowed, and jumped again.
                wantSpeed = userSpeed - trim;
            }
        } else if (targetKnown && !targetPlay && !buffering && !localBuffering && jumpAllowed(now)
                && landedOnMs != targetPosMs
                && Math.abs(posMs - targetPosMs) > DRIFT_HARD_SEEK_MS) {
            // A paused room is a position too, and joining one has to land on it. Nothing above runs
            // at a standstill — there is no rate to trim and the gap never closes by itself — so the
            // member sat wherever its own player came up, and then whoever pressed play first dragged
            // the room to their position instead of joining it.
            //
            // Only when the room itself is paused, not merely when playback is stopped: while the
            // room holds for somebody who is buffering, targetPlay is still true and the target goes
            // on extrapolating, so seeking to it would jump ahead of where the room actually is.
            //
            // At the hard-seek threshold, not the deadband: at a standstill the gap to a peer is a
            // network hop plus up to one tick anyway, so policing tenths of a second here made every
            // remote pause cost everybody a backwards jump and a rebuffer.
            //
            // Once per target, because a seek lands on the nearest sync point — which may be a
            // keyframe away from what was asked for. Measuring that as a fresh gap would ask for the
            // same frame again on every tick, and while paused nothing plays the difference off.
            action.seekToMs = Math.max(0, targetPosMs);
            landedOnMs = targetPosMs;
            lastHardSeekAt = now;
            // And say nothing about where we come down: the sync point the player picks is not a
            // decision the viewer made, and at a standstill nothing plays the difference off, so
            // unannounced it stays. The window closes itself early once we are in step (below).
            settle(now + SETTLE_MS);
        }

        if (wantPlay != play) {
            action.play = wantPlay;
        }
        if (Math.abs(wantSpeed - speed) > SPEED_EPSILON) {
            action.speed = wantSpeed;
        }

        // Once we are genuinely in step there is nothing left to settle, so stop holding our tongue
        // rather than sitting out the rest of the three seconds.
        if (settling && targetKnown && Math.abs(posMs - target(now)) <= DRIFT_DEADBAND_MS) {
            settleUntil = 0;
        }

        // The player is about to look like this, so this is the baseline the next tick compares
        // against — which is the whole reason none of it comes back as a local action.
        setLast(action.seekToMs >= 0 ? action.seekToMs : posMs, wantPlay, wantSpeed, now);
        return action;
    }

    /** Take in a frame from another member. Nothing is applied here — the next tick does that, so
     *  there is one place where playback is touched and no reentrancy to reason about.
     *
     *  @return what to tell the viewer somebody did, or null for nothing — which is the answer for a
     *          heartbeat that only confirms where the room already was, and for an action that lost an
     *          ordering race, changed nothing here, and must not be announced as though it had. */
    public TogetherManager.Act onFrame(final JSONObject frame, final long now) {
        final String type = LpartyCodec.type(frame);
        final String framePid = LpartyCodec.pid(frame);

        if (LpartyCodec.T_BUFFERING.equals(type)) {
            if (LpartyCodec.bufferingValue(frame)) {
                stalls.put(framePid, new Stall(LpartyCodec.nick(frame), now));
            } else {
                stalls.remove(framePid);
            }
            return null;
        }

        // An action carries a verb and is always meant; a heartbeat is just a position report.
        final boolean isAction = LpartyCodec.T_ACT.equals(type);
        if (!isAction && !LpartyCodec.T_SYNC.equals(type)) {
            return null;
        }

        final long frameSeq = LpartyCodec.seq(frame);
        // Catch our counter up on anything we hear, heartbeats included — not just on the actions we
        // accept. A member joining a room that has been running starts at zero, so its first press
        // carried a number the room had already passed and was thrown out as stale: several presses in
        // a row did nothing, and it stopped following anybody in the meantime. Only the counter is
        // raised here; which report the room follows is still decided below.
        if (frameSeq >= 0) {
            seq = Math.max(seq, frameSeq);
        }
        // Our own command has the floor for a moment. Everything the room says in that window was
        // decided before it heard us — including the owner's next heartbeat, which still reports the
        // position we just left — and taking any of it back would undo what the viewer just did. The
        // counter above is still caught up, so nothing said here is lost as an ordering reference.
        //
        // This used to be covered by accident: an action of ours made us the room's reference, and
        // heartbeats were only followed from the reference. Following the owner instead is right, but it
        // left the viewer's own seek to be argued with by reports already on their way.
        if (now - localCommandAt < LOCAL_COMMAND_MS) {
            return null;
        }
        if (isAction) {
            if (frameSeq >= 0) {
                // A player that keeps our counter: last command wins, and simultaneous commands are
                // settled by id rather than by a host role, so two people seeking at the same moment
                // converge in one exchange instead of trading positions.
                if (frameSeq < appliedSeq
                        || (frameSeq == appliedSeq && framePid.compareTo(appliedPid) <= 0)) {
                    return null;
                }
                appliedSeq = frameSeq;
            }
            // A Lampa peer sends no counter, so there is nothing to order by and its action is
            // simply taken — which is the behaviour its own client has among its own kind.
            appliedPid = framePid;
        } else if (pid.equals(ownerPid)) {
            // Ours is the room's position when the room is ours: an owner takes nobody's word for where
            // playback is. Their rule, and safe.
            return null;
        } else if (appliedPid.isEmpty()) {
            // Nobody has acted yet, so the first heartbeat we hear becomes the reference — otherwise a
            // room where nobody touches the controls never syncs at all.
            appliedPid = framePid;
        } else if (!appliedPid.equals(framePid)) {
            // Followed from whoever acted last — NOT from the owner, tempting as their 1.3.0 change is.
            // An owner is not necessarily watching anything: the Lampa plugin hands playback to an
            // external player and stays in the room as host, where its getVideo() falls through to
            // whatever <video> is left on the page — paused, and frozen at a position nobody is at. It
            // heartbeats that. Following the owner made that frozen position the truth and undid every
            // seek made here within a second or two; following the last member to act cannot, because a
            // seek makes that member us. The wandering target their change fixes is the lesser fault.
            return null;
        }

        final long framePos = LpartyCodec.positionMs(frame);

        // Whether the sender is actually moving, which its own two reports answer and it does not. A
        // Lampa peer sends no buffering frame of any kind, and its heartbeat reports position and state
        // without checking that playback has advanced — so one that is refilling goes on saying "playing"
        // at the same position every two seconds. Extrapolated forward between those frames, that
        // position sawtooths by the whole interval, and every reset of it read as the room jumping
        // backwards: four seeks in seven seconds on the line this comment came from, alternating
        // direction, while the room in truth sat still.
        //
        // Fed into the same wait a member's own buffering frame opens, so a stall inferred here stops the
        // room exactly as one that was announced. Judged on heartbeats alone: an action carries a position
        // its sender has just moved to on purpose.
        //
        // Against the room's own rate, not against the clock: at half speed a player advances by half the
        // gap between two reports, which flat arithmetic reads as a stall — so one member choosing 0.5x
        // made everybody else stop and wait for them, for ever.
        if (!framePid.equals(reportPid)) {
            // A different member to read from, so how long the last one had been standing still says
            // nothing about this one. Left behind, it dated the next stall to a wait already spent, which
            // reads as expired the moment it opens.
            stallSince = 0;
        } else if (!isAction && LpartyCodec.playing(frame)
                && now - reportAt >= REPORT_GAP_MS) {
            final float rate = LpartyCodec.hasSpeed(frame) ? LpartyCodec.speed(frame) : userSpeed;
            final long moved = framePos - reportPosMs;
            final long due = Math.round((now - reportAt) * Math.max(0.05f, rate));
            if (moved >= 0 && moved * 2 < due) {
                // Stamped when the sender first stopped moving, not on every report that follows: the
                // fifteen-second cap in waitingFor is measured off this, and a timestamp refreshed by each
                // frame would push the deadline out for ever — the one thing that cap is there to prevent.
                if (stallSince == 0) {
                    stallSince = now;
                }
                stalls.put(framePid, new Stall(LpartyCodec.nick(frame), stallSince));
            } else {
                stallSince = 0;
                stalls.remove(framePid);
            }
        }
        reportPid = framePid;
        reportPosMs = framePos;
        reportAt = now;

        // A report at the end of our own media is that sender's file running out, not a place to follow
        // it to. Their client announces the end of an episode as a pause carrying the duration, and
        // obeyed literally that skipped whatever was left here — nineteen seconds of it on the run this
        // comment came from — and parked us on the last frame. Left alone, our own copy plays out and the
        // room's next media change is heard as usual. After the reading above, not before it: their file
        // ending is exactly when a stall of theirs has to be let go of, or the room waits out the whole cap
        // at every episode boundary.
        if (durationMs > 0 && framePos >= durationMs - END_SLACK_MS) {
            return null;
        }

        // Nothing about a position is worth hearing while we are on media the room has not caught up with:
        // a position carries no word for which file it is in, so the reference member's own timeline — the
        // end of the episode we have just left — was applied to the one we have just opened, and seeked it
        // straight into its credits. Their media change, which is what closes this window, is the only
        // thing that can say the two of us are back on one file.
        if (unconfirmedUntil != 0) {
            if (now < unconfirmedUntil) {
                return null;
            }
            unconfirmedUntil = 0;
        }

        // Whether the room went somewhere. Read off the position rather than taken from the frame's own
        // word for itself, for the same reason outgoing state is diffed rather than intercepted: a sender
        // may say nothing — their client throttles its seek announcements, and ours cannot announce one it
        // makes during a settle window — and a room that jumps in silence looks broken.
        final boolean jumped = targetKnown && Math.abs(framePos - target(now)) > JUMP_NOTICE_MS;

        // Stamped here rather than in setTarget, which the tick also calls: what keeps the room
        // alive is hearing from somebody, not us restating our own position.
        lastFrameAt = now;
        // A sender whose protocol has no speed field says nothing about speed — keep ours rather than
        // hearing "1.0" in its silence.
        setTarget(framePos, LpartyCodec.playing(frame),
                LpartyCodec.hasSpeed(frame) ? LpartyCodec.speed(frame) : userSpeed, now);
        // Adopt the room's speed as our own. Without this the room agrees on a rate it never plays
        // at: the gap would reopen as fast as the ±5% nudge could close it, and everyone below the
        // agreed speed would be dragged forward by a hard seek every few seconds instead.
        userSpeed = targetSpeed;
        // The verb when there is one — it names a pause or a resume, which a position cannot. A jump with
        // no verb on it is a seek by whoever sent it, and a verbless action that did not move anything (a
        // bare speed change) stays as quiet as it always was.
        final TogetherManager.Act act = isAction ? LpartyCodec.act(frame) : null;
        return act != null ? act : (jumped ? TogetherManager.Act.SEEKED : null);
    }

    /**
     * A member is gone: drop the stall the room was holding for them, and stop taking their word for
     * where the room is. Without this a member killed mid-stall kept everybody paused for the full
     * fifteen seconds of its wait, over a float reading their name — and if they were the one the room
     * was following, every surviving heartbeat was refused for as long as nobody pressed anything, so the
     * room quietly stopped correcting at all.
     */
    public void forget(final String memberPid) {
        if (memberPid == null) {
            return;
        }
        stalls.remove(memberPid);
        if (memberPid.equals(appliedPid)) {
            appliedPid = "";
        }
    }

    /** Report our own stall. Returns the frame to broadcast, or null when nothing changed. */
    public JSONObject localBuffering(final boolean value, final long now) {
        if (localBuffering == value) {
            return null;
        }
        localBuffering = value;
        return LpartyCodec.buffering(pid, nick, value);
    }

    /** Our current view of the room, for the snapshot a joining member is waiting for. */
    public boolean playing() {
        return intentPlay;
    }

    private static void add(final Action action, final JSONObject frame) {
        if (frame != null) {
            action.out.add(frame);
        }
    }

    /** Whose stall the room is currently holding for, or null. Our own stall does not count: the
     *  player is already stopped, and a "waiting for you" notice helps nobody. */
    private String waitingFor(final long now) {
        if (localBuffering) {
            return null;
        }
        final Iterator<Map.Entry<String, Stall>> it = stalls.entrySet().iterator();
        while (it.hasNext()) {
            final Stall stall = it.next().getValue();
            if (now - stall.at > BUFFER_WAIT_MAX_MS) {
                it.remove();
                continue;
            }
            return stall.nick;
        }
        return null;
    }

    private void setLast(final long posMs, final boolean play, final float speed, final long now) {
        lastPosMs = posMs;
        lastPlay = play;
        lastSpeed = speed;
        lastAt = now;
    }

    private void setTarget(final long posMs, final boolean play, final float speed, final long now) {
        targetPosMs = posMs;
        targetPlay = play;
        targetSpeed = speed <= 0 ? 1f : speed;
        targetAt = now;
        targetKnown = true;
    }

    /** Whether enough time has passed since the last jump to be allowed another. */
    private boolean jumpAllowed(final long now) {
        return now - lastHardSeekAt > HARD_SEEK_COOLDOWN_MS;
    }

    private long target(final long now) {
        return targetPosMs + (targetPlay ? Math.round((now - targetAt) * targetSpeed) : 0);
    }
}
