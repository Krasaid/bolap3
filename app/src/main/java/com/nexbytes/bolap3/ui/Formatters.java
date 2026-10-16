package com.nexbytes.bolap3.ui;

import java.util.Locale;

public final class Formatters {

    private Formatters() {
    }

    /** 0:00, 3:45, 1:02:03. Non-positive durations return {@code unknown}. */
    public static String duration(long millis, String unknown) {
        if (millis <= 0) {
            return unknown;
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /** Playback position: like {@link #duration} but 0 shows as 0:00. */
    public static String position(long millis) {
        return duration(millis, "0:00");
    }

    /** 1x, 1.25x, 0.5x */
    public static String speed(float speed) {
        if (speed == Math.floor(speed)) {
            return String.valueOf((int) speed);
        }
        String text = String.format(Locale.ROOT, "%.2f", speed);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
