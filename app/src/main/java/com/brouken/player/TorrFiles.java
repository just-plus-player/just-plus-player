package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.NetworkDocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A TorrServer, dressed as a folder of folders.
 *
 * <p>The torrents it holds are the folders; the files inside a torrent are the rows. Playing one is
 * a plain HTTP read of {@code /stream}, so nothing new carries the bytes — the scheme is rewritten
 * on the way out and the credentials of the saved place go with it, exactly as WebDAV's are (see the
 * resolver in {@code PlayerActivity}).
 *
 * <p><b>No library, no new dependency.</b> The whole protocol used here is two POSTs of a one-field
 * JSON object to {@code /torrents} and a GET of {@code /echo}, which is okhttp — already here for
 * the media itself — and {@code org.json}, which is the platform's.
 *
 * <p><b>The address.</b> A torrent has no path on the server: a file is named by the torrent's
 * infohash and its own index. So the address carries what is needed to ask for it again, and the
 * path is the trail of titles, because the player reads a played file's name off the last segment:
 *
 * <pre>
 * torr://host:8090                       a saved server, plain http
 * torrs://host/ts                        the same behind an https reverse proxy: the path is its prefix
 * torr://host:8090/Dune.2024?b=…&amp;h=…      one torrent, as a folder
 * torr://host:8090/Dune.2024/Dune.mkv?b=…&amp;h=…&amp;i=2   one file in it
 * </pre>
 *
 * {@code b} is the base address every request goes to — carried for the same reason DLNA carries its
 * control address, since below the root the path is titles and the prefix would otherwise be lost.
 * {@code h} is the infohash and {@code i} the file's index, which TorrServer numbers from one.
 *
 * <p>Unlike a DLNA address this one holds no {@code res=}: the stream address is derived from those
 * three every time, so a favourite still opens after the server has been restarted and rebuilt.
 */
final class TorrFiles {

    static final String SCHEME = "torr";
    static final String SCHEME_SECURE = "torrs";

    /** What a TorrServer listens on when nobody has moved it. */
    static final int DEFAULT_PORT = 8090;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * The client the {@code /echo} question is asked with: the media one's connection pool, and a
     * second and a half of patience instead of its two minutes. A subnet is 254 addresses.
     */
    private static final okhttp3.OkHttpClient ASKING = PlayerActivity.MEDIA_HTTP_CLIENT.newBuilder()
            .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build();

    /** {@code TorrentClosed} — the one state that will never grow a file list. */
    private static final int CLOSED = 4;
    /**
     * How long to keep asking a torrent for its file list. The server's own page asks for a minute;
     * half of that is already far past the point where a torrent with peers has answered, and it is
     * a browser folder that somebody is waiting in front of.
     */
    private static final long INFO_DEADLINE_MS = 30_000;
    private static final long INFO_POLL_MS = 700;

    private static final String TORRENTS = "/torrents";
    private static final String VIEWED = "/viewed";

    private static final String BASE = "b";
    private static final String HASH = "h";
    private static final String INDEX = "i";

    private TorrFiles() {
    }

    static boolean speaks(@Nullable final Uri uri) {
        final String scheme = uri == null ? null : uri.getScheme();
        return SCHEME.equals(scheme) || SCHEME_SECURE.equals(scheme);
    }

    /** Where every request goes: carried in the address, or the saved place's own scheme and path. */
    static String base(final Uri uri) {
        final String carried = uri.getQueryParameter(BASE);
        if (carried != null && !carried.isEmpty()) {
            return carried;
        }
        final StringBuilder base = new StringBuilder(
                SCHEME_SECURE.equals(uri.getScheme()) ? "https://" : "http://");
        base.append(uri.getAuthority());
        for (final String segment : uri.getPathSegments()) {
            base.append('/').append(Uri.encode(segment));
        }
        return base.toString();
    }

    /**
     * The path a server sits under, which for a place saved behind a reverse proxy is its prefix and
     * for one reached directly is empty. What {@link NetworkPlaces} files the place — and therefore
     * its password — under.
     */
    static String prefix(final Uri uri) {
        return android.text.TextUtils.join("/", Uri.parse(base(uri)).getPathSegments());
    }

