package com.brouken.player;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ForwardingExtractor;
import androidx.media3.extractor.ForwardingExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.mkv.MatroskaExtractor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Makes a Matroska file whose index is reached through a <em>chain</em> of SeekHeads seekable.
 *
 * <p>A SeekHead is Matroska's table of contents: one {@code Seek} entry per top-level element, each
 * naming what it points at ({@code SeekID}) and where it lives ({@code SeekPosition}). Nothing stops
 * one of those entries from pointing at <em>another</em> SeekHead, and mkvmerge writes exactly that
 * whenever the real index cannot live at the front of the file — the head holds a 17-byte stub
 * pointing at the end of the file, and the SeekHead there lists Info, Tracks, Tags and the Cues.
 *
 * <p>{@link MatroskaExtractor} does not follow that link. It remembers a Cues position only from an
 * entry whose {@code SeekID} is {@code Cues} itself, so on such a file it reaches the first Cluster
 * having found no index and settles for {@code SeekMap.Unseekable}. Everything downstream follows
 * from that single verdict: Media3 disables the time bar (a disabled view cannot take D-pad focus,
 * so on TV the bar is unreachable and DOWN dismisses the controls instead), every seek is dropped,
 * playback always starts at zero, and {@code savePlayer()} declines to remember a position. The
 * picture and the duration are fine throughout, which is what makes it look like a control bug
 * rather than a container one. VLC and mpv follow the link, so the same file seeks there.
 *
 * <p>This reads the chain itself — two peeks, no parsing of its own beyond the table of contents —
 * and hands the answer to the extractor, whose own seek-for-cues machinery then does the rest. The
 * hand-over is a write into a private field, because Media3 offers no way to say it: the extractor
 * is created by a factory, {@code cuesContentPosition} has no setter, and the Cues position is not
 * knowable without a read from the far end of the file that no {@code Extractor} API can express.
 *
 * <p>Both peeks happen before the extractor has read a single byte, and that is load-bearing rather
 * than tidy: {@code DefaultEbmlReader} pops its stack of open master elements only when the input
 * crosses an element's end going <em>forward</em>. Jump to the far end of the file after it has
 * opened Segment and jump back, and Segment never closes — every element after that is nested a
 * level too deep. Media3's own seek-for-cues survives only because it returns to a position past the
 * Cluster it popped. So: resolve the chain first, hand over, never reposition the delegate. The state
 * machine only ever moves forwards, so a reopen that lands somewhere unexpected gives up rather than
 * chasing.
 *
 * <p>ponytail: reflection into Media3 internals. Contained — the field is looked up once, and if a
 * Media3 release renames it nothing is wrapped and files like this stay exactly as unseekable as
 * they are today. The reflection-free alternative is to wrap {@link ExtractorInput} and rewrite the
 * eight bytes of the stub entry in flight ({@code SeekID} → Cues, position → the real one, same
 * length, zero-padded), which costs seventeen forwarding methods to save one field write.
 */
final class MkvCuesLink extends ForwardingExtractor {

    private static final int ID_EBML_HEADER = 0x1A45DFA3;
    private static final int ID_SEGMENT = 0x18538067;
    private static final int ID_SEEK_HEAD = 0x114D9B74;
    private static final int ID_SEEK = 0x4DBB;
    private static final int ID_SEEK_ID = 0x53AB;
    private static final int ID_SEEK_POSITION = 0x53AC;
    private static final int ID_CUES = 0x1C53BB6B;

    /**
     * How far to look for a SeekHead. It is the first child of Segment in every file that has one,
     * and a table of contents is a few dozen bytes; this is room for a header, a Void and an index
     * far larger than any of them. Deliberately not more: at the far end of a torrent-backed stream
     * these bytes are pieces somebody has to fetch before the picture starts.
     */
    private static final int PEEK_BYTES = 16 * 1024;

    /** Where the {@code Cues} position lands. Null if a Media3 release moved it — see the class doc. */
    private static final Field CUES_CONTENT_POSITION = cuesContentPositionField();

    private static final int STATE_READ_HEAD = 0;
    private static final int STATE_READ_CHAINED_SEEK_HEAD = 1;
    private static final int STATE_DONE = 2;

