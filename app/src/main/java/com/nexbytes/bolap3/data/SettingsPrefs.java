package com.nexbytes.bolap3.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.nexbytes.bolap3.library.SongSort;

/** User settings (the Settings screen). */
public final class SettingsPrefs {

    public static final String PREFS_NAME = "settings";

    public static final String KEY_THEME = "theme";
    public static final String KEY_DYNAMIC_COLORS = "dynamic_colors";
    public static final String KEY_MIN_DURATION_SEC = "min_duration_sec";
    public static final String KEY_AUTO_REFRESH = "auto_refresh";
    public static final String KEY_SKIP_SILENCE = "skip_silence";
    public static final String KEY_REPLAY_GAIN = "replay_gain";
    public static final String KEY_SORT_ORDER = "sort_order";
    public static final String KEY_RESUME_ON_HEADPHONES = "resume_on_headphones";
    public static final String KEY_ARTWORK_COLORS = "artwork_colors";
    public static final String KEY_GROUPS_AS_GRID = "groups_as_grid";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private SettingsPrefs() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Defaults to dark, the app's original look. */
    public static String getTheme(Context context) {
        return prefs(context).getString(KEY_THEME, THEME_DARK);
    }

    public static void setTheme(Context context, String theme) {
        prefs(context).edit().putString(KEY_THEME, theme).apply();
    }

    public static int toNightMode(String theme) {
        if (THEME_LIGHT.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (THEME_SYSTEM.equals(theme)) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        return AppCompatDelegate.MODE_NIGHT_YES;
    }

    /**
     * Applies the theme to AppCompat and to the system (UiModeManager), so the launch splash
     * screen, drawn before any app code runs, matches it too.
     */
    public static void applyTheme(Context context, String theme) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(theme));
        android.app.UiModeManager uiModeManager = context.getSystemService(android.app.UiModeManager.class);
        if (uiModeManager != null) {
            uiModeManager.setApplicationNightMode(THEME_LIGHT.equals(theme)
                    ? android.app.UiModeManager.MODE_NIGHT_NO
                    : THEME_SYSTEM.equals(theme)
                    ? android.app.UiModeManager.MODE_NIGHT_AUTO
                    : android.app.UiModeManager.MODE_NIGHT_YES);
        }
    }

    public static boolean isDynamicColors(Context context) {
        return prefs(context).getBoolean(KEY_DYNAMIC_COLORS, false);
    }

    public static void setDynamicColors(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply();
    }

    public static int getMinDurationSeconds(Context context) {
        return prefs(context).getInt(KEY_MIN_DURATION_SEC, 0);
    }

    public static void setMinDurationSeconds(Context context, int seconds) {
        prefs(context).edit().putInt(KEY_MIN_DURATION_SEC, seconds).apply();
    }

    public static boolean isAutoRefresh(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_REFRESH, true);
    }

    public static void setAutoRefresh(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply();
    }

    public static boolean isSkipSilence(Context context) {
        return prefs(context).getBoolean(KEY_SKIP_SILENCE, false);
    }

    public static void setSkipSilence(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply();
    }

    public static boolean isReplayGain(Context context) {
        return prefs(context).getBoolean(KEY_REPLAY_GAIN, false);
    }

    public static void setReplayGain(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REPLAY_GAIN, enabled).apply();
    }

    /** Off by default: music starting on its own would surprise people who did not ask for it. */
    public static boolean isResumeOnHeadphones(Context context) {
        return prefs(context).getBoolean(KEY_RESUME_ON_HEADPHONES, false);
    }

    public static void setResumeOnHeadphones(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_RESUME_ON_HEADPHONES, enabled).apply();
    }

    /** Buttons and progress bars take their color from the current song's artwork. */
    public static boolean isArtworkColors(Context context) {
        return prefs(context).getBoolean(KEY_ARTWORK_COLORS, true);
    }

    public static void setArtworkColors(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ARTWORK_COLORS, enabled).apply();
    }

    /** Albums/artists/folders shown as a grid of covers (true) or as a list. */
    public static boolean isGroupsAsGrid(Context context) {
        return prefs(context).getBoolean(KEY_GROUPS_AS_GRID, true);
    }

    public static void setGroupsAsGrid(Context context, boolean grid) {
        prefs(context).edit().putBoolean(KEY_GROUPS_AS_GRID, grid).apply();
    }

    public static SongSort getSortOrder(Context context) {
        return SongSort.fromKey(prefs(context).getString(KEY_SORT_ORDER, null));
    }

    public static void setSortOrder(Context context, SongSort sort) {
        prefs(context).edit().putString(KEY_SORT_ORDER, sort.key).apply();
    }
}
