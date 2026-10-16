package com.nexbytes.bolap3.data;

import android.content.Context;
import android.content.SharedPreferences;

public class LibraryPrefs {

    public static final String SCAN_MODE_MEDIA_STORE = "media_store";
    public static final String SCAN_MODE_SAF_FOLDER = "saf_folder";

    /** Intent extras understood by ScanActivity. */
    public static final String EXTRA_SCAN_MODE = "scan_mode";
    public static final String EXTRA_FOLDER_URI = "folder_uri";

    private static final String PREFS_NAME = "library_prefs";
    private static final String KEY_LIBRARY_READY = "library_ready";
    private static final String KEY_SCAN_MODE = "scan_mode";
    private static final String KEY_FOLDER_URI = "folder_uri";

    public static void setLibraryReady(
            Context context,
            boolean ready,
            String scanMode,
            String folderUri
    ) {
        prefs(context).edit()
                .putBoolean(KEY_LIBRARY_READY, ready)
                .putString(KEY_SCAN_MODE, scanMode)
                .putString(KEY_FOLDER_URI, folderUri)
                .apply();
    }

    public static boolean isLibraryReady(Context context) {
        return prefs(context).getBoolean(KEY_LIBRARY_READY, false);
    }

    public static String getScanMode(Context context) {
        return prefs(context).getString(KEY_SCAN_MODE, null);
    }

    public static String getFolderUri(Context context) {
        return prefs(context).getString(KEY_FOLDER_URI, null);
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
