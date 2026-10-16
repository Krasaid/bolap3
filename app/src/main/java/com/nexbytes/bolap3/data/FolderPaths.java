package com.nexbytes.bolap3.data;

import androidx.annotation.Nullable;

import java.util.Collection;

/**
 * Compares folder paths without plain string-prefix bugs ("Music/Rock" must not contain
 * "Music/Rock2"). Paths are either MediaStore relative paths ("Music/Rock/") or SAF document
 * URIs, where a child's document id is the parent's id plus an encoded '/' ("%2F").
 */
public final class FolderPaths {

    private FolderPaths() {
    }

    /** True if {@code path} is {@code folder} itself or anything inside it. */
    public static boolean isWithin(@Nullable String path, @Nullable String folder) {
        if (path == null || folder == null || folder.isEmpty()) {
            return false;
        }

        if (path.equals(folder)) {
            return true;
        }

        if (!path.startsWith(folder)) {
            return false;
        }

        // "Music/Rock/" or a SAF volume root such as ".../document/primary%3A".
        if (folder.endsWith("/") || endsWithIgnoreCase(folder, "%3A")) {
            return true;
        }

        return path.startsWith("/", folder.length())
                || path.regionMatches(true, folder.length(), "%2F", 0, 3);
    }

    public static boolean isWithinAny(@Nullable String path, Collection<String> folders) {
        for (String folder : folders) {
            if (isWithin(path, folder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
