package com.nexbytes.bolap3.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in equalizer + bass boost attached to the player's audio session. The playback service
 * calls {@link #attach} whenever ExoPlayer's audio session changes; the settings live in
 * SharedPreferences and are re-applied on every attach. Main thread only.
 */
public final class EqualizerManager {

    private static final String TAG = "EqualizerManager";
    private static final String PREFS = "equalizer";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_BANDS = "bands";
    private static final String KEY_BASS = "bass";

    private static Equalizer equalizer;
    private static BassBoost bassBoost;
    private static int attachedSession = 0;

    private EqualizerManager() {
    }

    public static void attach(Context context, int audioSessionId) {
        if (audioSessionId == attachedSession && equalizer != null) {
            return;
        }
        release();
        if (audioSessionId <= 0) {
            return;
        }
        try {
            equalizer = new Equalizer(0, audioSessionId);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Equalizer unavailable", exception);
            equalizer = null;
            return;
        }
        try {
            bassBoost = new BassBoost(0, audioSessionId);
            if (!bassBoost.getStrengthSupported()) {
                bassBoost.release();
                bassBoost = null;
            }
        } catch (RuntimeException exception) {
            bassBoost = null;
        }
        attachedSession = audioSessionId;
        applySaved(context);
    }

    public static void release() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (bassBoost != null) {
            bassBoost.release();
            bassBoost = null;
        }
        attachedSession = 0;
    }

    /** Audio session the effects are attached to (0 when none). */
    public static int getAttachedSessionId() {
        return attachedSession;
    }

    public static boolean isAvailable() {
        return equalizer != null;
    }

    public static boolean isBassBoostAvailable() {
        return bassBoost != null;
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        applyEnabled(enabled);
    }

    public static int getBandCount() {
        return equalizer != null ? equalizer.getNumberOfBands() : 0;
    }

    /** {min, max} in millibels. */
    public static short[] getBandLevelRange() {
        return equalizer != null ? equalizer.getBandLevelRange() : new short[]{-1500, 1500};
    }

    public static int getCenterFrequencyHz(int band) {
        return equalizer != null ? equalizer.getCenterFreq((short) band) / 1000 : 0;
    }

    public static short getBandLevel(int band) {
        return equalizer != null ? equalizer.getBandLevel((short) band) : 0;
    }

    public static List<String> getPresetNames() {
        List<String> names = new ArrayList<>();
        if (equalizer != null) {
            for (short i = 0; i < equalizer.getNumberOfPresets(); i++) {
                names.add(equalizer.getPresetName(i));
            }
        }
        return names;
    }

    /** Selected preset index, or -1 for custom band levels. */
    public static int getPreset(Context context) {
        return prefs(context).getInt(KEY_PRESET, -1);
    }

    public static void usePreset(Context context, int preset) {
        if (equalizer == null) {
            return;
        }
        try {
            equalizer.usePreset((short) preset);
        } catch (RuntimeException exception) {
            return;
        }
        prefs(context).edit()
                .putInt(KEY_PRESET, preset)
                .putString(KEY_BANDS, currentBandsCsv())
                .apply();
    }

    public static void setBandLevel(Context context, int band, short levelMillibels) {
        if (equalizer == null) {
            return;
        }
        try {
            equalizer.setBandLevel((short) band, levelMillibels);
        } catch (RuntimeException exception) {
            return;
        }
        prefs(context).edit()
                .putInt(KEY_PRESET, -1)
                .putString(KEY_BANDS, currentBandsCsv())
                .apply();
    }

    /** 0..1000 */
    public static int getBassStrength(Context context) {
        return prefs(context).getInt(KEY_BASS, 0);
    }

    public static void setBassStrength(Context context, int strength) {
        prefs(context).edit().putInt(KEY_BASS, strength).apply();
        if (bassBoost != null) {
            try {
                bassBoost.setStrength((short) strength);
            } catch (RuntimeException ignored) {
                // Some devices reject strength changes while disabled.
            }
        }
    }

    public static void reset(Context context) {
        prefs(context).edit().remove(KEY_PRESET).remove(KEY_BANDS).putInt(KEY_BASS, 0).apply();
        if (equalizer != null) {
            for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
                try {
                    equalizer.setBandLevel(band, (short) 0);
                } catch (RuntimeException ignored) {
                    // Keep going with the other bands.
                }
            }
        }
        setBassStrength(context, 0);
    }

    private static void applySaved(Context context) {
        SharedPreferences prefs = prefs(context);
        int preset = prefs.getInt(KEY_PRESET, -1);
        try {
            if (preset >= 0 && preset < equalizer.getNumberOfPresets()) {
                equalizer.usePreset((short) preset);
            } else {
                short[] levels = parseBands(prefs.getString(KEY_BANDS, null));
                if (levels != null) {
                    for (short band = 0; band < Math.min(levels.length, equalizer.getNumberOfBands()); band++) {
                        equalizer.setBandLevel(band, levels[band]);
                    }
                }
            }
            if (bassBoost != null) {
                bassBoost.setStrength((short) prefs.getInt(KEY_BASS, 0));
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not restore equalizer settings", exception);
        }
        applyEnabled(prefs.getBoolean(KEY_ENABLED, false));
    }

    private static void applyEnabled(boolean enabled) {
        try {
            if (equalizer != null) {
                equalizer.setEnabled(enabled);
            }
            if (bassBoost != null) {
                bassBoost.setEnabled(enabled);
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not toggle equalizer", exception);
        }
    }

    private static String currentBandsCsv() {
        StringBuilder builder = new StringBuilder();
        for (short band = 0; band < equalizer.getNumberOfBands(); band++) {
            if (band > 0) builder.append(',');
            builder.append(equalizer.getBandLevel(band));
        }
        return builder.toString();
    }

    @Nullable
    private static short[] parseBands(@Nullable String csv) {
        if (csv == null || csv.isEmpty()) {
            return null;
        }
        String[] parts = csv.split(",");
        short[] levels = new short[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                levels[i] = Short.parseShort(parts[i].trim());
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return levels;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
