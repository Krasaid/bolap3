package com.nexbytes.bolap3.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * Playlist songs are joined with the songs table, so entries whose file disappeared from the
 * library are simply not shown (they come back if a later scan finds the same URI again).
 */
@Dao
public abstract class PlaylistDao {

    @Insert
    public abstract long insertPlaylist(PlaylistEntity playlist);

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    public abstract void rename(long playlistId, String name);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    abstract void deletePlaylistRow(long playlistId);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    abstract void deletePlaylistSongs(long playlistId);

    @Transaction
    public void deletePlaylist(long playlistId) {
        deletePlaylistSongs(playlistId);
        deletePlaylistRow(playlistId);
    }

    @Query("SELECT p.id AS id, p.name AS name, "
            + "(SELECT COUNT(*) FROM playlist_songs ps INNER JOIN songs s ON s.uri = ps.songUri "
            + "  WHERE ps.playlistId = p.id) AS songCount, "
            + "(SELECT ps.songUri FROM playlist_songs ps INNER JOIN songs s ON s.uri = ps.songUri "
            + "  WHERE ps.playlistId = p.id ORDER BY ps.position LIMIT 1) AS coverUri "
            + "FROM playlists p ORDER BY p.name COLLATE NOCASE")
    public abstract List<PlaylistSummary> getPlaylistSummaries();

    @Query("SELECT s.* FROM playlist_songs ps INNER JOIN songs s ON s.uri = ps.songUri "
            + "WHERE ps.playlistId = :playlistId ORDER BY ps.position")
    public abstract List<SongEntity> getSongs(long playlistId);

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_songs WHERE playlistId = :playlistId")
    abstract int nextPosition(long playlistId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract long insertSongRow(PlaylistSongEntity entity);

    /** @return false if the song was already in the playlist. */
    @Transaction
    public boolean addSong(long playlistId, String songUri, long now) {
        int position = nextPosition(playlistId);
        return insertSongRow(new PlaylistSongEntity(playlistId, songUri, position, now)) != -1;
    }

    @Query("SELECT songUri FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    abstract List<String> getSongUris(long playlistId);

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songUri = :songUri")
    abstract void setPosition(long playlistId, String songUri, int position);

    /**
     * Saves a new order. {@code visibleOrder} is what the user sees (songs still in the library);
     * entries hidden because their file is gone keep their relative order at the end.
     */
    @Transaction
    public void reorder(long playlistId, List<String> visibleOrder) {
        List<String> order = new java.util.ArrayList<>(visibleOrder);
        java.util.Set<String> visible = new java.util.HashSet<>(visibleOrder);
        for (String uri : getSongUris(playlistId)) {
            if (!visible.contains(uri)) {
                order.add(uri);
            }
        }
        for (int i = 0; i < order.size(); i++) {
            setPosition(playlistId, order.get(i), i);
        }
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songUri = :songUri")
    public abstract void removeSong(long playlistId, String songUri);

    @Query("DELETE FROM playlist_songs WHERE songUri IN (:uris)")
    public abstract void deleteSongReferences(List<String> uris);
}
