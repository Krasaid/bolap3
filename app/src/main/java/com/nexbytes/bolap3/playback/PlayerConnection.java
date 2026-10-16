package com.nexbytes.bolap3.playback;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

/**
 * Connects a screen to {@link PlaybackService}. Call {@link #connect} in onStart and
 * {@link #disconnect} in onStop; the controller is null until connected, so UI code must
 * handle that (the connection is asynchronous but usually takes a few milliseconds).
 */
public final class PlayerConnection {

    public interface Callback {
        void onConnected(MediaController controller);
    }

    private static final String TAG = "PlayerConnection";

    private final Context context;
    private final Player.Listener listener;
    @Nullable
    private ListenableFuture<MediaController> future;
    @Nullable
    private MediaController controller;

    public PlayerConnection(Context context, Player.Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void connect(Callback callback) {
        if (future != null) {
            return;
        }
        SessionToken token = new SessionToken(context, new ComponentName(context, PlaybackService.class));
        ListenableFuture<MediaController> pending = new MediaController.Builder(context, token).buildAsync();
        future = pending;
        pending.addListener(() -> {
            if (future != pending || pending.isCancelled()) {
                return;
            }
            try {
                controller = pending.get();
                controller.addListener(listener);
                callback.onConnected(controller);
            } catch (ExecutionException | InterruptedException exception) {
                Log.e(TAG, "Could not connect to the playback service", exception);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void disconnect() {
        if (controller != null) {
            controller.removeListener(listener);
        }
        if (future != null) {
            MediaController.releaseFuture(future);
        }
        future = null;
        controller = null;
    }

    @Nullable
    public MediaController get() {
        return controller;
    }
}
