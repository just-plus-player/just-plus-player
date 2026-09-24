package com.brouken.player;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Makes {@link #open} never throw: a failed open is reported from the first {@link #read} instead.
 *
 * <p>Media3's progressive loader records where to resume a failed load from in a {@code finally} that
 * runs whether the failure came from {@code open()} or from a read — and takes the position of the
 * {@code ExtractorInput} it has, which after a failed {@code open()} is still the <em>previous</em> one:
 * the input from before the jump the extractor just asked for, or from a loadable that was cancelled
 * by a seek. The retry then reads from that stale position with an extractor whose state already
 * assumes the jump. On a Matroska file that is the first cluster read into sample queues that were
 * never given a format (the extractor sends formats only once it has read the Cues it jumped for), or
 * a seek resumed from an arbitrary byte inside a block. A torrent backend that does not answer a range
 * request until it has fetched the piece is exactly the {@code open()} that fails.
 *
 * <p>With the failure deferred to {@code read()}, the loader has created its input at the requested
 * position and applied any pending extractor seek before the exception arrives, so the position it
 * records is the one it asked for, and the retry continues from there. The exception itself is
 * unchanged, so the retry policy sees what it always saw. Sits at the top of the chain, directly under
 * the loader's own {@code StatsDataSource}.
 */
final class DeferredOpenDataSource implements DataSource {

    static final class Factory implements DataSource.Factory {

        private final DataSource.Factory upstream;

        Factory(final DataSource.Factory upstream) {
            this.upstream = upstream;
        }

        @Override
        public DataSource createDataSource() {
            return new DeferredOpenDataSource(upstream.createDataSource());
        }
    }

    private final DataSource upstream;
    @Nullable
    private IOException openFailure;
    /** Whether the upstream's open() returned; its Uri and headers are only meaningful then. */
    private boolean upstreamOpen;

    private DeferredOpenDataSource(final DataSource upstream) {
        this.upstream = upstream;
    }

    @Override
    public void addTransferListener(final TransferListener listener) {
        upstream.addTransferListener(listener);
    }

    @Override
    public long open(final DataSpec dataSpec) throws IOException {
        try {
            final long length = upstream.open(dataSpec);
            upstreamOpen = true;
            return length;
        } catch (IOException e) {
            openFailure = e;
            return C.LENGTH_UNSET;
        }
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (openFailure != null) {
            final IOException failure = openFailure;
            openFailure = null;
            throw failure;
        }
        return upstream.read(buffer, offset, length);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return upstreamOpen ? upstream.getUri() : null;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstreamOpen ? upstream.getResponseHeaders() : DataSource.super.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        // Always, as the DataSource contract has it: a source whose open() threw still expects the
        // close() that resets it, and the deferred failure has made the loader think it was opened.
        openFailure = null;
        upstreamOpen = false;
        upstream.close();
    }
}
