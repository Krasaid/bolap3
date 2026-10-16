package com.nexbytes.bolap3.playback;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.List;

/** Song <-> MediaItem conversion. Queue items always use the song URI as mediaId. */
public final class MediaItemFactory {

    private MediaItemFactory() {
    }

    public static MediaItem fromSong(Song song) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist())
                .setAlbumTitle(song.getAlbum())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
        // No artworkUri: ExoPlayer extracts embedded artwork itself, and MediaStore's album-art
        // URI returns undecodable data for albums without art (the notification kept retrying it).
        if (song.getTrackNumber() > 0) {
            metadata.setTrackNumber(song.getTrackNumber() % 1000);
        }

        return new MediaItem.Builder()
                .setMediaId(song.getUri())
                .setUri(song.getUri())
                .setMediaMetadata(metadata.build())
                .build();
    }

    public static List<MediaItem> fromSongs(List<Song> songs) {
        List<MediaItem> items = new ArrayList<>(songs.size());
        for (Song song : songs) {
            items.add(fromSong(song));
        }
        return items;
    }

    /** The library song for a queue item, or a song rebuilt from the item's metadata. */
    @Nullable
    public static Song toSong(@Nullable MediaItem item) {
        if (item == null) {
            return null;
        }
        Song song = MusicRepository.findByUri(item.mediaId);
        if (song != null) {
            return song;
        }
        MediaMetadata metadata = item.mediaMetadata;
        return new Song(
                item.mediaId,
                metadata.title != null ? metadata.title.toString() : "",
                metadata.artist != null ? metadata.artist.toString() : "",
                metadata.albumTitle != null ? metadata.albumTitle.toString() : "",
                -1,
                metadata.artworkUri != null ? metadata.artworkUri.toString() : null,
                "",
                "",
                0,
                0,
                0
        );
    }

    /**
     * Makes an item coming from another app (Android Auto, a legacy controller) playable:
     * looks the song up by URI. Returns null if it cannot be resolved.
     */
    @Nullable
    public static MediaItem resolve(MediaItem requested) {
        String uri = BrowseTree.songUriOf(requested.mediaId);
        Song song = MusicRepository.findByUri(uri);
        if (song != null) {
            return fromSong(song);
        }
        if (requested.localConfiguration != null) {
            return requested;
        }
        if (requested.requestMetadata.mediaUri != null) {
            return requested.buildUpon().setUri(requested.requestMetadata.mediaUri).build();
        }
        return null;
    }
}
