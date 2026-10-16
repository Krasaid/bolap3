package com.nexbytes.bolap3.media;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Pure helpers for generated covers (songs/albums without artwork): a pair of gradient colors
 * that is always the same for the same album, and up to two initials.
 */
public final class CoverArt {

    private CoverArt() {
    }

    /** "Greatest Hits" -> "GH", "mareas" -> "M", "  " -> "♪". */
    public static String initials(@Nullable String text) {
        if (text == null) {
            return "♪";
        }
        StringBuilder result = new StringBuilder(2);
        for (String word : text.trim().split("[\\s\\-_./()\\[\\]&+,]+")) {
            int index = firstLetterOrDigit(word);
            if (index >= 0) {
                result.appendCodePoint(word.codePointAt(index));
                if (result.codePointCount(0, result.length()) == 2) {
                    break;
                }
            }
        }
        return result.length() == 0 ? "♪" : result.toString().toUpperCase(Locale.ROOT);
    }

    /** Two ARGB colors (start, end) for a diagonal gradient, derived from {@code seed}. */
    public static int[] colors(@Nullable String seed) {
        int hash = seed == null ? 0 : seed.hashCode();
        float hue = Math.floorMod(hash, 360);
        float shift = 25 + Math.floorMod(hash >>> 9, 40);
        return new int[]{
                hsvToArgb(hue, 0.55f, 0.72f),
                hsvToArgb((hue + shift) % 360, 0.65f, 0.38f)
        };
    }

    /** Same as android.graphics.Color.HSVToColor, without Android (usable in unit tests). */
    static int hsvToArgb(float hue, float saturation, float value) {
        float c = value * saturation;
        float x = c * (1 - Math.abs((hue / 60f) % 2 - 1));
        float m = value - c;
        float r;
        float g;
        float b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return 0xFF000000
                | (Math.round((r + m) * 255) << 16)
                | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    private static int firstLetterOrDigit(String word) {
        for (int i = 0; i < word.length(); i = word.offsetByCodePoints(i, 1)) {
            if (Character.isLetterOrDigit(word.codePointAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
