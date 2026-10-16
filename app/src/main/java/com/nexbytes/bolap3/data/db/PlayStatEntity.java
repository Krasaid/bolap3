package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Feeds the "Recientes" and "Más escuchadas" smart playlists. */
@Entity(tableName = "play_stats")
public class PlayStatEntity {

    @PrimaryKey
    @NonNull
    public String songUri;

    public int playCount;

    public long lastPlayed;

    public PlayStatEntity(@NonNull String songUri, int playCount, long lastPlayed) {
        this.songUri = songUri;
        this.playCount = playCount;
        this.lastPlayed = lastPlayed;
    }
}
