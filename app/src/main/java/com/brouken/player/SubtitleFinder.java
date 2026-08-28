package com.brouken.player;

import android.net.Uri;

import androidx.media3.common.util.Util;

import java.util.LinkedHashSet;
import java.util.Set;

import okhttp3.HttpUrl;

public class SubtitleFinder {

    private PlayerActivity activity;
    private Uri baseUri;
    private String path;
    private final Set<Uri> urls;

    public SubtitleFinder(PlayerActivity activity, Uri uri) {
        this.activity = activity;
        path = uri.getPath();
        path = path.substring(0, path.lastIndexOf('.'));
        baseUri = uri;
        urls = new LinkedHashSet<>();
    }

    public static boolean isUriCompatible(Uri uri) {
        String pth = uri.getPath();
        if (pth == null || pth.lastIndexOf('.') <= -1) {
            return false;
        }
        return !isStreamEndpoint(pth);
    }

    /**
     * Whether the path is a streaming endpoint rather than a file on a server. Guessing sidecar names
     * only makes sense next to a real file: an endpoint serves one blob, so every guess is a miss that
     * the backend still pays for. A torrent backend pays the most — TorrServe resolves the path inside
     * the raddish and spawns a reader per connection — and one was reported falling over under a player
     * that asked it ten to twenty times per opened film, for names that could not exist.
     *
     * <p>Matched on a whole path segment, so a film actually called "stream.mkv" on a file server is
     * left alone. Deliberately narrow: the point is to stop a known-pointless volley, not to guess at
     * which hosts deserve one.
     */
    private static boolean isStreamEndpoint(final String path) {
        for (final String segment : path.split("/")) {
            if (segment.equalsIgnoreCase("stream") || segment.equalsIgnoreCase("play")) {
                return true;
            }
        }
        return false;
    }

    private void addLanguage(String lang, String suffix) {
        urls.add(buildUri(lang + "." + suffix));
        urls.add(buildUri(Util.normalizeLanguageCode(lang) + "." + suffix));
    }

    private Uri buildUri(String suffix) {
        final String newPath = path + "." + suffix;
        return baseUri.buildUpon().path(newPath).build();
    }

    public void start() {
        // Prevent IllegalArgumentException in okhttp3.Request.Builder
        if (HttpUrl.parse(baseUri.toString()) == null) {
            return;
        }

        for (String suffix : new String[] { "srt", "ssa", "ass" }) {
            urls.add(buildUri(suffix));
            for (String language : Utils.getDeviceLanguages()) {
                addLanguage(language, suffix);
            }
        }
        urls.add(buildUri("vtt"));

        SubtitleFetcher subtitleFetcher = new SubtitleFetcher(activity, urls);
        subtitleFetcher.start();
    }

}
