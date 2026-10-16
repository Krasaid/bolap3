package com.nexbytes.bolap3.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

/** Helpers for folders picked with ACTION_OPEN_DOCUMENT_TREE. */
public final class FolderPicker {

    private FolderPicker() {
    }

    /**
     * Keeps access to the folder across reboots. Asks for write access too (needed to delete
     * files from the app) and falls back to read-only if the provider did not grant it.
     */
    public static void persist(ContentResolver resolver, Uri treeUri) {
        try {
            resolver.takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException writeNotGranted) {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }
}
