package com.brouken.player.together;

import org.json.JSONObject;

/**
 * The wire format, which is the Lampa {@code lparty.js} plugin's format. Everything about their
 * spelling lives here and nowhere else: short field names, positions in fractional seconds, and
 * playback state as the strings {@code "playing"} / {@code "paused"}.
 *
 * <p>Their client dispatches on {@code t} against a fixed list and drops anything it does not
 * recognise. That is what lets this be a superset rather than a compromise: {@link #T_SESSION} and
 * {@link #T_BUFFERING}, and the extra fields {@code sq} and {@code sp}, pass straight through a
 * Lampa peer while two of our own players still get the full launch payload, the
 * wait-for-whoever-is-buffering behaviour and playback speed.
 *
 * <p>Nothing is encrypted. Their client does not encrypt, so a shared room cannot.
 */
final class LpartyCodec {

    // --- Their vocabulary ---------------------------------------------------------------------
    static final String T_HELLO = "hello";
    static final String T_ME = "me";
    static final String T_STATE = "state";
    static final String T_SYNC = "sync";
    static final String T_ACT = "act";
    static final String T_URL = "url";
    static final String T_HOST = "host";
    static final String T_BYE = "bye";
    /** Added in their 1.2.0: the room stops at a position and waits for everybody to fill a buffer
     *  there, so a newcomer does not spend its first minute being dragged along. The owner calls the
     *  hold and lifts it; a member only says when it is ready. */
    static final String T_HOLD = "hold";
    static final String T_READY = "ready";
    static final String T_GO = "go";

    /** Their notice verbs — a Lampa peer turns these into a toast naming whoever acted. */
    static final String V_RESUMED = "resumed";
    static final String V_PAUSED = "paused";
    static final String V_SEEKED = "seeked";

    // --- Ours, invisible to them --------------------------------------------------------------
    static final String T_SESSION = "jsess";
    static final String T_BUFFERING = "buf";

    /** Our logical command counter. Absent on a frame from a Lampa peer, which is how we know to
     *  fall back to last-writer-wins for it. */
    private static final String SEQ = "sq";
    /** Playback speed, which their protocol has no notion of. */
    private static final String SPEED = "sp";

    private static final String PID = "u";
    private static final String NICK = "n";
    private static final String STATE = "s";
    private static final String POSITION = "p";
    private static final String VERB = "v";

    private static final String PLAYING = "playing";
    private static final String PAUSED = "paused";

    private LpartyCodec() {
    }

    // --- Reading ------------------------------------------------------------------------------

    static String type(final JSONObject frame) {
        return frame.optString("t");
    }

    /** The sender's own id, stable for as long as their app runs. Present on every frame. */
    static String pid(final JSONObject frame) {
        return frame.optString(PID);
    }

    static String nick(final JSONObject frame) {
        return frame.optString(NICK);
    }

    /** What the sender says they did, or null on a heartbeat and on a verb we have no name for. */
    static TogetherManager.Act act(final JSONObject frame) {
        switch (frame.optString(VERB)) {
            case V_PAUSED:  return TogetherManager.Act.PAUSED;
            case V_RESUMED: return TogetherManager.Act.RESUMED;
            case V_SEEKED:  return TogetherManager.Act.SEEKED;
            default:        return null;
        }
    }

    static long positionMs(final JSONObject frame) {
        return Math.max(0, Math.round(frame.optDouble(POSITION, 0) * 1000));
    }

    static boolean playing(final JSONObject frame) {
        return PLAYING.equals(frame.optString(STATE));
    }

    /** Whether the sender's protocol knows about speed at all. A Lampa peer's frames carry no {@code sp},
     *  and reading that absence as 1.0 made every one of its heartbeats an instruction to play at normal
     *  speed — which took the speed control away from anybody sharing a room with it. */
    static boolean hasSpeed(final JSONObject frame) {
        return frame.has(SPEED);
    }

    static float speed(final JSONObject frame) {
        final double value = frame.optDouble(SPEED, 1);
        return value <= 0 ? 1f : (float) value;
    }

    /** Our counter, or -1 when the sender does not keep one. */
    static long seq(final JSONObject frame) {
        return frame.optLong(SEQ, -1);
    }

    // --- Writing ------------------------------------------------------------------------------

