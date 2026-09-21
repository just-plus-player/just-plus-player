package com.brouken.player.media;

import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;

public final class TrackInfo {
   public final TrackGroup group;
   public final int type;
   public final int trackIndex;
   public final boolean isSupported;
   public final  boolean isSelected;
   public final Format format;
   public final boolean isDisableOption;

    public TrackInfo(TrackGroup group,
                     int type,
                     int trackIndex,
                     boolean isSupported,
                     boolean isSelected,
                     Format format,
                     boolean isDisableOption) {
        this.group = group;
        this.type = type;
        this.trackIndex = trackIndex;
        this.isSupported = isSupported;
        this.isSelected = isSelected;
        this.format = format;
        this.isDisableOption = isDisableOption;
    }
}
