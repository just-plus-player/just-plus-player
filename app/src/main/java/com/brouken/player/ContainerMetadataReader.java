package com.brouken.player;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Dispatches a container byte stream to the matching parser by signature and returns the list of
 * track names/languages found. Reads sequentially and never seeks — it is fed by the bytes the
 * player itself reads (see {@link TrackNameParsingDataSource}), so it only sees track metadata that
 * lives near the start of the stream (faststart MP4 with {@code moov} up front, Matroska headers).
 */
final class ContainerMetadataReader {

    private ContainerMetadataReader() {}

    static List<TrackMetadata> parse(InputStream inputStream) {
        // 8 bytes is enough for both the MKV EBML id and the MP4 ftyp signature.
        final byte[] header = new byte[8];
        final int bytesRead;
        try {
            bytesRead = inputStream.read(header);
        } catch (IOException e) {
            return Collections.emptyList();
        }

        if (bytesRead < 4) {
            return Collections.emptyList();
        }

        final PushbackInputStream pushbackStream = new PushbackInputStream(inputStream, 8);
        try {
            pushbackStream.unread(header, 0, bytesRead);
        } catch (IOException e) {
            return Collections.emptyList();
        }

        try {
            // MKV / WebM: EBML header 1A 45 DF A3
            if ((header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                    && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) {
                return MatroskaMetadataReader.parse(pushbackStream);
            }
            // MP4: 4-byte box size, then 'ftyp'
            if (bytesRead >= 8 && "ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII))) {
                return Mp4MetadataReader.parse(pushbackStream);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
