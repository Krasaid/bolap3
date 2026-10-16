package com.nexbytes.bolap3.playback;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.nexbytes.bolap3.data.FolderPaths;
import com.nexbytes.bolap3.library.SearchFilter;
import com.nexbytes.bolap3.library.SongSort;
import com.nexbytes.bolap3.model.Song;

import java.util.Collections;
import java.util.List;

/** Queue operations shared by the screens. All methods accept a null player (not connected yet). */
public final class PlaybackActions {

    private PlaybackActions() {
    }

    /** Replaces the queue with {@code songs} and starts playing {@code songs[index]}. */
    public static void playSongs(@Nullable Player player, List<Song> songs, int index) {
        if (player == null || songs.isEmpty()) {
            return;
        }
        int start = Math.max(0, Math.min(index, songs.size() - 1));
        player.setMediaItems(MediaItemFactory.fromSongs(songs), start, C.TIME_UNSET);
        player.prepare();
        player.play();
    }

    /** Replaces the queue with {@code songs} in random order (every song plays once). */
    public static void shuffleSongs(@Nullable Player player, List<Song> songs) {
        if (player == null || songs.isEmpty()) {
            return;
        }
        player.setShuffleModeEnabled(true);
        // No start index: playback begins with the first song of the shuffle order.
        player.setMediaItems(MediaItemFactory.fromSongs(songs), /* resetPosition= */ true);
        player.prepare();
        player.play();
    }

    /**
     * Songs matching a voice/Auto search, or null when nothing matches. An empty query means
     * "play some music": the whole library, shuffled by the caller.
     */
    @Nullable
    public static List<Song> searchSongs(List<Song> library, @Nullable String query) {
        List<Song> sorted = SongSort.TITLE.sorted(library);
        if (query == null || query.trim().isEmpty()) {
            return sorted.isEmpty() ? null : sorted;
        }
        List<Song> matches = SearchFilter.filterSongs(sorted, query);
        return matches.isEmpty() ? null : matches;
    }

    /** @return false if nothing matched. */
    public static boolean playFromSearch(@Nullable Player player, List<Song> library, @Nullable String query) {
        List<Song> songs = searchSongs(library, query);
        if (player == null || songs == null) {
            return false;
        }
        if (query == null || query.trim().isEmpty()) {
            shuffleSongs(player, songs);
        } else {
            playSongs(player, songs, 0);
        }
        return true;
    }

    public static void playNext(@Nullable Player player, Song song) {
        if (player == null) {
            return;
        }
        if (player.getMediaItemCount() == 0) {
            playSongs(player, Collections.singletonList(song), 0);
            return;
        }
        player.addMediaItem(player.getCurrentMediaItemIndex() + 1, MediaItemFactory.fromSong(song));
    }

    public static void addToQueue(@Nullable Player player, Song song) {
        if (player == null) {
            return;
        }
        if (player.getMediaItemCount() == 0) {
            playSongs(player, Collections.singletonList(song), 0);
            return;
        }
        player.addMediaItem(MediaItemFactory.fromSong(song));
    }

    public static void addToQueue(@Nullable Player player, List<Song> songs) {
        if (player == null || songs.isEmpty()) {
            return;
        }
        if (player.getMediaItemCount() == 0) {
            playSongs(player, songs, 0);
            return;
        }
        player.addMediaItems(MediaItemFactory.fromSongs(songs));
    }

    /** Removes every queue entry for a song (used after the file is deleted). */
    public static void removeFromQueue(@Nullable Player player, String songUri) {
        if (player == null) {
            return;
        }
        for (int i = player.getMediaItemCount() - 1; i >= 0; i--) {
            MediaItem item = player.getMediaItemAt(i);
            if (item.mediaId.equals(songUri)) {
                player.removeMediaItem(i);
            }
        }
    }

    /** Removes every queued song that lives in {@code folderPath} or its subfolders. */
    public static void removeFolderFromQueue(@Nullable Player player, String folderPath) {
        if (player == null) {
            return;
        }
        for (int i = player.getMediaItemCount() - 1; i >= 0; i--) {
            Song song = MediaItemFactory.toSong(player.getMediaItemAt(i));
            if (song != null && FolderPaths.isWithin(song.getFolderPath(), folderPath)) {
                player.removeMediaItem(i);
            }
        }
    }

    public static void togglePlayPause(@Nullable Player player) {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            return;
        }
        if (player.getPlaybackState() == Player.STATE_IDLE) {
            player.prepare();
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
            player.seekToDefaultPosition(player.getCurrentMediaItemIndex());
        }
        player.play();
    }

    /** off -> all -> one -> off */
    public static void cycleRepeat(@Nullable Player player) {
        if (player == null) {
            return;
        }
        switch (player.getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                break;
            case Player.REPEAT_MODE_ALL:
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                break;
            default:
                player.setRepeatMode(Player.REPEAT_MODE_OFF);
                break;
        }
    }

    public static void toggleShuffle(@Nullable Player player) {
        if (player != null) {
            player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
        }
    }

    @Nullable
    public static Song currentSong(@Nullable Player player) {
        return player != null ? MediaItemFactory.toSong(player.getCurrentMediaItem()) : null;
    }
}
