package com.brouken.player.together;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * The room's connection to <a href="https://ittysockets.com">itty.ws</a> — a public WebSocket relay
 * that needs no account, no API key and no token. It fans a message out to everyone else on a
 * channel and forgets it; nothing is stored and nothing comes back to the sender.
 *
 * <p>Two things the service leaves to the client, both done here: it never reconnects on its own,
 * and it sends no pings.
 *
 * <p>Callbacks arrive on OkHttp's reader thread. The caller marshals.
 */
public final class Relay {

    /** Relay events. All fire off the main thread. */
    public interface Listener {
        /** An application frame from another member, already unwrapped from the envelope. */
        void onFrame(JSONObject frame);

        /** The same frame with the sender's relay id beside it, for a listener that has to tie the two
         *  together. Only the room needs it; the default drops it. */
        default void onFrame(JSONObject frame, String senderUid) {
            onFrame(frame);
        }

        /** Somebody left, named as the relay itself names them. The default is the bare count, which is
         *  all the lobby ever wanted. */
        default void onLeave(String uid, String alias, int total) {
            onPeers(total);
        }

        /** Our own arrival, with the member count that includes us. The room handshake starts here:
         *  a count of one means nobody else is on the channel. */
        void onReady(int total);

        /** Somebody else arrived or left; {@code total} counts everyone including us. */
        void onPeers(int total);

        /** The socket came up (or went down) — the room badge follows this. */
        void onConnected(boolean connected);
    }

    /** Where rooms live unless told otherwise. The plugin's own default, so codes exchanged with it
     *  land in the same place without anybody configuring anything. */
    public static final String DEFAULT_BASE = "wss://itty.ws/c/";

    /** Process-wide because there is one relay at a time and every room and lobby socket uses it. */
    private static volatile String base = DEFAULT_BASE;
    /** Announce our relay id in others' join events — the Lampa plugin builds its member list from
     *  it and would otherwise track a member with no id at all. */
    private static final String OPTIONS = "?announce=true&list=true";
    /** Retry delays, then the last one repeats. Short enough to catch a Wi-Fi→LTE handover. */
    private static final long[] BACKOFF_MS = {1_000, 2_000, 5_000};
    private static final long PING_SEC = 20;

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .pingInterval(PING_SEC, TimeUnit.SECONDS)
            .build();