    /**
     * A position report. {@code act} is a deliberate action and carries a verb they can announce;
     * {@code sync} is the periodic heartbeat and stays silent.
     */
    static JSONObject transport(final String type, final String pid, final long seq,
                                final long posMs, final boolean playing, final float speed,
                                final String verb, final String nick) {
        final JSONObject frame = base(type, pid);
        if (frame == null) {
            return null;
        }
        try {
            frame.put(STATE, playing ? PLAYING : PAUSED);
            frame.put(POSITION, posMs / 1000.0);
            frame.put(SEQ, seq);
            frame.put(SPEED, speed);
            if (verb != null) {
                frame.put(VERB, verb);
                frame.put(NICK, nick);
            }
            return frame;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The room snapshot a joining member waits for. Their client rejects one without {@code url}
     * and leaves the room, so the caller must not send it before it has media.
     */
    static JSONObject state(final String pid, final String roomName, final String owner,
                            final String url, final String title, final String poster,
                            final int tmdbId, final String source, final String mediaType,
                            final boolean playing, final long posMs) {
        final JSONObject frame = base(T_STATE, pid);
        if (frame == null || url == null || url.isEmpty()) {
            return null;
        }
        try {
            return frame.put("rn", roomName == null ? "" : roomName)
                    .put("own", owner)
                    .put("url", url)
                    .put("ti", title == null ? "" : title)
                    .put("po", poster == null ? "" : poster)
                    .put("tm", tmdbId)
                    .put("src", source == null ? "" : source)
                    .put("ty", mediaType == null ? "movie" : mediaType)
                    .put(STATE, playing ? PLAYING : PAUSED)
                    .put(POSITION, posMs / 1000.0);
        } catch (Exception e) {
            return null;
        }
    }

    static String stateUrl(final JSONObject frame) {
        final String url = frame.optString("url", null);
        return url == null || url.isEmpty() ? null : url;
    }

    /** Null rather than empty, as their {@code msg.own || null} normalises it: an empty string taken for
     *  an owner id is an owner nobody can be, so no newcomer is ever answered and the media can never
     *  change, with no way out short of leaving the room. */
    static String stateOwner(final JSONObject frame) {
        final String owner = frame.optString("own", "");
        return owner.isEmpty() ? null : owner;
    }

    static String stateRoomName(final JSONObject frame) {
        return frame.optString("rn", "");
    }

    static String stateTitle(final JSONObject frame) {
        return frame.optString("ti", "");
    }

    /** The artwork the room is showing. Theirs is a tmdb address, so it is worth something on any
     *  device — unlike a launcher's own thumbnail, which is usually local to the sender. */
    static String statePoster(final JSONObject frame) {
        return frame.optString("po", "");
    }

    static JSONObject hello(final String pid, final String nick) {
        return named(T_HELLO, pid, nick);
    }

    static JSONObject me(final String pid, final String nick) {
        return named(T_ME, pid, nick);
    }

    /** "The room is mine now" — sent by whoever the election picked when the owner left. */
    static JSONObject host(final String pid, final String nick) {
        return named(T_HOST, pid, nick);
    }

    /** Stop here and fill a buffer. Owner only; the position is read back with {@link #positionMs}. */
    static JSONObject hold(final String pid, final long posMs) {
        return at(T_HOLD, pid, posMs);
    }

    /** "My buffer is full enough" — a member's one answer to a hold. */
    static JSONObject ready(final String pid) {
        return base(T_READY, pid);
    }

    /** Everybody is ready, carry on from here — and carry on doing what, which their frame leaves out.
     *  Written in the same field and the same words the rest of the protocol uses for it, so their client
     *  reads past it exactly as it reads past our seq and speed. */
    static JSONObject go(final String pid, final long posMs, final boolean playing) {
        final JSONObject frame = at(T_GO, pid, posMs);
        if (frame == null) {
            return null;
        }
        try {
            return frame.put(STATE, playing ? PLAYING : PAUSED);
        } catch (Exception e) {
            return null;
        }
    }

    /** What to come out of a hold doing. A sender that says nothing means play: their client resumes on
     *  every {@code go} it receives, and a room of theirs is only ever held while it was playing. */
    static boolean goPlaying(final JSONObject frame) {
        return !frame.has(STATE) || playing(frame);
    }

    private static JSONObject at(final String type, final String pid, final long posMs) {
        final JSONObject frame = base(type, pid);
        if (frame == null) {
            return null;
        }
        try {
            return frame.put(POSITION, posMs / 1000.0);
        } catch (Exception e) {
            return null;
        }
    }

    /** Named like the greetings are: a peer that hears only an id cannot say who just left, and its
     *  member list may already have dropped us by the time this arrives. */
    static JSONObject bye(final String pid, final String nick) {
        return named(T_BYE, pid, nick);
    }

    static JSONObject url(final String pid, final String url, final String title) {
        final JSONObject frame = base(T_URL, pid);
        if (frame == null) {
            return null;
        }
        try {
            return frame.put("url", url).put("ti", title == null ? "" : title);
        } catch (Exception e) {
            return null;
        }
    }

    /** Our own extension: the whole launch payload, for the players that understand it. */
    static JSONObject session(final String pid, final JSONObject session) {
        final JSONObject frame = base(T_SESSION, pid);
        if (frame == null || session == null) {
            return null;
        }
        try {
            return frame.put("ses", session);
        } catch (Exception e) {
            return null;
        }
    }

    static JSONObject sessionOf(final JSONObject frame) {
        return frame.optJSONObject("ses");
    }

    static JSONObject buffering(final String pid, final String nick, final boolean value) {
        final JSONObject frame = named(T_BUFFERING, pid, nick);
        if (frame == null) {
            return null;
        }
        try {
            return frame.put("v", value);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean bufferingValue(final JSONObject frame) {
        return frame.optBoolean("v");
    }

    private static JSONObject named(final String type, final String pid, final String nick) {
        final JSONObject frame = base(type, pid);
        if (frame == null) {
            return null;
        }
        try {
            return frame.put(NICK, nick == null ? "" : nick);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject base(final String type, final String pid) {
        try {
            return new JSONObject().put("t", type).put(PID, pid);
        } catch (Exception e) {
            return null;
        }
    }
}
