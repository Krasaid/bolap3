package com.nexbytes.bolap3.library;

import android.content.Context;

import androidx.annotation.WorkerThread;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.db.AppDatabase;
import com.nexbytes.bolap3.data.db.PlaylistSummary;
import com.nexbytes.bolap3.data.db.SongEntity;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.HomeSection;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Builds the Home tab from play statistics, playlists and the library. */
public final class HomeLoader {

    private static final int CAROUSEL_SIZE = 15;

    private HomeLoader() {
    }

    public static void load(Context context, PlaylistRepository.Callback<List<HomeSection>> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            List<HomeSection> sections = loadBlocking(app);
            AppExecutors.main(() -> callback.onResult(sections));
        });
    }

    @WorkerThread
    static List<HomeSection> loadBlocking(Context context) {
        List<Song> library = MusicRepository.loadBlocking(context);
        AppDatabase db = AppDatabase.getDatabase(context);
        List<GroupItem> albums = LibraryGrouping.group(library, GroupItem.TYPE_ALBUM,
                context.getString(R.string.unknown_group));

        List<Song> recent = toSongs(db.statsDao().getRecentSongs(CAROUSEL_SIZE));
        List<Song> mostPlayed = toSongs(db.statsDao().getMostPlayedSongs(CAROUSEL_SIZE));
        List<Song> favorites = toSongs(db.statsDao().getFavoriteSongs());
        List<Song> recentForRediscover = toSongs(db.statsDao().getRecentSongs(PlaylistRepository.SMART_LIST_LIMIT));

        long now = System.currentTimeMillis();
        long daySeed = HomeSections.daySeed(now, TimeZone.getDefault().getOffset(now));

        List<HomeSection> sections = new ArrayList<>();
        addSongs(sections, R.string.home_continue, recent);
        addGroups(sections, R.string.home_recently_added,
                HomeSections.recentlyAddedAlbums(library, albums, CAROUSEL_SIZE));
        addSongs(sections, R.string.playlist_favorites,
                favorites.subList(0, Math.min(CAROUSEL_SIZE, favorites.size())));
        addSongs(sections, R.string.playlist_most_played, mostPlayed);
        addGroups(sections, R.string.home_rediscover,
                HomeSections.rediscover(albums, recentForRediscover, daySeed, CAROUSEL_SIZE));

        List<GroupItem> playlists = new ArrayList<>();
        for (PlaylistSummary summary : db.playlistDao().getPlaylistSummaries()) {
            playlists.add(new GroupItem(GroupItem.TYPE_PLAYLIST, "playlist:" + summary.id, summary.name,
                    null, summary.songCount, MusicRepository.findByUri(summary.coverUri), summary.id));
        }
        addGroups(sections, R.string.home_your_playlists, playlists);
        return sections;
    }

    private static void addSongs(List<HomeSection> sections, int title, List<Song> songs) {
        if (!songs.isEmpty()) {
            sections.add(HomeSection.ofSongs(title, new ArrayList<>(songs)));
        }
    }

    private static void addGroups(List<HomeSection> sections, int title, List<GroupItem> groups) {
        if (!groups.isEmpty()) {
            sections.add(HomeSection.ofGroups(title, new ArrayList<>(groups)));
        }
    }

    private static List<Song> toSongs(List<SongEntity> entities) {
        List<Song> songs = new ArrayList<>(entities.size());
        for (SongEntity entity : entities) {
            Song cached = MusicRepository.findByUri(entity.getUri());
            songs.add(cached != null ? cached : entity.toSong());
        }
        return songs;
    }
}
