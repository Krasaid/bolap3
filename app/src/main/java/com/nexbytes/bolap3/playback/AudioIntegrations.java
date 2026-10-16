package com.nexbytes.bolap3.playback;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSessionService;

import com.nexbytes.bolap3.data.SettingsPrefs;

/**
 * Integrations between the player and the rest of the device, owned by {@link PlaybackService}:
 * <ul>
 *   <li>Announces the player's audio session to equalizer apps (Wavelet, Poweramp EQ, Dolby…),
 *       which only process audio from sessions announced with
 *       {@link AudioEffect#ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION}. ExoPlayer does not do it.</li>
 *   <li>Resumes playback when headphones are reconnected after a disconnection paused it
 *       (opt-in, see {@link HeadsetResumePolicy}).</li>
 * </ul>
 */
@OptIn(markerClass = UnstableApi.class)
final class AudioIntegrations {

    private static final String TAG = "AudioIntegrations";
    /** Bluetooth routes audio a moment after the device appears; resuming sooner can blip the speaker. */
    private static final long RESUME_DELAY_MS = 1_000;
    /** Media3 keeps the service in the foreground this long after a pause; resuming later may be refused. */
    private static final long RESUME_WINDOW_MS = MediaSessionService.DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS;

    private final Context context;
    private final ExoPlayer player;
    private final AudioManager audioManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int announcedSessionId = C.AUDIO_SESSION_ID_UNSET;
    /** elapsedRealtime of the last pause caused by headphones disconnecting, 0 = none. */
    private long noisyPauseAtMs = 0;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            announceAudioSession(audioSessionId);
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                noisyPauseAtMs = SystemClock.elapsedRealtime();
            } else if (playWhenReady) {
                noisyPauseAtMs = 0;
                handler.removeCallbacks(resumeRunnable);
            }
        }
    };

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            for (AudioDeviceInfo device : addedDevices) {
                if (device.isSink() && HeadsetResumePolicy.isHeadphones(device.getType())) {
                    handler.removeCallbacks(resumeRunnable);
                    handler.postDelayed(resumeRunnable, RESUME_DELAY_MS);
                    return;
                }
            }
        }
    };

    private final Runnable resumeRunnable = this::resumeIfAllowed;

    AudioIntegrations(Context context, ExoPlayer player) {
        this.context = context;
        this.player = player;
        this.audioManager = context.getSystemService(AudioManager.class);
    }

    void start() {
        player.addListener(playerListener);
        announceAudioSession(player.getAudioSessionId());
        if (audioManager != null) {
            audioManager.registerAudioDeviceCallback(deviceCallback, handler);
        }
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
        }
        player.removeListener(playerListener);
        announceAudioSession(C.AUDIO_SESSION_ID_UNSET);
    }

    private void resumeIfAllowed() {
        boolean resume = HeadsetResumePolicy.shouldResume(
                SettingsPrefs.isResumeOnHeadphones(context),
                noisyPauseAtMs,
                SystemClock.elapsedRealtime(),
                RESUME_WINDOW_MS,
                player.getMediaItemCount() > 0,
                player.isPlaying());
        if (!resume) {
            return;
        }
        noisyPauseAtMs = 0;
        if (player.getPlaybackState() == Player.STATE_IDLE) {
            player.prepare();
        }
        player.play();
    }

    /** Closes the previous session for equalizer apps (if any) and opens the new one. */
    private void announceAudioSession(int sessionId) {
        if (sessionId == announcedSessionId) {
            return;
        }
        if (announcedSessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendEffectBroadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, announcedSessionId);
        }
        announcedSessionId = sessionId;
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendEffectBroadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sessionId);
        }
    }

    private void sendEffectBroadcast(String action, int sessionId) {
        Intent intent = new Intent(action)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        try {
            // Running equalizer apps listen with dynamic receivers...
            context.sendBroadcast(intent);
            // ...but receivers declared in a manifest only get explicit broadcasts on Android 8+.
            for (ResolveInfo receiver : context.getPackageManager().queryBroadcastReceivers(intent, 0)) {
                context.sendBroadcast(new Intent(intent)
                        .setClassName(receiver.activityInfo.packageName, receiver.activityInfo.name));
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not announce audio session " + sessionId, exception);
        }
    }
}
