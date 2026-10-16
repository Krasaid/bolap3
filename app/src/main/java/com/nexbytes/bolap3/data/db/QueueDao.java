package com.nexbytes.bolap3.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class QueueDao {

    @Query("SELECT songUri FROM queue_items ORDER BY position")
    public abstract List<String> getQueueUris();

    @Query("DELETE FROM queue_items")
    abstract void deleteAll();

    @Insert
    abstract void insertAll(List<QueueItemEntity> items);

    @Transaction
    public void replace(List<String> uris) {
        deleteAll();
        java.util.ArrayList<QueueItemEntity> items = new java.util.ArrayList<>(uris.size());
        for (int i = 0; i < uris.size(); i++) {
            items.add(new QueueItemEntity(i, uris.get(i)));
        }
        insertAll(items);
    }
}
