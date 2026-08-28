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
                final File directory = new File(context.getCacheDir(), "media");
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

    /**
     * Writes through to the real cache sink until the byte cap for this open is reached, then swallows
     * the rest. Swallowing rather than failing: the write is the cache's business, not the playback's,
     * and {@link CacheDataSink} commits the part it did receive when it is closed.
     */
    private static final class HeadSink implements DataSink {

        private final DataSink delegate;
        private long written;

        HeadSink(final SimpleCache cache) {
            delegate = new CacheDataSink(cache, PER_OPEN_BYTES);
        }

        @Override
        public void open(final DataSpec dataSpec) throws IOException {
            written = 0;
            delegate.open(dataSpec);
        }

        @Override
        public void write(final byte[] buffer, final int offset, final int length) throws IOException {
            if (written >= PER_OPEN_BYTES) {
                return;
            }
            final int allowed = (int) Math.min(length, PER_OPEN_BYTES - written);
            delegate.write(buffer, offset, allowed);
            written += allowed;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
