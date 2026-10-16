package com.nexbytes.bolap3.playback;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.db.PlaylistSummary;
import com.nexbytes.bolap3.library.LibraryGrouping;
import com.nexbytes.bolap3.library.SongSort;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Library tree exposed to external browsers (Android Auto, Wear OS...):
 * root -> songs | albums -> album | artists -> artist | playlists -> favorites/playlist.
 * Playable children carry their parent in the id ("parent##songUri") so that picking one song
 * queues the whole album/playlist starting at that song.
 */
public final class BrowseTree {

    public static final String ROOT = "root";
    static final String SONGS = "songs";
    static final String ALBUMS = "albums";
    static final String ARTISTS = "artists";
    static final String PLAYLISTS = "playlists";
    static final String FAVORITES = "favorites";
    private static final String GROUP_ALBUM = "album|";
    private static final String GROUP_ARTIST = "artist|";
    private static final String GROUP_PLAYLIST = "playlist|";
    private static final String SEPARATOR = "##";

    private BrowseTree() {
    }

    public static MediaItem root(Context context) {
        return folder(ROOT, context.getString(R.string.browse_root), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null);
    }

    /** Strips the "parent##" prefix used by browse items; plain URIs are returned unchanged. */
    public static String songUriOf(String mediaId) {
        int separator = mediaId.indexOf(SEPARATOR);
        return separator >= 0 ? mediaId.substring(separator + SEPARATOR.length()) : mediaId;
    }

    @Nullable
    public static String parentOf(String mediaId) {
        int separator = mediaId.indexOf(SEPARATOR);
        return separator >= 0 ? mediaId.substring(0, separator) : null;
    }

    @WorkerThread
    public static List<MediaItem> children(Context context, String parentId) {
        List<Song> library = MusicRepository.loadBlocking(context);
        List<MediaItem> items = new ArrayList<>();

        switch (parentId) {
            case ROOT:
                items.add(folder(SONGS, context.getString(R.string.tab_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, null));
                items.add(folder(ALBUMS, context.getString(R.string.tab_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, null));
                items.add(folder(ARTISTS, context.getString(R.string.tab_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, null));
                items.add(folder(PLAYLISTS, context.getString(R.string.tab_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS, null));
                return items;
            case ALBUMS:
            case ARTISTS: {
                boolean albums = ALBUMS.equals(parentId);
                String type = albums ? GroupItem.TYPE_ALBUM : GroupItem.TYPE_ARTIST;
                for (GroupItem group : LibraryGrouping.group(library, type, context.getString(R.string.unknown_group))) {
                    Song cover = group.getCoverSong();
                    items.add(folder(
                            (albums ? GROUP_ALBUM : GROUP_ARTIST) + group.getKey(),
                            group.getTitle(),
                            albums ? MediaMetadata.MEDIA_TYPE_ALBUM : MediaMetadata.MEDIA_TYPE_ARTIST,
                            cover != null ? cover.getAlbumArtUri() : null));
                }
                return items;
            }
            case PLAYLISTS:
                items.add(folder(FAVORITES, context.getString(R.string.playlist_favorites), MediaMetadata.MEDIA_TYPE_PLAYLIST, null));
                for (PlaylistSummary summary : PlaylistRepository.getPlaylistSummariesBlocking(context)) {
                    items.add(folder(GROUP_PLAYLIST + summary.id, summary.name, MediaMetadata.MEDIA_TYPE_PLAYLIST, null));
                }
                return items;
            default:
                for (Song song : songsFor(context, parentId)) {
                    items.add(playable(parentId, song));
                }
                return items;
        }
    }

    /** Songs under a playable container (songs, an album, an artist or a playlist). */
    @WorkerThread
    public static List<Song> songsFor(Context context, String parentId) {
        List<Song> library = MusicRepository.loadBlocking(context);
        if (SONGS.equals(parentId)) {
            return SongSort.TITLE.sorted(library);
        }
        if (FAVORITES.equals(parentId)) {
            return PlaylistRepository.loadSongsBlocking(context, GroupItem.TYPE_FAVORITES, -1);
        }
        if (parentId.startsWith(GROUP_PLAYLIST)) {
            try {
                long id = Long.parseLong(parentId.substring(GROUP_PLAYLIST.length()));
                return PlaylistRepository.loadSongsBlocking(context, GroupItem.TYPE_PLAYLIST, id);
            } catch (NumberFormatException exception) {
                return Collections.emptyList();
            }
        }
        if (parentId.startsWith(GROUP_ALBUM)) {
            GroupItem group = GroupItem.library(GroupItem.TYPE_ALBUM, parentId.substring(GROUP_ALBUM.length()), "", null, 0, null);
            return LibraryGrouping.songsOf(library, group);
        }
        if (parentId.startsWith(GROUP_ARTIST)) {
            GroupItem group = GroupItem.library(GroupItem.TYPE_ARTIST, parentId.substring(GROUP_ARTIST.length()), "", null, 0, null);
            return LibraryGrouping.songsOf(library, group);
        }
        return Collections.emptyList();
    }

    @WorkerThread
    @Nullable
    public static MediaItem item(Context context, String mediaId) {
        if (ROOT.equals(mediaId)) {
            return root(context);
        }
        Song song = MusicRepository.findByUri(songUriOf(mediaId));
        if (song == null) {
            MusicRepository.loadBlocking(context);
            song = MusicRepository.findByUri(songUriOf(mediaId));
        }
        if (song != null) {
            String parent = parentOf(mediaId);
            return parent != null ? playable(parent, song) : MediaItemFactory.fromSong(song);
        }
        for (MediaItem child : children(context, ROOT)) {
            if (child.mediaId.equals(mediaId)) {
                return child;
            }
        }
        return null;
    }

    private static MediaItem playable(String parentId, Song song) {
        // No URI on purpose: the session must resolve it (and expand the parent) in onSetMediaItems.
        MediaItem base = MediaItemFactory.fromSong(song);
        return base.buildUpon()
                .setMediaId(parentId + SEPARATOR + song.getUri())
                .setUri((Uri) null)
                .build();
    }

    private static MediaItem folder(String id, String title, int mediaType, @Nullable String artworkUri) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType);
        if (artworkUri != null) {
            metadata.setArtworkUri(Uri.parse(artworkUri));
        }
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata.build()).build();
    }
}
