package com.brouken.player;

import android.content.Context;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSink;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A small on-disk cache in front of the network, sized for one thing: the parts of a container that are
 * read again and again.
 *
 * <p>Every {@code prepare()} on a progressive stream re-parses the container from scratch, and for a
 * Matroska that means four cold range requests before the first sample — the header at offset 0, the Cues
 * element which sits at the very end of the file, a cluster back at the front, and only then the playback
 * offset. Measured on a 56 GB remux: 357 KB, 380 KB, 70 KB, then the stream. Against a torrent backend
 * that is the worst possible shape, because such a server blocks the response and re-prioritises its
 * download to whatever offset was asked for — so a re-read drags the swarm to the file tail and back
 * while the viewer waits. The recovery ladder can do that several times in a cascade.
 *
 * <p>Those three reads are identical every time, so they are worth keeping. The rest of the stream is
 * not: caching a 50 Mbps film would write six megabytes a second to a TV box's flash for no gain. Hence
 * the cap below — each opened range may contribute at most its first megabyte, which covers the header
 * and the Cues whole and takes one megabyte off the playback stream. After the first play of a file its
 * container is on disk and a re-prepare asks the network for one range instead of four.
 *
 * <p>Best effort throughout: if the cache cannot be created the chain is returned unwrapped, and a cache
 * error during playback is ignored rather than surfaced (FLAG_IGNORE_CACHE_ON_ERROR).
 */
final class MediaCache {

    /** Under the app's cache directory, which is what makes it the system's to reclaim as well. */
    private static final String DIRECTORY = "media";
    /** Enough for the container heads of a good few films; evicted least-recently-used. */
    private static final long MAX_BYTES = 16 * 1024 * 1024;
    /** How much of each opened range may be written. See the class comment. */
    private static final long PER_OPEN_BYTES = 1024 * 1024;
    /** The build whose bytes are on disk; a different one starts empty (see clearOnNewBuild). */
    private static final String PREF_KEY_CACHE_BUILD = "mediaCacheBuild";

    private static SimpleCache cache;
    private static boolean unavailable;

    private MediaCache() {
    }

    /**
     * The chain with the cache in front of it, or {@code upstream} unchanged when a cache cannot be had.
     * Sits below the track-name tee, so what the tee parses is the same bytes whether they came from the
     * network or from disk.
     */
    static DataSource.Factory wrap(final Context context, final DataSource.Factory upstream) {
        final SimpleCache simpleCache = get(context);
        if (simpleCache == null) {
            return upstream;
        }
        final CacheDataSource.Factory factory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheWriteDataSinkFactory(() -> new HeadSink(simpleCache))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return factory;
    }

    private static synchronized SimpleCache get(final Context context) {
        if (cache == null && !unavailable) {
            try {
                final File directory = new File(context.getCacheDir(), DIRECTORY);
                clearOnNewBuild(context, directory);
                cache = new SimpleCache(directory,
                        new LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                        new StandaloneDatabaseProvider(context));
            } catch (Exception e) {
                // A second instance on the same directory, or no room to write: play without a cache.
                unavailable = true;
                Utils.log("media cache: unavailable, " + e);
            }
        }
        return cache;
    }

    /**
     * Drops everything cached for any media but {@code key}, called as the player is built for it.
     *
     * <p>Nothing in here expires by itself. There is no TTL, and no HTTP freshness either — a
     * {@link CacheDataSource} does not read Cache-Control, ETag or Expires — so without this a span
     * would live until the cache filled its sixteen megabytes, and even then reading one refreshes it:
     * SimpleCache touches a span it serves and the least-recently-used evictor treats that as new. In
     * practice that meant until the next app update, across restarts and reboots.
     *
     * <p>What this cache is actually for is the re-prepares of the media being played — the recovery
     * ladder, the re-read after a pause let the source go, a rebuild — and those all ask for the same
     * key, which is why they still cost nothing. The head of something watched yesterday has no reader
     * left, so it goes. Between sessions the cache therefore holds at most one item's head, and that
     * one only until something else is opened.
     */
    static synchronized void keepOnly(final Context context, final String key) {
        // Nothing has ever been cached on this device — do not build a cache just to empty it. (This is
        // the path of every viewer who only ever plays HLS, where the cache is not used at all.)
        if (cache == null && !new File(context.getCacheDir(), DIRECTORY).exists()) {
            return;
        }
        final SimpleCache simpleCache = get(context);
        if (simpleCache == null) {
            return;
        }
        // Over a copy: removing a resource writes through to the very set getKeys() hands out.
        for (final String cached : new ArrayList<>(simpleCache.getKeys())) {
            if (!cached.equals(key)) {
                simpleCache.removeResource(cached);
            }
        }
    }

