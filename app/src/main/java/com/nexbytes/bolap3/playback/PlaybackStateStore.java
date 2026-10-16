package com.nexbytes.bolap3.playback;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.db.AppDatabase;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the queue (Room) and the playback position/modes (SharedPreferences) so playback can
 * be restored after the process dies, and resumed from Bluetooth/notification "resume" buttons.
 */
public final class PlaybackStateStore {

    private static final String PREFS = "playback_state";
    private static final String KEY_INDEX = "index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_SHUFFLE = "shuffle";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_SPEED = "speed";

    public static final class Restored {
        public final List<MediaItem> items;
        public final int index;
        public final long positionMs;
        public final boolean shuffle;
        public final int repeatMode;
        public final float speed;

        Restored(List<MediaItem> items, int index, long positionMs, boolean shuffle, int repeatMode, float speed) {
            this.items = items;
            this.index = index;
            this.positionMs = positionMs;
            this.shuffle = shuffle;
            this.repeatMode = repeatMode;
            this.speed = speed;
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private PlaybackStateStore() {
    }

    @WorkerThread
    public static void saveQueue(Context context, List<String> uris) {
        AppDatabase.getDatabase(context).queueDao().replace(uris);
    }

    public static void saveProgress(Context context, int index, long positionMs) {
        prefs(context).edit()
                .putInt(KEY_INDEX, index)
                .putLong(KEY_POSITION, Math.max(0, positionMs))
                .apply();
    }

    public static void saveModes(Context context, boolean shuffle, int repeatMode, float speed) {
        prefs(context).edit()
                .putBoolean(KEY_SHUFFLE, shuffle)
                .putInt(KEY_REPEAT, repeatMode)
                .putFloat(KEY_SPEED, speed)
                .apply();
    }

    public static float getSpeed(Context context) {
        return prefs(context).getFloat(KEY_SPEED, 1f);
    }

    /** Loads the saved queue, dropping songs that are no longer in the library. */
    @WorkerThread
    public static Restored load(Context context) {
        MusicRepository.loadBlocking(context);
        List<String> uris = AppDatabase.getDatabase(context).queueDao().getQueueUris();
        SharedPreferences prefs = prefs(context);
        int savedIndex = prefs.getInt(KEY_INDEX, 0);
        long position = prefs.getLong(KEY_POSITION, 0);

        List<MediaItem> items = new ArrayList<>(uris.size());
        int keptBefore = 0;
        boolean currentKept = false;
        for (int i = 0; i < uris.size(); i++) {
            Song song = MusicRepository.findByUri(uris.get(i));
            if (song == null) {
                continue;
            }
            if (i < savedIndex) {
                keptBefore++;
            } else if (i == savedIndex) {
                currentKept = true;
            }
            items.add(MediaItemFactory.fromSong(song));
        }
        if (!currentKept) {
            // The current song is gone: continue with the one that followed it.
            position = 0;
        }
        int index = items.isEmpty() ? 0 : Math.min(keptBefore, items.size() - 1);

        int repeat = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF);
        if (repeat != Player.REPEAT_MODE_OFF && repeat != Player.REPEAT_MODE_ONE && repeat != Player.REPEAT_MODE_ALL) {
            repeat = Player.REPEAT_MODE_OFF;
        }

        return new Restored(items, index, position, prefs.getBoolean(KEY_SHUFFLE, false), repeat,
                prefs.getFloat(KEY_SPEED, 1f));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
