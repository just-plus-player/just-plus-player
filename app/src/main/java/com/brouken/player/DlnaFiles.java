package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.NetworkDocumentFile;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A folder on a DLNA media server, dressed as a {@link DocumentFile}.
 *
 * <p>The one source that needs no typing at all: a router with a disk in it, a television, Plex and
 * Jellyfin all announce themselves, so a server is found rather than described (see
 * {@link DlnaDiscovery}). It is also the one that is browse-only by definition.
 *
 * <p><b>No library.</b> Cling and its successors model the whole of UPnP — eventing, renderers,
 * device hosting — for a player that wants one action of one service. What that action costs here is
 * one SOAP POST and two pull-parses: the answer to {@code Browse} carries the listing as an escaped
 * DIDL-Lite document inside a {@code <Result>} element, so the envelope is parsed to get at the
 * string, and the string is parsed to get at the entries.
 *
 * <p><b>The address.</b> A server's files have no paths — every object is an opaque id — so the
 * address carries what is needed to ask for it again:
 *
 * <pre>
 * dlna://host:port/ctl/ContentDir                      a saved server: the path is its control address
 * dlna://host:port/Video/Cube.mkv?c=…&amp;id=…&amp;p=…&amp;res=…   everything below it
 * </pre>
 *
 * The path below a server is the trail of titles, because the player reads a played file's name off
 * the last segment and has to arrive at the same name the listing showed. {@code c} is the control
 * address, {@code id} the object to browse, {@code p} the object above it — which is how the folder
 * playlist finds a file's siblings — and {@code res} the plain HTTP address the bytes come from, so
 * playback needs nothing new: the scheme is swapped for that one on the way out (see the resolver in
 * {@code PlayerActivity}), exactly as a WebDAV address is.
 */
final class DlnaFiles {

    static final String SCHEME = "dlna";

    private static final String SERVICE = "urn:schemas-upnp-org:service:ContentDirectory:1";
    private static final MediaType SOAP = MediaType.parse("text/xml; charset=\"utf-8\"");
    /** The id of a server's top level, fixed by the specification. */
    private static final String ROOT = "0";
    /**
     * How many entries to ask for at once. {@code 0} means "all of them" in the specification and is
     * not honoured everywhere, and a folder of a thousand episodes is a real folder, so the listing
     * is paged either way.
     */
    private static final int PAGE = 200;
    private static final int PAGES = 50;

    private static final String CONTROL = "c";
    private static final String ID = "id";
    private static final String PARENT = "p";
    private static final String RES = "res";

    /** How long to wait for a moved server to answer for itself before giving up on it. */
    private static final long FIND_MS = 4000;

    /** One listener for a whole tree: a DLNA server is browsed anonymously, so it holds no state. */
    private static final Lister LISTER = new Lister();

    /**
     * The application, for the two things a listing needs and a {@link DocumentFile} does not carry:
     * the preference that decides whether a moved server is looked for, and the saved places to look
     * it up in. Set wherever a folder is built from a context, which is every way in from the browser
     * and the player; a listing that somehow arrives before one has been just does not rescue itself.
     */
    @Nullable
    private static Context app;

    private DlnaFiles() {
    }

    static boolean speaks(@Nullable final Uri uri) {
        return uri != null && SCHEME.equals(uri.getScheme());
    }

    static DocumentFile folder(final Uri uri, final String name) {
        return new NetworkDocumentFile(null, uri, name, true, 0, 0, LISTER);
    }

    /** The same, from somewhere that has a context to lend. */
    static DocumentFile folder(final Context context, final Uri uri, final String name) {
        app = context.getApplicationContext();
        return folder(uri, name);
    }

    /** The plain HTTP address a {@code dlna://} file's bytes come from. */
    static Uri media(final Uri uri) {
        final String res = uri.getQueryParameter(RES);
        return res == null ? uri : Uri.parse(res);
    }

