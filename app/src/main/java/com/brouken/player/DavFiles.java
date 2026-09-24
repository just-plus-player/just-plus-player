package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A folder on a WebDAV server, dressed as a {@link DocumentFile}.
 *
 * <p>WebDAV is HTTP, so nothing new carries the bytes: the player reads a {@code dav://} address
 * through the same OkHttp source as any link, with the scheme rewritten and the credentials added on
 * the way (see the resolver in {@code PlayerActivity}). Only the listing is particular, and it is
 * one {@code PROPFIND} — a request with an XML body, parsed by the platform's own pull parser. No
 * library: sardine and jackrabbit, which the two reference players carry, exist to do this one
 * request and to model the rest of the protocol, which a player never touches.
 *
 * <p>The scheme is kept as {@code dav}/{@code davs} all the way down rather than turned into
 * {@code http} at the door. That way the folder playlist, the resume position map, the saved trail
 * and the next-file step all recognise a network address by its scheme, exactly as they do for SMB,
 * instead of every one of them needing to know that this particular http link is really a folder.
 */
final class DavFiles {

    static final String SCHEME = "dav";
    static final String SCHEME_SECURE = "davs";

    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    /** Only what a listing needs. Asking for everything makes some servers answer with far more. */
    private static final String PROPS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
                    + "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>"
                    + "</d:prop></d:propfind>";

    private DavFiles() {
    }

    static boolean speaks(@Nullable final Uri uri) {
        final String scheme = uri == null ? null : uri.getScheme();
        return SCHEME.equals(scheme) || SCHEME_SECURE.equals(scheme);
    }

    /** The http(s) address behind a {@code dav(s)://} one. */
    static Uri http(final Uri uri) {
        return uri.buildUpon()
                .scheme(SCHEME_SECURE.equals(uri.getScheme()) ? "https" : "http")
                .build();
    }

    static DocumentFile folder(final Context context, final Uri uri, final String name) {
        return new androidx.documentfile.provider.NetworkDocumentFile(null, uri, name, true, 0, 0,
                new Lister(context));
    }

    /** The folder holding this address, or null at the root of the server. */
    @Nullable
    static DocumentFile parent(final Context context, final Uri uri) {
        final List<String> segments = uri.getPathSegments();
        if (segments.isEmpty()) {
            return null;
        }
        final Uri.Builder above = uri.buildUpon().path("");
        for (int i = 0; i < segments.size() - 1; i++) {
            above.appendPath(segments.get(i));
        }
        final Uri parent = above.build();
        final List<String> up = parent.getPathSegments();
        return folder(context, parent, up.isEmpty() ? parent.getHost() : up.get(up.size() - 1));
    }

    private static final class Lister
            implements androidx.documentfile.provider.NetworkDocumentFile.Children {

        private final Context context;

        Lister(final Context context) {
            this.context = context.getApplicationContext();
        }

        @NonNull
        @Override
        public DocumentFile[] of(
                @NonNull final androidx.documentfile.provider.NetworkDocumentFile folder) {
            try {
                final DocumentFile[] children = list(folder);
                NetworkFiles.listed();
                return children;
            } catch (final Exception e) {
                e.printStackTrace();
                NetworkFiles.failed(e);
                return new DocumentFile[0];
            }
        }

        private DocumentFile[] list(
                final androidx.documentfile.provider.NetworkDocumentFile folder) throws IOException {
            final Uri uri = folder.getUri();
            final Request.Builder request = new Request.Builder()
                    .url(http(uri).toString())
                    .method("PROPFIND", RequestBody.create(PROPS, XML))
                    .header("Depth", "1");
            final String authorization = NetworkFiles.authorization(context, uri);
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            try (Response response = PlayerActivity.MEDIA_HTTP_CLIENT
                    .newCall(request.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new DavException(response.code(), response.message());
                }
                final ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty answer to PROPFIND " + uri);
                }
                return entries(folder, uri, body.byteStream());
            }
        }

        /**
         * The {@code <response>} elements of a multistatus, minus the folder itself: a PROPFIND of
         * depth 1 answers for the collection first and its children after.
         */
        private DocumentFile[] entries(
                final androidx.documentfile.provider.NetworkDocumentFile folder, final Uri uri,
                final InputStream body) throws IOException {
            final List<DocumentFile> children = new ArrayList<>();
            final String self = normalise(http(uri).getEncodedPath());
            try {
                final XmlPullParser parser = Xml.newPullParser();
                parser.setInput(body, null);
                String href = null;
                boolean collection = false;
                long length = 0;
                long modified = 0;
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                        event = parser.next()) {
                    final String tag = parser.getName();
                    if (tag == null) {
                        continue;
                    }
                    final String name = local(tag);
                    if (event == XmlPullParser.START_TAG) {
                        switch (name) {
                            case "response":
                                href = null;
                                collection = false;
                                length = 0;
                                modified = 0;
                                break;
                            case "href":
                                href = parser.nextText();
                                break;
                            case "collection":
                                collection = true;
                                break;
                            case "getcontentlength":
                                length = number(parser.nextText());
                                break;
                            case "getlastmodified":
                                modified = date(parser.nextText());
                                break;
                            default:
                                break;
                        }
                    } else if (event == XmlPullParser.END_TAG && "response".equals(name)
                            && href != null) {
                        final DocumentFile child =
                                child(folder, uri, href, self, collection, length, modified);
                        if (child != null) {
                            children.add(child);
                        }
                    }
                }
            } catch (final Exception e) {
                throw new IOException(e);
            }
            return children.toArray(new DocumentFile[0]);
        }

        @Nullable
        private DocumentFile child(
                final androidx.documentfile.provider.NetworkDocumentFile folder, final Uri uri,
                final String href, final String self, final boolean collection, final long length,
                final long modified) {
            // An href may be a whole URL or just a path, and either may or may not end in a slash.
            final String path = normalise(Uri.parse(href).getEncodedPath());
            if (path == null || path.isEmpty() || path.equals(self)) {
                return null;
            }
            final String name = Uri.decode(path.substring(path.lastIndexOf('/') + 1));
            if (name.isEmpty()) {
                return null;
            }
            return new androidx.documentfile.provider.NetworkDocumentFile(folder,
                    uri.buildUpon().encodedPath(path).build(), name, collection,
                    collection ? 0 : length, modified, this);
        }

        /** Without its trailing slash, so a collection and its own href compare equal. */
        @Nullable
        private static String normalise(@Nullable final String path) {
            if (path == null) {
                return null;
            }
            return path.length() > 1 && path.endsWith("/")
                    ? path.substring(0, path.length() - 1) : path;
        }

        /** {@code d:getcontentlength} with the namespace prefix, whatever the server calls it. */
        private static String local(final String tag) {
            final int colon = tag.indexOf(':');
            return (colon < 0 ? tag : tag.substring(colon + 1)).toLowerCase(Locale.US);
        }

        private static long number(@Nullable final String text) {
            try {
                return text == null ? 0 : Long.parseLong(text.trim());
            } catch (final NumberFormatException e) {
                return 0;
            }
        }

        /** RFC 1123, which is what {@code getlastmodified} is defined to be. */
        private static long date(@Nullable final String text) {
            if (text == null) {
                return 0;
            }
            try {
                return new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .parse(text.trim()).getTime();
            } catch (final Exception e) {
                return 0;
            }
        }
    }

    /** A status the server answered with, kept so a 401 can be told from a 404. */
    static final class DavException extends IOException {
        final int code;

        DavException(final int code, final String message) {
            super("PROPFIND answered " + code + " " + message);
            this.code = code;
        }
    }
}
