package com.brouken.player;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Dispatches a container byte stream to the matching parser by signature and returns the track
 * metadata found — names, languages, and the frame rate where the container states one. Reads
 * sequentially and never seeks — it is fed by the bytes the player itself reads (see
 * {@link TrackNameParsingDataSource}), so it only sees metadata that lives near the start of the
 * stream (faststart MP4 with {@code moov} up front, Matroska and AVI headers).
 */
final class ContainerMetadataReader {

    private ContainerMetadataReader() {}

    static List<TrackMetadata> parse(InputStream inputStream) {
        // 12 bytes covers the longest signature: RIFF, the form size, then 'AVI '. Anything shorter
        // than that is not media, so a stream that cannot supply them is nothing to parse.
        final byte[] header = new byte[12];
        final PushbackInputStream pushbackStream = new PushbackInputStream(inputStream, header.length);
        try {
            new DataInputStream(inputStream).readFully(header);
            pushbackStream.unread(header);
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
            if ("ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII))) {
                return Mp4MetadataReader.parse(pushbackStream);
            }
            // AVI: RIFF form 'AVI '
            if ("RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                    && "AVI ".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
                return AviMetadataReader.parse(pushbackStream);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
