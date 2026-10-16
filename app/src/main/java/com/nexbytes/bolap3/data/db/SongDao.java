package com.nexbytes.bolap3.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class SongDao {

    @Query("SELECT * FROM songs")
    public abstract List<SongEntity> getAllSongs();

    @Query("SELECT * FROM songs WHERE uri IN (:uris)")
    public abstract List<SongEntity> getSongsByUris(List<String> uris);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertAll(List<SongEntity> songs);

    @Query("DELETE FROM songs")
    public abstract void deleteAll();

    @Query("SELECT COUNT(*) FROM songs")
    public abstract int getSongCount();

    @Query("DELETE FROM songs WHERE uri IN (:uris)")
    public abstract void deleteByUris(List<String> uris);

    /** Replaces the whole library atomically, so a crash never leaves it half-written. */
    @Transaction
    public void replaceAll(List<SongEntity> songs) {
        deleteAll();
        insertAll(songs);
    }
}
