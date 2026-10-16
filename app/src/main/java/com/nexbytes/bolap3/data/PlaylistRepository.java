package com.nexbytes.bolap3.data;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.db.AppDatabase;
import com.nexbytes.bolap3.data.db.PlaylistEntity;
import com.nexbytes.bolap3.data.db.PlaylistSummary;
import com.nexbytes.bolap3.data.db.SongEntity;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Playlists, favorites and play statistics. Work runs on {@link AppExecutors#db()} and callbacks
 * are delivered on the main thread. {@link #changes()} ticks after every modification so open
 * screens can reload.
 */
public final class PlaylistRepository {

    public static final int SMART_LIST_LIMIT = 100;

    public interface Callback<T> {
        void onResult(T result);
    }

    private static final AtomicInteger version = new AtomicInteger();
    private static final MutableLiveData<Integer> changes = new MutableLiveData<>(0);

    private PlaylistRepository() {
    }

    public static LiveData<Integer> changes() {
        return changes;
    }

    static void notifyChanged() {
        changes.postValue(version.incrementAndGet());
    }

    /** Smart lists (favorites, recent, most played) followed by the user's playlists. */
    public static void loadPlaylistGroups(Context context, Callback<List<GroupItem>> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            MusicRepository.loadBlocking(app);
            AppDatabase db = AppDatabase.getDatabase(app);
            List<GroupItem> groups = new ArrayList<>();

            groups.add(smartList(GroupItem.TYPE_FAVORITES, app.getString(R.string.playlist_favorites),
                    db.statsDao().getFavoriteSongs()));
            groups.add(smartList(GroupItem.TYPE_RECENT, app.getString(R.string.playlist_recent),
                    db.statsDao().getRecentSongs(SMART_LIST_LIMIT)));
            groups.add(smartList(GroupItem.TYPE_MOST_PLAYED, app.getString(R.string.playlist_most_played),
                    db.statsDao().getMostPlayedSongs(SMART_LIST_LIMIT)));

            for (PlaylistSummary summary : db.playlistDao().getPlaylistSummaries()) {
                groups.add(new GroupItem(
                        GroupItem.TYPE_PLAYLIST,
                        "playlist:" + summary.id,
                        summary.name,
                        null,
                        summary.songCount,
                        MusicRepository.findByUri(summary.coverUri),
                        summary.id
                ));
            }

            AppExecutors.main(() -> callback.onResult(groups));
        });
    }

    /** Songs of a playlist-like group, in playlist order. */
    public static void loadSongs(Context context, GroupItem group, Callback<List<Song>> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            List<Song> songs = loadSongsBlocking(app, group.getType(), group.getPlaylistId());
            AppExecutors.main(() -> callback.onResult(songs));
        });
    }

    @WorkerThread
    public static List<Song> loadSongsBlocking(Context context, String type, long playlistId) {
        AppDatabase db = AppDatabase.getDatabase(context);
        List<SongEntity> entities;

        if (GroupItem.TYPE_FAVORITES.equals(type)) {
            entities = db.statsDao().getFavoriteSongs();
        } else if (GroupItem.TYPE_RECENT.equals(type)) {
            entities = db.statsDao().getRecentSongs(SMART_LIST_LIMIT);
        } else if (GroupItem.TYPE_MOST_PLAYED.equals(type)) {
            entities = db.statsDao().getMostPlayedSongs(SMART_LIST_LIMIT);
        } else {
            entities = db.playlistDao().getSongs(playlistId);
        }

        return toSongs(entities);
    }

    @WorkerThread
    public static List<PlaylistSummary> getPlaylistSummariesBlocking(Context context) {
        return AppDatabase.getDatabase(context).playlistDao().getPlaylistSummaries();
    }

    public static void createPlaylist(Context context, String name, @Nullable Callback<Long> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            long id = AppDatabase.getDatabase(app).playlistDao()
                    .insertPlaylist(new PlaylistEntity(name.trim(), System.currentTimeMillis()));
            notifyChanged();
            if (callback != null) {
                AppExecutors.main(() -> callback.onResult(id));
            }
        });
    }

    public static void renamePlaylist(Context context, long playlistId, String name) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase.getDatabase(app).playlistDao().rename(playlistId, name.trim());
            notifyChanged();
        });
    }

    public static void deletePlaylist(Context context, long playlistId) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase.getDatabase(app).playlistDao().deletePlaylist(playlistId);
            notifyChanged();
        });
    }

    /** @param callback receives true if the song was added, false if it was already there. */
    public static void addToPlaylist(Context context, long playlistId, String songUri,
                                     @Nullable Callback<Boolean> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            boolean added = AppDatabase.getDatabase(app).playlistDao()
                    .addSong(playlistId, songUri, System.currentTimeMillis());
            notifyChanged();
            if (callback != null) {
                AppExecutors.main(() -> callback.onResult(added));
            }
        });
    }

    /** Adds several songs; the callback receives how many were new to the playlist. */
    public static void addAllToPlaylist(Context context, long playlistId, List<String> songUris,
                                        @Nullable Callback<Integer> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(app);
            long now = System.currentTimeMillis();
            int added = db.runInTransaction(() -> {
                int count = 0;
                for (String uri : songUris) {
                    if (db.playlistDao().addSong(playlistId, uri, now)) {
                        count++;
                    }
                }
                return count;
            });
            notifyChanged();
            if (callback != null) {
                AppExecutors.main(() -> callback.onResult(added));
            }
        });
    }

    public static void reorderPlaylist(Context context, long playlistId, List<String> songUris) {
        Context app = context.getApplicationContext();
        List<String> order = new ArrayList<>(songUris);
        AppExecutors.db().execute(() -> {
            AppDatabase.getDatabase(app).playlistDao().reorder(playlistId, order);
            notifyChanged();
        });
    }

    public static void addFavorites(Context context, List<String> songUris, @Nullable Runnable done) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(app);
            long now = System.currentTimeMillis();
            db.runInTransaction(() -> {
                for (String uri : songUris) {
                    db.statsDao().addFavorite(new com.nexbytes.bolap3.data.db.FavoriteEntity(uri, now));
                }
            });
            notifyChanged();
            if (done != null) {
                AppExecutors.main(done);
            }
        });
    }

    public static void removeFromPlaylist(Context context, long playlistId, String songUri) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase.getDatabase(app).playlistDao().removeSong(playlistId, songUri);
            notifyChanged();
        });
    }

    public static void isFavorite(Context context, String songUri, Callback<Boolean> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            boolean favorite = AppDatabase.getDatabase(app).statsDao().isFavorite(songUri);
            AppExecutors.main(() -> callback.onResult(favorite));
        });
    }

    /** @param callback receives the new state (true = now a favorite). */
    public static void toggleFavorite(Context context, String songUri, @Nullable Callback<Boolean> callback) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(app);
            boolean nowFavorite = db.runInTransaction(() -> {
                if (db.statsDao().isFavorite(songUri)) {
                    db.statsDao().removeFavorite(songUri);
                    return false;
                }
                db.statsDao().addFavorite(new com.nexbytes.bolap3.data.db.FavoriteEntity(
                        songUri, System.currentTimeMillis()));
                return true;
            });
            notifyChanged();
            if (callback != null) {
                AppExecutors.main(() -> callback.onResult(nowFavorite));
            }
        });
    }

    public static void recordPlay(Context context, String songUri) {
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> {
            AppDatabase.getDatabase(app).statsDao().recordPlay(songUri, System.currentTimeMillis());
            notifyChanged();
        });
    }

    private static GroupItem smartList(String type, String title, List<SongEntity> entities) {
        Song cover = entities.isEmpty() ? null : MusicRepository.findByUri(entities.get(0).getUri());
        return new GroupItem(type, type, title, null, entities.size(), cover, -1);
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
