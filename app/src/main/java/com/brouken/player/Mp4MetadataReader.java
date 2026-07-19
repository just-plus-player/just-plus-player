package com.brouken.player;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written MP4/ISO-BMFF box parser that walks {@code moov → trak → tkhd / mdia(mdhd,hdlr) / udta(name)}
 * to recover each track's id, container name, language and type.
 *
 * <p>The input is a sequential, non-seekable stream (fed from the player's own reads), so this only
 * works when {@code moov} precedes {@code mdat} (faststart). When {@code mdat} is seen first the
 * {@code moov} sits at the end of the file, out of reach here, and parsing bails.
 */
final class Mp4MetadataReader {

    private Mp4MetadataReader() {}

    static List<TrackMetadata> parse(InputStream inputStream) {
        final List<TrackMetadata> tracks = new ArrayList<>();
        final DataInputStream stream = new DataInputStream(inputStream);
        try {
            while (true) {
                final long size = readUInt32(stream);
                final String type = readType(stream);
                final long bodySize;
                if (size == 1L) {
                    bodySize = stream.readLong() - 16;
                } else if (size == 0L) {
                    bodySize = Long.MAX_VALUE; // extends to end of stream
                } else {
                    bodySize = size - 8;
                }

                if ("moov".equals(type)) {
                    parseMoov(stream, bodySize, tracks);
                    break;
                } else if ("mdat".equals(type)) {
                    // moov lives after mdat (not faststart) — unreachable from a forward-only stream.
                    break;
                } else {
                    if (bodySize > 0 && bodySize != Long.MAX_VALUE) {
                        skipFully(stream, bodySize);
                    } else {
                        break;
                    }
                }
            }
        } catch (EOFException e) {
            // Stream ended (or the tap was cut off) before moov was fully read — return what we have.
        } catch (IOException e) {
            // Pipe closed / broken — normal termination of the tap.
        }
        return tracks;
    }

    private static void parseMoov(DataInputStream stream, long size, List<TrackMetadata> out) throws IOException {
        long remaining = size;
        while (remaining > 8) {
            final long boxSize = readUInt32(stream);
            final String type = readType(stream);
            final long bodySize = boxSize == 1L ? stream.readLong() - 16 : boxSize - 8;
            final long actualBoxSize = boxSize == 1L ? bodySize + 16 : boxSize;

            if (actualBoxSize > remaining) break;

            if ("trak".equals(type)) {
                final TrackMetadata track = parseTrak(stream, bodySize);
                if (track != null) out.add(track);
            } else {
                skipFully(stream, bodySize);
            }
            remaining -= actualBoxSize;
        }
    }

    private static TrackMetadata parseTrak(DataInputStream stream, long size) throws IOException {
        long remaining = size;
        int trackId = -1;
        String trackName = null;
        String language = null;
        TrackMetadata.Type type = TrackMetadata.Type.UNKNOWN;

        while (remaining > 8) {
            final long boxSize = readUInt32(stream);
            final String typeStr = readType(stream);
            final long bodySize = boxSize == 1L ? stream.readLong() - 16 : boxSize - 8;
            final long actualBoxSize = boxSize == 1L ? bodySize + 16 : boxSize;

            if (actualBoxSize > remaining) break;

            switch (typeStr) {
                case "tkhd":
                    trackId = parseTkhd(stream, bodySize);
                    break;
                case "mdia": {
                    final String[] langAndHandler = parseMdia(stream, bodySize);
                    if (langAndHandler[0] != null) language = langAndHandler[0];
                    type = handlerToType(langAndHandler[1]);
                    break;
                }
                case "udta": {
                    final String name = parseUdta(stream, bodySize);
                    if (name != null) trackName = name;
                    break;
                }
                default:
                    skipFully(stream, bodySize);
                    break;
            }
            remaining -= actualBoxSize;
        }
        return trackId != -1 ? new TrackMetadata(trackId, trackName, language, type) : null;
    }

    private static TrackMetadata.Type handlerToType(String hdlrType) {
        if (hdlrType == null) return TrackMetadata.Type.UNKNOWN;
        switch (hdlrType) {
            case "vide": return TrackMetadata.Type.VIDEO;
            case "soun": return TrackMetadata.Type.AUDIO;
            case "sbtl":
            case "text":
            case "clcp": return TrackMetadata.Type.SUBTITLE;
            default: return TrackMetadata.Type.UNKNOWN;
        }
    }

    private static int parseTkhd(DataInputStream stream, long size) throws IOException {
        final int version = stream.readByte() & 0xFF;
        skipFully(stream, 3); // flags
        skipFully(stream, version == 1 ? 16 : 8); // creation + modification time
        final int trackId = stream.readInt();
        final int readSoFar = version == 1 ? 24 : 16;
        skipFully(stream, size - readSoFar);
        return trackId;
    }

    /** @return {@code [language, handlerType]}. */
    private static String[] parseMdia(DataInputStream stream, long size) throws IOException {
        long remaining = size;
        String language = null;
        String hdlrType = null;

        while (remaining > 8) {
            final long boxSize = readUInt32(stream);
            final String type = readType(stream);
            final long bodySize = boxSize == 1L ? stream.readLong() - 16 : boxSize - 8;
            final long actualBoxSize = boxSize == 1L ? bodySize + 16 : boxSize;

            if (actualBoxSize > remaining) break;

            switch (type) {
                case "mdhd":
                    language = parseMdhd(stream, bodySize);
                    break;
                case "hdlr":
                    hdlrType = parseHdlrType(stream, bodySize);
                    break;
                default:
                    skipFully(stream, bodySize);
                    break;
            }
            remaining -= actualBoxSize;
        }
        return new String[]{language, hdlrType};
    }

    private static String parseMdhd(DataInputStream stream, long size) throws IOException {
        final int version = stream.readByte() & 0xFF;
        skipFully(stream, 3);
        skipFully(stream, version == 1 ? 16 : 8);
        skipFully(stream, 4);
        skipFully(stream, version == 1 ? 8 : 4);

        final int langBits = stream.readUnsignedShort();
        final int readSoFar = version == 1 ? 34 : 22;
        skipFully(stream, size - readSoFar);

        final char c1 = (char) (((langBits >> 10) & 0x1F) + 0x60);
        final char c2 = (char) (((langBits >> 5) & 0x1F) + 0x60);
        final char c3 = (char) ((langBits & 0x1F) + 0x60);
        return "" + c1 + c2 + c3;
    }

    private static String parseHdlrType(DataInputStream stream, long size) throws IOException {
        // hdlr: 1 byte version + 3 bytes flags + 4 bytes pre_defined + 4 bytes handler_type + ...
        skipFully(stream, 8);
        final byte[] typeBytes = new byte[4];
        stream.readFully(typeBytes);
        final String typeStr = new String(typeBytes, StandardCharsets.US_ASCII);
        final long left = size - 12;
        if (left > 0) skipFully(stream, left);
        return typeStr;
    }

    private static String parseUdta(DataInputStream stream, long size) throws IOException {
        long remaining = size;
        String name = null;

        while (remaining > 8) {
            final long boxSize = readUInt32(stream);
            final String type = readType(stream);
            final long bodySize = boxSize == 1L ? stream.readLong() - 16 : boxSize - 8;
            final long actualBoxSize = boxSize == 1L ? bodySize + 16 : boxSize;

            if (actualBoxSize > remaining) break;

            if ("name".equals(type)) {
                final byte[] buffer = new byte[(int) bodySize];
                stream.readFully(buffer);

                int offset = 0;
                // FullBox version/flags (00 00 00 00)
                if (buffer.length > 4 && buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0 && buffer[3] == 0) {
                    offset = 4;
                    // packed language (00 00)
                    if (buffer.length > 6 && buffer[4] == 0 && buffer[5] == 0) {
                        offset = 6;
                    }
                }
                // String.trim() strips both spaces and NUL padding (all chars <= 0x20) from each end.
                name = new String(buffer, offset, buffer.length - offset, StandardCharsets.UTF_8).trim();
            } else {
                skipFully(stream, bodySize);
            }

            if (name != null && !name.isEmpty()) {
                final long left = remaining - actualBoxSize;
                if (left > 0) skipFully(stream, left);
                return name;
            }

            remaining -= actualBoxSize;
        }
        return name;
    }

    private static long readUInt32(DataInputStream stream) throws IOException {
        return stream.readInt() & 0xffffffffL;
    }

    private static String readType(DataInputStream stream) throws IOException {
        final byte[] bytes = new byte[4];
        stream.readFully(bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /** Skips exactly {@code n} bytes, blocking until they are available or throwing on EOF. */
    private static void skipFully(DataInputStream stream, long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            final long skipped = stream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else {
                // skip() made no progress — fall back to a blocking read to force the pipe forward.
                if (stream.read() < 0) throw new EOFException();
                remaining--;
            }
        }
    }
}
