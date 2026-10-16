package com.nexbytes.bolap3;

import com.nexbytes.bolap3.model.Song;

/** Builders for unit tests. */
public final class TestSongs {

    private TestSongs() {
    }

    public static Song song(String uri, String title, String artist, String album) {
        return new Song(uri, title, artist, album, -1, null, "Music", "Music/", 180_000, 0, 0);
    }

    public static Song song(String uri, String title, String artist, String album, long albumId,
                            String folderPath, long duration, long dateAdded, int track) {
        String folderName = folderPath.replaceAll("/$", "");
        folderName = folderName.substring(folderName.lastIndexOf('/') + 1);
        return new Song(uri, title, artist, album, albumId, null, folderName, folderPath, duration, dateAdded, track);
    }
}
