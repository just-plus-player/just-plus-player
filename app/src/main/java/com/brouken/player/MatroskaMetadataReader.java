package com.brouken.player;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal EBML/Matroska parser that walks {@code Segment → Tracks → TrackEntry} to recover each
 * track's number, {@code Name}, {@code Language} and type. Reads a forward-only stream fed by the
 * player's own reads.
 */
final class MatroskaMetadataReader {

    private MatroskaMetadataReader() {}

    static List<TrackMetadata> parse(InputStream inputStream) {
        try {
            final EbmlReader reader = new EbmlReader(inputStream);
            // EBML Header: 1A 45 DF A3
            if (reader.readId() != 0x1A45DFA3L) return new ArrayList<>();
            final long headerSize = reader.readSize();
            reader.skip(headerSize);

            final List<TrackMetadata> tracks = new ArrayList<>();

            // Locate Segment (18 53 80 67)
            while (true) {
                final long id = reader.readId();
                final long size = reader.readSize();
                if (id == 0x18538067L) { // Segment
                    parseSegment(reader, tracks);
                    break;
                } else {
                    reader.skip(size);
                }
            }
            return tracks;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void parseSegment(EbmlReader reader, List<TrackMetadata> tracks) {
        long bytesRead = 0L;
        final long limit = 512 * 1024; // cap the search for Tracks inside Segment
        try {
            while (bytesRead < limit) {
                final long id = reader.readId();
                final long s = reader.readSize();
                if (id == 0x1654AE6BL) { // Tracks
                    parseTracks(reader, s, tracks);
                    return;
                } else {
                    reader.skip(s);
                }
                bytesRead += s; // rough estimate
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseTracks(EbmlReader reader, long size, List<TrackMetadata> out) {
        long read = 0L;
        while (read < size) {
            try {
                final long id = reader.readId();
                final long s = reader.readSize();
                if (id == 0xAEL) { // TrackEntry
                    out.add(parseTrackEntry(reader, s));
                } else {
                    reader.skip(s);
                }
                read += s;
            } catch (Exception e) {
                break;
            }
        }
    }

    private static TrackMetadata parseTrackEntry(EbmlReader reader, long size) {
        int number = 0;
        String name = null;
        String lang = "und";
        TrackMetadata.Type type = TrackMetadata.Type.UNKNOWN;

        final long startPos = reader.totalBytesRead;

        while ((reader.totalBytesRead - startPos) < size) {
            try {
                final long id = reader.readId();
                final long s = reader.readSize();
                if (id == 0xD7L) { // TrackNumber
                    number = (int) reader.readUInt(s);
                } else if (id == 0x73C5L) { // TrackUID
                    reader.readUInt(s);
                } else if (id == 0x536EL) { // Name
                    name = reader.readString(s);
                } else if (id == 0x22B59CL) { // Language
                    lang = reader.readString(s);
                } else if (id == 0x83L) { // TrackType
                    final int mkvType = (int) reader.readUInt(s);
                    switch (mkvType) {
                        case 1: type = TrackMetadata.Type.VIDEO; break;
                        case 2: type = TrackMetadata.Type.AUDIO; break;
                        case 17: type = TrackMetadata.Type.SUBTITLE; break;
                        default: type = TrackMetadata.Type.UNKNOWN; break;
                    }
                } else {
                    reader.skip(s);
                }
            } catch (Exception e) {
                break;
            }
        }
        return new TrackMetadata(number, name, lang, type);
    }

    private static final class EbmlReader {
        private final InputStream input;
        long totalBytesRead = 0L;

        EbmlReader(InputStream input) {
            this.input = input;
        }

        private int readByte() throws IOException {
            final int b = input.read();
            if (b != -1) totalBytesRead++;
            return b;
        }

        private long readVInt() throws IOException {
            final int first = readByte();
            if (first == -1) throw new EOFException();
            int mask = 0x80;
            int length = 1;
            while ((first & mask) == 0) {
                mask >>= 1;
                length++;
                if (length > 8) throw new IOException("Invalid EBML vint");
            }
            long value = first & (mask - 1);
            for (int i = 0; i < length - 1; i++) {
                final int b = readByte();
                if (b == -1) throw new EOFException();
                value = (value << 8) | (b & 0xFFL);
            }
            return value;
        }

        long readId() throws IOException {
            return readVInt();
        }

        long readSize() throws IOException {
            return readVInt();
        }

        void skip(long bytes) {
            long skipped = 0L;
            try {
                while (skipped < bytes) {
                    final long s = input.skip(bytes - skipped);
                    if (s <= 0) {
                        if (input.read() < 0) break;
                        skipped++;
                    } else {
                        skipped += s;
                    }
                }
            } catch (IOException ignored) {
            }
            totalBytesRead += skipped;
        }

        String readString(long size) throws IOException {
            final byte[] bytes = new byte[(int) size];
            int read = 0;
            while (read < size) {
                final int r = input.read(bytes, read, (int) (size - read));
                if (r == -1) break;
                read += r;
            }
            totalBytesRead += read;
            return trimTrailingNul(new String(bytes, 0, read, StandardCharsets.UTF_8));
        }

        long readUInt(long size) throws IOException {
            long value = 0L;
            for (int i = 0; i < size; i++) {
                final int b = readByte();
                if (b != -1) value = (value << 8) | (b & 0xFFL);
            }
            return value;
        }

        private static String trimTrailingNul(String s) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == 0) end--;
            return s.substring(0, end);
        }
    }
}