    private final MatroskaExtractor matroska;

    private int state = STATE_READ_HEAD;
    /** Absolute offset of Segment's first content byte; SeekPositions are relative to it. */
    private long segmentContentPosition = C.POSITION_UNSET;

    private MkvCuesLink(Extractor delegate, MatroskaExtractor matroska) {
        super(delegate);
        this.matroska = matroska;
    }

    /**
     * Two extra reads at most, and only on a file that actually chains: the head peek returns bytes
     * the extractor is about to read anyway, and a file whose SeekHead names the Cues outright — or
     * has no SeekHead at all — is left alone on the spot.
     */
    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        switch (state) {
            case STATE_READ_HEAD: {
                // A source that does not state its length cannot be seeked into anyway, and an index
                // at an offset nobody can address is not worth a round trip to read. Not settled for
                // good, though: a load whose open() failed shows up here with no length once, and the
                // retry has one.
                if (input.getLength() == C.LENGTH_UNSET) {
                    break;
                }
                // The state moves only once the peek has returned. A peek that throws — the network
                // going away mid-read — leaves the work undone, and the retry of the same load comes
                // back through here to do it; settled beforehand, it would have been skipped for good.
                final long chained = chainedSeekHeadPosition(peek(input));
                state = STATE_DONE;
                if (chained != C.POSITION_UNSET) {
                    state = STATE_READ_CHAINED_SEEK_HEAD;
                    seekPosition.position = chained;
                    return RESULT_SEEK;
                }
                break;
            }
            case STATE_READ_CHAINED_SEEK_HEAD: {
                final long cues = cuesPosition(peek(input));
                state = STATE_DONE;
                if (cues != C.POSITION_UNSET) {
                    try {
                        CUES_CONTENT_POSITION.setLong(matroska, cues);
                    } catch (IllegalAccessException e) {
                        Utils.log("mkv cues link: " + e);
                    }
                }
                // Back to the start: the extractor has read nothing yet and expects to begin at the
                // EBML header. It finds the Cues itself from here, by the same seek it would have
                // made had the stub named them.
                seekPosition.position = 0;
                return RESULT_SEEK;
            }
            default:
                break;
        }
        return super.read(input, seekPosition);
    }

    /** Up to {@link #PEEK_BYTES} from the input's current position, leaving the read position alone. */
    private static byte[] peek(ExtractorInput input) throws IOException {
        final byte[] buffer = new byte[PEEK_BYTES];
        int read = 0;
        // peekFully() throws at the end of input rather than returning short, and the chained
        // SeekHead sits a few hundred bytes from the end of the file — so read what is there.
        while (read < buffer.length) {
            final int count = input.peek(buffer, read, buffer.length - read);
            if (count == C.RESULT_END_OF_INPUT) {
                break;
            }
            read += count;
        }
        input.resetPeekPosition();
        return read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
    }

    /**
     * The absolute position of the SeekHead this file's first SeekHead points at, or
     * {@link C#POSITION_UNSET} if there is no chain to follow — no SeekHead, one that names the Cues
     * itself (the ordinary file, which needs nothing from here), or bytes that are not Matroska.
     */
    private long chainedSeekHeadPosition(byte[] head) {
        try {
            final MatroskaMetadataReader.EbmlReader reader =
                    new MatroskaMetadataReader.EbmlReader(new ByteArrayInputStream(head));
            if (reader.readId() != ID_EBML_HEADER) {
                return C.POSITION_UNSET;
            }
            reader.skip(reader.readSize());
            while (true) {
                final long id = reader.readId();
                final long size = reader.readSize();
                if (id == ID_SEGMENT) {
                    break;
                }
                reader.skip(size);
            }
            segmentContentPosition = reader.totalBytesRead;
            while (true) {
                final long id = reader.readId();
                final long size = reader.readSize();
                if (id == ID_SEEK_HEAD) {
                    final long[] entries = readSeekHead(reader, size);
                    // A SeekHead that names the Cues needs nothing from here, even if it also links
                    // to another one — the extractor already has what it looks for.
                    return entries[0] != C.POSITION_UNSET || entries[1] == C.POSITION_UNSET
                            ? C.POSITION_UNSET : segmentContentPosition + entries[1];
                }
                reader.skip(size);
            }
        } catch (Exception e) {
            // Out of peeked bytes, or a file shaped in a way this does not follow. Playback is not
            // this class's to break: the extractor reads the same stream it always did.
            return C.POSITION_UNSET;
        }
    }

    /** The absolute Cues position named by the SeekHead these bytes start with. */
    private long cuesPosition(byte[] chainedSeekHead) {
        try {
            final MatroskaMetadataReader.EbmlReader reader =
                    new MatroskaMetadataReader.EbmlReader(new ByteArrayInputStream(chainedSeekHead));
            if (reader.readId() != ID_SEEK_HEAD) {
                return C.POSITION_UNSET;
            }
            final long[] entries = readSeekHead(reader, reader.readSize());
            return entries[0] == C.POSITION_UNSET
                    ? C.POSITION_UNSET : segmentContentPosition + entries[0];
        } catch (Exception e) {
            return C.POSITION_UNSET;
        }
    }

    /**
     * Walks one SeekHead's entries, positioned at its first content byte.
     *
     * @return {@code {cues, chainedSeekHead}}, each a Segment-relative position or
     *     {@link C#POSITION_UNSET}.
     */
    private static long[] readSeekHead(MatroskaMetadataReader.EbmlReader reader, long size)
            throws IOException {
        final long[] found = {C.POSITION_UNSET, C.POSITION_UNSET};
        final long end = reader.totalBytesRead + size;
        while (reader.totalBytesRead < end) {
            final long id = reader.readId();
            final long entrySize = reader.readSize();
            if (id != ID_SEEK) {
                reader.skip(entrySize);
                continue;
            }
            final long entryEnd = reader.totalBytesRead + entrySize;
            long seekId = C.POSITION_UNSET;
            long seekPosition = C.POSITION_UNSET;
            while (reader.totalBytesRead < entryEnd) {
                final long childId = reader.readId();
                final long childSize = reader.readSize();
                // SeekID holds the target's element ID exactly as it is written on disk, marker bits
                // and all — the same shape as the constants above.
                if (childId == ID_SEEK_ID) {
                    seekId = reader.readUInt(childSize);
                } else if (childId == ID_SEEK_POSITION) {
                    seekPosition = reader.readUInt(childSize);
                } else {
                    reader.skip(childSize);
                }
            }
            if (seekPosition == C.POSITION_UNSET) {
                continue;
            }
            if (seekId == ID_CUES) {
                found[0] = seekPosition;
            } else if (seekId == ID_SEEK_HEAD) {
                found[1] = seekPosition;
            }
        }
        return found;
    }

    private static Field cuesContentPositionField() {
        try {
            final Field field = MatroskaExtractor.class.getDeclaredField("cuesContentPosition");
            field.setAccessible(true);
            return field;
        } catch (Throwable t) {
            Utils.log("mkv cues link: unavailable, " + t);
            return null;
        }
    }

    /**
     * Wraps Matroska — and only Matroska — wherever it comes from. Goes <em>outside</em>
     * {@link Dv7Converter}, which tests the extractor array for {@code instanceof MatroskaExtractor}
     * and would find a wrapper instead; from out here the Matroska instance is still reachable
     * through {@link Extractor#getUnderlyingImplementation()}.
     */
    static final class Factory extends ForwardingExtractorsFactory {

        Factory(ExtractorsFactory factory) {
            super(factory);
        }

        @Override
        public Extractor[] createExtractors() {
            return wrap(super.createExtractors());
        }

        @Override
        public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
            return wrap(super.createExtractors(uri, responseHeaders));
        }

        /** A wrapper per extractor: Media3 keeps a set alive per media period, and state is per file. */
        private static Extractor[] wrap(Extractor[] extractors) {
            if (CUES_CONTENT_POSITION == null) {
                return extractors;
            }
            for (int i = 0; i < extractors.length; i++) {
                final Extractor underlying = extractors[i].getUnderlyingImplementation();
                if (underlying instanceof MatroskaExtractor) {
                    extractors[i] = new MkvCuesLink(extractors[i], (MatroskaExtractor) underlying);
                }
            }
            return extractors;
        }
    }
}