    /**
     * Drops everything the previous build cached. A container head that no longer matches what the
     * server serves reads exactly like a corrupt stream, and after an update the first play of a file
     * would otherwise be answered partly from bytes an older build wrote, with nothing to tell the two
     * apart. Keyed on the version code, so it happens once per install and needs nothing remembered by
     * hand.
     */
    private static void clearOnNewBuild(final Context context, final File directory) {
        final android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getInt(PREF_KEY_CACHE_BUILD, 0) == BuildConfig.VERSION_CODE) {
            return;
        }
        if (directory.exists()) {
            SimpleCache.delete(directory, new StandaloneDatabaseProvider(context));
        }
        prefs.edit().putInt(PREF_KEY_CACHE_BUILD, BuildConfig.VERSION_CODE).apply();
    }

    /** Enough of the start of a response to tell a text manifest from a container. */
    private static final int SNIFF_BYTES = 8;

    /**
     * Whether a response that begins with these bytes is text rather than a media container: '#' opens an
     * HLS playlist (#EXTM3U), '<' a DASH or SmoothStreaming manifest and an HTML error page with it, after
     * a UTF-8 BOM and any leading whitespace. No container this cache is for starts with either — Matroska
     * opens 1A 45 DF A3, MPEG-TS 0x47, MP4 carries "ftyp" at offset four, AVI "RIFF".
     *
     * <p>Sniffed from the bytes rather than decided from the URL because the URL is exactly what does not
     * say. A launcher that sends video/* for everything over a link with no telling extension — Lampa does
     * both — leaves a live HLS stream looking progressive until its first response arrives, and the wrong
     * guess here is the worst one there is: a live playlist lists only its current window, so a copy kept
     * on disk and served back on every reload stops playback at the end of that window for good, with the
     * player waiting for a playlist that can no longer change.
     */
    private static boolean isTextManifest(final byte[] head, final int length) {
        int i = 0;
        if (length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            i = 3; // UTF-8 BOM, which both kinds of manifest are allowed to carry
        }
        while (i < length && (head[i] == ' ' || head[i] == '\n'
                || head[i] == '\r' || head[i] == '\t')) {
            i++;
        }
        return i < length && (head[i] == '#' || head[i] == '<');
    }

    /**
     * Writes through to the real cache sink until the byte cap for this open is reached, then swallows
     * the rest. Swallowing rather than failing: the write is the cache's business, not the playback's,
     * and {@link CacheDataSink} commits the part it did receive when it is closed.
     *
     * <p>A response that turns out to be a manifest is refused whole (see {@link #isTextManifest}), which
     * is why the sink below it is opened lazily: nothing must reach it before there are enough bytes to
     * judge, and a refused response must leave no file behind at all.
     */
    private static final class HeadSink implements DataSink {

        private final DataSink delegate;
        private long written;
        /** The spec of an open the delegate has not been given yet; null once it has, or after close. */
        private DataSpec pendingOpen;
        /** The first bytes of this open, held back until there are SNIFF_BYTES of them to judge. */
        private final byte[] head = new byte[SNIFF_BYTES];
        private int headLength;
        private boolean judged;
        private boolean refused;

        HeadSink(final SimpleCache cache) {
            delegate = new CacheDataSink(cache, PER_OPEN_BYTES);
        }

        @Override
        public void open(final DataSpec dataSpec) {
            written = 0;
            headLength = 0;
            // Only a read from the start of a resource can be a whole manifest, and only there do the
            // first bytes mean what isTextManifest reads them as. Mid-file bytes are judged by nobody.
            judged = dataSpec.position != 0;
            refused = false;
            pendingOpen = dataSpec;
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) throws IOException {
            if (refused || written >= PER_OPEN_BYTES) {
                return;
            }
            if (!judged) {
                final int take = Math.min(length, SNIFF_BYTES - headLength);
                System.arraycopy(buffer, offset, head, headLength, take);
                headLength += take;
                if (headLength < SNIFF_BYTES) {
                    return; // not enough to tell yet; close() flushes what there is
                }
                judged = true;
                refused = isTextManifest(head, headLength);
                if (refused) {
                    return;
                }
                writeThrough(head, 0, headLength);
                headLength = 0;
                if (take == length) {
                    return;
                }
                writeThrough(buffer, offset + take, length - take);
                return;
            }
            writeThrough(buffer, offset, length);
        }

        private void writeThrough(final byte[] buffer, final int offset, final int length)
                throws IOException {
            final int allowed = (int) Math.min(length, PER_OPEN_BYTES - written);
            if (allowed <= 0) {
                return;
            }
            if (pendingOpen != null) {
                delegate.open(pendingOpen);
                pendingOpen = null;
            }
            delegate.write(buffer, offset, allowed);
            written += allowed;
        }

        @Override
        public void close() throws IOException {
            // A response too short to judge is too short to be a container head worth keeping either, but
            // it is written rather than dropped: it is also what a bounded read of a few bytes looks like.
            if (!judged && headLength > 0) {
                judged = true;
                writeThrough(head, 0, headLength);
                headLength = 0;
            }
            if (pendingOpen != null) {
                pendingOpen = null; // nothing was ever written, so there is nothing to close
                return;
            }
            delegate.close();
        }
    }
}
