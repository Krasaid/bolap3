package com.nexbytes.bolap3.playback;

import android.media.AudioDeviceInfo;

/**
 * Decides when to resume playback because headphones (wired, USB or Bluetooth) were connected.
 * Only resumes a pause that was caused by disconnecting them ("audio becoming noisy"), and only
 * within a short window: a manual pause never restarts by itself, and Android only lets the
 * service go back to the foreground while Media3 still keeps it there after a pause.
 */
public final class HeadsetResumePolicy {

    private HeadsetResumePolicy() {
    }

    /** Output devices that count as "headphones" (including USB DACs and BLE audio). */
    public static boolean isHeadphones(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return true;
            default:
                return false;
        }
    }

    /**
     * @param noisyPauseAtMs elapsed-realtime of the last pause caused by a disconnection, or 0
     * @param nowMs          current elapsed-realtime
     * @param windowMs       how long after that pause a reconnection still resumes
     */
    public static boolean shouldResume(boolean enabled, long noisyPauseAtMs, long nowMs, long windowMs,
                                       boolean hasMedia, boolean isPlaying) {
        return enabled
                && hasMedia
                && !isPlaying
                && noisyPauseAtMs > 0
                && nowMs - noisyPauseAtMs <= windowMs;
    }
}
