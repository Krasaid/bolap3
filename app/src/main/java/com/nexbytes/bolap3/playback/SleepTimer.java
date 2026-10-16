package com.nexbytes.bolap3.playback;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Sleep timer living next to the player (same process, main thread). Either pauses after a
 * duration or at the end of the current song, using ExoPlayer's pause-at-end-of-item support
 * so the next song never starts.
 */
@OptIn(markerClass = UnstableApi.class)
public final class SleepTimer {

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable PAUSE = SleepTimer::pauseNow;

    @Nullable
    private static ExoPlayer player;
    private static long deadlineElapsedMs = 0;
    private static boolean endOfTrack = false;

    private static final Player.Listener END_OF_TRACK_LISTENER = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (endOfTrack && !playWhenReady
                    && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                cancel();
            }
        }
    };

    private SleepTimer() {
    }

    /** Called by the playback service. */
    static void attach(ExoPlayer exoPlayer) {
        player = exoPlayer;
        exoPlayer.addListener(END_OF_TRACK_LISTENER);
    }

    static void detach() {
        cancel();
        if (player != null) {
            player.removeListener(END_OF_TRACK_LISTENER);
        }
        player = null;
    }

    public static void start(int minutes) {
        cancel();
        deadlineElapsedMs = SystemClock.elapsedRealtime() + minutes * 60_000L;
        handler.postDelayed(PAUSE, minutes * 60_000L);
    }

    /** @return false if no song is loaded (nothing to wait for). */
    public static boolean startEndOfTrack() {
        cancel();
        if (player == null || player.getCurrentMediaItem() == null) {
            return false;
        }
        endOfTrack = true;
        player.setPauseAtEndOfMediaItems(true);
        return true;
    }

    public static void cancel() {
        handler.removeCallbacks(PAUSE);
        deadlineElapsedMs = 0;
        if (endOfTrack && player != null) {
            player.setPauseAtEndOfMediaItems(false);
        }
        endOfTrack = false;
    }

    public static boolean isActive() {
        return endOfTrack || deadlineElapsedMs > 0;
    }

    public static boolean isEndOfTrack() {
        return endOfTrack;
    }

    public static long getRemainingMs() {
        return deadlineElapsedMs > 0 ? Math.max(0, deadlineElapsedMs - SystemClock.elapsedRealtime()) : 0;
    }

    private static void pauseNow() {
        deadlineElapsedMs = 0;
        if (player != null) {
            player.pause();
        }
    }
}
