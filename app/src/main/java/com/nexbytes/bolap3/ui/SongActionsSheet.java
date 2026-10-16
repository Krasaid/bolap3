package com.nexbytes.bolap3.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.playback.PlaybackActions;

/** The per-song options menu, shared by the library and the player screen. */
public final class SongActionsSheet {

    /** What the hosting screen provides. */
    public interface Host {
        @Nullable
        Player player();

        SongDeleteHelper deleteHelper();

        RingtoneSetter ringtoneSetter();

        /** Opens the album or artist ({@link GroupItem#TYPE_ALBUM}/{@link GroupItem#TYPE_ARTIST}) of the song. */
        void openGroupOf(Song song, String type);
    }

    private SongActionsSheet() {
    }

    /**
     * @param playlistId the user playlist the song is shown in (adds "remove from playlist"), or -1
     * @param queueActions whether to offer "play next" / "add to queue"
     * @param extras extra options added at the end (player screen), may be null
     */
    public static void show(Activity activity, Song song, Host host, long playlistId, boolean queueActions,
                            @Nullable ExtraOptions extras) {
        OptionsSheet sheet = new OptionsSheet(activity, song.getTitle(), song.getArtist());

        if (queueActions) {
            sheet.add(R.drawable.ic_playlist_play, activity.getString(R.string.action_play_next), () -> {
                PlaybackActions.playNext(host.player(), song);
                Toast.makeText(activity, R.string.will_play_next, Toast.LENGTH_SHORT).show();
            });
            sheet.add(R.drawable.ic_queue_add, activity.getString(R.string.action_add_queue), () -> {
                PlaybackActions.addToQueue(host.player(), song);
                Toast.makeText(activity, R.string.added_to_queue, Toast.LENGTH_SHORT).show();
            });
        }

        sheet.add(R.drawable.ic_playlist_add, activity.getString(R.string.playlist_add_to),
                () -> PlaylistDialogs.pickAndAdd(activity, song));

        TextView favoriteLabel = sheet.add(R.drawable.ic_favorite_border,
                activity.getString(R.string.action_favorite_add),
                () -> PlaylistRepository.toggleFavorite(activity, song.getUri(), nowFavorite ->
                        Toast.makeText(activity,
                                nowFavorite ? R.string.favorite_added : R.string.favorite_removed,
                                Toast.LENGTH_SHORT).show()));
        PlaylistRepository.isFavorite(activity, song.getUri(), favorite -> {
            if (favorite) {
                favoriteLabel.setText(R.string.action_favorite_remove);
            }
        });

        sheet.add(R.drawable.ic_album, activity.getString(R.string.action_go_album),
                () -> host.openGroupOf(song, GroupItem.TYPE_ALBUM));
        sheet.add(R.drawable.ic_artist, activity.getString(R.string.action_go_artist),
                () -> host.openGroupOf(song, GroupItem.TYPE_ARTIST));

        if (extras != null) {
            extras.addTo(sheet);
        }

        sheet.add(R.drawable.ic_share, activity.getString(R.string.action_share), () -> share(activity, song));
        sheet.add(R.drawable.ic_ringtone, activity.getString(R.string.action_set_ringtone),
                () -> host.ringtoneSetter().choose(song));

        if (playlistId > 0) {
            sheet.add(R.drawable.ic_close, activity.getString(R.string.action_remove_from_playlist),
                    () -> PlaylistRepository.removeFromPlaylist(activity, playlistId, song.getUri()));
        }

        sheet.add(R.drawable.ic_visibility_off, activity.getString(R.string.action_exclude_folder), () -> {
            MusicRepository.excludeFolder(activity, song.getFolderPath());
            PlaybackActions.removeFolderFromQueue(host.player(), song.getFolderPath());
            Toast.makeText(activity, R.string.folder_excluded, Toast.LENGTH_SHORT).show();
        });

        sheet.add(R.drawable.ic_delete, activity.getString(R.string.action_delete_file),
                activity.getColor(R.color.app_danger),
                () -> host.deleteHelper().confirmAndDelete(song));

        sheet.show();
    }

    public interface ExtraOptions {
        void addTo(OptionsSheet sheet);
    }

    private static void share(Activity activity, Song song) {
        Uri uri = Uri.parse(song.getUri());
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("audio/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // ClipData is what actually carries the read grant to the receiving app.
        intent.setClipData(ClipData.newRawUri(song.getTitle(), uri));
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_audio)));
    }
}
