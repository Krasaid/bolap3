package com.nexbytes.bolap3.data;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nexbytes.bolap3.data.db.AppDatabase;
import com.nexbytes.bolap3.data.db.SongEntity;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The song library: Room is the source of truth, with an immutable in-memory snapshot for fast
 * lookups. Snapshots are swapped atomically (never mutated in place), so readers on any thread
 * always see a consistent list. Observe {@link #songs()} to react to rescans and deletions.
 */
public final class MusicRepository {

    private static final MutableLiveData<List<Song>> songsLive = new MutableLiveData<>();
    private static final Object LOAD_LOCK = new Object();

    private static volatile List<Song> snapshot = Collections.emptyList();
    private static volatile Map<String, Song> byUri = Collections.emptyMap();
    private static volatile boolean loaded = false;

    private MusicRepository() {
    }

    public static LiveData<List<Song>> songs() {
        return songsLive;
    }

    /** Loads the library from Room once per process (asynchronously). */
    public static void ensureLoaded(Context context) {
        if (loaded) {
            return;
        }
        Context app = context.getApplicationContext();
        AppExecutors.db().execute(() -> loadBlocking(app));
    }

    /** Loads the library if needed and returns it. Safe to call from the playback service. */
    @WorkerThread
    public static List<Song> loadBlocking(Context context) {
        synchronized (LOAD_LOCK) {
            if (!loaded) {
                List<SongEntity> entities = AppDatabase.getDatabase(context).songDao().getAllSongs();
                List<Song> songs = new ArrayList<>(entities.size());
                for (SongEntity entity : entities) {
                    songs.add(entity.toSong());
                }
                publish(songs);
            }
            return snapshot;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** Current snapshot (empty until loaded). The returned list is unmodifiable. */
    public static List<Song> getSongsImmediate() {
        return snapshot;
    }

    @Nullable
    public static Song findByUri(@Nullable String uri) {
        return uri == null ? null : byUri.get(uri);
    }

    /** Replaces the library with a fresh scan result. Blocks until written. */
    @WorkerThread
    public static void replaceAll(Context context, List<Song> songs) {
        List<SongEntity> entities = new ArrayList<>(songs.size());
        for (Song song : songs) {
            entities.add(SongEntity.fromSong(song));
        }

        synchronized (LOAD_LOCK) {
            AppDatabase.getDatabase(context).songDao().replaceAll(entities);
            publish(new ArrayList<>(songs));
        }
    }

    /**
     * Removes songs whose files were deleted, together with their playlist entries, favorite
     * flags and play stats.
     */
    public static void removeSongs(Context context, Collection<String> uris) {
        if (uris.isEmpty()) {
            return;
        }
        Context app = context.getApplicationContext();
        List<String> uriList = new ArrayList<>(uris);

        AppExecutors.db().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(app);
            db.runInTransaction(() -> {
                db.songDao().deleteByUris(uriList);
                db.playlistDao().deleteSongReferences(uriList);
                db.statsDao().deleteFavorites(uriList);
                db.statsDao().deleteStats(uriList);
            });
            removeFromSnapshot(uriList);
            PlaylistRepository.notifyChanged();
        });
    }

    /** Hides a folder (and its subfolders) from the library now and in future scans. */
    public static void excludeFolder(Context context, String folderPath) {
        LibraryFilterPrefs.excludeFolder(context, folderPath);
        Context app = context.getApplicationContext();

        AppExecutors.db().execute(() -> {
            List<String> toRemove = new ArrayList<>();
            for (Song song : loadBlocking(app)) {
                if (FolderPaths.isWithin(song.getFolderPath(), folderPath)) {
                    toRemove.add(song.getUri());
                }
            }
            if (!toRemove.isEmpty()) {
                AppDatabase.getDatabase(app).songDao().deleteByUris(toRemove);
                removeFromSnapshot(toRemove);
            }
        });
    }

    @WorkerThread
    public static int getSongCount(Context context) {
        return AppDatabase.getDatabase(context).songDao().getSongCount();
    }

    private static void removeFromSnapshot(List<String> uris) {
        synchronized (LOAD_LOCK) {
            Set<String> removed = new java.util.HashSet<>(uris);
            List<Song> remaining = new ArrayList<>(snapshot.size());
            for (Song song : snapshot) {
                if (!removed.contains(song.getUri())) {
                    remaining.add(song);
                }
            }
            publish(remaining);
        }
    }

    private static void publish(List<Song> songs) {
        List<Song> immutable = Collections.unmodifiableList(songs);
        Map<String, Song> index = new HashMap<>(songs.size() * 2);
        for (Song song : songs) {
            index.put(song.getUri(), song);
        }
        byUri = index;
        snapshot = immutable;
        loaded = true;
        songsLive.postValue(immutable);
    }
}
