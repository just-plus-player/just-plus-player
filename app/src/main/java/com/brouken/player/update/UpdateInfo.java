package com.brouken.player.update;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One available update, resolved from a GitHub release. Immutable data holder.
 */
public final class UpdateInfo {
    /** Numeric version code derived from the release tag ({@code major*1_000_000 + minor*1_000 + patch}). */
    public final int versionCode;
    /** Raw release tag, e.g. {@code v1.2.3}. */
    public final String tagName;
    /** Human-readable version, e.g. {@code 1.2.3} (tag without a leading {@code v}). */
    public final String versionName;
    /** Release notes (markdown), possibly empty. */
    public final String changelog;
    /** Direct download URL of the APK asset. */
    public final String apkUrl;
    /** APK size in bytes (0 if unknown). */
    public final long size;

    public UpdateInfo(int versionCode, String tagName, String versionName, String changelog, String apkUrl, long size) {
        this.versionCode = versionCode;
        this.tagName = tagName;
        this.versionName = versionName;
        this.changelog = changelog;
        this.apkUrl = apkUrl;
        this.size = size;
    }

    /**
     * Serialises the whole find into one string, so Prefs can keep it across process death: the check is
     * throttled, and without a remembered result the "update available" button would come and go with
     * whichever launch happened to hit the network.
     */
    public String toJson() {
        final JSONObject json = new JSONObject();
        try {
            json.put("versionCode", versionCode);
            json.put("tagName", tagName);
            json.put("versionName", versionName);
            json.put("changelog", changelog);
            json.put("apkUrl", apkUrl);
            json.put("size", size);
        } catch (JSONException e) {
            return null;
        }
        return json.toString();
    }

    /** Inverse of {@link #toJson()}; null for anything unreadable. */
    public static UpdateInfo fromJson(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            final JSONObject json = new JSONObject(raw);
            return new UpdateInfo(json.optInt("versionCode"), json.optString("tagName"),
                    json.optString("versionName"), json.optString("changelog"),
                    json.optString("apkUrl"), json.optLong("size"));
        } catch (JSONException e) {
            return null;
        }
    }
}
