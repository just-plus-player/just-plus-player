package com.brouken.player.together;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Turns an intent's extras into JSON and back, so a room can carry the whole session its host was
 * launched with — the playlist and its per-episode names, posters, seasons, imdb/tmdb ids, quality
 * variants, subtitle lists and headers — rather than only the URL of what is playing.
 *
 * <p>Generic on purpose: the launcher contract has indexed keys ({@code video_list.quality_urls.3})
 * and nests bundles inside parcelable arrays, so a hand-written field list would be wrong the day
 * either side gains a key. Types are tagged per entry and rebuilt as the shapes the intent parser
 * already accepts.
 *
 * <p>Nothing arbitrary is executed on the receiving end: the rebuilt bundle is read only through the
 * launcher keys the player already knows, and the only object type that comes back out of it is a
 * {@link Uri} built from a string. Unknown keys are inert.
 */
public final class SessionCodec {

    private static final String TYPE = "t";
    private static final String VALUE = "v";

    private static final int MAX_DEPTH = 4;

    private SessionCodec() {
    }

    public static JSONObject toJson(final Bundle bundle) {
        return toJson(bundle, 0);
    }

    private static JSONObject toJson(final Bundle bundle, final int depth) {
        final JSONObject out = new JSONObject();
        if (bundle == null || depth > MAX_DEPTH) {
            return out;
        }
        for (String key : bundle.keySet()) {
            final JSONObject encoded = encode(bundle.get(key), depth);
            if (encoded != null) {
                try {
                    out.put(key, encoded);
                } catch (Exception ignored) {
                    // A key JSON will not take is a key not worth the session.
                }
            }
        }
        return out;
    }

    public static Bundle toBundle(final JSONObject json) {
        return toBundle(json, 0);
    }

    private static Bundle toBundle(final JSONObject json, final int depth) {
        final Bundle out = new Bundle();
        if (json == null || depth > MAX_DEPTH) {
            return out;
        }
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final JSONObject entry = json.optJSONObject(key);
            if (entry != null) {
                put(out, key, entry, depth);
            }
        }
        return out;
    }

    private static JSONObject encode(final Object value, final int depth) {
        try {
            if (value instanceof String) {
                return entry("s", value);
            }
            if (value instanceof CharSequence) {
                return entry("s", value.toString());
            }
            if (value instanceof Integer) {
                return entry("i", value);
            }
            if (value instanceof Long) {
                return entry("l", value);
            }
            if (value instanceof Boolean) {
                return entry("b", value);
            }
            if (value instanceof Float || value instanceof Double) {
                return entry("d", ((Number) value).doubleValue());
            }
            if (value instanceof Uri) {
                return entry("u", value.toString());
            }
            if (value instanceof Bundle) {
                return entry("bu", toJson((Bundle) value, depth + 1));
            }
            if (value instanceof String[]) {
                final JSONArray array = new JSONArray();
                for (String item : (String[]) value) {
                    array.put(item == null ? JSONObject.NULL : item);
                }
                return entry("sa", array);
            }
            if (value instanceof int[]) {
                final JSONArray array = new JSONArray();
                for (int item : (int[]) value) {
                    array.put(item);
                }
                return entry("ia", array);
            }
            if (value instanceof long[]) {
                final JSONArray array = new JSONArray();
                for (long item : (long[]) value) {
                    array.put(item);
                }
                return entry("la", array);
            }
            if (value instanceof ArrayList) {
                final JSONArray array = new JSONArray();
                for (Object item : (ArrayList<?>) value) {
                    array.put(item == null ? JSONObject.NULL : String.valueOf(item));
                }
                return entry("sl", array);
            }
            if (value instanceof Parcelable[]) {
                final JSONArray array = new JSONArray();
                for (Parcelable item : (Parcelable[]) value) {
                    final JSONObject encoded = encode(item, depth + 1);
                    array.put(encoded == null ? JSONObject.NULL : encoded);
                }
                return entry("pa", array);
            }
        } catch (Exception ignored) {
            // Fall through: an entry we cannot describe is simply left out of the session.
        }
        return null;
    }

    private static void put(final Bundle out, final String key, final JSONObject entry, final int depth) {
        final String type = entry.optString(TYPE);
        try {
            switch (type) {
                case "s":
                    out.putString(key, entry.optString(VALUE));
                    break;
                case "i":
                    out.putInt(key, entry.optInt(VALUE));
                    break;
                case "l":
                    out.putLong(key, entry.optLong(VALUE));
                    break;
                case "b":
                    out.putBoolean(key, entry.optBoolean(VALUE));
                    break;
                case "d":
                    out.putDouble(key, entry.optDouble(VALUE));
                    break;
                case "u":
                    out.putParcelable(key, Uri.parse(entry.optString(VALUE)));
                    break;
                case "bu":
                    out.putBundle(key, toBundle(entry.optJSONObject(VALUE), depth + 1));
                    break;
                case "sa":
                case "sl": {
                    final JSONArray array = entry.optJSONArray(VALUE);
                    final String[] items = new String[array == null ? 0 : array.length()];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = array.isNull(i) ? null : array.optString(i);
                    }
                    // Both shapes come back as a plain array; the intent parser reads either.
                    out.putStringArray(key, items);
                    break;
                }
                case "ia": {
                    final JSONArray array = entry.optJSONArray(VALUE);
                    final int[] items = new int[array == null ? 0 : array.length()];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = array.optInt(i);
                    }
                    out.putIntArray(key, items);
                    break;
                }
                case "la": {
                    final JSONArray array = entry.optJSONArray(VALUE);
                    final long[] items = new long[array == null ? 0 : array.length()];
                    for (int i = 0; i < items.length; i++) {
                        items[i] = array.optLong(i);
                    }
                    out.putLongArray(key, items);
                    break;
                }
                case "pa": {
                    final JSONArray array = entry.optJSONArray(VALUE);
                    final int size = array == null ? 0 : array.length();
                    final Parcelable[] items = new Parcelable[size];
                    for (int i = 0; i < size; i++) {
                        final JSONObject item = array.optJSONObject(i);
                        final String itemType = item == null ? "" : item.optString(TYPE);
                        if ("u".equals(itemType)) {
                            items[i] = Uri.parse(item.optString(VALUE));
                        } else if ("bu".equals(itemType)) {
                            items[i] = toBundle(item.optJSONObject(VALUE), depth + 1);
                        }
                    }
                    out.putParcelableArray(key, items);
                    break;
                }
                default:
                    break;
            }
        } catch (Exception ignored) {
            // A malformed entry costs that entry, not the session.
        }
    }

    private static JSONObject entry(final String type, final Object value) throws Exception {
        return new JSONObject().put(TYPE, type).put(VALUE, value);
    }
}
