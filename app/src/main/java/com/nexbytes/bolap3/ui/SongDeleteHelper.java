package com.nexbytes.bolap3.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.media3.common.Player;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.playback.PlaybackActions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Deletes song files from the device. MediaStore songs go through one system confirmation
 * dialog for all of them (createDeleteRequest); songs from a picked folder are deleted through
 * the DocumentsProvider. Deleted songs are removed from the queue, library and playlists.
 * Must be created in onCreate (it registers an activity result launcher).
 */
public final class SongDeleteHelper {

    private final ComponentActivity activity;
    private final Supplier<Player> playerSupplier;
    private final ActivityResultLauncher<IntentSenderRequest> launcher;
    private List<Song> pendingMediaStore = Collections.emptyList();

    public SongDeleteHelper(ComponentActivity activity, Supplier<Player> playerSupplier) {
        this.activity = activity;
        this.playerSupplier = playerSupplier;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    List<Song> songs = pendingMediaStore;
                    pendingMediaStore = Collections.emptyList();
                    if (!songs.isEmpty() && result.getResultCode() == Activity.RESULT_OK) {
                        onDeleted(songs);
                    }
                });
    }

    public void confirmAndDelete(Song song) {
        confirmAndDelete(Collections.singletonList(song));
    }

    public void confirmAndDelete(List<Song> songs) {
        if (songs.isEmpty()) {
            return;
        }
        String message = songs.size() == 1
                ? activity.getString(R.string.delete_message, songs.get(0).getTitle())
                : activity.getResources().getQuantityString(R.plurals.delete_message_many, songs.size(), songs.size());
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> delete(new ArrayList<>(songs)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void delete(List<Song> songs) {
        List<Song> mediaStore = new ArrayList<>();
        List<Song> documents = new ArrayList<>();
        for (Song song : songs) {
            if (MediaStore.AUTHORITY.equals(Uri.parse(song.getUri()).getAuthority())) {
                mediaStore.add(song);
            } else {
                documents.add(song);
            }
        }

        if (!mediaStore.isEmpty()) {
            List<Uri> uris = new ArrayList<>(mediaStore.size());
            for (Song song : mediaStore) {
                uris.add(Uri.parse(song.getUri()));
            }
            try {
                PendingIntent request = MediaStore.createDeleteRequest(activity.getContentResolver(), uris);
                pendingMediaStore = mediaStore;
                launcher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build());
            } catch (RuntimeException exception) {
                pendingMediaStore = Collections.emptyList();
                Toast.makeText(activity, R.string.delete_failed, Toast.LENGTH_SHORT).show();
            }
        }

        if (!documents.isEmpty()) {
            AppExecutors.io().execute(() -> {
                List<Song> deleted = new ArrayList<>();
                for (Song song : documents) {
                    try {
                        if (DocumentsContract.deleteDocument(activity.getContentResolver(), Uri.parse(song.getUri()))) {
                            deleted.add(song);
                        }
                    } catch (Exception ignored) {
                        // Reported below as a partial failure.
                    }
                }
                AppExecutors.main(() -> {
                    if (!deleted.isEmpty()) {
                        onDeleted(deleted);
                    }
                    if (deleted.size() < documents.size()) {
                        Toast.makeText(activity, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private void onDeleted(List<Song> songs) {
        List<String> uris = new ArrayList<>(songs.size());
        for (Song song : songs) {
            PlaybackActions.removeFromQueue(playerSupplier.get(), song.getUri());
            uris.add(song.getUri());
        }
        MusicRepository.removeSongs(activity, uris);
        Toast.makeText(activity, songs.size() == 1
                ? activity.getString(R.string.delete_done)
                : activity.getResources().getQuantityString(R.plurals.deleted_many, songs.size(), songs.size()),
                Toast.LENGTH_SHORT).show();
    }
}
