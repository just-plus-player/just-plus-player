package com.brouken.player;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The file hash api.opensubtitles.com is keyed by: the file's own length plus the first and last
 * 64 KiB of it, summed as little-endian 64-bit words and left to overflow. Ported from Vimu, which
 * is the only player of the three looked at that asks the index this question at all.
 *
 * <p>What it buys is the one thing an id cannot: an id names the <em>title</em>, so an index answers
 * it with subtitles for every release and every cut of that title, and which of them fits this file
 * is then guessed at by how long it runs (SubtitleSearch.ofOneCut, SubtitleFetcher.fitsMedia). A
 * hash names the <em>file</em> — this rip, this cut, these timings — and the guessing is skipped.
 *
 * <p>It is an addition and not a replacement, because it answers only for a file somebody else has
 * seen. Re-encode the video, splice thirty seconds of advertising into it, trim a frame off the
 * tail, and the length changes and the hash is of a file that exists nowhere else: the index has
 * nothing, and the search falls back on the id exactly as it did before. That is the good failure —
 * a hash never answers <em>wrongly</em>, it only fails to answer. So none of the duration heuristics
 * come out; they are what carries every file this misses.
 *
 * <p>Not a guarantee either, even on a hit. The association is uploader-supplied, and the well-known
 * test hash {@code 8e245d9679d31e12} today answers with seven files across three unrelated titles.
 * Which is why the id is still sent alongside: the two together cannot both be wrong about the same
 * entry, so a stray hash cannot drag in another film. It is also why this is only ever an addition to
 * a search that already had an id — a file with no recognised id is still not searched at all, here or
 * before, and making the hash stand on its own is a change to the callers rather than to this class.
 *
 * <p>Read through the player's own {@link DataSource} stack rather than through a file API, so that
 * every scheme the app plays is hashable on the same terms and with the same credentials: smb://
 * has a positioned read, dav:// and dlna:// are rewritten to their http addresses on the way out,
 * and file:// and content:// are seekable to begin with. A source that cannot say how long the file
 * is — a live stream, an http server with no {@code Content-Length} — is refused rather than
 * guessed at, since half a hash is not a worse hash, it is a different file's.
 */
final class MovieHash {

    private MovieHash() {}

    /** 64 KiB off each end. Part of the algorithm, not a buffer size to tune. */
    private static final int CHUNK = 64 * 1024;

    /** A file smaller than both chunks together has no defined hash. Nor is one worth searching for. */
    private static final long MIN_SIZE = CHUNK * 2L;

    static final class Hash {
        /** Sixteen lowercase hex digits, as the API wants it. */
        final String hex;
        final long size;

        Hash(String hex, long size) {
            this.hex = hex;
            this.size = size;
        }
    }

    /**
     * One answer per media, kept because the two subtitle lines search separately and a manual search
     * repeats what an automatic one already did — and on a network share each miss is two round trips
     * and a seek to the end of a file being streamed from the front.
     *
     * <p>ponytail: never trimmed, like SubtitleFetcher.WRONG_CUT. One short entry per file played.
     */
    private static final Map<String, Hash> CACHE = new ConcurrentHashMap<>();

    /** Cached "this one cannot be hashed", so a stream is not re-probed on every search. */
    private static final Hash NONE = new Hash(null, 0);

    /**
     * @param factory the player's own data source stack, so that every scheme it can play can be read
     * @param durationMs part of the key, not of the hash. A URL is not always one file for the life of
     *                   a process: a torrent backend or a proxy hands out an address fixed by port and
     *                   path and plays whatever was last handed to it, and a hash remembered under
     *                   that address would then be another film's — which is worse than no hash at
     *                   all, since {@code moviehash_match} outranks everything below language. How
     *                   long the file runs is what tells those apart, and it is the same key
     *                   SubtitleFetcher.WRONG_CUT is written under, for the same reason.
     * @return the hash, or null when this media cannot be hashed — which is not an error and not
     *         worth a notice: the search carries on by id
     */
    static Hash of(DataSource.Factory factory, Uri uri, long durationMs) {
        if (factory == null || uri == null) {
            return null;
        }
        final String key = uri + "|" + durationMs;
        final Hash cached = CACHE.get(key);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        final Hash hash = compute(factory, uri);
        CACHE.put(key, hash == null ? NONE : hash);
        if (hash != null) {
            Utils.log("moviehash: " + hash.hex + " over " + hash.size + " bytes");
        }
        return hash;
    }

    private static Hash compute(DataSource.Factory factory, Uri uri) {
        final byte[] buffer = new byte[CHUNK];
        final long size;
        long sum;

        // Opened without a length so that open() answers with the whole file's, which is both the
        // first term of the sum and where the tail is. Closed after 64 KiB, so nothing more is read.
        DataSource source = factory.createDataSource();
        try {
            size = source.open(new DataSpec.Builder().setUri(uri).build());
            if (size == C.LENGTH_UNSET || size < MIN_SIZE) {
                Utils.log("moviehash: not hashable, length " + size + " (" + uri.getScheme() + ")");
                return null;
            }
            if (!readFully(source, buffer)) {
                return null;
            }
            sum = size + sum(buffer);
        } catch (Exception e) {
            Utils.log("moviehash: head " + e);
            return null;
        } finally {
            close(source);
        }

        source = factory.createDataSource();
        try {
            source.open(new DataSpec.Builder().setUri(uri)
                    .setPosition(size - CHUNK).setLength(CHUNK).build());
            if (!readFully(source, buffer)) {
                return null;
            }
            sum += sum(buffer);
        } catch (Exception e) {
            // Where a server takes no positioned read. The head alone is not a weaker hash, it is a
            // wrong one, so there is nothing to salvage here.
            Utils.log("moviehash: tail " + e);
            return null;
        } finally {
            close(source);
        }

        return new Hash(String.format(Locale.US, "%016x", sum), size);
    }

    /** Overflow is the algorithm: the sum is meant to wrap at 64 bits. */
    private static long sum(byte[] buffer) {
        long total = 0;
        for (int i = 0; i + 8 <= buffer.length; i += 8) {
            long word = 0;
            for (int b = 7; b >= 0; b--) {
                word = (word << 8) | (buffer[i + b] & 0xFFL);
            }
            total += word;
        }
        return total;
    }

    /**
     * A short read is a different file's hash, so it is a failure rather than something to pad.
     *
     * <p>Anything not positive ends it, not just {@code RESULT_END_OF_INPUT}. A source returning 0 for
     * a non-empty request is breaking the contract, but this stack is a long one — SmbDataSource over
     * ResolvingDataSource over CacheDataSource over whatever the scheme resolves to, some of it this
     * app's own — and the cost of trusting every link in it is a worker thread spinning forever with
     * no timeout to end it. One comparison buys the guarantee that this loop terminates.
     */
    private static boolean readFully(DataSource source, byte[] buffer) throws IOException {
        int done = 0;
        while (done < buffer.length) {
            final int read = source.read(buffer, done, buffer.length - done);
            if (read <= 0) {
                Utils.log("moviehash: short read, " + done + " of " + buffer.length);
                return false;
            }
            done += read;
        }
        return true;
    }

    private static void close(DataSource source) {
        try {
            source.close();
        } catch (IOException ignored) {
            // Nothing was written and the answer is already in hand.
        }
    }
}
