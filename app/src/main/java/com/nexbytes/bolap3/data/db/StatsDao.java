package com.nexbytes.bolap3.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/** Favorites and play statistics. */
@Dao
public abstract class StatsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public abstract void addFavorite(FavoriteEntity favorite);

    @Query("DELETE FROM favorites WHERE songUri = :songUri")
    public abstract void removeFavorite(String songUri);

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songUri = :songUri)")
    public abstract boolean isFavorite(String songUri);

    @Query("SELECT s.* FROM favorites f INNER JOIN songs s ON s.uri = f.songUri "
            + "ORDER BY f.addedAt DESC")
    public abstract List<SongEntity> getFavoriteSongs();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract void insertStatIfMissing(PlayStatEntity stat);

    @Query("UPDATE play_stats SET playCount = playCount + 1, lastPlayed = :now WHERE songUri = :songUri")
    abstract void incrementPlayCount(String songUri, long now);

    @Transaction
    public void recordPlay(String songUri, long now) {
        insertStatIfMissing(new PlayStatEntity(songUri, 0, now));
        incrementPlayCount(songUri, now);
    }

    @Query("SELECT s.* FROM play_stats p INNER JOIN songs s ON s.uri = p.songUri "
            + "ORDER BY p.lastPlayed DESC LIMIT :limit")
    public abstract List<SongEntity> getRecentSongs(int limit);

    @Query("SELECT s.* FROM play_stats p INNER JOIN songs s ON s.uri = p.songUri "
            + "WHERE p.playCount > 0 ORDER BY p.playCount DESC, p.lastPlayed DESC LIMIT :limit")
    public abstract List<SongEntity> getMostPlayedSongs(int limit);

    @Query("DELETE FROM favorites WHERE songUri IN (:uris)")
    public abstract void deleteFavorites(List<String> uris);

    @Query("DELETE FROM play_stats WHERE songUri IN (:uris)")
    public abstract void deleteStats(List<String> uris);
}
