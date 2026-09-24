package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The folders and files somebody has put aside, local and network in one list.
 *
 * <p>A favorite is an address and a name, and nothing else: what it leads to is read when the row
 * is drawn, the way {@link NetworkPlaces} reads a share. That is what lets one list hold a folder
 * on this device and a folder on a NAS without either of them knowing about the other -
 * {@code BrowserActivity.itemFor} turns the address back into the same {@code DocumentFile} the
 * rest of the browser deals in.
 *
 * <p>The name travels beside the address because the address cannot always be asked for one: a
 * media server's titles carry no extension and a storage volume is called something the system
 * knows and the path does not.
 */
final class Favorites {

    private static final String PREFS = "favorites";
    private static final String KEY_LIST = "list";

    /**
     * One saved row: what to call it, where it is, whether it opens or plays, and the three things a
     * row shows that its address does not carry.
     *
     * <p>A media server's picture, size and running time arrive in the listing, not in the address -
     * {@code dlna://} holds a control address, an object id and the address of the bytes and nothing
     * else - so a favorite rebuilt from its uri alone would be a glyph with no size beside a local
     * file that has both. They are written down here because the row being pressed already knows
     * them, which is cheaper than either storing nothing or asking the server again.
     *
     * <p>The picture is an address on the server. Most media servers take whatever port is free when
     * they start, so it can rot; a picture that will not load leaves the glyph, which is where the
     * row would have been anyway. The size and the time never rot.
     */
    static final class Favorite {
        final String name;
        final Uri uri;
        final boolean folder;
        /** Where the server keeps a picture of it, or empty. */
        final String art;
        /** Bytes, or 0 when nothing said. */
        final long size;
        /** How long it plays for in milliseconds, or 0 when nothing said. */
        final long duration;

        Favorite(final String name, final Uri uri, final boolean folder, final String art,
                 final long size, final long duration) {
            this.name = name;
            this.uri = uri;
            this.folder = folder;
            this.art = art == null ? "" : art;
            this.size = size;
            this.duration = duration;
        }
    }

    private Favorites() {
    }

    /** Everything saved, oldest first: a list this short is remembered by where its rows sit. */
    static List<Favorite> all(final Context context) {
        final List<Favorite> found = new ArrayList<>();
        for (final String line : prefs(context).getString(KEY_LIST, "").split("\n")) {
            final String[] parts = line.split("\t", -1);
            if (parts.length < 3) {
                continue;
            }
            // A line written before a favorite remembered what its row showed has three fields.
            found.add(new Favorite(parts[0], Uri.parse(parts[1]), "1".equals(parts[2]),
                    parts.length > 3 ? parts[3] : "",
                    parts.length > 4 ? number(parts[4]) : 0,
                    parts.length > 5 ? number(parts[5]) : 0));
        }
        return found;
    }

    /**
     * The addresses put aside, held so that a listing can ask about every row it draws. Without it
     * each row parsed the whole store, which a folder of five hundred files does five hundred times
     * over, on every scroll. Dropped by {@link #write}, the one place the set can change.
     */
    private static Set<String> saved;

    static boolean has(final Context context, final Uri uri) {
        if (saved == null) {
            saved = new HashSet<>();
            for (final Favorite favorite : all(context)) {
                saved.add(favorite.uri.toString());
            }
        }
        return saved.contains(uri.toString());
    }

    /** Adds one, or replaces the one already at that address. */
    static void add(final Context context, final String name, final Uri uri, final boolean folder,
                    final String art, final long size, final long duration) {
        final List<Favorite> favorites = all(context);
        remove(favorites, uri);
        // A tab or a newline in a name would be a second field or a second row: both are legal in a
        // file name, and neither survives a line-per-record store.
        favorites.add(new Favorite(name.replaceAll("[\t\n]", " "), uri, folder,
                art == null ? "" : art.replaceAll("[\t\n]", ""), size, duration));
        write(context, favorites);
    }

    private static long number(final String text) {
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException ignored) {
            // An unreadable number is a number nobody stated.
            return 0;
        }
    }

    static void remove(final Context context, final Uri uri) {
        final List<Favorite> favorites = all(context);
        remove(favorites, uri);
        write(context, favorites);
    }

    /**
     * Everything put aside out of a place, dropped with the place itself: a server that has been
     * forgotten cannot be opened, and a row that leads nowhere is worse than no row.
     *
     * <p>Hung off the two places where somebody says so - forgetting a server, taking a torrent off
     * one - and not off {@link NetworkPlaces#remove}, which is also how a media server that moved to
     * a different port is re-saved: there the place goes and comes straight back, and the favorites
     * under it are still good.
     */
    static void removeUnder(final Context context, final NetworkPlaces.Place place) {
        final List<Favorite> favorites = all(context);
        boolean dropped = false;
        for (int i = favorites.size() - 1; i >= 0; i--) {
            if (place.holds(favorites.get(i).uri)) {
                favorites.remove(i);
                dropped = true;
            }
        }
        if (dropped) {
            write(context, favorites);
        }
    }

    /**
     * Everything put aside out of one torrent, dropped with the torrent. A torrent's files are
     * addressed by its hash, so that is what says which rows went with it; the server is asked about
     * as well, because two servers can hold the same torrent and only one of them was told to drop
     * it.
     */
    static void removeUnder(final Context context, final Uri server, final String hash) {
        if (hash == null || hash.isEmpty()) {
            return;
        }
        final List<Favorite> favorites = all(context);
        boolean dropped = false;
        for (int i = favorites.size() - 1; i >= 0; i--) {
            final Uri uri = favorites.get(i).uri;
            if (hash.equals(uri.getQueryParameter("h"))
                    && server.getAuthority() != null
                    && server.getAuthority().equals(uri.getAuthority())) {
                favorites.remove(i);
                dropped = true;
            }
        }
        if (dropped) {
            write(context, favorites);
        }
    }

    private static void remove(final List<Favorite> favorites, final Uri uri) {
        for (int i = favorites.size() - 1; i >= 0; i--) {
            if (favorites.get(i).uri.equals(uri)) {
                favorites.remove(i);
            }
        }
    }

    private static void write(final Context context, final List<Favorite> favorites) {
        saved = null;
        final StringBuilder text = new StringBuilder();
        for (final Favorite favorite : favorites) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(favorite.name).append('\t').append(favorite.uri).append('\t')
                    .append(favorite.folder ? '1' : '0').append('\t').append(favorite.art)
                    .append('\t').append(favorite.size).append('\t').append(favorite.duration);
        }
        prefs(context).edit().putString(KEY_LIST, text.toString()).apply();
    }

    private static SharedPreferences prefs(final Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
