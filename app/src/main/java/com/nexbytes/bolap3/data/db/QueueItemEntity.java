package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Persisted playback queue, restored when the playback service starts again. */
@Entity(tableName = "queue_items")
public class QueueItemEntity {

    @PrimaryKey
    public int position;

    @NonNull
    public String songUri;

    public QueueItemEntity(int position, @NonNull String songUri) {
        this.position = position;
        this.songUri = songUri;
    }
}
