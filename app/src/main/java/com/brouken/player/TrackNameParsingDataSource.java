package com.brouken.player;

import android.net.Uri;

import androidx.media3.datasource.DataSink;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TeeDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Wraps an upstream {@link DataSource} and, on the first read of a media item (offset 0), tees the
 * bytes the player reads into a background parser ({@link ContainerMetadataReader}) that recovers
 * per-track container names. Uses only public Media3 API ({@link TeeDataSource} + {@link DataSink}),
 * so it works against the locally-built ExoPlayer AAR without touching its internals.
 *
 * <p>Because it only tees the read that starts at offset 0, it captures metadata that the player
 * reads from the front of the stream (faststart MP4, Matroska headers). A {@code moov} at the end of
 * the file is fetched by the player via a separate seek/reopen and is therefore not seen here — the
 * caller degrades to the language name in that case.
 */
final class TrackNameParsingDataSource implements DataSource {

    /** Receives parsed track metadata (on a background thread) and reports whether it already has it. */
    interface Listener {
        void onMetadataParsed(List<TrackMetadata> tracks);
        boolean isMetadataParsed();
    }

    // 64 KB in-RAM pipe — a standard IO chunk; keeps memory bounded and provides backpressure.
    private static final int PIPE_BUFFER_SIZE = 64 * 1024;
    // Upper bound on bytes fed to the parser. A large faststart moov can be tens of MB (the sample
    // tables of a multi-hour 4K file), so this is generous; a non-faststart file bails on the first
    // mdat instead of streaming this far.
    private static final int MAX_BYTES_TO_PARSE = 128 * 1024 * 1024;

    private final DataSource upstream;
    private final Listener listener;
    private final PipeSink pipeSink = new PipeSink();

    private TeeDataSource teeDataSource;

    TrackNameParsingDataSource(DataSource upstream, Listener listener) {
        this.upstream = upstream;
        this.listener = listener;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if (dataSpec.position == 0 && !listener.isMetadataParsed()) {
            teeDataSource = new TeeDataSource(upstream, pipeSink);
            return teeDataSource.open(dataSpec);
        }
        teeDataSource = null;
        return upstream.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return teeDataSource != null
                ? teeDataSource.read(buffer, offset, length)
                : upstream.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return upstream.getUri();
    }

    @Override
    public void close() throws IOException {
        try {
            if (teeDataSource != null) {
                teeDataSource.close();
            } else {
                upstream.close();
            }
        } finally {
            pipeSink.stopParsing();
            teeDataSource = null;
        }
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return upstream.getResponseHeaders();
    }

    private final class PipeSink implements DataSink {
        private PipedOutputStream pipedOut;
        private Thread parsingThread;
        private long totalBytesWritten;
        private volatile boolean parsingActive;

        @Override
        public void open(DataSpec dataSpec) {
            if (dataSpec.position != 0 || listener.isMetadataParsed()) {
                return;
            }
            closePipes();
            totalBytesWritten = 0;
            parsingActive = true;

            final PipedInputStream pipedIn;
            try {
                pipedOut = new PipedOutputStream();
                pipedIn = new PipedInputStream(pipedOut, PIPE_BUFFER_SIZE);
            } catch (IOException e) {
                parsingActive = false;
                pipedOut = null;
                return;
            }

            parsingThread = new Thread(() -> {
                try {
                    final List<TrackMetadata> tracks = ContainerMetadataReader.parse(pipedIn);
                    if (!tracks.isEmpty()) {
                        listener.onMetadataParsed(tracks);
                    }
                } catch (Exception ignored) {
                    // Pipe closed / interrupted — normal termination.
                } finally {
                    parsingActive = false;
                    try {
                        pipedIn.close();
                    } catch (IOException ignored) {
                    }
                }
            }, "TrackNameParser");
            parsingThread.setDaemon(true);
            parsingThread.start();
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            if (!parsingActive) {
                return;
            }
            if (totalBytesWritten >= MAX_BYTES_TO_PARSE) {
                stopParsing();
                return;
            }
            try {
                // Blocks once the 64 KB pipe fills until the parser drains it (backpressure).
                pipedOut.write(buffer, offset, length);
                totalBytesWritten += length;
            } catch (IOException e) {
                // "Pipe closed"/"Pipe broken" — the parser finished.
                stopParsing();
            }
        }

        @Override
        public void close() {
            stopParsing();
        }

        void stopParsing() {
            if (parsingActive) {
                parsingActive = false;
                if (parsingThread != null) {
                    parsingThread.interrupt();
                }
            }
            closePipes();
        }

        private void closePipes() {
            if (pipedOut != null) {
                try {
                    pipedOut.close();
                } catch (IOException ignored) {
                }
                pipedOut = null;
            }
        }
    }

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;
        private final Listener listener;

        Factory(DataSource.Factory upstreamFactory, Listener listener) {
            this.upstreamFactory = upstreamFactory;
            this.listener = listener;
        }

        @Override
        public DataSource createDataSource() {
            return new TrackNameParsingDataSource(upstreamFactory.createDataSource(), listener);
        }
    }
}
