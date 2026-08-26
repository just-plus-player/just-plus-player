package com.brouken.player;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ForwardingRenderer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;

/**
 * Splits the media's text tracks between two renderers, which is what lets the second subtitle line
 * show a track that lives inside the media — an HLS rendition, a track muxed into a Matroska file —
 * rather than only a file the app fetched itself.
 *
 * <p>ExoPlayer is built to render one text track. {@code MappingTrackSelector.selectTracks} is final,
 * and it hands every {@code TrackGroup} to exactly one renderer, chosen by {@code findRenderer}:
 * whichever reports the highest support, compared with a strict {@code >}. The tie-breaker that would
 * spread groups over idle renderers is switched on for metadata only. So two identical text renderers
 * are not two halves of the job — the first one takes every group and the second is handed nothing.
 *
 * <p>The way past that is the same comparison. Each renderer answers {@code supportsFormat} for its
 * own line and no other: the first line's renderer says it cannot handle the one format the second
 * line has been given, and the second line's renderer says it can handle nothing else. Each group
 * then has exactly one renderer that will take it, and the selector puts it there by itself — no
 * subclass of the selector, no group reported twice, and nothing at all changed while the second line
 * is off, because then the first renderer still answers for everything.
 *
 * <p>Changing the choice is a re-selection: set the format and call {@code invalidate()} on the track
 * selector, which asks every renderer again.
 */
final class SecondaryTextTrack {

    /**
     * The format the second line is showing from the media, or null while it is off or painting a
     * file. Written on the app thread when the viewer picks a track, read on the playback thread by
     * the selector — hence volatile.
     */
    private volatile Format chosen;

    void set(Format format) {
        chosen = format;
    }

    Format get() {
        return chosen;
    }

    boolean isChosen(Format format) {
        final Format current = chosen;
        return current != null && current.equals(format);
    }

    /** The first line's renderer: everything except whatever the second line has been given. */
    Renderer forPrimary(Renderer renderer) {
        return new Scoped(renderer, false);
    }

    /** The second line's renderer: that one format and nothing else. */
    Renderer forSecondary(Renderer renderer) {
        return new Scoped(renderer, true);
    }

    /**
     * A renderer that answers for one line only. Nothing but {@code supportsFormat} is changed — it
     * decodes and renders exactly as the renderer it wraps, and is only ever asked to, because the
     * selector believes what it says here.
     */
    private final class Scoped extends ForwardingRenderer {

        private final Renderer delegate;
        private final boolean secondary;

        Scoped(Renderer renderer, boolean secondary) {
            super(renderer);
            this.delegate = renderer;
            this.secondary = secondary;
        }

        @Override
        public RendererCapabilities getCapabilities() {
            final RendererCapabilities capabilities = delegate.getCapabilities();
            return new RendererCapabilities() {
                @Override
                public String getName() {
                    return capabilities.getName();
                }

                @Override
                public int getTrackType() {
                    return capabilities.getTrackType();
                }

                @Override
                public int supportsFormat(Format format) throws ExoPlaybackException {
                    final boolean mine = isChosen(format);
                    if (secondary != mine) {
                        // Not this line's track. Reported as a format this renderer cannot take, which
                        // is what makes the selector look at the other one.
                        return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
                    }
                    return capabilities.supportsFormat(format);
                }

                @Override
                public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
                    return capabilities.supportsMixedMimeTypeAdaptation();
                }

                @Override
                public void setListener(Listener listener) {
                    capabilities.setListener(listener);
                }

                @Override
                public void clearListener() {
                    capabilities.clearListener();
                }
            };
        }
    }

}
