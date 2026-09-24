package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

/**
 * Which protocol a network address belongs to, and where the reason for an empty listing is left.
 *
 * <p>Everything above here — the browser's rows and search, the player's next-file step and folder
 * playlist — deals in {@link DocumentFile} and never learns which protocol it is looking at.
 */
final class NetworkFiles {

    /**
     * Why the last listing on this thread came back empty, if it was not simply empty.
     *
     * <p>A {@code DocumentFile} cannot report a failure — {@code listFiles()} returns an array and
     * nothing else — so the reason is left here for whoever asked, on the thread that asked. Which
     * is enough: the browser lists on one thread and reads this immediately afterwards.
     */
    private static final ThreadLocal<Throwable> FAILURE = new ThreadLocal<>();

    private NetworkFiles() {
    }

    /** True for the addresses this app reaches over a network of its own accord. */
    static boolean isNetwork(@Nullable final Uri uri) {
        final String scheme = uri == null ? null : uri.getScheme();
        return SmbSessions.SCHEME.equals(scheme) || DavFiles.SCHEME.equals(scheme)
                || DavFiles.SCHEME_SECURE.equals(scheme) || DlnaFiles.SCHEME.equals(scheme)
                || TorrFiles.SCHEME.equals(scheme) || TorrFiles.SCHEME_SECURE.equals(scheme);
    }

    /**
     * The address to print under a saved place's name: where it answers, and nothing else. Two
     * servers can both be called Home, and the name is the part somebody typed.
     *
     * <p>No scheme. Four of the six here are this app's own bookkeeping - {@code dlna}, {@code torr},
     * {@code dav} and {@code davs} name the protocol so that one {@code Uri} can say which code
     * opens it, and nothing outside this process has ever heard of them; {@code dlna://host:8200} is
     * not an address anybody can use. The two that are real, {@code smb} and the {@code http} a media
     * server or a TorrServer actually speaks, are not worth the width either: the row already carries
     * a glyph for its protocol, and this line is the one place that says <em>which machine</em>. What
     * is left is what the picker prints while the machine is being chosen, so a place reads the same
     * before and after it is saved.
     *
     * <p>A port the protocol would have assumed is left off with it - a media server that announced
     * none at all was filed under 80 by {@code DlnaFiles.place} rather than by anything the server
     * said. A media server's path goes too: it is a control endpoint rather than the folder its
     * objects are under - the same reason {@code Place.holds} does not ask about it - so it says
     * nothing about where the files are and is long enough to push the host off the line.
     *
     * @return null for an address that is not on a network, which has nothing of the kind to print
     */
    @Nullable
    static String address(@Nullable final Uri uri) {
        if (!isNetwork(uri)) {
            return null;
        }
        final String scheme = uri.getScheme();
        final boolean secure = DavFiles.SCHEME_SECURE.equals(scheme)
                || TorrFiles.SCHEME_SECURE.equals(scheme);
        final boolean assumed = uri.getPort() == (secure ? 443 : 80)
                && !SmbSessions.SCHEME.equals(scheme);
        final String at = (uri.getHost() == null ? "" : uri.getHost())
                + (uri.getPort() > 0 && !assumed ? ":" + uri.getPort() : "");
        return DlnaFiles.SCHEME.equals(scheme) ? at : at + uri.getPath();
    }

    /** The folder at this address, whichever protocol it is. Nothing is asked until it is listed. */
    static DocumentFile folder(final Context context, final Uri uri, final String name) {
        if (DlnaFiles.speaks(uri)) {
            return DlnaFiles.folder(context, uri, name);
        }
        if (TorrFiles.speaks(uri)) {
            return TorrFiles.folder(context, uri, name);
        }
        return DavFiles.speaks(uri)
                ? DavFiles.folder(context, uri, name)
                : SmbFiles.folder(context, uri, name);
    }

    /** The folder holding this address, or null at the top of a place. */
    @Nullable
    static DocumentFile parent(final Context context, final Uri uri) {
        if (DlnaFiles.speaks(uri)) {
            return DlnaFiles.parent(uri);
        }
        if (TorrFiles.speaks(uri)) {
            return TorrFiles.parent(context, uri);
        }
        return DavFiles.speaks(uri) ? DavFiles.parent(context, uri) : SmbFiles.parent(context, uri);
    }

    /**
     * The picture the listing offered for this row, or null.
     *
     * <p>Two protocols state one and the rest do not: a media server names an {@code albumArtURI},
     * and a torrent server a poster. Asked here so no caller has to know which it is looking at.
     */
    @Nullable
    static String artwork(final DocumentFile file) {
        final String served = DlnaFiles.artwork(file);
        return served != null ? served : TorrFiles.artwork(file);
    }

    /** The file at this address, named after its last path segment. */
    static DocumentFile file(final Uri uri) {
        return SmbFiles.file(uri);
    }

    /** The reason, and it is taken away in the reading: a failure is answered once. */
    @Nullable
    static Throwable failure() {
        final Throwable failure = FAILURE.get();
        FAILURE.remove();
        return failure;
    }

    static void failed(final Throwable failure) {
        FAILURE.set(failure);
    }

    static void listed() {
        FAILURE.remove();
    }

    /**
     * True for the failures a different password or user would fix, whichever protocol said so. SMB
     * answers with a status code of its own; WebDAV, being http, answers 401 or 403.
     */
    static boolean needsPassword(@Nullable final Throwable failure) {
        if (SmbSessions.needsPassword(failure)) {
            return true;
        }
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DavFiles.DavException) {
                final int code = ((DavFiles.DavException) t).code;
                return code == 401 || code == 403;
            }
            if (t instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                final int code = ((androidx.media3.datasource.HttpDataSource
                        .InvalidResponseCodeException) t).responseCode;
                return code == 401 || code == 403;
            }
        }
        return false;
    }

    /**
     * {@code Basic} for the place that opens this address, or null where it is browsed anonymously.
     *
     * <p>Here rather than in one protocol, because it reads nothing but the saved place: WebDAV and
     * a TorrServer behind a reverse proxy authenticate the same way, and so does whatever plain HTTP
     * source comes next.
     */
    @Nullable
    static String authorization(final Context context, final Uri uri) {
        final String name = user(context, uri);
        if (name.isEmpty()) {
            return null;
        }
        return basic(name, password(context, uri));
    }

    private static String basic(final String user, final String password) {
        final String pair = user + ":" + password;
        return "Basic " + android.util.Base64.encodeToString(
                pair.getBytes(java.nio.charset.Charset.forName("UTF-8")),
                android.util.Base64.NO_WRAP);
    }

    /** The credentials a saved place holds for this address, or empty strings. */
    static String user(final Context context, final Uri uri) {
        return uri.getHost() == null ? "" : NetworkPlaces.user(context, uri.getHost(), share(uri));
    }

    static String password(final Context context, final Uri uri) {
        return uri.getHost() == null ? "" : NetworkPlaces.password(context, uri.getHost(), share(uri));
    }

    private static String share(final Uri uri) {
        if (TorrFiles.speaks(uri)) {
            // Below the root a torrent address spells its path as torrent titles, so the first
            // segment names no place at all and the lookup would fall through to whatever else sits
            // on that host. The base the address carries is the place itself.
            return TorrFiles.prefix(uri);
        }
        final java.util.List<String> segments = uri.getPathSegments();
        return segments.isEmpty() ? "" : segments.get(0);
    }
}
