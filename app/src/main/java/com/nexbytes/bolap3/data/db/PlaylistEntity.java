package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name;

    public long createdAt;

    public PlaylistEntity(@NonNull String name, long createdAt) {
        this.name = name;
        this.createdAt = createdAt;
    }
}
