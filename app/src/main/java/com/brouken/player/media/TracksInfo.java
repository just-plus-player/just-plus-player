package com.brouken.player.media;

import androidx.media3.common.C;
import androidx.media3.extractor.metadata.Chapter;

import com.google.common.collect.ImmutableList;

import org.jspecify.annotations.NonNull;

public final class TracksInfo {
    public static final TracksInfo EMPTY = new TracksInfo(
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of(),
            ImmutableList.of()
    );

    public final ImmutableList<TrackInfo> video;
    public final ImmutableList<TrackInfo> audio;
    public final ImmutableList<TrackInfo> subtitles;
    public final ImmutableList<Chapter> chapters;

    public TracksInfo(ImmutableList<TrackInfo> video,
                      ImmutableList<TrackInfo> audio,
                      ImmutableList<TrackInfo> subtitles,
                      ImmutableList<Chapter> chapters) {
        this.video = video;
        this.audio = audio;
        this.subtitles = subtitles;
        this.chapters = chapters;
    }

    public int indexOfSelected(int trackType) {
        ImmutableList<TrackInfo> tracks;
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
                tracks = video;
                break;
            case C.TRACK_TYPE_AUDIO:
                tracks = audio;
                break;
            case C.TRACK_TYPE_TEXT:
                tracks = subtitles;
                break;
            default:
                return C.INDEX_UNSET;
        }
        for(int index = 0; index < tracks.size(); index++) {
            if(tracks.get(index).isSelected) {
                return index;
            }
        }
        return C.INDEX_UNSET;
    }

    public interface Supplier {
        @NonNull TracksInfo get();
    }
}
