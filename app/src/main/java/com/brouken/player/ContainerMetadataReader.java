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
 * sequentially and never seeks — it is given the front of the stream the player itself read (see
 * {@link TrackNameParsingDataSource}), so it only sees metadata that lives near the start
 * (faststart MP4 with {@code moov} up front, Matroska and AVI headers).
 */
final class ContainerMetadataReader {

    /** 12 bytes covers the longest signature: RIFF, the form size, then 'AVI '. */
    static final int SIGNATURE_BYTES = 12;

    /**
     * The containers this dispatches to, with how many leading bytes are worth collecting for each.
     * Anything not listed here is not parsed at all, so the caller need not keep its bytes.
     */
    private enum Container {
        // Sized from where real headers end, with room to spare. Measured against ffprobe on real rips:
        // Matroska writes Tracks within the first few kilobytes — 6.5 KB was the worst of eleven files, and
        // 471 bytes on one carrying a 400 KB attachment, since Attachments follow Tracks — and an AVI opens
        // on hdrl at 36 bytes. Whatever is not here by then is not coming, and waiting only delays the
        // answer: the buffer is what bounds the parse, so this is also the ceiling on what it holds.
        MATROSKA(256 * 1024),
        AVI(64 * 1024),
        // A faststart moov is the sample table of the whole file, so it is the one header that gets big.
        // ponytail: an MP4 whose moov exceeds this loses only its per-track udta/name, which is rare to
        // begin with — its frame rate and bitrate come from Format either way. Raise it if one turns up.
        MP4(512 * 1024);

        final int headerBytes;

        Container(int headerBytes) {
            this.headerBytes = headerBytes;
        }
    }

    private ContainerMetadataReader() {}

    /** The container the first {@link #SIGNATURE_BYTES} bytes announce, or null for one we do not parse. */
    private static Container signature(byte[] header) {
        if (header == null || header.length < SIGNATURE_BYTES) {
            return null;
        }
        // MKV / WebM: EBML header 1A 45 DF A3
        if ((header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) {
            return Container.MATROSKA;
        }
        // MP4: 4-byte box size, then 'ftyp'
        if ("ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII))) {
            return Container.MP4;
        }
        // AVI: RIFF form 'AVI '
        if ("RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                && "AVI ".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
            return Container.AVI;
        }
        return null;
    }

    /** Whether the first {@link #SIGNATURE_BYTES} bytes announce a Matroska container. */
    static boolean isMatroska(byte[] header) {
        return signature(header) == Container.MATROSKA;
    }

    /**
     * Bytes of the front of the stream worth collecting for the matching parser, or 0 for a container
     * this does not parse — an HLS manifest, an MPEG-TS segment, anything whose header is not at offset
     * 0. Lets a caller buffering the header stop as soon as it knows there is nothing here to read.
     */
    static int headerBudget(byte[] header) {
        final Container container = signature(header);
        return container == null ? 0 : container.headerBytes;
    }

    static List<TrackMetadata> parse(InputStream inputStream) {
        // Anything shorter than a signature is not media, so a stream that cannot supply one is
        // nothing to parse.
        final byte[] header = new byte[SIGNATURE_BYTES];
        final PushbackInputStream pushbackStream = new PushbackInputStream(inputStream, header.length);
        try {
            new DataInputStream(inputStream).readFully(header);
            pushbackStream.unread(header);
        } catch (IOException e) {
            return Collections.emptyList();
        }

        final Container container = signature(header);
        if (container == null) {
            return Collections.emptyList();
        }
        try {
            switch (container) {
                case MATROSKA:
                    return MatroskaMetadataReader.parse(pushbackStream);
                case MP4:
                    return Mp4MetadataReader.parse(pushbackStream);
                default:
                    return AviMetadataReader.parse(pushbackStream);
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
