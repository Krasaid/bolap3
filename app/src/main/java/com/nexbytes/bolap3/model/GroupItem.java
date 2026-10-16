package com.nexbytes.bolap3.model;

import androidx.annotation.Nullable;

/**
 * A row in the albums/artists/folders/playlists lists. {@link #getKey()} identifies the group
 * (two albums with the same name but different album ids get different keys); the title is only
 * for display.
 */
public final class GroupItem {

    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_FOLDER = "folder";
    public static final String TYPE_PLAYLIST = "playlist";
    public static final String TYPE_FAVORITES = "favorites";
    public static final String TYPE_RECENT = "recent";
    public static final String TYPE_MOST_PLAYED = "most_played";

    private final String type;
    private final String key;
    private final String title;
    private final String artistLine;
    private final int songCount;
    private final Song coverSong;
    private final long playlistId;

    public GroupItem(
            String type,
            String key,
            String title,
            @Nullable String artistLine,
            int songCount,
            @Nullable Song coverSong,
            long playlistId
    ) {
        this.type = type;
        this.key = key;
        this.title = title;
        this.artistLine = artistLine;
        this.songCount = songCount;
        this.coverSong = coverSong;
        this.playlistId = playlistId;
    }

    public static GroupItem library(String type, String key, String title, @Nullable String artistLine,
                                    int songCount, @Nullable Song coverSong) {
        return new GroupItem(type, key, title, artistLine, songCount, coverSong, -1);
    }

    public String getType() { return type; }

    public String getKey() { return key; }

    public String getTitle() { return title; }

    /** Secondary line shown before the song count (album artist), or null. */
    @Nullable
    public String getArtistLine() { return artistLine; }

    public int getSongCount() { return songCount; }

    @Nullable
    public Song getCoverSong() { return coverSong; }

    /** Database id for {@link #TYPE_PLAYLIST}, -1 otherwise. */
    public long getPlaylistId() { return playlistId; }

    public boolean isUserPlaylist() { return TYPE_PLAYLIST.equals(type); }

    /** Groups whose songs come from the database (playlists and smart lists). */
    public boolean isPlaylistLike() {
        return TYPE_PLAYLIST.equals(type)
                || TYPE_FAVORITES.equals(type)
                || TYPE_RECENT.equals(type)
                || TYPE_MOST_PLAYED.equals(type);
    }
}