    /**
     * The folder holding this address, or null at the top of a server.
     *
     * <p>One level, which is all anyone asks for: the player wants a played file's siblings, and the
     * browser walks back down its own trail. So the folder handed back knows the object above this
     * one but not the object above that, and a server's top level is spelt with its own id rather
     * than rebuilt as the saved place's address — both spellings list the same thing.
     */
    @Nullable
    static DocumentFile parent(final Uri uri) {
        if (uri.getQueryParameter(ID) == null) {
            return null;
        }
        final String control = control(uri);
        final String above = uri.getQueryParameter(PARENT);
        final List<String> segments = uri.getPathSegments();
        if (segments.size() < 2 || above == null || above.isEmpty() || ROOT.equals(above)) {
            return folder(new Uri.Builder().scheme(SCHEME).encodedAuthority(uri.getAuthority())
                    .appendQueryParameter(CONTROL, control)
                    .appendQueryParameter(ID, ROOT).build(), uri.getHost());
        }
        final Uri.Builder built = new Uri.Builder().scheme(SCHEME)
                .encodedAuthority(uri.getAuthority());
        for (int i = 0; i < segments.size() - 1; i++) {
            built.appendPath(segments.get(i));
        }
        return folder(built.appendQueryParameter(CONTROL, control)
                        .appendQueryParameter(ID, above).build(),
                segments.get(segments.size() - 2));
    }

    /**
     * A discovered server as a saved place. The control address is what a place keeps — it is the one
     * thing needed to ask the server anything, and a place has exactly one field for it.
     */
    static NetworkPlaces.Place place(final String name, final String control, final String udn) {
        final Uri url = Uri.parse(control);
        final String path = TextUtils.join("/", url.getPathSegments())
                + (url.getQuery() == null ? "" : "?" + url.getQuery());
        return new NetworkPlaces.Place(SCHEME, name, url.getHost(),
                url.getPort() > 0 ? url.getPort() : 80, path, "", udn);
    }

    /**
     * Where a saved server is now, when the address it was saved at has stopped answering - or null
     * when there is nothing to be done: the server was saved before its own
     * name was kept, or nothing on the network answers for that name.
     *
     * <p>Most media servers take whatever port is free when they start, so this is what a restart of
     * the box costs: the saved address points at nothing while the server itself is right there,
     * announcing the same UPnP device name it always had. Found, the new address is saved as well as
     * used, so the next open pays for none of this.
     *
     * <p>Only after a failure, so a server that is where it was never sets off a search.
     */
    @Nullable
    private static String moved(final String control) {
        final Context context = app;
        if (context == null) {
            return null;
        }
        final Uri was = Uri.parse(control);
        final NetworkPlaces.Place saved = NetworkPlaces.byAddress(context, SCHEME, was.getHost(),
                was.getPort() > 0 ? was.getPort() : 80);
        if (saved == null || saved.udn.isEmpty()) {
            return null;
        }
        final DlnaDiscovery.Server server = DlnaDiscovery.find(saved.udn, FIND_MS);
        if (server == null || server.control.equals(control)) {
            // Nothing answered for it, or it answered from where it already was - in which case the
            // listing failed for some other reason and the saved address is not the thing to change.
            return null;
        }
        // Removed before it is added, and not only replaced: a place is identified by the name the
        // server announced once it has one, and a server saved before that was kept is identified by
        // its address - so the two do not match each other and the dead one would stay in the list.
        NetworkPlaces.remove(context, saved);
        NetworkPlaces.add(context, place(saved.name, server.control, saved.udn), "");
        return server.control;
    }

    /** Where to send a {@code Browse}: carried in the address, or the saved place's own path. */
    private static String control(final Uri uri) {
        final String carried = uri.getQueryParameter(CONTROL);
        return carried != null ? carried
                : "http://" + uri.getAuthority() + "/" + TextUtils.join("/", uri.getPathSegments());
    }

    private static String object(final Uri uri) {
        final String id = uri.getQueryParameter(ID);
        return id == null || id.isEmpty() ? ROOT : id;
    }

    /** Lists over the ContentDirectory service. */
    private static final class Lister implements NetworkDocumentFile.Children {

