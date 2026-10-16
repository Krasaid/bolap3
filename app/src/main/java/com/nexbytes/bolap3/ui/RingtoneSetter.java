package com.nexbytes.bolap3.ui;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.model.Song;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * "Use as ringtone / notification sound / alarm". Changing system sounds needs the special
 * "modify system settings" permission, granted by the user in a system screen; when they come
 * back with it granted the pending choice is applied. Songs from a picked folder (SAF) are
 * copied into Ringtones/Notifications/Alarms first, because the system cannot read a URI that
 * only this app has access to. Must be created in onCreate (registers a result launcher).
 */
public final class RingtoneSetter {

    private static final String TAG = "RingtoneSetter";

    private final ComponentActivity activity;
    private final ActivityResultLauncher<Intent> permissionLauncher;
    @Nullable
    private Song pendingSong;
    private int pendingType;

    public RingtoneSetter(ComponentActivity activity) {
        this.activity = activity;
        this.permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Song song = pendingSong;
                    pendingSong = null;
                    // The settings screen returns no result code; check the permission itself.
                    if (song != null && Settings.System.canWrite(activity)) {
                        apply(song, pendingType);
                    }
                });
    }

    /** Asks which system sound to replace, then applies it (requesting the permission if needed). */
    public void choose(Song song) {
        int[] types = {RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION, RingtoneManager.TYPE_ALARM};
        String[] labels = {
                activity.getString(R.string.ringtone_call),
                activity.getString(R.string.ringtone_notification),
                activity.getString(R.string.ringtone_alarm)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.ringtone_title, song.getTitle()))
                .setItems(labels, (dialog, which) -> {
                    if (Settings.System.canWrite(activity)) {
                        apply(song, types[which]);
                    } else {
                        requestPermission(song, types[which]);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void requestPermission(Song song, int type) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ringtone_permission_title)
                .setMessage(R.string.ringtone_permission_message)
                .setPositiveButton(R.string.ringtone_open_settings, (dialog, which) -> {
                    pendingSong = song;
                    pendingType = type;
                    permissionLauncher.launch(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + activity.getPackageName())));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void apply(Song song, int type) {
        AppExecutors.io().execute(() -> {
            boolean success;
            try {
                Uri soundUri = systemReadableUri(song, type);
                RingtoneManager.setActualDefaultRingtoneUri(activity, type, soundUri);
                success = true;
            } catch (Exception exception) {
                Log.w(TAG, "Could not set " + song.getUri() + " as sound type " + type, exception);
                success = false;
            }
            boolean done = success;
            AppExecutors.main(() -> Toast.makeText(activity,
                    done ? doneMessage(type) : R.string.ringtone_failed, Toast.LENGTH_SHORT).show());
        });
    }

    /** MediaStore songs are used as they are; anything else is copied into MediaStore first. */
    @WorkerThread
    private Uri systemReadableUri(Song song, int type) throws IOException {
        Uri source = Uri.parse(song.getUri());
        if (MediaStore.AUTHORITY.equals(source.getAuthority())) {
            return source;
        }

        ContentResolver resolver = activity.getContentResolver();
        String mimeType = resolver.getType(source);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, song.getTitle());
        values.put(MediaStore.Audio.Media.TITLE, song.getTitle());
        values.put(MediaStore.Audio.Media.ARTIST, song.getArtist());
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType != null ? mimeType : "audio/mpeg");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, directoryFor(type));
        values.put(MediaStore.Audio.Media.IS_RINGTONE, type == RingtoneManager.TYPE_RINGTONE ? 1 : 0);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, type == RingtoneManager.TYPE_NOTIFICATION ? 1 : 0);
        values.put(MediaStore.Audio.Media.IS_ALARM, type == RingtoneManager.TYPE_ALARM ? 1 : 0);
        values.put(MediaStore.Audio.Media.IS_MUSIC, 0);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri copy = resolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (copy == null) {
            throw new IOException("MediaStore refused the copy");
        }
        try (InputStream in = resolver.openInputStream(source);
             OutputStream out = resolver.openOutputStream(copy)) {
            if (in == null || out == null) {
                throw new IOException("Could not open the files");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } catch (IOException | RuntimeException exception) {
            resolver.delete(copy, null, null);
            throw exception;
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.Audio.Media.IS_PENDING, 0);
        resolver.update(copy, done, null, null);
        return copy;
    }

    private static String directoryFor(int type) {
        if (type == RingtoneManager.TYPE_NOTIFICATION) {
            return Environment.DIRECTORY_NOTIFICATIONS;
        }
        if (type == RingtoneManager.TYPE_ALARM) {
            return Environment.DIRECTORY_ALARMS;
        }
        return Environment.DIRECTORY_RINGTONES;
    }

    @StringRes
    private static int doneMessage(int type) {
        if (type == RingtoneManager.TYPE_NOTIFICATION) {
            return R.string.ringtone_set_notification;
        }
        if (type == RingtoneManager.TYPE_ALARM) {
            return R.string.ringtone_set_alarm;
        }
        return R.string.ringtone_set_call;
    }
}
