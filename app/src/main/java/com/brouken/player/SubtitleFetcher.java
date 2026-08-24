package com.brouken.player;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int READ_TIMEOUT_SEC = 20;
    /** Whole-list budget, so a dozen dead guesses cost one wait rather than a dozen. */
    private static final long TOTAL_BUDGET_MS = 40_000;

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

    /** The name the file was uploaded under, out of {@code attachment; filename="…"}. */
    private static final Pattern ATTACHMENT_NAME = Pattern.compile("filename[^=]*=\"?([^\";]+)");

    public SubtitleFetcher(PlayerActivity activity, List<Uri> urls) {
        this(activity, urls, null);
    }

    /**
     * @param cacheName what to call the downloaded copy; null takes the name from the URL. A subtitle
     *                  found online has no useful name in its URL — often just an id — while the caller
     *                  knows the language, and the name is where the language is read back from.
     */
    public SubtitleFetcher(PlayerActivity activity, List<Uri> urls, String cacheName) {
        this.activity = activity;
        this.urls = urls;
        this.cacheName = cacheName;
    }

    /** Fire and forget: download in the background and attach whatever turns up. */
    public void start() {
        final Thread thread = new Thread(() -> {
            final Uri file = fetchNow();
            if (file == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                activity.mPrefs.updateSubtitle(file);
                activity.addSubtitleTrack(file);
            });
        }, "SubtitleFetcher");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Downloads on the calling thread. The online search runs on one already and needs the answer:
     * a source whose file will not come down has to give way to the next source, not end the search.
     *
     * @return the local copy, or null when none of the candidates produced a usable file
     */
    Uri fetchNow() {
        final long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
        for (Uri url : urls) {
            // The sidecar lookup hands over a dozen guessed file names, most of which do not exist.
            // One timeout each against an unreachable host would hold this thread for minutes, so the
            // list as a whole gets a budget rather than every entry getting its own patience.
            if (Thread.currentThread().isInterrupted() || System.currentTimeMillis() > deadline) {
                return null;
            }
            final Uri file = fetch(CLIENT, url);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    /** @return the local copy, or null to try the next candidate. */
    private Uri fetch(OkHttpClient client, Uri url) {
        // Prevents IllegalArgumentException in okhttp3.Request.Builder.
        if (HttpUrl.parse(url.toString()) == null) {
            return null;
        }
        final Request request = new Request.Builder().url(url.toString()).build();
        try (Response response = client.newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null || body.contentLength() > MAX_BYTES) {
                return null;
            }
            // The aggregators say nothing about forced tracks in their index — and the Stremio addon
            // has no field that could — but the file still arrives under the name its uploader gave
            // it, and that is where "forced" is written. A forced track is a few dozen cues for
            // foreign dialogue: switched on as the only subtitle there is, it reads as a broken
            // player rather than as a subtitle, so it gives way to the next candidate.
            final String name = attachmentName(response);
            if (SubtitleSearch.forcedName(name)) {
                Utils.log("subtitle download: forced track, skipping " + name);
                return null;
            }
            InputStream stream = body.byteStream();
            // The legacy OpenSubtitles API hands back a gzipped file rather than a gzipped response,
            // so nothing below the socket unpacks it for us.
            if (url.getPath() != null && url.getPath().endsWith(".gz")) {
                stream = new GZIPInputStream(stream);
            }
            return Utils.convertInputStreamToUTF(activity, url, stream, cacheName);
        } catch (IOException e) {
            Utils.log("subtitle download: " + e);
            return null;
        }
    }

    /** @return the uploader's own file name, which subs5.strem.io sends back, or null when none does */
    private static String attachmentName(Response response) {
        final String disposition = response.header("Content-Disposition");
        if (disposition == null) {
            return null;
        }
        final Matcher matcher = ATTACHMENT_NAME.matcher(disposition);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
