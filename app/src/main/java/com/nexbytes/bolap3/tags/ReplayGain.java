package com.nexbytes.bolap3.tags;

import androidx.annotation.Nullable;

/**
 * Converts ReplayGain to a player volume. Positive gains are capped at full volume: boosting
 * above 1.0 would clip, and ExoPlayer's volume range is 0..1 anyway.
 */
public final class ReplayGain {

    private ReplayGain() {
    }

    public static float volumeFor(@Nullable Float trackGainDb, @Nullable Float albumGainDb) {
        Float gain = trackGainDb != null ? trackGainDb : albumGainDb;
        if (gain == null || gain.isNaN()) {
            return 1f;
        }
        float volume = (float) Math.pow(10, gain / 20.0);
        return Math.max(0f, Math.min(1f, volume));
    }
}
