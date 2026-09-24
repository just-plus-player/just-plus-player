package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.Assertions;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a file off an SMB 2/3 share, straight into the player.
 *
 * <p>SMB has a positioned read, so this is all it takes — no local HTTP server, no Range header to
 * parse, no re-opening the stream in order to seek. {@link DataSource#open} is handed the position
 * it wants and {@code File.read(buffer, offset, ...)} answers from there.
 *
 * <p>Addresses look like {@code smb://host[:port]/share/path/file.mkv}. Credentials, when they are
 * not in the address itself, come from the saved place — see {@link SmbSessions}.
 */
final class SmbDataSource extends BaseDataSource {

    /**
     * The extractor reads the container's structure a byte at a time — Media3's ExtractorInput
     * passes the length straight through, and MatroskaExtractor asks for one byte per EBML id. That
     * is free on a local file and buffered by the transport over http, but over SMB every read is a
     * round trip: 9806 bytes in 30 s from the cues of a 14.7 GB file before this window existed,
     * with the player's own watchdog cancelling the load. So one network read fills a window and the
     * small reads are served from it — 131 797 sub-kilobyte reads per 8 MiB became eight requests.
     *
     * <p>1 MiB because that is what SMB 3 negotiates as its maximum read, and asking for four
     * produces the same number of round trips.
     */
    private static final int WINDOW = 1024 * 1024;

    /**
     * Switches on the uri's scheme: SMB here, everything else to the factory that was already in
     * place. Shaped after {@code DefaultDataSource}, which does the same for the schemes it knows —
     * this only adds the one it does not.
     */
    static final class Factory implements DataSource.Factory {

        private final Context context;
        private final DataSource.Factory fallback;

        Factory(final Context context, final DataSource.Factory fallback) {
            this.context = context.getApplicationContext();
            this.fallback = fallback;
        }

        @Override
        public DataSource createDataSource() {
            return new Switch(new SmbDataSource(context), fallback.createDataSource());
        }
    }

    private static final class Switch implements DataSource {

        private final SmbDataSource smb;
        private final DataSource fallback;

        @Nullable
        private DataSource chosen;

        Switch(final SmbDataSource smb, final DataSource fallback) {
            this.smb = smb;
            this.fallback = fallback;
        }

        @Override
        public void addTransferListener(final TransferListener listener) {
            smb.addTransferListener(listener);
            fallback.addTransferListener(listener);
        }

        @Override
        public long open(final DataSpec dataSpec) throws IOException {
            Assertions.checkState(chosen == null);
            chosen = SmbSessions.SCHEME.equals(dataSpec.uri.getScheme()) ? smb : fallback;
            return chosen.open(dataSpec);
        }

        @Override
        public int read(final byte[] buffer, final int offset, final int length) throws IOException {
            return Assertions.checkNotNull(chosen).read(buffer, offset, length);
        }

        @Nullable
        @Override
        public Uri getUri() {
            return chosen == null ? null : chosen.getUri();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders() {
            return chosen == null ? DataSource.super.getResponseHeaders() : chosen.getResponseHeaders();
        }

        @Override
        public void close() throws IOException {
            if (chosen != null) {
                try {
                    chosen.close();
                } finally {
                    chosen = null;
                }
            }
        }
    }

    private final Context context;

    @Nullable
    private Uri uri;
    @Nullable
    private com.hierynomus.smbj.share.File file;
    private long position;
    private long bytesRemaining;
    private boolean opened;

    @Nullable
    private byte[] window;
    private long windowStart;
    private int windowLength;

    private SmbDataSource(final Context context) {
        super(/* isNetwork= */ true);
        this.context = context;
    }

    @Override
    public long open(final DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        transferInitializing(dataSpec);

        file = SmbSessions.open(context, uri);

        position = dataSpec.position;
        final long size = file.getFileInformation().getStandardInformation().getEndOfFile();
        bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? size - position : dataSpec.length;
        if (bytesRemaining < 0) {
            throw new IOException("Position " + position + " past the end of " + uri + " (" + size + ")");
        }

        windowLength = 0;
        opened = true;
        transferStarted(dataSpec);
        return bytesRemaining;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        final int wanted = (int) Math.min(length, bytesRemaining);

        // A request as big as the window has nothing to gain from being copied through it.
        if (wanted >= WINDOW) {
            windowLength = 0;
            final int read = readAt(buffer, offset, position, wanted);
            return read <= 0 ? C.RESULT_END_OF_INPUT : advance(read);
        }

        if (position < windowStart || position >= windowStart + windowLength) {
            if (window == null) {
                window = new byte[WINDOW];
            }
            final int fill = (int) Math.min(WINDOW, bytesRemaining);
            final int read = readAt(window, 0, position, fill);
            if (read <= 0) {
                windowLength = 0;
                return C.RESULT_END_OF_INPUT;
            }
            windowStart = position;
            windowLength = read;
        }

        final int from = (int) (position - windowStart);
        final int served = Math.min(wanted, windowLength - from);
        System.arraycopy(Assertions.checkNotNull(window), from, buffer, offset, served);
        return advance(served);
    }

    private int readAt(final byte[] into, final int offset, final long at, final int length)
            throws IOException {
        try {
            return Assertions.checkNotNull(file).read(into, at, offset, length);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    private int advance(final int read) {
        position += read;
        bytesRemaining -= read;
        bytesTransferred(read);
        return read;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() {
        try {
            if (file != null) {
                file.close();
            }
        } catch (final Exception ignored) {
            // A closed handle on a connection that has already gone is not news.
        } finally {
            file = null;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}
