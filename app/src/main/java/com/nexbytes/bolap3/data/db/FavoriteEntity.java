package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {

    @PrimaryKey
    @NonNull
    public String songUri;

    public long addedAt;

    public FavoriteEntity(@NonNull String songUri, long addedAt) {
        this.songUri = songUri;
        this.addedAt = addedAt;
    }
}
