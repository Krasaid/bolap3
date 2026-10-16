package com.nexbytes.bolap3.playback;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * One-shot commands from places without their own controller (home-screen widget, quick settings
 * tile). Connecting starts the playback service if needed; playing with an empty queue triggers
 * playback resumption, which restores the saved queue.
 */
public final class RemoteCommands {

    public static final String PLAY_PAUSE = "play_pause";
    public static final String NEXT = "next";
    public static final String PREVIOUS = "previous";

    private static final String TAG = "RemoteCommands";
    private static final long RELEASE_DELAY_MS = 1500;

    private RemoteCommands() {
    }

    public static void send(Context context, String command, @Nullable BroadcastReceiver.PendingResult pendingResult) {
        Context app = context.getApplicationContext();
        SessionToken token = new SessionToken(app, new ComponentName(app, PlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(app, token).buildAsync();

        future.addListener(() -> {
            try {
                MediaController controller = future.get();
                switch (command) {
                    case NEXT:
                        controller.seekToNext();
                        break;
                    case PREVIOUS:
                        controller.seekToPrevious();
                        break;
                    case PLAY_PAUSE:
                    default:
                        PlaybackActions.togglePlayPause(controller);
                        break;
                }
            } catch (Exception exception) {
                Log.w(TAG, "Command " + command + " failed", exception);
            }
            // Give the session time to process the command before disconnecting.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                MediaController.releaseFuture(future);
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }, RELEASE_DELAY_MS);
        }, ContextCompat.getMainExecutor(app));
    }
}
