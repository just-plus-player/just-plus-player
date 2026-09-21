package com.brouken.player.media;

import static androidx.core.os.BundleCompat.getParcelableArrayList;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.extractor.metadata.Chapter;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public final class PlaybackSessionCollector implements Player.Listener {
    private static final String KEY_AUDIO_TRACK_INDEX = "audio";
    private static final String KEY_SUBTITLES_TRACK_INDEX = "subtitles";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_PLAYBACK_ITEMS = "playback_items";
    private static final String KEY_INDEX = "index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_CREDITS = "credits";
    private static final String KEY_TIMESTAMP = "timestamp";

    private static final String KEY_STATE_AUDIO_TRACK_INDEX = "state_audio_idx";
    private static final String KEY_STATE_SUBTITLES_TRACK_INDEX = "state_subs_idx";
    private static final String KEY_STATE_INDEX = "state_index";
    private static final String KEY_STATE_LAST_KNOWN_POSITION = "state_last_position";
    private static final String KEY_STATE_LAST_KNOWN_DURATION = "state_last_duration";


    private final TracksResolver tracksResolver;
    private final TracksInfo.Supplier tracksSupplier;
    private final ArrayList<Bundle> playbackLog;
    private Long startedAt;
    private int audioTrackIndex = C.INDEX_UNSET;
    private int subtitlesTrackIndex = C.INDEX_UNSET;
    private int index = C.INDEX_UNSET;
    private long lastKnownPosition = 0L;
    private long lastKnownDuration = C.TIME_UNSET;

    public PlaybackSessionCollector(TracksResolver tracksResolver, TracksInfo.Supplier tracksSupplier) {
        this.tracksResolver = tracksResolver;
        this.tracksSupplier = tracksSupplier;
        startedAt = System.currentTimeMillis();
        playbackLog = new ArrayList<>();
    }

    public @NonNull Bundle saveState() {
        Bundle state = new Bundle();
        state.putLong(KEY_STARTED_AT, startedAt);
        state.putParcelableArrayList(KEY_PLAYBACK_ITEMS, playbackLog);
        state.putInt(KEY_STATE_AUDIO_TRACK_INDEX, audioTrackIndex);
        state.putInt(KEY_STATE_SUBTITLES_TRACK_INDEX, subtitlesTrackIndex);
        state.putInt(KEY_STATE_INDEX, index);
        state.putLong(KEY_STATE_LAST_KNOWN_POSITION, lastKnownPosition);
        state.putLong(KEY_STATE_LAST_KNOWN_DURATION, lastKnownDuration);
        return state;
    }

    public void restoreState(@Nullable Bundle state) {
        if (state == null) return;
        this.startedAt = state.getLong(KEY_STARTED_AT, System.currentTimeMillis());
        ArrayList<Bundle> restoredLog = getParcelableArrayList(state, KEY_PLAYBACK_ITEMS, Bundle.class);
        if (restoredLog != null) {
            this.playbackLog.clear();
            this.playbackLog.addAll(restoredLog);
        }

        this.audioTrackIndex = state.getInt(KEY_STATE_AUDIO_TRACK_INDEX, C.INDEX_UNSET);
        this.subtitlesTrackIndex = state.getInt(KEY_STATE_SUBTITLES_TRACK_INDEX, C.INDEX_UNSET);
        this.index = state.getInt(KEY_STATE_INDEX, C.INDEX_UNSET);
        this.lastKnownPosition = state.getLong(KEY_STATE_LAST_KNOWN_POSITION, 0L);
        this.lastKnownDuration = state.getLong(KEY_STATE_LAST_KNOWN_DURATION, C.TIME_UNSET);
    }

    @Override
    public void onEvents(@NonNull Player player, Player.Events events) {
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            if (index != C.INDEX_UNSET) {
                long duration = player.getDuration();
                if (duration < 0) {
                    duration = player.getCurrentTimeline()
                            .getWindow(index, new Timeline.Window())
                            .getDurationMs();
                }
                stopCurrent(lastKnownPosition, duration);
            }
            if (player.getCurrentMediaItem() == null) {
                return;
            }
            index = player.getCurrentMediaItemIndex();
        }

        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            switch (player.getPlaybackState()) {
                case Player.STATE_READY:
                    if (index == C.INDEX_UNSET && player.getCurrentMediaItem() != null) {
                        index = player.getCurrentMediaItemIndex();
                    }
                    break;
                case Player.STATE_ENDED:
                case Player.STATE_IDLE:
                    if (index != C.INDEX_UNSET) {
                        long position = lastKnownPosition;
                        if (player.getPlaybackState() == Player.STATE_ENDED) {
                            position = player.getContentPosition();
                        }
                        stopCurrent(position, player.getDuration());
                    }
                    break;
                default:
            }
        }
        if (player.getPlaybackState() == Player.STATE_READY && player.getPlayWhenReady()) {
            lastKnownPosition = player.getCurrentPosition();
            if (lastKnownDuration == C.TIME_UNSET) {
                lastKnownDuration = player.getDuration();
            }
        }
    }

    @Override
    public void onTrackSelectionParametersChanged(@NonNull TrackSelectionParameters parameters) {
        if (index == C.INDEX_UNSET) return;
        TracksInfo tracks = tracksSupplier.get();
        audioTrackIndex = tracksResolver.resolveOverriddenIndex(
                tracks,
                C.TRACK_TYPE_AUDIO,
                parameters
        );
        subtitlesTrackIndex = tracksResolver.resolveOverriddenIndex(
                tracks,
                C.TRACK_TYPE_TEXT,
                parameters
        );
    }

    @Override
    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                        @NonNull Player.PositionInfo newPosition,
                                        int reason) {
        if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
            lastKnownPosition = oldPosition.positionMs;
        }

    }

    public @NonNull Bundle buildHistory() {
        if (index != C.INDEX_UNSET) {
            stopCurrent(lastKnownPosition, lastKnownDuration);
        }
        Bundle retVal = new Bundle();
        if (audioTrackIndex >= 0) {
            retVal.putInt(KEY_AUDIO_TRACK_INDEX, audioTrackIndex);
        }
        if (subtitlesTrackIndex >= 0) {
            retVal.putInt(KEY_SUBTITLES_TRACK_INDEX, subtitlesTrackIndex);
        }
        retVal.putLong(KEY_STARTED_AT, startedAt);
        retVal.putParcelableArrayList(KEY_PLAYBACK_ITEMS, playbackLog);
        return retVal;
    }

    private void append(int index, long position, long duration, long credits) {
        if (index < 0) {
            return;
        }
        Bundle entry = new Bundle();
        entry.putInt(KEY_INDEX, index);
        entry.putLong(KEY_POSITION, position);
        entry.putLong(KEY_DURATION, duration);
        entry.putLong(KEY_CREDITS, credits);
        entry.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
        playbackLog.add(entry);
    }

    private void stopCurrent(long position, long duration) {
        int idx = index;
        index = C.INDEX_UNSET;
        if (idx == C.INDEX_UNSET) {
            return;
        }
        append(idx, position, duration, resolveCreditsPosition(duration));
        lastKnownPosition = 0L;
        lastKnownDuration = C.TIME_UNSET;
    }

    private long resolveCreditsPosition(long duration) {
        if (duration == C.TIME_UNSET) return C.TIME_UNSET;
        List<Chapter> chapters = tracksSupplier.get().chapters;
        if (!chapters.isEmpty()) {
            long windowStart = (long) (duration * 0.8);
            ListIterator<Chapter> it = chapters.listIterator(chapters.size());
            while (it.hasPrevious()) {
                Chapter c = it.previous();
                long start = c.getStartTimeMs();
                if (start > duration) {
                    continue;
                }
                if (start < windowStart) {
                    break;
                }
                // TODO fix logic
                if (c.getTitle() != null) {
                    String title = c.getTitle().value.trim().toLowerCase();
                    if (title.equals("credits")) {
                        return start;
                    }
                }
                return start;
            }
        }
        return (long) (duration * 0.87);
    }
}
