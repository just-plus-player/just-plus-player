package com.brouken.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The network folders someone has added, and the passwords that open them.
 *
 * <p>A place is remembered by its address and its user; the password is encrypted with a key that
 * lives in the device's keystore and never leaves it, so what sits in preferences is a blob and not
 * a password. The reference player keeps them in the clear and that is not a precedent to copy.
 *
 * <p>The address a place hands out — and therefore the one that reaches {@code Prefs.mediaUri}, the
 * resume position map and the browse trail — carries no credentials at all. They are looked up by
 * host and share at the moment a file is opened.
 */
final class NetworkPlaces {

    private static final String PREFS = "network_places";
    private static final String KEY_PLACES = "places";
    private static final String ALIAS = "network-credentials";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /**
     * One saved place: what it speaks, what to call it, where it is, and who opens it.
     *
     * <p>For a share the path is one segment and is usually empty — a whole host is the more useful
     * thing to save, because then its shares are just the folders inside it and there is nothing to
     * choose when adding one. For a WebDAV server the path is where the files begin, which on some
     * of them is several segments deep.
     */
    static final class Place {
        final String scheme;
        final String name;
        final String host;
        final int port;
        final String share;
        final String user;
        /**
         * What a media server calls itself, {@code uuid:...}, for the one protocol here that moves:
         * most DLNA servers take whatever port is free when they start, so the address a server was
         * added at is not where it will be after the box it runs on is restarted. Empty for every
         * other protocol, and for a server added before this was kept.
         */
        final String udn;

        Place(final String name, final String host, final int port, final String share,
              final String user) {
            this(SmbSessions.SCHEME, name, host, port, share, user);
        }

        Place(final String scheme, final String name, final String host, final int port,
              final String share, final String user) {
            this(scheme, name, host, port, share, user, "");
        }

        Place(final String scheme, final String name, final String host, final int port,
              final String share, final String user, final String udn) {
            this.scheme = scheme;
            this.name = name;
            this.host = host;
            this.port = port;
            this.share = share;
            this.user = user;
            this.udn = udn == null ? "" : udn;
        }

        /** {@code smb://host[:port][/share]} or {@code dav(s)://host[:port]/path} — no credentials. */
        Uri uri() {
            final Uri.Builder uri = new Uri.Builder().scheme(scheme)
                    .encodedAuthority(host + (port > 0 ? ":" + port : ""));
            for (final String segment : share.split("/")) {
                if (!segment.isEmpty()) {
                    uri.appendPath(segment);
                }
            }
            return uri.build();
        }

        /**
         * Whether that address lives in this place - what everything saved out of a place is found
         * by when the place itself is dropped.
         *
         * <p>The port is not asked about on a media server, and neither is the path: most of them
         * take whatever port is free when they start, and what a DLNA place holds is a control
         * endpoint rather than the folder its objects are under. Host and protocol are all such a
         * server is. A share or a WebDAV path is exactly where it says it is, so there both are
         * checked - a place saved as one share of a host does not hold what was put aside from
         * another share of the same host.
         */
        boolean holds(final Uri uri) {
            if (uri == null || !scheme.equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            final String at = uri.getHost();
            if (at == null || !at.equalsIgnoreCase(host)) {
                return false;
            }
            if (DlnaFiles.SCHEME.equals(scheme)) {
                return true;
            }
            if (port > 0 && uri.getPort() > 0 && uri.getPort() != port) {
                return false;
            }
            final String root = strip(share);
            final String path = strip(uri.getPath());
            return root.isEmpty() || path.equals(root) || path.startsWith(root + "/");
        }

        /**
         * What identifies a place, and what its password is filed under. A share keeps the form it
         * has always had, so nothing already saved loses its password to this.
         */
        String id() {
            // A media server that announced a name of its own is that name and not its address, or a
            // restart on a different port would save itself a second time and lose the one the viewer
            // gave it. Everything else is where it is: that is what a share or a WebDAV path means.
            if (!udn.isEmpty()) {
                return scheme + ":" + udn;
            }
            return (SmbSessions.SCHEME.equals(scheme) ? "" : scheme + ":")
                    + host + ":" + port + "/" + share;
        }
    }

    private static String strip(final String path) {
        return path == null ? "" : path.replaceAll("^/+|/+$", "");
    }

    private NetworkPlaces() {
    }

    /** The saved media server with this device name, or null when none was saved under it. */
    @Nullable
    static Place byUdn(final Context context, final String udn) {
        if (udn == null || udn.isEmpty()) {
            return null;
        }
        for (final Place place : all(context)) {
            if (udn.equals(place.udn)) {
                return place;
            }
        }
        return null;
    }

    /** The saved media server answering at this address, or null. */
    @Nullable
    static Place byAddress(final Context context, final String scheme, final String host,
                           final int port) {
        for (final Place place : all(context)) {
            if (place.scheme.equals(scheme) && place.host.equals(host) && place.port == port) {
                return place;
            }
        }
        return null;
    }

