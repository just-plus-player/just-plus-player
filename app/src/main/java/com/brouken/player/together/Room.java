package com.brouken.player.together;

import android.net.Uri;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * A watch-together room: its code, and the relay channel derived from it.
 *
 * <p>The derivation is deliberately identical to the {@code lparty.js} Lampa plugin, which runs on
 * the same public relay — {@code "lparty-r-" + sha256(CODE|password)}. Matching it byte for byte is
 * the whole of what makes a shared room possible: get this wrong and the two clients sit in
 * different channels, each convinced the other never arrived.
 *
 * <p>Nothing here is a secret in the cryptographic sense. Message bodies travel in the clear
 * because the other client sends them in the clear, so the code is an access token and nothing
 * more: whoever has it is in the room, and the relay sees the contents either way.
 */
public final class Room {

    /** Where an invite sends whoever is given one unless told otherwise: the web player's own page,
     *  because that is the one address a room actually has. It opens the room in their client, and a
     *  launcher that hands the same link on to this player is read back by {@link #inviteFrom(Uri)} — so
     *  one link serves everybody. */
    public static final String DEFAULT_INVITE_PAGE = "https://siaivo.isroot.in/lparty/";

    /** Process-wide, like the relay's own address: one page at a time, and every invite written uses it.
     *  Only the link handed out follows this — an invite arriving here is read by its {@code room}
     *  parameter whatever page it came from, so changing this cannot cut anybody off. */
    private static volatile String invitePage = DEFAULT_INVITE_PAGE;

    /** Channel prefix the Lampa plugin uses. Part of the wire contract, not a preference. */
    private static final String CHANNEL_PREFIX = "lparty-r-";
    /** Hex characters of the digest kept as the channel name. Theirs keeps 24. */
    private static final int CHANNEL_HEX = 24;

    private static final int CODE_LENGTH = 6;
    /** The plugin's alphabet, verbatim: no I/O/0/1, so nothing has to be spelled out twice. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** What the web player names its invite, on whatever address it happens to be hosted at. */
    private static final String PARAM_ROOM = "room";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String code;
    private final String password;
    private final String channel;

    /**
     * @param password the room's password, or empty for none. It is mixed into the channel hash
     *                 exactly as the plugin does it, so a wrong password is not an error — it is a
     *                 different channel, and the room simply appears not to exist.
     */
    public Room(final String code, final String password) {
        this.code = code;
        this.password = password == null ? "" : password;
        this.channel = CHANNEL_PREFIX
                + hex(sha256(code.toUpperCase(Locale.US) + "|" + this.password), CHANNEL_HEX);
    }