        @NonNull
        @Override
        public DocumentFile[] of(@NonNull final NetworkDocumentFile folder) {
            final String control = control(folder.getUri());
            try {
                final DocumentFile[] children = list(folder, control);
                NetworkFiles.listed();
                return children;
            } catch (final Exception e) {
                final String now = moved(control);
                if (now != null) {
                    try {
                        final DocumentFile[] children = list(folder, now);
                        NetworkFiles.listed();
                        return children;
                    } catch (final Exception ignored) {
                        // Found and still will not answer: the address was not the whole of it, so
                        // carry on to the walk below with the address that does answer.
                    }
                }
                // The object id is the other half of an address that can rot. A server renumbers its
                // objects when it rebuilds its database — a restart is enough on the servers a router
                // or a television runs — and the id a favourite wrote down then names nothing. Only
                // the root id is fixed by the specification, which is why coming down from the server
                // row never fails and opening a favourite does. So come down from the root: the path
                // of a dlna:// address is its trail of titles, kept for exactly this kind of reason.
                try {
                    final DocumentFile[] children = fromRoot(folder, now != null ? now : control);
                    if (children != null) {
                        NetworkFiles.listed();
                        return children;
                    }
                } catch (final Exception ignored) {
                    // The walk is a rescue, not a second opinion: when it cannot finish, what the
                    // viewer's own address said is still the failure worth reporting.
                }
                // An empty folder and a server that would not answer are the same empty list from
                // here, so the reason is left for the caller rather than only printed.
                e.printStackTrace();
                NetworkFiles.failed(e);
                return new DocumentFile[0];
            }
        }

        /**
         * The same folder, found by walking down from the server's root and matching each title in
         * the address, or null where that trail leads nowhere either.
         *
         * <p>Only for an address that carries an object id: there the path is a trail of titles. The
         * top of a server spells its path as the control address instead, and it is already at the
         * root — there is nothing to walk.
         */
        @Nullable
        private DocumentFile[] fromRoot(final NetworkDocumentFile folder, final String control)
                throws IOException {
            if (folder.getUri().getQueryParameter(ID) == null) {
                return null;
            }
            String object = ROOT;
            for (final String title : folder.getUri().getPathSegments()) {
                String below = null;
                for (final DocumentFile child : list(folder, control, object)) {
                    if (child.isDirectory() && title.equals(child.getName())) {
                        below = child.getUri().getQueryParameter(ID);
                        break;
                    }
                }
                if (below == null) {
                    // Renamed, moved or gone. Answering with the server's root instead would be a
                    // different folder wearing this one's name.
                    return null;
                }
                object = below;
            }
            return list(folder, control, object);
        }

        private DocumentFile[] list(final NetworkDocumentFile folder, final String control)
                throws IOException {
            return list(folder, control, object(folder.getUri()));
        }

        private DocumentFile[] list(final NetworkDocumentFile folder, final String control,
                                    final String object) throws IOException {
            final List<DocumentFile> children = new ArrayList<>();
            int from = 0;
            for (int page = 0; page < PAGES; page++) {
                final int seen = entries(folder, control, browse(control, object, from), children);
                if (seen < PAGE) {
                    break;
                }
                from += seen;
            }
            return children.toArray(new DocumentFile[0]);
        }

