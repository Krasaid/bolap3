package com.nexbytes.bolap3.media;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.palette.graphics.Palette;

import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.model.Song;

import java.util.Objects;

/**
 * The accent color taken from the current song's artwork (or its generated cover), shared by
 * every screen so buttons, progress bars and tabs follow the music. Null means "use the theme
 * accent" (setting off, gray artwork, nothing playing).
 */
public final class NowPlayingAccent {

    private static final MutableLiveData<Integer> accent = new MutableLiveData<>(null);
    private static final LruCache<String, Integer> cache = new LruCache<>(64);
    /** Distinguishes "no accent for this song" from "not computed" in the cache. */
    private static final int NO_ACCENT = 0;

    @Nullable
    private static String requestKey;

    private NowPlayingAccent() {
    }

    public static LiveData<Integer> get() {
        return accent;
    }

    /** Call when the current song changes (from an Activity: its night mode decides the tone). */
    @MainThread
    public static void update(Context activityContext, @Nullable Song song) {
        if (song == null || !SettingsPrefs.isArtworkColors(activityContext)) {
            requestKey = null;
            setIfChanged(null);
            return;
        }
        boolean night = (activityContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String key = song.getUri() + (night ? "#n" : "#d");
        if (key.equals(requestKey)) {
            return;
        }
        requestKey = key;

        Integer cached = cache.get(key);
        if (cached != null) {
            setIfChanged(cached == NO_ACCENT ? null : cached);
            return;
        }

        Context app = activityContext.getApplicationContext();
        AppExecutors.io().execute(() -> {
            Integer color = compute(app, song, night);
            cache.put(key, color != null ? color : NO_ACCENT);
            AppExecutors.main(() -> {
                if (key.equals(requestKey)) {
                    setIfChanged(color);
                }
            });
        });
    }

    /** Forget the current song so the next {@link #update} recomputes (setting toggled). */
    @MainThread
    public static void invalidate() {
        requestKey = null;
        setIfChanged(null);
    }

    @WorkerThread
    @Nullable
    private static Integer compute(Context context, Song song, boolean night) {
        Bitmap bitmap = AlbumArtLoader.loadBlocking(context, song, AlbumArtLoader.SIZE_THUMB);
        if (bitmap == null) {
            int generated = CoverArt.colors(AlbumArtLoader.albumSeed(song))[0];
            return AccentMath.normalize(generated, night);
        }
        Palette palette = Palette.from(bitmap).maximumColorCount(16).generate();
        Palette.Swatch[] candidates = {
                palette.getVibrantSwatch(),
                night ? palette.getLightVibrantSwatch() : palette.getDarkVibrantSwatch(),
                palette.getDominantSwatch(),
                palette.getMutedSwatch()
        };
        for (Palette.Swatch swatch : candidates) {
            if (swatch != null) {
                Integer normalized = AccentMath.normalize(swatch.getRgb(), night);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private static void setIfChanged(@Nullable Integer color) {
        if (!Objects.equals(accent.getValue(), color)) {
            accent.setValue(color);
        }
    }
}
