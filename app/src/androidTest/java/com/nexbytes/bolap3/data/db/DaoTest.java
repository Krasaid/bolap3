package com.nexbytes.bolap3.data.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DaoTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        db.songDao().replaceAll(Arrays.asList(song("a"), song("b"), song("c")));
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void replaceAll_swapsTheLibrary() {
        db.songDao().replaceAll(Collections.singletonList(song("z")));
        assertEquals(1, db.songDao().getSongCount());
        assertEquals("z", db.songDao().getAllSongs().get(0).getUri());
    }

    @Test
    public void playlist_keepsOrderAndIgnoresDuplicates() {
        long id = db.playlistDao().insertPlaylist(new PlaylistEntity("Mix", 1));
        assertTrue(db.playlistDao().addSong(id, "c", 1));
        assertTrue(db.playlistDao().addSong(id, "a", 2));
        assertFalse(db.playlistDao().addSong(id, "c", 3));

        List<SongEntity> songs = db.playlistDao().getSongs(id);
        assertEquals(2, songs.size());
        assertEquals("c", songs.get(0).getUri());
        assertEquals("a", songs.get(1).getUri());
    }

    @Test
    public void playlistSummary_countsOnlySongsStillInLibrary() {
        long id = db.playlistDao().insertPlaylist(new PlaylistEntity("Mix", 1));
        db.playlistDao().addSong(id, "missing", 1);
        db.playlistDao().addSong(id, "b", 2);

        PlaylistSummary summary = db.playlistDao().getPlaylistSummaries().get(0);
        assertEquals("Mix", summary.name);
        assertEquals(1, summary.songCount);
        assertEquals("b", summary.coverUri);
    }

    @Test
    public void deletePlaylist_removesItsSongs() {
        long id = db.playlistDao().insertPlaylist(new PlaylistEntity("Mix", 1));
        db.playlistDao().addSong(id, "a", 1);
        db.playlistDao().deletePlaylist(id);
        assertTrue(db.playlistDao().getPlaylistSummaries().isEmpty());
        assertTrue(db.playlistDao().getSongs(id).isEmpty());
    }

    @Test
    public void stats_countPlaysAndOrderSmartLists() {
        db.statsDao().recordPlay("a", 100);
        db.statsDao().recordPlay("b", 200);
        db.statsDao().recordPlay("a", 300);

        List<SongEntity> mostPlayed = db.statsDao().getMostPlayedSongs(10);
        assertEquals("a", mostPlayed.get(0).getUri());
        List<SongEntity> recent = db.statsDao().getRecentSongs(10);
        assertEquals("a", recent.get(0).getUri());
        assertEquals("b", recent.get(1).getUri());
    }

    @Test
    public void favorites() {
        db.statsDao().addFavorite(new FavoriteEntity("b", 1));
        db.statsDao().addFavorite(new FavoriteEntity("b", 2));
        assertTrue(db.statsDao().isFavorite("b"));
        assertEquals(1, db.statsDao().getFavoriteSongs().size());
        db.statsDao().removeFavorite("b");
        assertFalse(db.statsDao().isFavorite("b"));
    }

    @Test
    public void queue_roundTrip() {
        db.queueDao().replace(Arrays.asList("c", "a", "b"));
        db.queueDao().replace(Arrays.asList("b", "c"));
        assertEquals(Arrays.asList("b", "c"), db.queueDao().getQueueUris());
    }

    private static SongEntity song(String uri) {
        return new SongEntity(uri, "Title " + uri, "Artist", "Album", 1, null, "Music", "Music/", 1000, 0, 0);
    }
}
