package com.brouken.player;

import android.content.res.Resources;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.ui.DefaultTrackNameProvider;

import java.util.Locale;
import java.util.Map;

class CustomDefaultTrackNameProvider extends DefaultTrackNameProvider {
    // Format.id -> rich container name (e.g. "DUB | Blu-Ray CEE | DTS | 5.1 | 754 kbps"), shared live
    // with PlayerActivity and populated once the container has been parsed. May be null.
    private Map<String, String> trackNames;

    public CustomDefaultTrackNameProvider(Resources resources) {
        super(resources);
    }

    void setTrackNames(Map<String, String> trackNames) {
        this.trackNames = trackNames;
    }

    @Override
    public String getTrackName(Format format) {
        // Compact, informative label:
        //   <title> [<codec> <channels> <bitrate>k] (<lang>)
        // where <title> is the track's rich metadata label (e.g. "DUB | Blu-Ray CEE | DTS | 5.1 | 754 kbps"),
        // falling back to the language name, then to the framework's default name.
        final boolean hasLang = format.language != null && !format.language.isEmpty()
                && !"und".equals(format.language);

        String title = format.label;
        if (title == null || title.isEmpty()) {
            // Name read straight from the container when Media3 exposes no label.
            if (trackNames != null && format.id != null) {
                title = trackNames.get(format.id);
            }
        }
        if (title == null || title.isEmpty()) {
            if (hasLang) {
                title = capitalize(new Locale(format.language).getDisplayLanguage());
            }
        }

        final String tech = techInfo(format);

        final StringBuilder sb = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            sb.append(title);
        }
        if (!tech.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('[').append(tech).append(']');
        }
        if (hasLang) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('(').append(format.language).append(')');
        }
        return sb.length() > 0 ? sb.toString() : super.getTrackName(format);
    }

    /** Compact technical info: "AAC 5.1 384k" for audio, the codec name for subtitles/other. */
    static String techInfo(Format format) {
        if (format.sampleMimeType == null) {
            return "";
        }
        String codec = formatNameFromMime(format.sampleMimeType);
        if (codec == null) {
            codec = formatNameFromMime(format.codecs);
        }
        if (!MimeTypes.isAudio(format.sampleMimeType)) {
            return codec != null ? codec : "";
        }
        final StringBuilder t = new StringBuilder();
        if (codec != null) {
            t.append(codec);
        }
        if (format.channelCount != Format.NO_VALUE && format.channelCount > 0) {
            if (t.length() > 0) t.append(' ');
            t.append(Utils.formatChannels(format.channelCount));
        }
        final int bitrate = format.averageBitrate != Format.NO_VALUE ? format.averageBitrate : format.peakBitrate;
        if (bitrate > 0) {
            if (t.length() > 0) t.append(' ');
            t.append(bitrate / 1000).append('k');
        }
        return t.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    static String formatNameFromMime(final String mimeType) {
        if (mimeType == null) {
            return null;
        }
        switch (mimeType) {
            case MimeTypes.VIDEO_H264:
                return "H.264";
            case MimeTypes.VIDEO_H265:
                return "H.265";
            case MimeTypes.VIDEO_AV1:
                return "AV1";
            case MimeTypes.VIDEO_VP9:
                return "VP9";
            case MimeTypes.VIDEO_VP8:
                return "VP8";
            case MimeTypes.VIDEO_MPEG2:
                return "MPEG-2";
            case MimeTypes.VIDEO_MP4V:
                return "MPEG-4";
            case MimeTypes.VIDEO_H263:
                return "H.263";
            case MimeTypes.VIDEO_DOLBY_VISION:
                return "Dolby Vision";

            case MimeTypes.AUDIO_DTS:
                return "DTS";
            case MimeTypes.AUDIO_DTS_HD:
                return "DTS-HD";
            case MimeTypes.AUDIO_DTS_EXPRESS:
                return "DTS Express";
            case MimeTypes.AUDIO_TRUEHD:
                return "TrueHD";
            case MimeTypes.AUDIO_AC3:
                return "AC-3";
            case MimeTypes.AUDIO_E_AC3:
                return "E-AC-3";
            case MimeTypes.AUDIO_E_AC3_JOC:
                return "E-AC-3-JOC";
            case MimeTypes.AUDIO_AC4:
                return "AC-4";
            case MimeTypes.AUDIO_AAC:
                return "AAC";
            case MimeTypes.AUDIO_MPEG:
                return "MP3";
            case MimeTypes.AUDIO_MPEG_L2:
                return "MP2";
            case MimeTypes.AUDIO_VORBIS:
                return "Vorbis";
            case MimeTypes.AUDIO_OPUS:
                return "Opus";
            case MimeTypes.AUDIO_FLAC:
                return "FLAC";
            case MimeTypes.AUDIO_ALAC:
                return "ALAC";
            case MimeTypes.AUDIO_WAV:
                return "WAV";
            case MimeTypes.AUDIO_AMR:
                return "AMR";
            case MimeTypes.AUDIO_AMR_NB:
                return "AMR-NB";
            case MimeTypes.AUDIO_AMR_WB:
                return "AMR-WB";
            case MimeTypes.AUDIO_IAMF:
                return "IAMF";
            case MimeTypes.AUDIO_MPEGH_MHA1:
            case MimeTypes.AUDIO_MPEGH_MHM1:
                return "MPEG-H";

            case MimeTypes.APPLICATION_PGS:
                return "PGS";
            case MimeTypes.APPLICATION_SUBRIP:
                return "SRT";
            case MimeTypes.TEXT_SSA:
                return "SSA";
            case MimeTypes.TEXT_VTT:
                return "VTT";
            case MimeTypes.APPLICATION_TTML:
                return "TTML";
            case MimeTypes.APPLICATION_TX3G:
                return "TX3G";
            case MimeTypes.APPLICATION_DVBSUBS:
                return "DVB";
        }
        return null;
    }
}
