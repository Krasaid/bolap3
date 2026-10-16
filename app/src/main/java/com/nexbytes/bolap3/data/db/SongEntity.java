package com.nexbytes.bolap3.data.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.nexbytes.bolap3.model.Song;

@Entity(tableName = "songs")
public class SongEntity {

    @PrimaryKey
    @NonNull
    private String uri;
    private String title;
    private String artist;
    private String album;
    private long albumId;
    private String albumArtUri;
    private String folderName;
    private String folderPath;
    private long duration;

    // Added in schema version 3.
    @ColumnInfo(defaultValue = "0")
    private long dateAdded;

    @ColumnInfo(defaultValue = "0")
    private int trackNumber;

    public SongEntity(@NonNull String uri, String title, String artist, String album, long albumId,
                      String albumArtUri, String folderName, String folderPath, long duration,
                      long dateAdded, int trackNumber) {
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

    public static SongEntity fromSong(Song song) {
        return new SongEntity(
                song.getUri(),
                song.getTitle(),
                song.getArtist(),
                song.getAlbum(),
                song.getAlbumId(),
                song.getAlbumArtUri(),
                song.getFolderName(),
                song.getFolderPath(),
                song.getDuration(),
                song.getDateAdded(),
                song.getTrackNumber()
        );
    }

    public Song toSong() {
        return new Song(uri, title, artist, album, albumId, albumArtUri, folderName, folderPath,
                duration, dateAdded, trackNumber);
    }

    @NonNull
    public String getUri() { return uri; }

    public String getTitle() { return title; }

    public String getArtist() { return artist; }

    public String getAlbum() { return album; }

    public long getAlbumId() { return albumId; }

    public String getAlbumArtUri() { return albumArtUri; }

    public String getFolderName() { return folderName; }

    public String getFolderPath() { return folderPath; }

    public long getDuration() { return duration; }

    public long getDateAdded() { return dateAdded; }

    public int getTrackNumber() { return trackNumber; }
}
