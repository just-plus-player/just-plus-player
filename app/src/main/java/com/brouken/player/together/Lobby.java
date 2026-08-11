package com.brouken.player.together;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finding rooms without a directory server. All that exists is one more relay channel that everyone
 * agrees on: a client wanting a list shouts {@code who} into it, and every host sitting there
 * answers with an advertisement for its own room. Nothing is stored anywhere; the list is whoever
 * happened to be listening.
 *
 * <p>The channel and the two frame shapes are the Lampa {@code lparty.js} plugin's, so its rooms
 * show up in our list and ours show up in its browser.
 *
 * <p>The bargain is worth stating plainly: publishing means the room's name, poster and viewer
 * count are readable by anyone who asks the lobby, whether or not they ever join.
 */
final class Lobby {

    interface Found {
        /** Ads collected, best-attended first. Delivered once, on the main thread. */
        void onRooms(List<JSONObject> rooms);
    }

    /** What a room advertises about itself. Built by the caller, sent verbatim. */
    interface AdSource {
        JSONObject ad();
    }

    private static final String CHANNEL = "lparty-lobby-v1";
    private static final String T_WHO = "who";
    private static final String T_AD = "ad";
    /** How long to listen after asking. Theirs is the same, and hosts answer inside 400 ms. */
    private static final long COLLECT_MS = 1_500;
    /** Hosts stagger their replies so a busy lobby does not answer as one. */
    private static final int REPLY_MIN_MS = 50;
    private static final int REPLY_SPREAD_MS = 350;

    private Lobby() {
    }

    /** Ask the lobby what is out there. The socket lives only as long as the collection window. */
    static void discover(final Handler handler, final Found callback) {
        final Map<String, JSONObject> found = new LinkedHashMap<>();
        final Relay[] relay = new Relay[1];

        relay[0] = new Relay(CHANNEL, new Relay.Listener() {
            @Override
            public void onFrame(final JSONObject frame) {
                if (!T_AD.equals(frame.optString("t"))) {
                    return;
                }
                final JSONObject ad = frame.optJSONObject("r");
                if (ad != null && !ad.optString("id").isEmpty()) {
                    synchronized (found) {
                        found.put(ad.optString("id"), ad);
                    }
                }
            }

            @Override
            public void onReady(final int total) {
                relay[0].send(who());
            }

            @Override
            public void onPeers(final int total) {
            }

            @Override
            public void onConnected(final boolean connected) {
            }
        });
        relay[0].open();

        handler.postDelayed(() -> {
            relay[0].close();
            final List<JSONObject> rooms;
            synchronized (found) {
                rooms = new ArrayList<>(found.values());
            }
            // Busiest first: an empty room is the least useful thing to offer.
            rooms.sort((a, b) -> b.optInt("members") - a.optInt("members"));
            callback.onRooms(rooms);
        }, COLLECT_MS);
    }

    /**
     * Sit in the lobby for as long as our room lasts and answer anyone asking. Silent otherwise —
     * the ad is only ever a reply, never a broadcast, which is what keeps an idle lobby idle.
     */
    static final class Publisher {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final SecureRandom random = new SecureRandom();
        private final AdSource source;
        private Relay relay;

        Publisher(final AdSource source) {
            this.source = source;
        }

        void start() {
            if (relay != null) {
                return;
            }
            relay = new Relay(CHANNEL, new Relay.Listener() {
                @Override
                public void onFrame(final JSONObject frame) {
                    if (!T_WHO.equals(frame.optString("t"))) {
                        return;
                    }
                    handler.postDelayed(() -> {
                        final JSONObject ad = source.ad();
                        if (relay != null && ad != null) {
                            relay.send(advert(ad));
                        }
                    }, REPLY_MIN_MS + random.nextInt(REPLY_SPREAD_MS));
                }

                @Override
                public void onReady(final int total) {
                }

                @Override
                public void onPeers(final int total) {
                }

                @Override
                public void onConnected(final boolean connected) {
                }
            });
            relay.open();
        }

        void stop() {
            if (relay != null) {
                relay.close();
                relay = null;
            }
        }
    }

    private static JSONObject who() {
        try {
            return new JSONObject().put("t", T_WHO);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject advert(final JSONObject ad) {
        try {
            return new JSONObject().put("t", T_AD).put("r", ad);
        } catch (Exception e) {
            return null;
        }
    }
}
