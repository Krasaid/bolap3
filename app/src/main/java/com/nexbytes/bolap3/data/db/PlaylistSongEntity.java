package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** A song in a playlist. Songs are referenced by URI, like everywhere else in the app. */
@Entity(tableName = "playlist_songs", primaryKeys = {"playlistId", "songUri"})
public class PlaylistSongEntity {

    public long playlistId;

    @NonNull
    public String songUri;

    public int position;

    public long addedAt;

    public PlaylistSongEntity(long playlistId, @NonNull String songUri, int position, long addedAt) {
        this.playlistId = playlistId;
        this.songUri = songUri;
        this.position = position;
        this.addedAt = addedAt;
    }
}
