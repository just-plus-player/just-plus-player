package com.brouken.player;

import android.media.MediaCodecInfo.CodecProfileLevel;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ForwardingExtractor;
import androidx.media3.extractor.ForwardingExtractorOutput;
import androidx.media3.extractor.ForwardingExtractorsFactory;
import androidx.media3.extractor.ForwardingTrackOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;

import java.util.List;
import java.util.Map;

/**
 * Plays Dolby Vision profile 10 as the AV1 it is, on a device with no Dolby Vision decoder.
 *
 * <p>Profile 10 is the AV1-based Dolby Vision: a plain AV1 bitstream with the RPU in a metadata OBU
 * that an AV1 decoder skips. The container says so only in a block addition mapping ({@code dvcC}),
 * and Media3 reads that and relabels the track {@code video/dolby-vision} with codecs {@code
 * dav1.10.08} — the AV1 stream and its {@code av1C} initialisation data untouched underneath.
 *
 * <p>Media3 then does the right thing twice and the wrong thing once. It finds no {@code
 * video/dolby-vision} decoder on a phone that has none, opens the AV1 decoder instead
 * ({@code getAlternativeDecoderInfos}), sizes its input buffers for AV1 — and then, in {@code
 * MediaCodecVideoRenderer.getMediaFormat}, writes {@code KEY_PROFILE} = {@code
 * DolbyVisionProfileDvav110} onto that AV1 codec, because the test there is on the mime of the
 * <em>format</em> and not of the decoder being configured. An AV1 decoder has no such profile
 * number, and the ones that will not be configured with it throw {@code IllegalArgumentException}
 * out of {@code MediaCodec.configure}: a Mi Note 10 on Android 11 ends the film on the error screen
 * after three retries, where the emulator's own AOSP codec swallows the number and plays. Decoder
 * fallback cannot rescue it — every AV1 decoder on the device is handed the same configuration —
 * and upstream still writes it this way on main.
 *
 * <p>So the mime is corrected where it is first published, which also puts the track in front of
 * every renderer that could never see it under a Dolby Vision mime. The picture is the base layer's
 * HDR10, which is what VLC shows for the same file and the only thing a device without a Dolby
 * Vision decoder could ever have shown.
 *
 * <p>Left alone when the device does list a profile 10 decoder: there the Dolby Vision path is real,
 * the profile Media3 sets is the one the codec asked for, and taking it away would trade Dolby
 * Vision for HDR10 on the hardware that can actually do it.
 */
final class Dv10AsAv1 extends ForwardingExtractorsFactory {

    /** Cached answer to "does this device list a Dolby Vision profile 10 decoder"; a device cannot change its mind. */
    @Nullable
    private static volatile Boolean profile10Listed;

    Dv10AsAv1(ExtractorsFactory delegate) {
        super(delegate);
    }

    @Override
    public Extractor[] createExtractors() {
        return wrap(super.createExtractors(), null);
    }

    @Override
    public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
        return wrap(super.createExtractors(uri, responseHeaders), uri);
    }

    private static Extractor[] wrap(Extractor[] extractors, @Nullable Uri uri) {
        for (int i = 0; i < extractors.length; i++) {
            extractors[i] = new ForwardingExtractor(extractors[i]) {
                @Override
                public void init(ExtractorOutput output) {
                    super.init(new ForwardingExtractorOutput(output) {
                        /**
                         * The one place in the app where the file's own table of sync points passes
                         * by. A progressive extractor builds it from the container - Matroska's Cues,
                         * MP4's stss - and hands it over before the first sample, so keeping a
                         * reference costs no read of its own. {@link PlayerActivity} asks it where the
                         * keyframes are, so a seek by the remote can be aimed at one instead of
                         * decoding its way to a time that lies between two.
                         */
                        @Override
                        public void seekMap(SeekMap seekMap) {
                            PlayerActivity.rememberSeekMap(uri, seekMap);
                            super.seekMap(seekMap);
                        }

                        @Override
                        public TrackOutput track(int id, int type) {
                            final TrackOutput track = super.track(id, type);
                            if (type != C.TRACK_TYPE_VIDEO) {
                                return track;
                            }
                            return new ForwardingTrackOutput(track) {
                                @Override
                                public void format(Format format) {
                                    super.format(asAv1(format));
                                }
                            };
                        }
                    });
                }
            };
        }
        return extractors;
    }

    /**
     * The same format under its real codec, or the format itself when this is not profile 10.
     *
     * <p>The codec string goes rather than being rewritten into an {@code av01.…} one: Media3 reads
     * the profile out of it and nothing here knows the AV1 profile, tier and bit depth that string
     * has to carry. Absent it, the decoder is chosen by mime and the profile check is skipped, which
     * is exactly what a container that never named an AV1 codec deserves.
     */
    static Format asAv1(Format format) {
        // dav1 is the AV1 sample entry and the only one profile 10 uses; dvav/dva1 are the AVC ones
        // (profile 9) and dvhe/dvh1 the HEVC ones, none of which this can redirect.
        if (!MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
                || format.codecs == null
                || !format.codecs.startsWith("dav1.")
                || deviceListsProfile10()) {
            return format;
        }
        Utils.log("Dolby Vision " + format.codecs + " offered as AV1: no profile 10 decoder here");
        return format.buildUpon()
                .setSampleMimeType(MimeTypes.VIDEO_AV1)
                .setCodecs(null)
                .build();
    }

    private static boolean deviceListsProfile10() {
        final Boolean cached = profile10Listed;
        if (cached != null) {
            return cached;
        }
        boolean supported = false;
        try {
            for (MediaCodecInfo info : MediaCodecUtil.getDecoderInfos(
                    MimeTypes.VIDEO_DOLBY_VISION, /* secure= */ false, /* tunneling= */ false)) {
                for (CodecProfileLevel profileLevel : info.getProfileLevels()) {
                    supported |= profileLevel.profile == CodecProfileLevel.DolbyVisionProfileDvav110;
                }
            }
        } catch (MediaCodecUtil.DecoderQueryException | RuntimeException e) {
            supported = false;
        }
        profile10Listed = supported;
        return supported;
    }
}