    /**
     * A fresh room code, in the plugin's shape so codes from either side look alike: six characters
     * from an alphabet with the ambiguous ones left out — no {@code I} against {@code 1}, no
     * {@code O} against {@code 0} — which is what makes a code safe to read out over the phone.
     * 32^6 is about a billion, so it is also the only real barrier to a stranger wandering in.
     */
    public static String newCode() {
        final StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public String code() {
        return code;
    }

    public String channel() {
        return channel;
    }

    /** Whether joining needs more than the code — what the lobby advertises as {@code pwd}. */
    public boolean hasPassword() {
        return !password.isEmpty();
    }

    /** The link to hand out: the invite page with the whole room in one parameter, base64 of
     *  {@code "CODE:password"} exactly as their client writes it. The password rides along because what is
     *  kept private is the link, not its parts. */
    public String invite() {
        final String value = password.isEmpty() ? code : code + ':' + password;
        return invitePage + "?" + PARAM_ROOM + "=" + Uri.encode(Base64.encodeToString(bytes(value),
                Base64.NO_WRAP));
    }

    /**
     * Write future invites to a different page — another instance of the web player, or any page that
     * knows what to do with a {@code room} parameter. Anything that is not an http(s) address falls back
     * to the default rather than quietly producing links nobody can open, exactly as the relay's own
     * setting treats its own.
     *
     * @param url an {@code http://} or {@code https://} address, or empty for {@link #DEFAULT_INVITE_PAGE}
     */
    public static void setInvitePage(final String url) {
        final String trimmed = url == null ? "" : url.trim();
        if (trimmed.isEmpty()
                || !(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            invitePage = DEFAULT_INVITE_PAGE;
            return;
        }
        // The room is appended as this address's query, so a query already on it is dropped rather than
        // written twice — a link with two of them opens nowhere.
        final int query = trimmed.indexOf('?');
        invitePage = query == -1 ? trimmed : trimmed.substring(0, query);
    }

    /** The page invites are written to, for a settings screen to show. */
    public static String invitePage() {
        return invitePage;
    }

    /** UTF-8, or the platform's default on a JVM that somehow lacks it — which no Android has. */
    private static byte[] bytes(final String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value.getBytes();
        }
    }

    /** A room to join, as something outside a link hands it over. */
    public static final class Invite {
        public final String code;
        public final String password;

        Invite(final String code, final String password) {
            this.code = code;
            this.password = password;
        }
    }

    /**
     * An invite carried as a bare value rather than a link: base64 of {@code "CODE:password"} — the
     * shape HTTP basic auth uses for a user and a password, which is where the encoding comes from. A
     * plain code is accepted too, and is looked for first: six characters cannot be mistaken for base64
     * that way round, whereas the reverse depends on a decoder that refuses bad padding, and Android's
     * returns rubbish instead.
     *
     * @return null when the value is neither, which is the answer for anything else that turns up here
     */
    public static Invite inviteFrom(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        final String raw = value.trim();
        if (isCode(raw)) {
            return new Invite(raw, "");
        }
        final String decoded = decodeBase64(raw);
        if (decoded == null) {
            return null;
        }
        // The first colon only: a password may well contain more of them.
        final int separator = decoded.indexOf(':');
        final String code = (separator == -1 ? decoded : decoded.substring(0, separator)).trim();
        if (!isCode(code)) {
            return null;
        }
        return new Invite(code, separator == -1 ? "" : decoded.substring(separator + 1));
    }

    /** Null for anything that is not base64, so nothing here throws on a value meant for someone else. */
    private static String decodeBase64(final String value) {
        for (int flags : new int[] { Base64.DEFAULT, Base64.URL_SAFE }) {
            try {
                return new String(Base64.decode(value, flags), "UTF-8");
            } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
                // Try the next alphabet, then give up.
            }
        }
        return null;
    }

    /**
     * The room an address invites us into, or null when it invites us nowhere.
     *
     * <p>One shape, which is the one a room has: any address at all carrying the invite as its
     * {@code room} parameter — {@link #invite()} writes the web player's page, and a launcher hands the
     * same link on when it sends a viewer here to join rather than to watch a file. The address itself is
     * that page, not media: there is nothing in it to play, and what the room is watching arrives over
     * the room's own channel a moment later.
     */
    public static Invite inviteFrom(final Uri uri) {
        return uri == null ? null : inviteFromValue(uri.getQueryParameter(PARAM_ROOM));
    }

    /** The invite in a value that may be absent, so the caller does not have to check first. */
    private static Invite inviteFromValue(final String value) {
        return value == null ? null : inviteFrom(value);
    }

    /** Six characters, letters or digits. Wide enough for both generators — ours makes digits, the
     *  plugin's makes letters — and the code is upper-cased before it is hashed either way. */
    public static boolean isCode(final String text) {
        if (text == null || text.length() != CODE_LENGTH) {
            return false;
        }
        final String upper = text.toUpperCase(Locale.US);
        for (int i = 0; i < CODE_LENGTH; i++) {
            final char c = upper.charAt(i);
            if ((c < '0' || c > '9') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }

    private static byte[] sha256(final String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(utf8(text));
        } catch (Exception e) {
            // SHA-256 and UTF-8 are required of every JVM; there is no sane recovery.
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(final String text) throws UnsupportedEncodingException {
        return text.getBytes("UTF-8");
    }

    /** First {@code chars} hex characters of the digest, lower case — the plugin's {@code substr}. */
    private static String hex(final byte[] bytes, final int chars) {
        final StringBuilder sb = new StringBuilder(chars);
        for (int i = 0; i < bytes.length && sb.length() < chars; i++) {
            sb.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16));
            if (sb.length() < chars) {
                sb.append(Character.forDigit(bytes[i] & 0xF, 16));
            }
        }
        return sb.toString();
    }
}
