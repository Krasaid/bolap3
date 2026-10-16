package com.nexbytes.bolap3.scan;

import androidx.annotation.Nullable;

import java.util.Locale;

/** File-name helpers for folder (SAF) scans, where there is no MediaStore metadata. */
public final class FileNames {

    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".m4a", ".wav", ".flac", ".aac", ".ogg", ".oga", ".opus"
    };

    private FileNames() {
    }

    public static boolean isAudioFile(@Nullable String fileName, @Nullable String mimeType) {
        if (fileName != null && fileName.startsWith(".")) {
            return false;
        }
        if (fileName != null && audioExtensionLength(fileName) > 0) {
            return true;
        }
        return mimeType != null && mimeType.startsWith("audio/") && !mimeType.equals("audio/x-mpegurl");
    }

    /** "My Song.Remix.MP3" -> "My Song.Remix". Only a known audio extension at the end is removed. */
    public static String stripAudioExtension(@Nullable String fileName, String fallback) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return fallback;
        }
        int extensionLength = audioExtensionLength(fileName);
        String stripped = fileName.substring(0, fileName.length() - extensionLength).trim();
        return stripped.isEmpty() ? fallback : stripped;
    }

    /** "song.mp3" -> "song". Removes any extension; used to find "song.lrc" next to "song.flac". */
    public static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** "Music/Rock/" -> "Rock" */
    public static String lastPathSegment(String path) {
        String clean = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = clean.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < clean.length() - 1 ? clean.substring(lastSlash + 1) : clean;
    }

    /** Track/disc tags: "3/12" -> 3, "07" -> 7, garbage or values above 999 -> 0. */
    public static int parseLeadingInt(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        int result = 0;
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            result = result * 10 + (c - '0');
            if (result > 999) {
                return 0;
            }
        }
        return result;
    }

    private static int audioExtensionLength(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : AUDIO_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return extension.length();
            }
        }
        return 0;
    }
}
