package com.nexbytes.bolap3.library;

import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the albums/artists/folders lists. Albums are keyed by MediaStore album id when known,
 * otherwise by album name + artist, so different albums that share a name stay separate.
 * Folders are keyed by full path, so two folders called "Music" in different places stay separate.
 */
public final class LibraryGrouping {

    private LibraryGrouping() {
    }

    public static String keyFor(Song song, String type) {
        switch (type) {
            case GroupItem.TYPE_ARTIST:
                return "artist:" + normalizeKey(song.getArtist());
            case GroupItem.TYPE_FOLDER:
                return "folder:" + song.getFolderPath();
            case GroupItem.TYPE_ALBUM:
            default:
                if (song.getAlbumId() > 0) {
                    return "album:" + song.getAlbumId();
                }
                return "album:" + normalizeKey(song.getAlbum()) + "|" + normalizeKey(song.getArtist());
        }
    }

    /** Groups sorted alphabetically by title. */
    public static List<GroupItem> group(List<Song> songs, String type, String unknownLabel) {
        Map<String, List<Song>> buckets = new LinkedHashMap<>();
        for (Song song : songs) {
            buckets.computeIfAbsent(keyFor(song, type), key -> new ArrayList<>()).add(song);
        }

        List<GroupItem> groups = new ArrayList<>(buckets.size());
        for (Map.Entry<String, List<Song>> entry : buckets.entrySet()) {
            List<Song> members = sortMembers(entry.getValue(), type);
            Song first = members.get(0);
            String title = titleFor(first, type);
            if (title == null || title.trim().isEmpty()) {
                title = unknownLabel;
            }

            groups.add(GroupItem.library(
                    type,
                    entry.getKey(),
                    title,
                    GroupItem.TYPE_ALBUM.equals(type) ? albumArtist(members) : null,
                    members.size(),
                    first
            ));
        }

        groups.sort(SongSort.textComparator(GroupItem::getTitle));
        return groups;
    }

    /** Songs belonging to a group, in the natural order for that group type. */
    public static List<Song> songsOf(List<Song> songs, GroupItem group) {
        List<Song> members = new ArrayList<>();
        for (Song song : songs) {
            if (group.getKey().equals(keyFor(song, group.getType()))) {
                members.add(song);
            }
        }
        return sortMembers(members, group.getType());
    }

    private static List<Song> sortMembers(List<Song> members, String type) {
        Comparator<Song> comparator;
        if (GroupItem.TYPE_ALBUM.equals(type)) {
            comparator = Comparator.comparingInt(Song::getTrackNumber)
                    .thenComparing(SongSort.textComparator(Song::getTitle));
        } else if (GroupItem.TYPE_ARTIST.equals(type)) {
            comparator = SongSort.ALBUM.comparator();
        } else {
            comparator = SongSort.TITLE.comparator();
        }
        List<Song> sorted = new ArrayList<>(members);
        sorted.sort(comparator);
        return sorted;
    }

    private static String titleFor(Song song, String type) {
        switch (type) {
            case GroupItem.TYPE_ARTIST:
                return song.getArtist();
            case GroupItem.TYPE_FOLDER:
                return song.getFolderName();
            case GroupItem.TYPE_ALBUM:
            default:
                return song.getAlbum();
        }
    }

    /** The artist shared by every song of the album, or null for compilations. */
    private static String albumArtist(List<Song> members) {
        String artist = members.get(0).getArtist();
        for (Song song : members) {
            if (!Objects.equals(artist, song.getArtist())) {
                return null;
            }
        }
        return artist;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
