package com.brouken.player.update;

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
}
