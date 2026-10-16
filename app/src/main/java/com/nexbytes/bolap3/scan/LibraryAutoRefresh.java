package com.nexbytes.bolap3.scan;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.model.Song;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps a device-wide (MediaStore) library in sync: watches MediaStore for changes while the
 * process is alive and rescans once when the library opens. Folder (SAF) libraries are only
 * refreshed manually, because walking a folder tree is much more expensive.
 */
public final class LibraryAutoRefresh {

    private static final long DEBOUNCE_MS = 3000;

    private static Handler handler;
    private static Context appContext;
    private static final Runnable REFRESH = LibraryAutoRefresh::runRefresh;

    private LibraryAutoRefresh() {
    }

    public static synchronized void register(Context context) {
        if (handler != null) {
            return;
        }
        appContext = context.getApplicationContext();
        HandlerThread thread = new HandlerThread("bolap3-refresh", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());

        appContext.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange, @Nullable Uri uri) {
                        requestRefresh();
                    }
                });
    }

    /** Schedules a background rescan (coalesced with other requests in the next few seconds). */
    public static synchronized void requestRefresh() {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(REFRESH);
        handler.postDelayed(REFRESH, DEBOUNCE_MS);
    }

    private static void runRefresh() {
        Context context = appContext;
        if (!LibraryPrefs.isLibraryReady(context)
                || !LibraryPrefs.SCAN_MODE_MEDIA_STORE.equals(LibraryPrefs.getScanMode(context))
                || !SettingsPrefs.isAutoRefresh(context)
                || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            List<Song> scanned = new MusicScanner(context, new AtomicBoolean(false)).scanMediaStore(null);
            List<Song> current = MusicRepository.loadBlocking(context);

            // Song.equals compares every field, so this also catches edited tags.
            if (!scanned.isEmpty() && !new HashSet<>(scanned).equals(new HashSet<>(current))) {
                MusicRepository.replaceAll(context, scanned);
            }
        } catch (MusicScanner.ScanException ignored) {
            // Permission revoked or MediaStore unavailable: keep the current library.
        }
    }
}