    /** The infohash this address belongs to, or null at the top of a server. */
    @Nullable
    static String hashOf(final Uri uri) {
        final String hash = uri.getQueryParameter(HASH);
        return hash == null || hash.isEmpty() ? null : hash;
    }

    /**
     * The plain http(s) address a file's bytes come from. The same URL the server's own web page
     * builds: the file name for anything that reads one off the path, and the pair that actually
     * names the file in the query.
     */
    static Uri stream(final Uri uri) {
        final String name = uri.getLastPathSegment();
        return Uri.parse(base(uri) + "/stream/" + Uri.encode(name == null ? "file" : name)
                + "?link=" + Uri.encode(String.valueOf(hashOf(uri)))
                + "&index=" + Uri.encode(String.valueOf(uri.getQueryParameter(INDEX)))
                + "&play");
    }

    static DocumentFile folder(final Context context, final Uri uri, final String name) {
        return new NetworkDocumentFile(null, uri, name, true, 0, 0, new Lister(context));
    }

    /**
     * The folder holding this address, or null at the top of a server. One level, which is all
     * anyone asks for: the player wants a played file's siblings, and the browser walks back down
     * its own trail.
     */
    @Nullable
    static DocumentFile parent(final Context context, final Uri uri) {
        if (hashOf(uri) == null) {
            return null;
        }
        final String base = base(uri);
        final List<String> segments = uri.getPathSegments();
        if (uri.getQueryParameter(INDEX) == null || segments.size() < 2) {
            // A torrent, so the folder above it is the server itself — spelt as the saved place
            // spells it, which is what lets the browser recognise the row as one.
            final Uri root = root(base);
            final List<String> path = root.getPathSegments();
            return folder(context, root, path.isEmpty() ? root.getHost() : path.get(path.size() - 1));
        }
        final Uri.Builder above = new Uri.Builder().scheme(uri.getScheme())
                .encodedAuthority(uri.getAuthority());
        for (int i = 0; i < segments.size() - 1; i++) {
            above.appendPath(segments.get(i));
        }
        return folder(context, above.appendQueryParameter(BASE, base)
                        .appendQueryParameter(HASH, hashOf(uri)).build(),
                segments.get(segments.size() - 2));
    }

    /**
     * A torrent, and the picture its server offered for it.
     *
     * <p>The one thing a row over the network gets for nothing: it arrived with the listing and it is
     * an address rather than a file to be read. The same trick {@link DlnaFiles} plays with
     * {@code albumArtURI}, and the reason both are reached through {@link NetworkFiles#artwork}.
     */
    private static final class Listed extends NetworkDocumentFile {
        @Nullable
        final String art;
        /** How long it plays for, where the server measured it, or 0. */
        final long running;

        Listed(final DocumentFile parent, final Uri uri, final String name, final boolean directory,
               final long length, final long modified, @Nullable final Children children,
               @Nullable final String art, final long running) {
            super(parent, uri, name, directory, length, modified, children);
            this.art = art;
            this.running = running;
        }
    }

    /** How many files the server listed, however it listed them. */
    private static int countFiles(@Nullable final JSONArray stats) {
        return stats == null ? 0 : stats.length();
    }

    /**
     * How long this file plays for, where the server said so. See the note in {@code files()}: only a
     * torrent holding one file can state it, because the server keeps one time per torrent.
     */
    static long runningTime(final DocumentFile file) {
        return file instanceof Listed ? ((Listed) file).running : 0;
    }

    /** The picture this torrent's server offered for it, or null. */
    @Nullable
    static String artwork(final DocumentFile file) {
        return file instanceof Listed ? ((Listed) file).art : null;
    }

    /** A poster address worth using: an absolute http one, and not a blob or a relative path. */
    @Nullable
    private static String poster(final JSONObject torrent) {
        final String said = torrent.optString("poster").trim();
        return said.startsWith("http") ? said : null;
    }

    /** A torrent whose file list the server has not produced yet. */
    static final class StillLoading extends IOException {
        StillLoading(final String name, final String state) {
            super("Torrent " + name + " has no file list yet"
                    + (state == null || state.isEmpty() ? "" : " (" + state + ")"));
        }
    }

