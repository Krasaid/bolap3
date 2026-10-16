package com.nexbytes.bolap3.library;

import androidx.annotation.Nullable;

import com.nexbytes.bolap3.model.Song;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Sort orders for the songs tab. Text comparisons ignore case and accents. */
public enum SongSort {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    DURATION("duration"),
    DATE_ADDED("date_added");

    public final String key;

    SongSort(String key) {
        this.key = key;
    }

    public static SongSort fromKey(@Nullable String key) {
        for (SongSort sort : values()) {
            if (sort.key.equals(key)) {
                return sort;
            }
        }
        return TITLE;
    }

    public List<Song> sorted(List<Song> songs) {
        List<Song> copy = new ArrayList<>(songs);
        copy.sort(comparator());
        return copy;
    }

    public Comparator<Song> comparator() {
        Comparator<Song> byTitle = textComparator(Song::getTitle);
        switch (this) {
            case ARTIST:
                return textComparator(Song::getArtist)
                        .thenComparing(textComparator(Song::getAlbum))
                        .thenComparingInt(Song::getTrackNumber)
                        .thenComparing(byTitle);
            case ALBUM:
                return textComparator(Song::getAlbum)
                        .thenComparingInt(Song::getTrackNumber)
                        .thenComparing(byTitle);
            case DURATION:
                return Comparator.comparingLong(Song::getDuration).thenComparing(byTitle);
            case DATE_ADDED:
                return Comparator.comparingLong(Song::getDateAdded).reversed().thenComparing(byTitle);
            case TITLE:
            default:
                return byTitle;
        }
    }

    /** Collator-based comparator; nulls sort last. */
    public static <T> Comparator<T> textComparator(java.util.function.Function<T, String> getter) {
        Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        return (a, b) -> {
            String left = getter.apply(a);
            String right = getter.apply(b);
            if (left == null) return right == null ? 0 : 1;
            if (right == null) return -1;
            return collator.compare(left, right);
        };
    }
}
