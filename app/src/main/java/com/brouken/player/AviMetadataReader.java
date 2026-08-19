package com.brouken.player;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Reads the frame rate out of an AVI's main header. The {@code hdrl} list is the first chunk of the
 * RIFF-AVI form and {@code avih/dwMicroSecPerFrame} its first field, so this is a handful of bytes
 * from the very front of the stream — the only thing worth recovering here, since AVI describes its
 * video with a BITMAPINFOHEADER that carries neither a rate nor a bitrate.
 *
 * <p>Returns a single video {@link TrackMetadata} carrying the rate, or nothing when the layout is
 * not what it expects. Track names are not an AVI thing; {@code strn} is vanishingly rare.
 */
final class AviMetadataReader {

    private AviMetadataReader() {}

    /** Bytes of leading chunks (JUNK padding and the like) to look past before giving up on hdrl. */
    private static final long MAX_BYTES_BEFORE_HDRL = 64 * 1024;

    /** {@code inputStream} positioned at the start of the file; the RIFF/AVI signature is re-read here. */
    static List<TrackMetadata> parse(InputStream inputStream) {
        final DataInputStream stream = new DataInputStream(inputStream);
        try {
            if (!"RIFF".equals(readFourCc(stream))) return Collections.emptyList();
            stream.skipBytes(4); // RIFF size
            if (!"AVI ".equals(readFourCc(stream))) return Collections.emptyList();

            long scanned = 0;
            while (scanned < MAX_BYTES_BEFORE_HDRL) {
                final String chunk = readFourCc(stream);
                // Chunks are padded to an even length.
                final long size = (readUInt32(stream) + 1) & ~1L;
                if ("LIST".equals(chunk) && "hdrl".equals(readFourCc(stream))) {
                    return parseHdrl(stream);
                }
                skipFully(stream, "LIST".equals(chunk) ? size - 4 : size);
                scanned += size;
            }
        } catch (IOException e) {
            // The header ended before it was fully read.
        }
        return Collections.emptyList();
    }

    private static List<TrackMetadata> parseHdrl(DataInputStream stream) throws IOException {
        if (!"avih".equals(readFourCc(stream))) return Collections.emptyList();
        readUInt32(stream); // avih size
        final long microSecPerFrame = readUInt32(stream);
        if (microSecPerFrame <= 0) return Collections.emptyList();
        return Collections.singletonList(new TrackMetadata(0, null, "und", TrackMetadata.Type.VIDEO,
                1_000_000f / microSecPerFrame));
    }

    private static String readFourCc(DataInputStream stream) throws IOException {
        final byte[] bytes = new byte[4];
        stream.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /** Little-endian, as everything in RIFF is. */
    private static long readUInt32(DataInputStream stream) throws IOException {
        return Integer.reverseBytes(stream.readInt()) & 0xFFFFFFFFL;
    }

    private static void skipFully(DataInputStream stream, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            final long skipped = stream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                // skip() made no progress — read a byte to move on.
                if (stream.read() < 0) throw new IOException("Truncated AVI header");
                remaining--;
            }
        }
    }
}
