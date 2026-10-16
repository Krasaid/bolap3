package com.nexbytes.bolap3.ui;

import android.app.Activity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.db.PlaylistSummary;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.util.List;
import java.util.function.Consumer;

public final class PlaylistDialogs {

    private PlaylistDialogs() {
    }

    /** "Add to playlist": lists the playlists plus a "new playlist" entry. */
    public static void pickAndAdd(Activity activity, Song song) {
        pickAndAdd(activity, java.util.Collections.singletonList(song));
    }

    /** Same for several songs at once (multi-selection). */
    public static void pickAndAdd(Activity activity, List<Song> songs) {
        AppExecutors.db().execute(() -> {
            List<PlaylistSummary> playlists = PlaylistRepository.getPlaylistSummariesBlocking(activity);
            AppExecutors.main(() -> {
                if (activity.isFinishing()) {
                    return;
                }
                String[] names = new String[playlists.size() + 1];
                names[0] = "+ " + activity.getString(R.string.playlist_new);
                for (int i = 0; i < playlists.size(); i++) {
                    names[i + 1] = playlists.get(i).name;
                }

                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.playlist_add_to)
                        .setItems(names, (dialog, which) -> {
                            if (which == 0) {
                                promptName(activity, R.string.playlist_new, null, name ->
                                        PlaylistRepository.createPlaylist(activity, name, id ->
                                                addAndToast(activity, id, name, songs)));
                            } else {
                                PlaylistSummary target = playlists.get(which - 1);
                                addAndToast(activity, target.id, target.name, songs);
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        });
    }

    public static void createPlaylist(Activity activity) {
        promptName(activity, R.string.playlist_new, null, name ->
                PlaylistRepository.createPlaylist(activity, name, id ->
                        Toast.makeText(activity, activity.getString(R.string.playlist_created, name),
                                Toast.LENGTH_SHORT).show()));
    }

    /** Rename / delete menu for a user playlist. */
    public static void showPlaylistOptions(Activity activity, GroupItem playlist, Runnable onDeleted) {
        OptionsSheet sheet = new OptionsSheet(activity, playlist.getTitle(), null);
        sheet.add(R.drawable.ic_edit, activity.getString(R.string.action_rename), () ->
                promptName(activity, R.string.action_rename, playlist.getTitle(), name ->
                        PlaylistRepository.renamePlaylist(activity, playlist.getPlaylistId(), name)));
        sheet.add(R.drawable.ic_delete, activity.getString(R.string.action_delete),
                activity.getColor(R.color.app_danger), () ->
                        new MaterialAlertDialogBuilder(activity)
                                .setMessage(activity.getString(R.string.playlist_delete_confirm, playlist.getTitle()))
                                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                                    PlaylistRepository.deletePlaylist(activity, playlist.getPlaylistId());
                                    onDeleted.run();
                                })
                                .setNegativeButton(R.string.action_cancel, null)
                                .show());
        sheet.show();
    }

    public static void promptName(Activity activity, @StringRes int title, @Nullable String initial,
                                  Consumer<String> onName) {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        EditText input = view.findViewById(R.id.etDialogInput);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(initial == null ? R.string.action_create : R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        Runnable submit = () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(activity.getString(R.string.playlist_name_empty));
                return;
            }
            dialog.dismiss();
            onName.accept(name);
        };

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit.run());
            input.requestFocus();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        dialog.show();
    }

    private static void addAndToast(Activity activity, long playlistId, String name, List<Song> songs) {
        if (songs.size() == 1) {
            PlaylistRepository.addToPlaylist(activity, playlistId, songs.get(0).getUri(), added ->
                    Toast.makeText(activity,
                            activity.getString(added ? R.string.playlist_added : R.string.playlist_already_contains, name),
                            Toast.LENGTH_SHORT).show());
            return;
        }
        List<String> uris = new java.util.ArrayList<>(songs.size());
        for (Song song : songs) {
            uris.add(song.getUri());
        }
        PlaylistRepository.addAllToPlaylist(activity, playlistId, uris, added ->
                Toast.makeText(activity,
                        activity.getResources().getQuantityString(R.plurals.playlist_added_many, added, added, name),
                        Toast.LENGTH_SHORT).show());
    }
}
