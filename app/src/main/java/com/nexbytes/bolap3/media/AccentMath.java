package com.nexbytes.bolap3.media;

import androidx.annotation.Nullable;

/**
 * Turns a color picked from artwork into a usable accent: saturated enough to read as a color and
 * with a lightness that contrasts with the dark or light background. Pure Java (unit tested).
 */
public final class AccentMath {

    /** Below this saturation the artwork is basically gray: keep the theme accent instead. */
    static final float MIN_SOURCE_SATURATION = 0.18f;

    private AccentMath() {
    }

    @Nullable
    public static Integer normalize(int argb, boolean night) {
        float[] hsl = rgbToHsl(argb);
        if (hsl[1] < MIN_SOURCE_SATURATION) {
            return null;
        }
        hsl[1] = Math.max(hsl[1], 0.45f);
        hsl[2] = night ? clamp(hsl[2], 0.55f, 0.68f) : clamp(hsl[2], 0.30f, 0.40f);
        return hslToRgb(hsl);
    }

    /** Black or white, whichever reads better on {@code argb}. */
    public static int onColor(int argb) {
        return luminance(argb) > 0.45 ? 0xFF000000 : 0xFFFFFFFF;
    }

    static double luminance(int argb) {
        return 0.2126 * linear((argb >> 16) & 0xFF) + 0.7152 * linear((argb >> 8) & 0xFF)
                + 0.0722 * linear(argb & 0xFF);
    }

    static float[] rgbToHsl(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h = 0;
        float s = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = ((g - b) / d + (g < b ? 6 : 0)) * 60f;
            } else if (max == g) {
                h = ((b - r) / d + 2) * 60f;
            } else {
                h = ((r - g) / d + 4) * 60f;
            }
        }
        return new float[]{h, s, l};
    }

    static int hslToRgb(float[] hsl) {
        float h = hsl[0];
        float s = hsl[1];
        float l = hsl[2];
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = l - c / 2f;
        float r;
        float g;
        float b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return 0xFF000000
                | (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
