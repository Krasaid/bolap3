package com.nexbytes.bolap3.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Schema history (JSON exports live in app/schemas):
 * 1 - unknown, never exported: still dropped and recreated.
 * 2 - songs table.
 * 3 - songs.dateAdded/trackNumber, playlists, playlist_songs, favorites, play_stats, queue_items.
 * When changing an entity, bump the version and add a Migration instead of wiping user data.
 */
@Database(
        entities = {
                SongEntity.class,
                PlaylistEntity.class,
                PlaylistSongEntity.class,
                FavoriteEntity.class,
                PlayStatEntity.class,
                QueueItemEntity.class
        },
        version = 3,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public static final String NAME = "hifi_player_database";

    public abstract SongDao songDao();

    public abstract PlaylistDao playlistDao();

    public abstract StatsDao statsDao();

    public abstract QueueDao queueDao();

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `dateAdded` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `trackNumber` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, "
                    + "`songUri` TEXT NOT NULL, `position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`playlistId`, `songUri`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`songUri` TEXT NOT NULL, "
                    + "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`songUri`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `play_stats` (`songUri` TEXT NOT NULL, "
                    + "`playCount` INTEGER NOT NULL, `lastPlayed` INTEGER NOT NULL, PRIMARY KEY(`songUri`))");
            db.execSQL("CREATE TABLE IF NOT EXISTS `queue_items` (`position` INTEGER NOT NULL, "
                    + "`songUri` TEXT NOT NULL, PRIMARY KEY(`position`))");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, NAME)
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigrationFrom(true, 1)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
