package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The connections to SMB shares, and the address arithmetic around them. Shared by the two things
 * that need a share: the data source that plays a file, and the browser that lists a folder.
 *
 * <p>One connection per host and share, kept for as long as the process lives: the extractor opens
 * a file many times over a playback and a listing walks a tree, and a TCP connect, a protocol
 * negotiation and an NTLM exchange for each of those is what makes reading over a proxy slow.
 *
 * <p>ponytail: one entry per share, dropped and rebuilt on the first failure. A pool would only earn
 * its keep with two shares read at once.
 */
final class SmbSessions {

    static final String SCHEME = "smb";

    private static final Map<String, DiskShare> SHARES = new HashMap<>();
    private static final Map<String, SMBClient> CLIENTS = new HashMap<>();

    private SmbSessions() {
    }

    /** Opens a file for reading, reconnecting once if the connection we had has gone. */
    static com.hierynomus.smbj.share.File open(final Context context, final Uri uri)
            throws IOException {
        try {
            return openFile(context, uri, false);
        } catch (final Exception first) {
            // A cached connection the server has since dropped fails here and nowhere else, so the
            // one retry belongs here rather than in a keep-alive. A refused password or a share that
            // is not there will refuse again, and retrying it only doubles the wait before the
            // person is told: measured 11 s to give up on a wrong password, four of those retries
            // the player's own.
            if (permanent(first)) {
                throw first instanceof IOException ? (IOException) first : new IOException(first);
            }
            return openFile(context, uri, true);
        }
    }

    /** Lists a folder, reconnecting once on the same terms as {@link #open}. */
    static List<com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation> list(
            final Context context, final Uri uri) throws IOException {
        try {
            return share(context, uri, false).list(pathOf(uri));
        } catch (final Exception first) {
            if (permanent(first)) {
                throw first instanceof IOException ? (IOException) first : new IOException(first);
            }
            try {
                return share(context, uri, true).list(pathOf(uri));
            } catch (final IOException e) {
                throw e;
            } catch (final Exception e) {
                throw new IOException(e);
            }
        }
    }

    private static com.hierynomus.smbj.share.File openFile(final Context context, final Uri uri,
                                                           final boolean fresh) throws IOException {
        try {
            return share(context, uri, fresh).openFile(pathOf(uri),
                    EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN, null);
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    private static synchronized DiskShare share(final Context context, final Uri uri,
                                                final boolean fresh) throws IOException {
        final String name = shareOf(uri);
        final String key = uri.getHost() + ":" + uri.getPort() + "/" + name;

        if (!fresh) {
            final DiskShare cached = SHARES.get(key);
            if (cached != null && cached.isConnected()) {
                return cached;
            }
        }
        drop(key);

        final SmbConfig config = SmbConfig.builder()
                .withTimeout(20, TimeUnit.SECONDS)
                .withSoTimeout(30, TimeUnit.SECONDS)
                .withDfsEnabled(false)
                .build();
        final SMBClient client = new SMBClient(config);
        try {
            final int port = uri.getPort() > 0 ? uri.getPort() : SMBClient.DEFAULT_PORT;
            final Connection connection = client.connect(uri.getHost(), port);
            final Session session = connection.authenticate(credentials(context, uri, name));
            final DiskShare share = (DiskShare) session.connectShare(name);
            CLIENTS.put(key, client);
            SHARES.put(key, share);
            return share;
        } catch (final IOException e) {
            close(client);
            throw e;
        } catch (final Exception e) {
            close(client);
            throw new IOException(e);
        }
    }

    /**
     * Credentials come from the address when it carries them — either in the authority,
     * {@code smb://user:password@host/share}, or as the query parameters the reference player
     * established, {@code ?u=&p=&d=}; both are pasted in the wild. Otherwise from the saved place,
     * which is where a folder browsed in the app gets them, so that no address the app stores or
     * plays has a password in it.
     */
    private static AuthenticationContext credentials(final Context context, final Uri uri,
                                                     final String share) {
        String user = user(uri);
        String password = password(uri);
        if (user.isEmpty()) {
            user = NetworkPlaces.user(context, uri.getHost(), share);
            password = NetworkPlaces.password(context, uri.getHost(), share);
        }
        if (user.isEmpty()) {
            return AuthenticationContext.anonymous();
        }
        final String domain = value(uri, "d");
        return new AuthenticationContext(user, password.toCharArray(),
                domain.isEmpty() ? null : domain);
    }

    private static void drop(final String key) {
        SHARES.remove(key);
        close(CLIENTS.remove(key));
    }

    private static void close(@Nullable final SMBClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (final Exception ignored) {
                // Nothing to do about a connection that will not close.
            }
        }
    }

    /** True for the failures a different password or user would fix, and only those. */
    static boolean needsPassword(@Nullable final Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SMBApiException) {
                switch (((SMBApiException) t).getStatus()) {
                    case STATUS_LOGON_FAILURE:
                    case STATUS_LOGON_TYPE_NOT_GRANTED:
                    case STATUS_ACCESS_DENIED:
                    case STATUS_ACCOUNT_DISABLED:
                    case STATUS_PASSWORD_EXPIRED:
                        return true;
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    /** True for the failures that will fail again: credentials, permissions, a wrong name. */
    static boolean permanent(@Nullable final Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SMBApiException) {
                switch (((SMBApiException) t).getStatus()) {
                    case STATUS_LOGON_FAILURE:
                    case STATUS_LOGON_TYPE_NOT_GRANTED:
                    case STATUS_ACCESS_DENIED:
                    case STATUS_ACCOUNT_DISABLED:
                    case STATUS_PASSWORD_EXPIRED:
                    case STATUS_BAD_NETWORK_NAME:
                    case STATUS_BAD_NETWORK_PATH:
                    case STATUS_OBJECT_NAME_NOT_FOUND:
                    case STATUS_OBJECT_PATH_NOT_FOUND:
                        return true;
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    /** {@code smb://host/share/dir/file.mkv} — the first path segment is the share. */
    static String shareOf(final Uri uri) throws IOException {
        final List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) {
            throw new IOException("No share in " + uri);
        }
        return segments.get(0);
    }

    /** Everything under the share, in the backslash form SMB2 asks for. */
    static String pathOf(final Uri uri) {
        final List<String> segments = uri.getPathSegments();
        final StringBuilder path = new StringBuilder();
        for (int i = 1; i < segments.size(); i++) {
            if (path.length() > 0) {
                path.append('\\');
            }
            path.append(segments.get(i));
        }
        return path.toString();
    }

    static String user(final Uri uri) {
        final String info = uri.getUserInfo();
        if (info == null || info.isEmpty()) {
            return value(uri, "u");
        }
        final int colon = info.indexOf(':');
        return Uri.decode(colon < 0 ? info : info.substring(0, colon));
    }

    static String password(final Uri uri) {
        final String info = uri.getUserInfo();
        if (info == null || info.isEmpty()) {
            return value(uri, "p");
        }
        final int colon = info.indexOf(':');
        return colon < 0 ? "" : Uri.decode(info.substring(colon + 1));
    }

    private static String value(final Uri uri, final String key) {
        final String value = uri.getQueryParameter(key);
        return value == null ? "" : value;
    }
}
