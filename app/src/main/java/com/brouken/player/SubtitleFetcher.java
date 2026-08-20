package com.brouken.player;

import android.net.Uri;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads the first of several candidate subtitle URLs that answers, and hands it to the player.
 *
 * Candidates are tried one at a time and each is fetched exactly once. This used to probe them all in
 * parallel and then re-fetch the winner — which suits the job it was written for, guessing a dozen
 * sidecar file names beside a video where nearly every guess is a 404, but costs two requests for
 * every subtitle actually found. Small hosts do not take that kindly: shegu.st answers the probe and
 * then lets the second connection time out, so a file that had already been located never arrived.
 */
class SubtitleFetcher {

    private final PlayerActivity activity;
    private final List<Uri> urls;
    private final String cacheName;
    private final String announcement;

    private static final int CONNECT_TIMEOUT_SEC = 15;
    private static final int READ_TIMEOUT_SEC = 20;

    /**
     * The same client the search uses, so a download reuses the connection the search just opened
     * rather than negotiating its own — which is where these hosts start timing out.
     */
    private static final OkHttpClient CLIENT = SubtitleSearch.CLIENT.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    /** Subtitles are text; anything this size is not one, and is not worth the memory to find out. */
    private static final long MAX_BYTES = 2_000_000;

    public SubtitleFetcher(PlayerActivity activity, List<Uri> urls) {
        this(activity, urls, null, null);
    }

    /**
     * @param cacheName    what to call the downloaded copy; null takes the name from the URL
     * @param announcement shown once the track is actually attached, not when the file is merely
     *                     located — announcing a find and then making the viewer wait for the
     *                     download reads as a stall, or as a lie when the download then fails
     */
    public SubtitleFetcher(PlayerActivity activity, List<Uri> urls, String cacheName,
                           String announcement) {
        this.activity = activity;
        this.urls = urls;
        this.cacheName = cacheName;
        this.announcement = announcement;
    }

    public void start() {
        new Thread(this::fetchNow).start();
    }

    /**
     * Downloads on the calling thread. The online search runs on one already and needs the answer:
     * a source whose file will not come down has to give way to the next source, not end the search.
     *
     * @return true once a file has been downloaded and handed to the player
     */
    boolean fetchNow() {
        for (Uri url : urls) {
            if (fetch(CLIENT, url)) {
                return true;
            }
        }
        return false;
    }

    /** @return true once a subtitle has been downloaded and handed over; false to try the next URL. */
    private boolean fetch(OkHttpClient client, Uri url) {
        // Prevents IllegalArgumentException in okhttp3.Request.Builder.
        if (HttpUrl.parse(url.toString()) == null) {
            return false;
        }
        final Request request = new Request.Builder().url(url.toString()).build();
        try (Response response = client.newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null || body.contentLength() > MAX_BYTES) {
                return false;
            }
            InputStream stream = body.byteStream();
            // The legacy OpenSubtitles API hands back a gzipped file rather than a gzipped response,
            // so nothing below the socket unpacks it for us.
            if (url.getPath() != null && url.getPath().endsWith(".gz")) {
                stream = new GZIPInputStream(stream);
            }
            final Uri subtitleUri = Utils.convertInputStreamToUTF(activity, url, stream, cacheName);
            if (subtitleUri == null) {
                return false;
            }
            activity.runOnUiThread(() -> {
                activity.mPrefs.updateSubtitle(subtitleUri);
                if (activity.addSubtitleTrack(subtitleUri) && announcement != null) {
                    Toast.makeText(activity, announcement, Toast.LENGTH_SHORT).show();
                }
            });
            return true;
        } catch (IOException e) {
            Utils.log("subtitle download: " + e);
            return false;
        }
    }
}
