package com.nexbytes.bolap3.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioDeviceInfo;

import org.junit.Test;

public class HeadsetResumePolicyTest {

    private static final long WINDOW = 600_000;

    @Test
    public void headphoneTypes() {
        assertTrue(HeadsetResumePolicy.isHeadphones(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertTrue(HeadsetResumePolicy.isHeadphones(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertTrue(HeadsetResumePolicy.isHeadphones(AudioDeviceInfo.TYPE_USB_DEVICE));
        assertFalse(HeadsetResumePolicy.isHeadphones(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertFalse(HeadsetResumePolicy.isHeadphones(AudioDeviceInfo.TYPE_BLUETOOTH_SCO));
    }

    @Test
    public void resumesAfterDisconnectionWithinWindow() {
        assertTrue(HeadsetResumePolicy.shouldResume(true, 1_000, 61_000, WINDOW, true, false));
    }

    @Test
    public void doesNotResumeWhenDisabledManualPauseOrTooLate() {
        assertFalse(HeadsetResumePolicy.shouldResume(false, 1_000, 2_000, WINDOW, true, false));
        assertFalse(HeadsetResumePolicy.shouldResume(true, 0, 2_000, WINDOW, true, false));
        assertFalse(HeadsetResumePolicy.shouldResume(true, 1_000, 1_000 + WINDOW + 1, WINDOW, true, false));
    }

    @Test
    public void doesNotResumeWithoutMediaOrWhilePlaying() {
        assertFalse(HeadsetResumePolicy.shouldResume(true, 1_000, 2_000, WINDOW, false, false));
        assertFalse(HeadsetResumePolicy.shouldResume(true, 1_000, 2_000, WINDOW, true, true));
    }
}
