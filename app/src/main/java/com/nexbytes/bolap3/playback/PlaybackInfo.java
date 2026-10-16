package com.nexbytes.bolap3.playback;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * Snapshot of what is playing, written by the playback service and read by the home-screen
 * widget and the quick settings tile (which cannot hold a MediaController themselves).
 */
public final class PlaybackInfo {

    @Nullable public static volatile String title;
    @Nullable public static volatile String artist;
    @Nullable public static volatile Bitmap artwork;
    public static volatile boolean playing;
    public static volatile boolean hasMedia;

    private PlaybackInfo() {
    }

    static void clear() {
        title = null;
        artist = null;
        artwork = null;
        playing = false;
        hasMedia = false;
    }
}
