package com.nexbytes.bolap3.library;

import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Pure selection logic for the Home carousels (unit tested). */
public final class HomeSections {

    private HomeSections() {
    }

    /** Albums ordered by their newest song, newest first. */
    public static List<GroupItem> recentlyAddedAlbums(List<Song> library, List<GroupItem> albums, int limit) {
        Map<String, Long> newestByAlbum = new HashMap<>();
        for (Song song : library) {
            String key = LibraryGrouping.keyFor(song, GroupItem.TYPE_ALBUM);
            Long current = newestByAlbum.get(key);
            if (current == null || song.getDateAdded() > current) {
                newestByAlbum.put(key, song.getDateAdded());
            }
        }
        List<GroupItem> sorted = new ArrayList<>(albums);
        sorted.sort((a, b) -> Long.compare(
                newestByAlbum.getOrDefault(b.getKey(), 0L),
                newestByAlbum.getOrDefault(a.getKey(), 0L)));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Albums not heard recently, in a random order that stays the same for the whole day
     * ({@code daySeed}), so the carousel doesn't reshuffle every time the screen refreshes.
     */
    public static List<GroupItem> rediscover(List<GroupItem> albums, Collection<Song> recentlyPlayed,
                                             long daySeed, int limit) {
        Set<String> recentAlbums = new HashSet<>();
        for (Song song : recentlyPlayed) {
            recentAlbums.add(LibraryGrouping.keyFor(song, GroupItem.TYPE_ALBUM));
        }
        List<GroupItem> candidates = new ArrayList<>();
        for (GroupItem album : albums) {
            if (!recentAlbums.contains(album.getKey())) {
                candidates.add(album);
            }
        }
        java.util.Collections.shuffle(candidates, new Random(daySeed));
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    /** Days since the epoch in local time, used as the daily shuffle seed. */
    public static long daySeed(long nowMillis, int utcOffsetMillis) {
        return Math.floorDiv(nowMillis + utcOffsetMillis, 24L * 60 * 60 * 1000);
    }
}