        /** One {@code Browse}, answered with the listing as an escaped DIDL-Lite document. */
        private String browse(final String control, final String object, final int from)
                throws IOException {
            final String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                    + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                    + "<u:Browse xmlns:u=\"" + SERVICE + "\">"
                    + "<ObjectID>" + escape(object) + "</ObjectID>"
                    + "<BrowseFlag>BrowseDirectChildren</BrowseFlag>"
                    + "<Filter>*</Filter>"
                    + "<StartingIndex>" + from + "</StartingIndex>"
                    + "<RequestedCount>" + PAGE + "</RequestedCount>"
                    + "<SortCriteria></SortCriteria>"
                    + "</u:Browse></s:Body></s:Envelope>";
            final Request request = new Request.Builder()
                    .url(control)
                    .header("SOAPACTION", "\"" + SERVICE + "#Browse\"")
                    .post(RequestBody.create(envelope, SOAP))
                    .build();
            try (Response response = PlayerActivity.MEDIA_HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Browse answered " + response.code() + " "
                            + response.message());
                }
                final ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Empty answer to Browse " + control);
                }
                return result(body.byteStream());
            }
        }

        /** The {@code <Result>} of the envelope, unescaped by the parser into the DIDL document. */
        private String result(final InputStream body) throws IOException {
            try {
                final XmlPullParser parser = Xml.newPullParser();
                parser.setInput(body, null);
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                        event = parser.next()) {
                    if (event == XmlPullParser.START_TAG
                            && "result".equals(local(parser.getName()))) {
                        final String didl = parser.nextText();
                        return didl == null ? "" : didl;
                    }
                }
            } catch (final Exception e) {
                throw new IOException(e);
            }
            throw new IOException("Browse answered without a Result");
        }

        /**
         * The containers and items of one page, added to {@code children}. Returns how many objects
         * the server listed rather than how many were kept, because that is what says whether there
         * is another page: an item with nothing to play is skipped but still counts.
         */
        private int entries(final NetworkDocumentFile folder, final String control,
                            final String didl, final List<DocumentFile> children)
                throws IOException {
            int seen = 0;
            try {
                final XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new StringReader(didl));
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                        event = parser.next()) {
                    if (event != XmlPullParser.START_TAG) {
                        continue;
                    }
                    final String tag = local(parser.getName());
                    final boolean directory = "container".equals(tag);
                    if (!directory && !"item".equals(tag)) {
                        continue;
                    }
                    seen++;
                    final DocumentFile child = entry(folder, control, parser, directory);
                    if (child != null) {
                        children.add(child);
                    }
                }
            } catch (final Exception e) {
                throw new IOException(e);
            }
            return seen;
        }

        /**
         * One {@code <container>} or {@code <item>}, read from its start tag to its end tag.
         *
         * <p>An item can carry several {@code <res>} elements — a thumbnail, a transcode, the file
         * itself. A still is never the thing to play, whatever it declares, so anything else beats
         * it; among the rest the biggest wins, which is the original wherever a size is stated. The
         * still has to be ruled out by what it is rather than by its size, because it is the one
         * {@code res} that always states one: a picture of 8 kB would otherwise beat a film that
         * declares no size at all.
         *
         * <p>{@code protocolInfo} is also where the type comes from: a DLNA title need not end in
         * {@code .mkv}, and without a type of its own such a file would be filtered out of the
         * browser's own list as not a video.
         */
        @Nullable
        private DocumentFile entry(final NetworkDocumentFile folder, final String control,
                                   final XmlPullParser parser, final boolean directory)
                throws Exception {
            final String id = parser.getAttributeValue(null, "id");
            final String above = parser.getAttributeValue(null, "parentID");
            final int depth = parser.getDepth();
            String title = null;
            String kind = null;
            String res = null;
            String mime = null;
            String poster = null;
            String thumb = null;
            String picture = null;
            long written = 0;
            long length = 0;
            long running = 0;
            long biggest = -1;
            long posterPixels = -1;
            long thumbPixels = -1;
            boolean still = true;
            while (true) {
                final int event = parser.next();
                if (event == XmlPullParser.END_DOCUMENT
                        || (event == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    break;
                }
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                final String tag = local(parser.getName());
                if ("title".equals(tag)) {
                    title = parser.nextText();
                } else if ("albumarturi".equals(tag)) {
                    // What the server means as the picture for this item. Whatever is behind it is
                    // the server's business - the cover it pulled out of the file, a folder.jpg next
                    // to it, a frame it grabbed, a poster it downloaded - and it is already a small
                    // JPEG at an address, which is the whole reason this is worth having.
                    final long says = artPixels(null, attribute(parser, "profileid"));
                    final String named = parser.nextText();
                    // A server may offer the same art at several profiles, one element each. The last
                    // one is no more the best than the first: take whichever says it is biggest, and
                    // where none of them says anything, the first still wins as before.
                    if (named != null && (poster == null || says > posterPixels)) {
                        poster = named;
                        posterPixels = says;
                    }
                } else if ("class".equals(tag)) {
                    kind = parser.nextText();
                } else if ("date".equals(tag)) {
                    written = day(parser.nextText());
                } else if ("res".equals(tag)) {
                    final String protocol = parser.getAttributeValue(null, "protocolInfo");
                    final long size = number(parser.getAttributeValue(null, "size"));
                    final long plays = millis(parser.getAttributeValue(null, "duration"));
                    final String frame = parser.getAttributeValue(null, "resolution");
                    final String said = mimeOf(protocol);
                    final boolean image = said != null && said.startsWith("image/");
                    final String url = parser.nextText();
                    // The still is no use as the thing to play and is exactly what a row wants to
                    // show, so it is kept here on its way past rather than only rejected below.
                    //
                    // The biggest of them, not the first. A server that offers several offers the
                    // list-sized one first - JPEG_TN, 160 x 160, made for a remote control's list -
                    // and taking it is why a row of this browser used to look like a postage stamp
                    // next to the same file on a desktop client. What the others are is stated:
                    // either a resolution attribute or a profile name in protocolInfo.
                    if (image && url != null && url.startsWith("http")) {
                        final long says = artPixels(frame, protocol);
                        if (thumb == null || says > thumbPixels) {
                            thumb = url.trim();
                            thumbPixels = says;
                        }
                    }
                    // Anything beats a still; between two of a kind, the bigger one.
                    final boolean better = (still && !image)
                            || (still == image && size > biggest);
                    if (url != null && url.startsWith("http") && better
                            && (protocol == null || protocol.startsWith("http-get"))) {
                        biggest = size;
                        still = image;
                        picture = shorterSide(frame);
                        res = url.trim();
                        length = size;
                        running = plays;
                        mime = said;
                    }
                }
            }
            if (id == null || title == null || title.isEmpty() || (!directory && res == null)) {
                return null;
            }
            final Uri.Builder built = new Uri.Builder().scheme(SCHEME)
                    .encodedAuthority(folder.getUri().getAuthority());
            // A server's top level has its control address where a folder has its trail of titles, so
            // its children start a trail rather than continuing that.
            if (folder.getUri().getQueryParameter(ID) != null) {
                for (final String segment : folder.getUri().getPathSegments()) {
                    built.appendPath(segment);
                }
            }
            built.appendPath(title)
                    .appendQueryParameter(CONTROL, control)
                    .appendQueryParameter(ID, id);
            if (above != null && !above.isEmpty()) {
                built.appendQueryParameter(PARENT, above);
            }
            if (res != null) {
                built.appendQueryParameter(RES, res);
            }
            final Uri uri = built.build();
            return directory
                    ? new NetworkDocumentFile(folder, uri, title, true, 0, 0, this)
                    : new Item(folder, uri, title, length, running, written, picture,
                            mime != null ? mime : typeOf(kind),
                            biggerArt(art(poster, posterPixels, thumb, thumbPixels)));
        }

        /**
         * The picture for a row: the server's own art where it has one, the biggest still otherwise.
         *
         * <p>An {@code albumArtURI} is what the server means as the picture for the item, so it wins
         * a tie and wins when neither side states a size. It does not win when the stills say they
         * are bigger: a server that answers a bare 160 x 160 albumArtURI alongside a JPEG_MED
         * {@code res} has said which one is worth showing, and it is not the one it labelled for a
         * remote control.
         */
        @Nullable
        private static String art(@Nullable final String poster, final long posterPixels,
                                  @Nullable final String thumb, final long thumbPixels) {
            final String art = poster != null && poster.startsWith("http") ? poster.trim() : null;
            if (art == null) {
                return thumb;
            }
            return thumb != null && thumbPixels > posterPixels ? thumb : art;
        }

        /** An attribute by its local name, since the listing's namespaces are in the names. */
        @Nullable
        private static String attribute(final XmlPullParser parser, final String name) {
            for (int i = 0; i < parser.getAttributeCount(); i++) {
                if (name.equals(local(parser.getAttributeName(i)))) {
                    return parser.getAttributeValue(i);
                }
            }
            return null;
        }

        /** The type out of {@code http-get:*:video/x-matroska:DLNA.ORG_PN=…}, or null. */
        @Nullable
        private static String mimeOf(@Nullable final String protocol) {
            if (protocol == null) {
                return null;
            }
            final String[] fields = protocol.split(":");
            return fields.length > 2 && fields[2].contains("/") ? fields[2] : null;
        }

        /** The type out of {@code object.item.videoItem}, for a server that declares no other. */
        private static String typeOf(@Nullable final String kind) {
            final String said = kind == null ? "" : kind.toLowerCase(Locale.US);
            if (said.contains("video")) {
                return "video/*";
            }
            if (said.contains("audio")) {
                return "audio/*";
            }
            if (said.contains("image")) {
                return "image/*";
            }
            return "application/octet-stream";
        }

        /** {@code dc:title} with whatever prefix this server uses. */
        private static String local(@Nullable final String tag) {
            if (tag == null) {
                return "";
            }
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

        /**
         * {@code H:MM:SS.mmm} as milliseconds, which is how a DLNA res states how long it plays for.
         * Zero for anything that does not parse: a row without a running time is fine, and a wrong
         * one would put a played run at the wrong place on the still.
         */
        private static long millis(@Nullable final String stated) {
            if (stated == null) {
                return 0;
            }
            final String[] parts = stated.trim().split(":");
            if (parts.length != 3) {
                return 0;
            }
            try {
                final long hours = Long.parseLong(parts[0].isEmpty() ? "0" : parts[0]);
                final long minutes = Long.parseLong(parts[1]);
                final double seconds = Double.parseDouble(parts[2].replace(',', '.'));
                return (long) (((hours * 60 + minutes) * 60 + seconds) * 1000);
            } catch (final NumberFormatException e) {
                return 0;
            }
        }

        /** An object id is the server's own string and is allowed to hold XML's own characters. */
        private static String escape(final String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /**
     * A file whose type the server stated. Everything else about it is a
     * {@link NetworkDocumentFile}: only the type has to come from somewhere other than the name,
     * because a DLNA title carries no extension.
     */
    private static final class Item extends NetworkDocumentFile {

        private final String mime;
        /** How long it plays for, as the server stated it, or 0 when it did not. */
        final long running;
        /** How the row names the picture - "1080p" - out of the same {@code res}, or null. */
        @Nullable
        final String frame;
        /** The picture the server offers for it, as an address, or null when it offered none. */
        @Nullable
        final String art;

        Item(final DocumentFile parent, final Uri uri, final String name, final long length,
             final long running, final long written, @Nullable final String frame, final String mime,
             @Nullable final String art) {
            super(parent, uri, name, false, length, written, null);
            this.mime = mime;
            this.running = running;
            this.frame = frame;
            this.art = art;
        }

        @Override
        public String getType() {
            return mime;
        }
    }

    /**
     * How long this file plays for, if it came from a server that said so - the one answer a network
     * row cannot get by opening the file, since opening it is a stream over the network.
     */
    static long runningTime(final DocumentFile file) {
        return file instanceof Item ? ((Item) file).running : 0;
    }

    /**
     * How many pixels the server says a picture has: from a {@code resolution} attribute where there
     * is one, otherwise from the DLNA profile it names, otherwise 0.
     *
     * <p>The profiles are fixed ceilings in the DLNA media format specification, so a name is as good
     * as a measurement for choosing between two of them: {@code JPEG_TN} is 160 x 160 - the size made
     * for a remote control's list, and what a row of this browser used to get every time - and
     * {@code JPEG_SM}, {@code JPEG_MED} and {@code JPEG_LRG} are 640 x 480, 1024 x 768 and
     * 4096 x 4096. The string can be a whole {@code protocolInfo} or a bare {@code dlna:profileID};
     * both are searched the same way, since the profile appears verbatim in each.
     */
    static long artPixels(@Nullable final String resolution, @Nullable final String profile) {
        if (resolution != null) {
            final int cut = resolution.indexOf('x');
            if (cut > 0) {
                try {
                    final long wide = Long.parseLong(resolution.substring(0, cut).trim());
                    final long high = Long.parseLong(resolution.substring(cut + 1).trim());
                    if (wide > 0 && high > 0) {
                        return wide * high;
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }
        final String said = profile == null ? "" : profile.toUpperCase(Locale.US);
        if (said.contains("_LRG")) {
            return 4096L * 4096L;
        }
        if (said.contains("_MED")) {
            return 1024L * 768L;
        }
        if (said.contains("_SM")) {
            return 640L * 480L;
        }
        if (said.contains("_TN")) {
            return 160L * 160L;
        }
        return 0;
    }

    /** The picture this browser would like, in pixels across - a tile is nowhere near this on a phone. */
    private static final int ART_WANTED = 720;

    /**
     * The same address, asking for a bigger picture where it is asking for a size at all.
     *
     * <p>A server that renders its artwork on demand puts the size in the query, and the size it puts
     * there is the one it thinks a DLNA client wants: Jellyfin and Emby write
     * {@code maxWidth=200&maxHeight=200}, Plex {@code width=320&height=320}. Nothing else in the
     * listing says a bigger one is available, so asking is the only way to find out, and the answer
     * costs one request either way. Only values already in the address are touched, and only upwards,
     * so a server that clamps or ignores the number returns exactly what it returned before.
     */
    static String biggerArt(@Nullable final String url) {
        if (url == null || url.indexOf('?') < 0) {
            return url;
        }
        final Matcher matcher = ART_SIZE.matcher(url);
        final StringBuffer raised = new StringBuffer(url.length() + 8);
        while (matcher.find()) {
            long said;
            try {
                said = Long.parseLong(matcher.group(2));
            } catch (final NumberFormatException e) {
                said = ART_WANTED;
            }
            matcher.appendReplacement(raised,
                    Matcher.quoteReplacement(matcher.group(1) + Math.max(said, ART_WANTED)));
        }
        matcher.appendTail(raised);
        return raised.toString();
    }

    /** {@code &maxWidth=200} and its three siblings, the only part of an address this touches. */
    private static final Pattern ART_SIZE = Pattern.compile(
            "([?&](?:maxWidth|maxHeight|width|height)=)(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * The picture this file's server offers for it, or null.
     *
     * <p>The one place a row over the network can get a picture for nothing: it arrived with the
     * listing, and it is an address rather than a file to be read. A share and a WebDAV server say
     * nothing about what is inside a file, so their rows keep the glyph.
     */
    @Nullable
    static String artwork(final DocumentFile file) {
        return file instanceof Item ? ((Item) file).art : null;
    }

    /**
     * What the server said the picture is, as a row says it - "1080p" - or null where it said
     * nothing. A share and a WebDAV server state a size and a date and no more; a media server reads
     * the file itself before it answers, and states the one thing the browser could otherwise only
     * get by streaming the file to read its header.
     */
    @Nullable
    static String resolution(final DocumentFile file) {
        return file instanceof Item ? ((Item) file).frame : null;
    }

    /**
     * {@code resolution="1920x1080"} as the shorter side with a p after it. The shorter side because
     * a clip shot upright is a 1080p clip, the same as the film that is 1920 x 1080.
     */
    @Nullable
    private static String shorterSide(@Nullable final String frame) {
        if (frame == null) {
            return null;
        }
        final int cut = frame.indexOf('x');
        try {
            final int side = Math.min(Integer.parseInt(frame.substring(0, cut).trim()),
                    Integer.parseInt(frame.substring(cut + 1).trim()));
            return side > 0 ? side + "p" : null;
        } catch (final RuntimeException ignored) {
            // A server that writes its resolution some other way says nothing about it.
            return null;
        }
    }

    /**
     * {@code <dc:date>} as milliseconds. The day alone: servers write it as a date, as a date and a
     * time, and with or without a zone, and the row shows a day either way.
     */
    private static long day(@Nullable final String stated) {
        if (stated == null || stated.length() < 10) {
            return 0;
        }
        try {
            final java.util.Date parsed = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .parse(stated.substring(0, 10));
            return parsed == null ? 0 : parsed.getTime();
        } catch (final java.text.ParseException ignored) {
            // Not a date this app can read is not a date this row will show.
            return 0;
        }
    }
}
