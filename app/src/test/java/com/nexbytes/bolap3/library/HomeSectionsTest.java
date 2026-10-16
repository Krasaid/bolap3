package com.nexbytes.bolap3.library;

import static com.nexbytes.bolap3.TestSongs.song;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeSectionsTest {

    private final Song oldA = song("a1", "A1", "X", "Old", 1, "M/", 1, 1_000, 1);
    private final Song newB = song("b1", "B1", "Y", "New", 2, "M/", 1, 9_000, 1);
    private final Song midC1 = song("c1", "C1", "Z", "Mid", 3, "M/", 1, 2_000, 1);
    private final Song midC2 = song("c2", "C2", "Z", "Mid", 3, "M/", 1, 5_000, 2);
    private final List<Song> library = Arrays.asList(oldA, newB, midC1, midC2);
    private final List<GroupItem> albums = LibraryGrouping.group(library, GroupItem.TYPE_ALBUM, "?");

    @Test
    public void recentlyAdded_usesNewestSongOfEachAlbum() {
        List<GroupItem> result = HomeSections.recentlyAddedAlbums(library, albums, 10);
        assertEquals("New", result.get(0).getTitle());
        assertEquals("Mid", result.get(1).getTitle());
        assertEquals("Old", result.get(2).getTitle());
        assertEquals(2, HomeSections.recentlyAddedAlbums(library, albums, 2).size());
    }

    @Test
    public void rediscover_skipsRecentAlbumsAndIsStableForTheDay() {
        List<GroupItem> first = HomeSections.rediscover(albums, Collections.singletonList(newB), 42, 10);
        assertEquals(2, first.size());
        for (GroupItem album : first) {
            assertFalse("New".equals(album.getTitle()));
        }
        assertEquals(first, HomeSections.rediscover(albums, Collections.singletonList(newB), 42, 10));
    }

    @Test
    public void daySeed_changesAtLocalMidnight() {
        long day = 24L * 60 * 60 * 1000;
        int utcMinus6 = -6 * 60 * 60 * 1000;
        assertEquals(HomeSections.daySeed(10 * day + 7 * 3_600_000L, utcMinus6),
                HomeSections.daySeed(10 * day + 29 * 3_600_000L, utcMinus6));
    }
}
