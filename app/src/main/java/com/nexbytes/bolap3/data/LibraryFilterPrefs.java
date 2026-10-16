package com.nexbytes.bolap3.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class LibraryFilterPrefs {

    private static final String PREFS_NAME = "library_filter_prefs";
    private static final String KEY_EXCLUDED_FOLDERS = "excluded_folders";

    public static void excludeFolder(Context context, String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            return;
        }

        Set<String> current = getExcludedFolders(context);
        current.add(folderPath);
        prefs(context).edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply();
    }

    /** Removes folders from the exclusion list; their songs return on the next scan. */
    public static void restoreFolders(Context context, Collection<String> folderPaths) {
        Set<String> current = getExcludedFolders(context);
        current.removeAll(folderPaths);
        prefs(context).edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply();
    }

    public static boolean isFolderExcluded(Context context, String folderPath) {
        return FolderPaths.isWithinAny(folderPath, getExcludedFolders(context));
    }

    /** Returns a copy that callers may modify. Read it once per scan, not once per song. */
    public static Set<String> getExcludedFolders(Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_EXCLUDED_FOLDERS, new HashSet<>()));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
