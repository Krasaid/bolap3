package com.nexbytes.bolap3.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/** Users upgrading from the original app (schema 2) must keep their library. */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migrate2To3_keepsSongsAndAddsTables() throws IOException {
        try (SupportSQLiteDatabase db = helper.createDatabase(DB, 2)) {
            db.execSQL("INSERT INTO songs (uri, title, artist, album, albumId, albumArtUri, folderName, folderPath, duration) "
                    + "VALUES ('content://media/external/audio/media/1', 'Song', 'Artist', 'Album', 7, NULL, 'Music', 'Music/', 1000)");
        }

        // Validates the resulting schema against the v3 entities.
        try (SupportSQLiteDatabase db = helper.runMigrationsAndValidate(DB, 3, true, AppDatabase.MIGRATION_2_3)) {
            try (Cursor cursor = db.query("SELECT title, dateAdded, trackNumber FROM songs")) {
                assertTrue(cursor.moveToFirst());
                assertEquals("Song", cursor.getString(0));
                assertEquals(0, cursor.getLong(1));
                assertEquals(0, cursor.getInt(2));
            }
            db.execSQL("INSERT INTO playlists (name, createdAt) VALUES ('Mix', 1)");
            db.execSQL("INSERT INTO favorites (songUri, addedAt) VALUES ('content://media/external/audio/media/1', 1)");
        }
    }
}