    static List<Place> all(final Context context) {
        final List<Place> places = new ArrayList<>();
        for (final String line : prefs(context).getString(KEY_PLACES, "").split("\n")) {
            final String[] parts = line.split("\t", -1);
            if (parts.length < 5) {
                continue;
            }
            int port = 0;
            try {
                port = Integer.parseInt(parts[2]);
            } catch (final NumberFormatException ignored) {
                // An unparsable port is the default port.
            }
            // A line written before there was more than one protocol has five fields and is a
            // share; one written before a media server kept its own name has six.
            places.add(new Place(parts.length > 5 ? parts[5] : SmbSessions.SCHEME,
                    parts[0], parts[1], port, parts[3], parts[4],
                    parts.length > 6 ? parts[6] : ""));
        }
        return places;
    }

    /** Adds a place, or replaces the one at the same address. */
    static void add(final Context context, final Place place, final String password) {
        final List<Place> places = all(context);
        for (int i = places.size() - 1; i >= 0; i--) {
            if (places.get(i).id().equals(place.id())) {
                places.remove(i);
            }
        }
        places.add(place);
        write(context, places);
        prefs(context).edit().putString(secretKey(place), encrypt(password)).apply();
    }

    /**
     * Gives a place a new name. Nothing else moves: a place is identified by its address, and the
     * password is keyed off that too, so renaming touches one field of one line.
     */
    static void rename(final Context context, final Place place, final String name) {
        final List<Place> places = all(context);
        for (int i = 0; i < places.size(); i++) {
            final Place saved = places.get(i);
            if (saved.id().equals(place.id())) {
                places.set(i, new Place(saved.scheme, name, saved.host, saved.port, saved.share,
                        saved.user, saved.udn));
            }
        }
        write(context, places);
    }

    static void remove(final Context context, final Place place) {
        final List<Place> places = all(context);
        for (int i = places.size() - 1; i >= 0; i--) {
            if (places.get(i).id().equals(place.id())) {
                places.remove(i);
            }
        }
        write(context, places);
        prefs(context).edit().remove(secretKey(place)).apply();
    }

    /** The user saved for this address, or an empty string when it is browsed anonymously. */
    static String user(final Context context, final String host, final String share) {
        final Place place = find(context, host, share);
        return place == null ? "" : place.user;
    }

    /** The password saved for this address, or an empty string when there is none. */
    static String password(final Context context, final String host, final String share) {
        final Place place = find(context, host, share);
        if (place == null) {
            return "";
        }
        final String stored = prefs(context).getString(secretKey(place), null);
        return stored == null ? "" : decrypt(stored);
    }

    /** The saved place an {@code smb://host[/share/...]} address belongs to, or null. */
    @Nullable
    static Place forUri(final Context context, final Uri uri) {
        if (uri.getHost() == null) {
            return null;
        }
        final List<String> segments = uri.getPathSegments();
        return find(context, uri.getHost(), segments.isEmpty() ? "" : segments.get(0));
    }

    /**
     * The place that opens this address: the one saved for exactly this share if there is one, and
     * otherwise any place on that host. The fallback is what a host added by the picker relies on -
     * it holds no share and has to supply the credentials for every share under it - and what a
     * WebDAV place relies on too, since its path is deeper than the one segment a lookup carries.
     *
     * <p>ponytail: first place on the host wins the fallback. Two shares on one host with different
     * credentials would need the share saved for each, which saving them does.
     */
    @Nullable
    private static Place find(final Context context, final String host, final String share) {
        Place onHost = null;
        for (final Place place : all(context)) {
            if (!place.host.equalsIgnoreCase(host)) {
                continue;
            }
            if (place.share.equalsIgnoreCase(share)) {
                return place;
            }
            if (onHost == null) {
                onHost = place;
            }
        }
        return onHost;
    }

    private static void write(final Context context, final List<Place> places) {
        final StringBuilder text = new StringBuilder();
        for (final Place place : places) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(place.name).append('\t').append(place.host).append('\t')
                    .append(place.port).append('\t').append(place.share).append('\t')
                    .append(place.user).append('\t').append(place.scheme).append('\t')
                    .append(place.udn);
        }
        prefs(context).edit().putString(KEY_PLACES, text.toString()).apply();
    }

    private static SharedPreferences prefs(final Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String secretKey(final Place place) {
        return "secret\t" + place.id();
    }

    private static String encrypt(final String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            final byte[] iv = cipher.getIV();
            final byte[] secret = cipher.doFinal(plain.getBytes("UTF-8"));
            final byte[] both = new byte[iv.length + secret.length];
            System.arraycopy(iv, 0, both, 0, iv.length);
            System.arraycopy(secret, 0, both, iv.length, secret.length);
            return Base64.encodeToString(both, Base64.NO_WRAP);
        } catch (final Exception e) {
            // Without a keystore there is nowhere safe to put it, so it is not put anywhere: the
            // place stays and asks for the password again.
            return "";
        }
    }

    private static String decrypt(final String stored) {
        if (stored.isEmpty()) {
            return "";
        }
        try {
            final byte[] both = Base64.decode(stored, Base64.NO_WRAP);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(TAG_BITS, both, 0, IV_LENGTH));
            return new String(cipher.doFinal(both, IV_LENGTH, both.length - IV_LENGTH), "UTF-8");
        } catch (final Exception e) {
            return "";
        }
    }

    @NonNull
    private static SecretKey key() throws Exception {
        final KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        final Key existing = keystore.getKey(ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        final KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
