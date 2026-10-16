package com.nexbytes.bolap3.library;

import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Library search: case- and accent-insensitive, every word of the query must appear somewhere
 * in the title, artist or album ("cancion beatles" matches "Canción" by "The Beatles").
 */
public final class SearchFilter {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchFilter() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    public static boolean isBlank(String query) {
        return query == null || query.trim().isEmpty();
    }

    public static List<Song> filterSongs(List<Song> songs, String query) {
        if (isBlank(query)) {
            return songs;
        }
        String[] tokens = tokens(query);
        List<Song> result = new ArrayList<>();
        for (Song song : songs) {
            String haystack = normalize(song.getTitle() + " " + song.getArtist() + " " + song.getAlbum());
            if (containsAll(haystack, tokens)) {
                result.add(song);
            }
        }
        return result;
    }

    public static List<GroupItem> filterGroups(List<GroupItem> groups, String query) {
        if (isBlank(query)) {
            return groups;
        }
        String[] tokens = tokens(query);
        List<GroupItem> result = new ArrayList<>();
        for (GroupItem group : groups) {
            String haystack = normalize(group.getTitle() + " "
                    + (group.getArtistLine() != null ? group.getArtistLine() : ""));
            if (containsAll(haystack, tokens)) {
                result.add(group);
            }
        }
        return result;
    }

    private static String[] tokens(String query) {
        return SPACES.split(normalize(query));
    }

    private static boolean containsAll(String haystack, String[] tokens) {
        for (String token : tokens) {
            if (!token.isEmpty() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }
}
