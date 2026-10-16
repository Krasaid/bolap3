package com.nexbytes.bolap3.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable song as shown in the library. {@link #getUri()} is the identity: it is the Room
 * primary key, the MediaItem mediaId and the key used by playlists, favorites and play stats.
 */
public final class Song {

    private final String uri;
    private final String title;
    private final String artist;
    private final String album;
    private final long albumId;
    private final String albumArtUri;
    private final String folderName;
    private final String folderPath;
    private final long duration;
    private final long dateAdded;
    private final int trackNumber;

    public Song(
            @NonNull String uri,
            String title,
            String artist,
            String album,
            long albumId,
            @Nullable String albumArtUri,
            String folderName,
            String folderPath,
            long duration,
            long dateAdded,
            int trackNumber
    ) {
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.albumArtUri = albumArtUri;
        this.folderName = folderName;
        this.folderPath = folderPath;
        this.duration = duration;
        this.dateAdded = dateAdded;
        this.trackNumber = trackNumber;
    }

    @NonNull
    public String getUri() { return uri; }

    public String getTitle() { return title; }

    public String getArtist() { return artist; }

    public String getAlbum() { return album; }

    /** MediaStore album id, or a value <= 0 when unknown (SAF songs). */
    public long getAlbumId() { return albumId; }

    @Nullable
    public String getAlbumArtUri() { return albumArtUri; }

    public String getFolderName() { return folderName; }

    /**
     * MediaStore RELATIVE_PATH (e.g. "Music/Rock/") for device scans, or the folder's document URI
     * for SAF scans. Use {@link com.nexbytes.bolap3.data.FolderPaths} to compare these.
     */
    public String getFolderPath() { return folderPath; }

    public long getDuration() { return duration; }

    /** Epoch milliseconds when the file was added/modified, 0 when unknown. */
    public long getDateAdded() { return dateAdded; }

    /** disc * 1000 + track (MediaStore convention), 0 when unknown. */
    public int getTrackNumber() { return trackNumber; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Song)) return false;
        Song song = (Song) other;
        return uri.equals(song.uri)
                && albumId == song.albumId
                && duration == song.duration
                && dateAdded == song.dateAdded
                && trackNumber == song.trackNumber
                && java.util.Objects.equals(title, song.title)
                && java.util.Objects.equals(artist, song.artist)
                && java.util.Objects.equals(album, song.album)
                && java.util.Objects.equals(albumArtUri, song.albumArtUri)
                && java.util.Objects.equals(folderName, song.folderName)
                && java.util.Objects.equals(folderPath, song.folderPath);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
