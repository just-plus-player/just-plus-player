package com.brouken.player.media;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.extractor.metadata.Chapter;

import com.google.common.collect.ImmutableList;

import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class TracksResolver {

    public TracksInfo resolve(Tracks tracks) {
        ArrayList<TrackInfo> video = new ArrayList<>();
        ArrayList<TrackInfo> audio = new ArrayList<>();
        ArrayList<TrackInfo> subtitles = new ArrayList<>();
        ImmutableList<Chapter> chapters = ImmutableList.of();
        for (Tracks.Group g : tracks.getGroups()) {
            for (int trackIndex = 0; trackIndex < g.length; trackIndex++) {
                Format format = g.getTrackFormat(trackIndex);
                boolean isSelected = g.isTrackSelected(trackIndex);
                TrackInfo info = new TrackInfo(
                        g.getMediaTrackGroup(),
                        g.getType(),
                        trackIndex,
                        g.isTrackSupported(trackIndex),
                        isSelected,
                        format,
                        false
                );
                switch (info.type) {
                    case C.TRACK_TYPE_VIDEO:
                        video.add(info);
                        if (isSelected && chapters.isEmpty()) {
                            chapters = resolveChapters(format);
                        }
                        break;
                    case C.TRACK_TYPE_AUDIO:
                        audio.add(info);
                        break;
                    case C.TRACK_TYPE_TEXT:
                        subtitles.add(info);
                        break;
                }
            }
        }

        if (!subtitles.isEmpty()) {
            boolean isSelected = true;
            for (TrackInfo st : subtitles) {
                if (st.isSelected) {
                    isSelected = false;
                    break;
                }
            }
            subtitles.add(0, new TrackInfo(
                    null,
                    C.TRACK_TYPE_TEXT,
                    -1,
                    true,
                    isSelected,
                    new Format.Builder().build(),
                    true
            ));
        }
        return new TracksInfo(
                ImmutableList.copyOf(video),
                ImmutableList.copyOf(audio),
                ImmutableList.copyOf(subtitles),
                chapters
        );
    }


    private ImmutableList<Chapter> resolveChapters(Format format) {
        if (format.metadata == null || format.metadata.length() == 0) {
            return ImmutableList.of();
        }
        return format.metadata.getEntriesOfType(Chapter.class);
    }

    public int resolveOverriddenIndex(@NonNull TracksInfo tracks,
                                      int trackType,
                                      @NonNull TrackSelectionParameters parameters) {
        List<TrackInfo> list = null;
        switch (trackType) {
            case C.TRACK_TYPE_AUDIO:
                list = tracks.audio;
                break;
            case C.TRACK_TYPE_TEXT:
                list = tracks.subtitles;
                break;
            case C.TRACK_TYPE_VIDEO:
                list = tracks.video;
                break;
            default:
                return C.INDEX_UNSET;
        }

        for (int index = 0; index < list.size(); index++) {
            TrackInfo info = list.get(index);
            TrackSelectionOverride override = parameters.overrides.get(info.group);
            if (override == null) {
                continue;
            }
            for (Integer idx : override.trackIndices) {
                if (info.trackIndex == idx) {
                    return index;
                }
            }
        }
        return C.INDEX_UNSET;
    }
}