    private static final ScheduledExecutorService RETRY =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable, "TogetherRetry");
                thread.setDaemon(true);
                return thread;
            });

    private final String channel;
    private final String query;
    private final Listener listener;
    /** Pinned when this relay is made. The static one is what a settings change edits, and reading it
     *  afresh on every retry meant a live room could come back up on a different relay — an empty
     *  channel, reported as connected, where nothing anyone did reached anybody. */
    private final String endpoint;

    /** Written from the main thread, the reader thread and the retry executor alike. */
    private volatile WebSocket socket;
    private volatile boolean closed;
    private volatile int attempt;
    /** Which socket is the current one. Bumped for each, and stamped on the callbacks that socket will
     *  deliver, so a report from one that has already been replaced can be told apart from a live one —
     *  by identity it cannot, because onOpen can arrive before the field holding it is even assigned. */
    private volatile int generation;
    /** Our own id on this relay. Not the application id: this one is the relay's, and it is new after
     *  every reconnect. Written on the reader thread, read from the main one. */
    private volatile String uid = "";
    /** Everybody on the channel, us included. Guarded because it is written on the reader thread. */
    private final Set<String> members = new LinkedHashSet<>();

    public String uid() {
        return uid;
    }

    /** The channel's members in the order the other client sorts them to decide who takes a vacated
     *  room over — plain ascending, so both ends elect the same one. A copy, taken under the lock. */
    public List<String> memberUids() {
        final List<String> sorted;
        synchronized (members) {
            sorted = new ArrayList<>(members);
        }
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Point every future socket at a different relay. Anything that is not a WebSocket URL falls
     * back to the default rather than quietly breaking the feature, and a missing trailing slash is
     * forgiven — the channel is appended straight onto this.
     *
     * @param url a {@code ws://} or {@code wss://} prefix, or empty for {@link #DEFAULT_BASE}
     */
    public static void setBase(final String url) {
        final String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()
                || !(trimmed.startsWith("ws://") || trimmed.startsWith("wss://"))) {
            base = DEFAULT_BASE;
            return;
        }
        base = trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    /** The relay in effect, for a settings screen to show. */
    public static String base() {
        return base;
    }

    public Relay(final String channel, final Listener listener) {
        this(channel, null, listener);
    }

    /**
     * @param alias what the relay itself should call us in the join and leave events it sends to
     *              everybody else. Worth setting on a room and not on the lobby: the lobby channel is
     *              shared by every listener, and nothing there needs to know what this device is
     *              called. Null or empty leaves us anonymous, as before.
     */
    public Relay(final String channel, final String alias, final Listener listener) {
        this.channel = channel;
        this.listener = listener;
        this.endpoint = Relay.base;
        this.query = alias == null || alias.isEmpty() ? OPTIONS : OPTIONS + "&as=" + encode(alias);
    }

    /** {@code encodeURIComponent}, which is what the other client encodes its own alias with — so a
     *  space stays a space rather than arriving as a {@code +}, and a Cyrillic nick survives. */
    private static String encode(final String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public void open() {
        if (closed || socket != null) {
            return;
        }
        final WebSocket opened = CLIENT.newWebSocket(
                new Request.Builder().url(endpoint + channel + query).build(),
                new Callbacks(++generation));
        socket = opened;
        // A retry runs on its own thread, so the room can be left while we are still inside the call
        // above — and then close() found no socket to close and this one lived on, keeping us a silent
        // member of a room we walked out of. Closing it here is the same check, after the fact.
        if (closed) {
            socket = null;
            opened.close(1000, null);
        }
    }

    /** Leave for good. The relay drops the channel once the last member is gone. */
    public void close() {
        closed = true;
        final WebSocket open = socket;
        socket = null;
        if (open != null) {
            open.close(1000, null);
        }
    }

    /** Send a frame. Silently does nothing while the socket is down — the room re-syncs on the next
     *  heartbeat once it is back, so a dropped frame costs nothing worth queueing for. */
    public void send(final JSONObject frame) {
        final WebSocket open = socket;
        if (open == null || frame == null) {
            return;
        }
        try {
            open.send(frame.toString());
        } catch (Exception ignored) {
            // A closed socket is not worth interrupting playback over.
        }
    }

    private void retry() {
        if (closed) {
            return;
        }
        socket = null;
        final long delay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        attempt++;
        RETRY.schedule(this::open, delay, TimeUnit.MILLISECONDS);
    }

    private final class Callbacks extends WebSocketListener {

        private final int generation;

        Callbacks(final int generation) {
            this.generation = generation;
        }

        /**
         * Whether this socket still speaks for the relay. Two ways it might not:
         *
         * <p>The relay has been left. Without this check a frame already in flight when the room was
         * left arrived after the next room had been entered — the listener's own guard asks only whether
         * it has a relay, and by then it has, the new one — so the old room's media, owner and connection
         * state were applied to the new room.
         *
         * <p>Or the socket has been replaced by a reconnect. A dying socket's last word arrives after its
         * successor is already up, and taken at face value it puts the room back into "reconnecting" and
         * leaves it there: the state is false, and nothing will say true again until the *next* drop.
         * Worse, it would send a second retry down a relay that had already recovered.
         */
        private boolean spent() {
            return closed || generation != Relay.this.generation;
        }

        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            if (spent()) {
                return;
            }
            // Only a socket that still speaks for the relay may reset the backoff: a superseded one
            // coming up late would hand the next real drop a fresh 1s delay it has not earned.
            attempt = 0;
            listener.onConnected(true);
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            if (spent()) {
                return;
            }
            try {
                final JSONObject envelope = new JSONObject(text);
                final String type = envelope.optString("type");
                if ("join".equals(type)) {
                    final String who = envelope.optString("uid");
                    if (envelope.optBoolean("self")) {
                        uid = who;
                        // Our own arrival carries the roster that was already there. Ourselves added
                        // outright rather than trusted to be in it: an election that compares the lowest
                        // id against our own can never pick us out of a list we are missing from, and a
                        // set makes the belt-and-braces free if the relay does include us.
                        final JSONArray users = envelope.optJSONArray("users");
                        synchronized (members) {
                            members.clear();
                            members.add(who);
                            for (int i = 0; users != null && i < users.length(); i++) {
                                final JSONObject user = users.optJSONObject(i);
                                if (user != null && !user.optString("uid").isEmpty()) {
                                    members.add(user.optString("uid"));
                                }
                            }
                        }
                        listener.onReady(envelope.optInt("total", 1));
                    } else {
                        synchronized (members) {
                            members.add(who);
                        }
                        listener.onPeers(envelope.optInt("total", 1));
                    }
                    return;
                }
                if ("leave".equals(type)) {
                    final String who = envelope.optString("uid");
                    synchronized (members) {
                        members.remove(who);
                    }
                    listener.onLeave(who, envelope.optString("alias"),
                            envelope.optInt("total", 1));
                    return;
                }
                // Everything else is an application frame. The relay wraps what was sent: the body
                // arrives nested under "message", beside the envelope's own uid/date. The JS client
                // flattens the two before handing them to its callers, which reads like the wire
                // format and is not — taking the payload from the envelope finds nothing, and every
                // frame is dropped while the room still looks perfectly connected.
                final JSONObject body = envelope.optJSONObject("message");
                if (body != null && body.has("t")) {
                    listener.onFrame(body, envelope.optString("uid"));
                }
            } catch (Exception ignored) {
                // Malformed frame — drop it and keep listening.
            }
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            // The ids the relay gave us die with the socket; a reconnect is issued fresh ones.
            uid = "";
            synchronized (members) {
                members.clear();
            }
            if (spent()) {
                return;
            }
            listener.onConnected(false);
            retry();
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
            // The ids the relay gave us die with the socket; a reconnect is issued fresh ones.
            uid = "";
            synchronized (members) {
                members.clear();
            }
            if (spent()) {
                return;
            }
            listener.onConnected(false);
            retry();
        }
    }
}