    /** A discovered or typed server as a saved place. */
    static NetworkPlaces.Place place(final String name, final String host, final int port,
                                     final boolean secure, final String prefix, final String user) {
        return new NetworkPlaces.Place(secure ? SCHEME_SECURE : SCHEME, name, host, port,
                prefix == null ? "" : prefix, user);
    }

    /**
     * True when something at this address answers {@code /echo} the way a TorrServer does.
     *
     * <p>On its own short timeout, sharing the media client's connection pool but none of its
     * patience: that one waits two minutes for a stalling film, and this is asked once per address
     * of a subnet.
     */
    static boolean answers(final String base) {
        try {
            final Request request = new Request.Builder().url(base + "/echo").build();
            try (Response response = ASKING.newCall(request).execute()) {
                final ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    return false;
                }
                final String said = body.string().trim();
                if (said.isEmpty() || said.contains("<")) {
                    // Any web server answers something to any path, and a page of HTML is not a
                    // version.
                    return false;
                }
                // A version string and nothing else — "MatriX.135" — or, from a server with its
                // account database turned on, the refusal to say so: {"accsdb":true,"msg":…}. That
                // answer is as certain a TorrServer as the version is, and it is too long to pass
                // the bound, which is what made such a server save itself as a WebDAV one.
                return said.length() < 64 || said.contains("\"accsdb\"");
            }
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Tells the server how far into this file somebody got, so the next player to open it — Lampa,
     * the server's own page, this app on another device — opens it there.
     *
     * <p>Best effort: a timecode is a convenience, and a server that will not take one must not turn
     * closing a film into an error. Called off the main thread.
     */
    static void remember(final Context context, final Uri uri, final long positionMs) {
        final String hash = hashOf(uri);
        final String index = uri.getQueryParameter(INDEX);
        if (hash == null || index == null) {
            return;
        }
        try {
            command(context, uri, VIEWED, new JSONObject().put("action", "set").put("hash", hash)
                    .put("file_index", Integer.parseInt(index))
                    .put("timecode", positionMs / 1000.0));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The timecodes the server holds for this torrent, by file index, in milliseconds.
     *
     * <p>Empty for a server that keeps none — the setting is {@code TrackTimecode} and it can be
     * off — and for one that would not answer, which is not worth failing a listing over.
     */
    private static Map<Integer, Long> viewed(final Context context, final Uri uri,
                                             final String hash) {
        final Map<Integer, Long> seen = new HashMap<>();
        try {
            final JSONArray listed = new JSONArray(command(context, uri, VIEWED,
                    new JSONObject().put("action", "list").put("hash", hash)));
            for (int i = 0; i < listed.length(); i++) {
                final JSONObject was = listed.optJSONObject(i);
                // Filtered here as well as asked for: a server that ignores the hash answers with
                // every file it has ever played, and those indexes mean other torrents.
                if (was == null || !hash.equalsIgnoreCase(was.optString("hash"))) {
                    continue;
                }
                final long ms = (long) (was.optDouble("timecode", 0) * 1000);
                if (ms > 0) {
                    seen.put(was.optInt("file_index"), ms);
                }
            }
        } catch (final Exception e) {
            // A timecode is a convenience. A server that keeps none, or will not say, is not a
            // reason to fail the listing that asked.
            e.printStackTrace();
        }
        return seen;
    }

    /** Hands the server a magnet link or a torrent URL to take on. */
    static void add(final Context context, final Uri place, final String link) throws IOException {
        try {
            command(context, place, TORRENTS, new JSONObject().put("action", "add").put("link", link)
                    .put("save_to_db", true));
        } catch (final org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** Takes a torrent off the server for good. */
    static void remove(final Context context, final Uri place, final String hash)
            throws IOException {
        try {
            command(context, place, TORRENTS, new JSONObject().put("action", "rem").put("hash", hash));
        } catch (final org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    /** The saved place's address, rebuilt from the base a child carries. */
    private static Uri root(final String base) {
        final Uri http = Uri.parse(base);
        final Uri.Builder root = new Uri.Builder()
                .scheme("https".equals(http.getScheme()) ? SCHEME_SECURE : SCHEME)
                .encodedAuthority(http.getEncodedAuthority() == null ? "" : http.getEncodedAuthority());
        for (final String segment : http.getPathSegments()) {
            root.appendPath(segment);
        }
        return root.build();
    }

    /**
     * One POST to one endpoint of the server, answered with whatever that action answers with.
     *
     * <p>The endpoint is a parameter and not a constant inside here, and that is the whole point:
     * it used to be {@code /torrents} for everybody, and the call that meant to write a timecode
     * inherited it. {@code {"action":"set"}} means "store this timecode" to {@code /viewed} and
     * "overwrite this torrent's title, poster, category and data" to {@code /torrents} — so that
     * one wrong word wiped the poster off every film it played.
     */
    private static String command(final Context context, final Uri uri, final String endpoint,
                                  final JSONObject body) throws IOException {
        final Request.Builder request = new Request.Builder()
                .url(base(uri) + endpoint)
                .post(RequestBody.create(body.toString(), JSON));
        final String authorization = NetworkFiles.authorization(context, uri);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try (Response response = PlayerActivity.MEDIA_HTTP_CLIENT.newCall(request.build()).execute()) {
            if (!response.isSuccessful()) {
                // The same exception WebDAV raises, so that a 401 is recognised by the one test the
                // browser and the player already share — see NetworkFiles.needsPassword.
                throw new DavFiles.DavException(response.code(), response.message());
            }
            final ResponseBody answer = response.body();
            return answer == null ? "" : answer.string();
        }
    }

    /** The torrents on a server, and the files inside one. */
    private static final class Lister implements NetworkDocumentFile.Children {

        private final Context context;

        Lister(final Context context) {
            this.context = context.getApplicationContext();
        }

        @NonNull
        @Override
        public DocumentFile[] of(@NonNull final NetworkDocumentFile folder) {
            try {
                final DocumentFile[] children = hashOf(folder.getUri()) == null
                        ? torrents(folder) : files(folder);
                NetworkFiles.listed();
                return children;
            } catch (final Exception e) {
                // An empty folder and a server that would not answer are the same empty list from
                // here, so the reason is left for the caller rather than only printed.
                e.printStackTrace();
                NetworkFiles.failed(e);
                return new DocumentFile[0];
            }
        }

        /** The server's own list, one folder per torrent. */
        private DocumentFile[] torrents(final NetworkDocumentFile folder) throws Exception {
            final Uri uri = folder.getUri();
            final String base = base(uri);
            final JSONArray listed = new JSONArray(
                    command(context, uri, TORRENTS, new JSONObject().put("action", "list")));
            final List<DocumentFile> children = new ArrayList<>();
            for (int i = 0; i < listed.length(); i++) {
                final JSONObject torrent = listed.optJSONObject(i);
                if (torrent == null) {
                    continue;
                }
                final String hash = torrent.optString("hash");
                final String title = label(torrent);
                if (hash.isEmpty() || title.isEmpty()) {
                    continue;
                }
                final Uri at = new Uri.Builder().scheme(uri.getScheme())
                        .encodedAuthority(uri.getAuthority())
                        .appendPath(title)
                        .appendQueryParameter(BASE, base)
                        .appendQueryParameter(HASH, hash)
                        .build();
                // What the list already says about the torrent, and a row would otherwise have to
                // open it to learn: how big the whole thing is, and when the server took it in.
                children.add(new Listed(folder, at, title, true, torrent.optLong("torrent_size"),
                        torrent.optLong("timestamp") * 1000L, this, poster(torrent), 0));
            }
            return children.toArray(new DocumentFile[0]);
        }

        /**
         * The files inside one torrent. Asked for by hash rather than read out of the list, because
         * a torrent the server has not opened yet lists no files at all.
         *
         * <p><b>Asked until it answers.</b> {@code get} never waits: it opens the torrent and returns
         * whatever is known this instant, and for one that was only in the database that is a status
         * with no {@code file_stats} at all — the metadata is still being fetched from peers. Asking
         * once therefore showed an empty folder, and going back and in again showed the files,
         * because by then the first ask had done the warming. So it is asked until the files are
         * there, which is what the server's own web page does for the same reason.
         *
         * <p>ponytail: flat. A file's name is the last component of its path, so a pack of several
         * seasons is one list rather than a tree. Nest it if a real pack proves unnavigable.
         */
        private DocumentFile[] files(final NetworkDocumentFile folder) throws Exception {
            final Uri uri = folder.getUri();
            final String base = base(uri);
            final String hash = hashOf(uri);
            final JSONObject ask = new JSONObject().put("action", "get").put("hash", hash);
            final long until = System.currentTimeMillis() + INFO_DEADLINE_MS;
            JSONArray stats;
            String art = null;
            double seconds = 0;
            while (true) {
                final JSONObject torrent = new JSONObject(command(context, uri, TORRENTS, ask));
                stats = torrent.optJSONArray("file_stats");
                art = poster(torrent);
                seconds = torrent.optDouble("duration_seconds", 0);
                if (stats != null && stats.length() > 0) {
                    break;
                }
                if (torrent.optInt("stat", -1) == CLOSED
                        || System.currentTimeMillis() >= until) {
                    // Closed, or it has had long enough. An empty list here would read as a torrent
                    // with nothing in it, which is the one thing it is not.
                    throw new StillLoading(folder.getName(), torrent.optString("stat_string"));
                }
                Thread.sleep(INFO_POLL_MS);
            }
            // Asked for once per torrent, alongside the file list, because this is the thread that
            // can wait for it: the player reads a position on the main thread and could not.
            final Map<Integer, Long> seen = viewed(context, uri, hash);
            // The server states one running time for a torrent, having probed it. On a torrent of one
            // file - which is most films - that time is that file's, and it is the only way a row here
            // can have one: a file this app has not streamed cannot be opened to be asked. On a
            // torrent of several it belongs to whichever of them the server looked at, so it is left
            // alone rather than printed against twelve episodes.
            final long plays = countFiles(stats) == 1 ? (long) (seconds * 1000) : 0;
            final List<DocumentFile> children = new ArrayList<>();
            for (int i = 0; stats != null && i < stats.length(); i++) {
                final JSONObject stat = stats.optJSONObject(i);
                if (stat == null) {
                    continue;
                }
                // TorrServer numbers its files from one and says so — "in web id 0 is undefined" —
                // so the id it states is what /stream wants, and it is not recomputed here.
                final int index = stat.optInt("id");
                final String name = leaf(stat.optString("path"));
                if (index <= 0 || name.isEmpty()) {
                    continue;
                }
                final Uri at = new Uri.Builder().scheme(uri.getScheme())
                        .encodedAuthority(uri.getAuthority())
                        .appendPath(folder.getName())
                        .appendPath(name)
                        .appendQueryParameter(BASE, base)
                        .appendQueryParameter(HASH, hash)
                        .appendQueryParameter(INDEX, String.valueOf(index))
                        .build();
                // The torrent's own picture on every file in it: there is no other, and it is what
                // the player shows for what is playing and beside its neighbours in the panel.
                children.add(new Listed(folder, at, name, false, stat.optLong("length"), 0, null,
                        art, plays));
                // The server is where a torrent's timecode lives, so what it says is written into
                // this app's own map before anything reads it. That is the whole of "continue where
                // another player stopped": everything downstream - the run under the row, the
                // position the player opens at - already reads that map and learns nothing new.
                final Long was = seen.get(index);
                if (was != null) {
                    Prefs.rememberPosition(context, at, was);
                }
            }
            return children.toArray(new DocumentFile[0]);
        }

        /**
         * What to call a torrent: the title someone gave it, or the name that came with it. A path
         * segment cannot hold a slash, and either of those can.
         */
        private static String label(final JSONObject torrent) {
            final String title = torrent.optString("title").trim();
            final String name = title.isEmpty() ? torrent.optString("name").trim() : title;
            return name.replace('/', '⁄').replace('\\', '⁄');
        }

        /** The last component of {@code Season 1/ep01.mkv}, whichever separator the torrent used. */
        private static String leaf(final String path) {
            final String trimmed = path == null ? "" : path.trim();
            final int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
            return slash < 0 ? trimmed : trimmed.substring(slash + 1);
        }
    }
}
