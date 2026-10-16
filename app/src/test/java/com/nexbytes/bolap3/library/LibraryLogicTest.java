package com.nexbytes.bolap3.library;

import static com.nexbytes.bolap3.TestSongs.song;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LibraryLogicTest {

    private final Song a = song("u1", "Zebra", "Alpha", "First", 10, "Music/A/", 200_000, 3_000, 2);
    private final Song b = song("u2", "apple", "Beta", "Second", 20, "Music/B/", 100_000, 1_000, 1);
    private final Song c = song("u3", "Ángel", "Alpha", "First", 10, "Music/A/", 300_000, 2_000, 1);
    private final List<Song> library = Arrays.asList(a, b, c);

    @Test
    public void sortByTitle_ignoresCaseAndAccents() {
        assertEquals(Arrays.asList(c, b, a), SongSort.TITLE.sorted(library));
    }

    @Test
    public void sortByDateAdded_newestFirst() {
        assertEquals(Arrays.asList(a, c, b), SongSort.DATE_ADDED.sorted(library));
    }

    @Test
    public void sortByDuration() {
        assertEquals(Arrays.asList(b, a, c), SongSort.DURATION.sorted(library));
    }

    @Test
    public void sortFromUnknownKey_defaultsToTitle() {
        assertEquals(SongSort.TITLE, SongSort.fromKey("nope"));
        assertEquals(SongSort.ALBUM, SongSort.fromKey("album"));
    }

    @Test
    public void albumsAreGroupedByIdAndSortedByTrack() {
        List<GroupItem> albums = LibraryGrouping.group(library, GroupItem.TYPE_ALBUM, "?");
        assertEquals(2, albums.size());
        GroupItem first = albums.get(0);
        assertEquals("First", first.getTitle());
        assertEquals(2, first.getSongCount());
        assertEquals("Alpha", first.getArtistLine());
        assertEquals(Arrays.asList(c, a), LibraryGrouping.songsOf(library, first));
    }

    @Test
    public void albumsWithSameNameButDifferentIds_stayApart() {
        Song x = song("x", "One", "Artist X", "Greatest Hits", 1, "M/", 1, 0, 1);
        Song y = song("y", "Two", "Artist Y", "Greatest Hits", 2, "M/", 1, 0, 1);
        List<GroupItem> albums = LibraryGrouping.group(Arrays.asList(x, y), GroupItem.TYPE_ALBUM, "?");
        assertEquals(2, albums.size());
    }

    @Test
    public void albumsWithoutId_groupByNameAndArtist() {
        Song x = song("x", "One", "Same", "Album", -1, "M/", 1, 0, 1);
        Song y = song("y", "Two", "same ", "album", -1, "M/", 1, 0, 2);
        Song z = song("z", "Three", "Other", "Album", -1, "M/", 1, 0, 1);
        List<GroupItem> albums = LibraryGrouping.group(Arrays.asList(x, y, z), GroupItem.TYPE_ALBUM, "?");
        assertEquals(2, albums.size());
    }

    @Test
    public void compilationHasNoArtistLine() {
        Song x = song("x", "One", "A", "Mix", 5, "M/", 1, 0, 1);
        Song y = song("y", "Two", "B", "Mix", 5, "M/", 1, 0, 2);
        assertNull(LibraryGrouping.group(Arrays.asList(x, y), GroupItem.TYPE_ALBUM, "?").get(0).getArtistLine());
    }

    @Test
    public void foldersWithSameNameInDifferentPaths_stayApart() {
        Song x = song("x", "One", "A", "X", 1, "Music/Live/", 1, 0, 1);
        Song y = song("y", "Two", "B", "Y", 2, "Podcasts/Live/", 1, 0, 1);
        List<GroupItem> folders = LibraryGrouping.group(Arrays.asList(x, y), GroupItem.TYPE_FOLDER, "?");
        assertEquals(2, folders.size());
        assertEquals("Live", folders.get(0).getTitle());
    }

    @Test
    public void searchMatchesEveryWordIgnoringAccents() {
        assertEquals(Arrays.asList(c), SearchFilter.filterSongs(library, "angel alpha"));
        assertEquals(Arrays.asList(a, c), SearchFilter.filterSongs(library, "FIRST"));
        assertEquals(0, SearchFilter.filterSongs(library, "zebra beta").size());
        assertEquals(library, SearchFilter.filterSongs(library, "   "));
    }

    @Test
    public void searchFiltersGroups() {
        List<GroupItem> artists = LibraryGrouping.group(library, GroupItem.TYPE_ARTIST, "?");
        assertEquals(1, SearchFilter.filterGroups(artists, "bet").size());
    }
}
