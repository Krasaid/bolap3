package com.nexbytes.bolap3.playback;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionError;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.nexbytes.bolap3.PlayerActivity;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.audio.EqualizerManager;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.media.GeneratedCoverDrawable;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.tags.LyricsLoader;
import com.nexbytes.bolap3.tags.ReplayGain;
import com.nexbytes.bolap3.tags.TagReader;
import com.nexbytes.bolap3.tile.PlaybackTileService;
import com.nexbytes.bolap3.widget.PlayerWidgetProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Owns the ExoPlayer and the media session. Everything that plays audio goes through here:
 * the app's screens (via MediaController), the media notification, lock screen, headset and
 * Bluetooth buttons, Android Auto/Wear, the widget and the quick settings tile. Queue
 * navigation (next/previous/shuffle/repeat/auto-advance) is ExoPlayer's own playlist logic.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlaybackService extends MediaLibraryService {

    private static final String TAG = "PlaybackService";
    private static final long SAVE_POSITION_INTERVAL_MS = 10_000;
    private static final long SAVE_QUEUE_DEBOUNCE_MS = 1_000;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(AppExecutors.db());

    private ExoPlayer player;
    private MediaLibrarySession session;
    private SharedPreferences.OnSharedPreferenceChangeListener settingsListener;
    private AudioIntegrations audioIntegrations;

    /** Until the saved queue is restored, never overwrite it with the (empty) live queue. */
    private boolean restored = false;
    private int consecutiveErrors = 0;
    /** URI of the current item once its play has been counted (null = not counted yet). */
    @Nullable
    private String countedUri;

    private final Runnable savePositionRunnable = new Runnable() {
        @Override
        public void run() {
            saveProgress();
            if (player != null && player.isPlaying()) {
                handler.postDelayed(this, SAVE_POSITION_INTERVAL_MS);
            }
        }
    };

    private final Runnable saveQueueRunnable = this::saveQueue;

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setSkipSilenceEnabled(SettingsPrefs.isSkipSilence(this));
        player.addListener(playerListener);

        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback())
                .setSessionActivity(buildSessionActivity())
                .build();

        DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this)
                        .setChannelName(R.string.notification_channel_playback)
                        .build();
        notificationProvider.setSmallIcon(R.drawable.ic_notification);
        setMediaNotificationProvider(notificationProvider);

        setListener(new Listener() {
            @Override
            public void onForegroundServiceStartNotAllowedException() {
                Log.w(TAG, "Not allowed to start the playback notification from the background");
            }
        });

        SleepTimer.attach(player);
        EqualizerManager.attach(this, player.getAudioSessionId());
        audioIntegrations = new AudioIntegrations(this, player);
        audioIntegrations.start();

        settingsListener = (prefs, key) -> {
            if (SettingsPrefs.KEY_SKIP_SILENCE.equals(key)) {
                player.setSkipSilenceEnabled(SettingsPrefs.isSkipSilence(this));
            } else if (SettingsPrefs.KEY_REPLAY_GAIN.equals(key)) {
                applyReplayGain(player.getCurrentMediaItem());
            }
        };
        SettingsPrefs.prefs(this).registerOnSharedPreferenceChangeListener(settingsListener);

        restoreQueue();
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (restored) {
            saveProgress();
            saveQueue();
        }
        SettingsPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(settingsListener);
        SleepTimer.detach();
        audioIntegrations.release();
        EqualizerManager.release();
        PlaybackInfo.clear();
        refreshExternalViews();

        player.removeListener(playerListener);
        player.release();
        session.release();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ player events

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            countedUri = null;
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                // A new list was started: make sure shuffle plays every song of it.
                ensureShuffleStartsAtCurrent();
            }
            if (player.isPlaying()) {
                countPlay();
            }
            applyReplayGain(mediaItem);
            saveProgress();
            updateNowPlaying();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (isPlaying) {
                consecutiveErrors = 0;
                countPlay();
                handler.removeCallbacks(savePositionRunnable);
                handler.postDelayed(savePositionRunnable, SAVE_POSITION_INTERVAL_MS);
            } else {
                handler.removeCallbacks(savePositionRunnable);
                saveProgress();
            }
            updateNowPlaying();
        }

        @Override
        public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                handler.removeCallbacks(saveQueueRunnable);
                handler.postDelayed(saveQueueRunnable, SAVE_QUEUE_DEBOUNCE_MS);
            }
        }

        @Override
        public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
            updateNowPlaying();
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            if (shuffleModeEnabled) {
                ensureShuffleStartsAtCurrent();
            }
            saveModes();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            saveModes();
        }

        @Override
        public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
            saveModes();
        }

        @Override
        public void onAudioSessionIdChanged(int audioSessionId) {
            EqualizerManager.attach(PlaybackService.this, audioSessionId);
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.w(TAG, "Playback error", error);
            consecutiveErrors++;
            // Skip unplayable files (moved, deleted, unsupported) instead of stopping the queue,
            // but give up after a few failures in a row.
            if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                player.seekToNextMediaItem();
                player.prepare();
                player.play();
            }
        }
    };

    /**
     * ExoPlayer shuffles the whole list randomly, so when a list starts from a chosen song the
     * songs placed before it in the shuffle order would never play. Put the current song first.
     */
    private void ensureShuffleStartsAtCurrent() {
        if (!player.getShuffleModeEnabled()) {
            return;
        }
        Timeline timeline = player.getCurrentTimeline();
        int count = timeline.getWindowCount();
        int current = player.getCurrentMediaItemIndex();
        if (count < 2 || current == C.INDEX_UNSET || timeline.getFirstWindowIndex(true) == current) {
            return;
        }

        int[] order = new int[count];
        order[0] = current;
        int position = 1;
        for (int i = 0; i < count; i++) {
            if (i != current) {
                order[position++] = i;
            }
        }
        for (int i = count - 1; i > 1; i--) {
            int j = 1 + random.nextInt(i);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        player.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(order, random.nextLong()));
    }

    private void countPlay() {
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || Objects.equals(countedUri, item.mediaId)) {
            return;
        }
        countedUri = item.mediaId;
        PlaylistRepository.recordPlay(this, item.mediaId);
    }

    private void applyReplayGain(@Nullable MediaItem item) {
        if (item == null || !SettingsPrefs.isReplayGain(this)) {
            player.setVolume(1f);
            return;
        }
        String mediaId = item.mediaId;
        AppExecutors.io().execute(() -> {
            TagReader.Tags tags = LyricsLoader.readTags(this, Uri.parse(mediaId));
            float volume = tags != null ? ReplayGain.volumeFor(tags.trackGainDb, tags.albumGainDb) : 1f;
            handler.post(() -> {
                MediaItem current = player.getCurrentMediaItem();
                if (current != null && current.mediaId.equals(mediaId)) {
                    player.setVolume(volume);
                }
            });
        });
    }

    // ------------------------------------------------------------------ persistence

    private void restoreQueue() {
        executor.execute(() -> {
            PlaybackStateStore.Restored state = PlaybackStateStore.load(this);
            handler.post(() -> {
                if (player.getMediaItemCount() == 0 && !state.isEmpty()) {
                    player.setRepeatMode(state.repeatMode);
                    player.setShuffleModeEnabled(state.shuffle);
                    player.setPlaybackSpeed(state.speed);
                    player.setMediaItems(state.items, state.index, state.positionMs);
                    player.prepare();
                }
                restored = true;
                updateNowPlaying();
            });
        });
    }

    private void saveQueue() {
        if (!restored) {
            return;
        }
        List<String> uris = new ArrayList<>(player.getMediaItemCount());
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            uris.add(player.getMediaItemAt(i).mediaId);
        }
        AppExecutors.db().execute(() -> PlaybackStateStore.saveQueue(this, uris));
    }

    private void saveProgress() {
        if (restored && player.getMediaItemCount() > 0) {
            PlaybackStateStore.saveProgress(this, player.getCurrentMediaItemIndex(), player.getCurrentPosition());
        }
    }

    private void saveModes() {
        PlaybackStateStore.saveModes(this, player.getShuffleModeEnabled(), player.getRepeatMode(),
                player.getPlaybackParameters().speed);
    }

    // ------------------------------------------------------------------ widget / tile

    private void updateNowPlaying() {
        MediaItem item = player.getCurrentMediaItem();
        MediaMetadata metadata = player.getMediaMetadata();
        PlaybackInfo.hasMedia = item != null;
        PlaybackInfo.playing = player.isPlaying();
        PlaybackInfo.title = metadata.title != null ? metadata.title.toString() : null;
        PlaybackInfo.artist = metadata.artist != null ? metadata.artist.toString() : null;

        if (item == null) {
            PlaybackInfo.artwork = null;
            refreshExternalViews();
            return;
        }

        String mediaId = item.mediaId;
        byte[] artworkData = metadata.artworkData;
        AppExecutors.io().execute(() -> {
            Bitmap art = artworkData != null ? decodeSmall(artworkData) : null;
            if (art == null) {
                Song song = MediaItemFactory.toSong(item);
                art = AlbumArtLoader.loadBlocking(this, song, AlbumArtLoader.SIZE_THUMB);
                if (art == null && song != null) {
                    art = GeneratedCoverDrawable.toBitmap(AlbumArtLoader.albumSeed(song), song.getAlbum(),
                            AlbumArtLoader.SIZE_THUMB);
                }
            }
            Bitmap finalArt = art;
            handler.post(() -> {
                MediaItem current = player.getCurrentMediaItem();
                if (current != null && current.mediaId.equals(mediaId)) {
                    PlaybackInfo.artwork = finalArt;
                    refreshExternalViews();
                }
            });
        });
        refreshExternalViews();
    }

    private void refreshExternalViews() {
        PlayerWidgetProvider.refresh(this);
        try {
            TileService.requestListeningState(this, new ComponentName(this, PlaybackTileService.class));
        } catch (RuntimeException ignored) {
            // Tile not added by the user.
        }
    }

    @Nullable
    private static Bitmap decodeSmall(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= AlbumArtLoader.SIZE_THUMB) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private PendingIntent buildSessionActivity() {
        Intent intent = new Intent(this, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // ------------------------------------------------------------------ session callback

    private final class LibraryCallback implements MediaLibrarySession.Callback {

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(BrowseTree.root(PlaybackService.this), params));
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String mediaId) {
            return executor.submit(() -> {
                MediaItem item = BrowseTree.item(PlaybackService.this, mediaId);
                return item != null
                        ? LibraryResult.ofItem(item, null)
                        : LibraryResult.ofError(SessionError.ERROR_BAD_VALUE);
            });
        }

        @NonNull
        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                @NonNull MediaLibrarySession session,
                @NonNull MediaSession.ControllerInfo browser,
                @NonNull String parentId,
                int page,
                int pageSize,
                @Nullable LibraryParams params) {
            return executor.submit(() -> {
                List<MediaItem> children = BrowseTree.children(PlaybackService.this, parentId);
                int from = (int) Math.min((long) page * pageSize, children.size());
                int to = (int) Math.min((long) from + pageSize, children.size());
                return LibraryResult.ofItemList(children.subList(from, to), params);
            });
        }

        @NonNull
        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull List<MediaItem> mediaItems) {
            if (allPlayable(mediaItems)) {
                return Futures.immediateFuture(mediaItems);
            }
            return executor.submit(() -> {
                List<MediaItem> searchResults = searchResults(mediaItems);
                if (searchResults != null) {
                    return searchResults;
                }
                MusicRepository.loadBlocking(PlaybackService.this);
                List<MediaItem> resolved = new ArrayList<>(mediaItems.size());
                for (MediaItem item : mediaItems) {
                    MediaItem playable = MediaItemFactory.resolve(item);
                    if (playable != null) {
                        resolved.add(playable);
                    }
                }
                return resolved;
            });
        }

        /** Results for a single "search" request item, or null if this is not a search. */
        @Nullable
        private List<MediaItem> searchResults(List<MediaItem> mediaItems) {
            if (mediaItems.size() != 1 || mediaItems.get(0).requestMetadata.searchQuery == null) {
                return null;
            }
            String query = mediaItems.get(0).requestMetadata.searchQuery;
            List<Song> songs = PlaybackActions.searchSongs(MusicRepository.loadBlocking(PlaybackService.this), query);
            if (songs == null) {
                return new ArrayList<>();
            }
            if (query.trim().isEmpty()) {
                songs = new ArrayList<>(songs);
                java.util.Collections.shuffle(songs, random);
            }
            return MediaItemFactory.fromSongs(songs);
        }

        @NonNull
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs) {
            if (allPlayable(mediaItems)) {
                return Futures.immediateFuture(
                        new MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs));
            }
            return executor.submit(() -> {
                // Voice search ("play X") from Android Auto or the Assistant.
                List<MediaItem> searchResults = searchResults(mediaItems);
                if (searchResults != null) {
                    return new MediaSession.MediaItemsWithStartPosition(searchResults, 0, C.TIME_UNSET);
                }
                // A single song picked in Android Auto: queue its whole album/playlist.
                if (mediaItems.size() == 1) {
                    String parent = BrowseTree.parentOf(mediaItems.get(0).mediaId);
                    if (parent != null) {
                        List<Song> songs = BrowseTree.songsFor(PlaybackService.this, parent);
                        String uri = BrowseTree.songUriOf(mediaItems.get(0).mediaId);
                        for (int i = 0; i < songs.size(); i++) {
                            if (songs.get(i).getUri().equals(uri)) {
                                return new MediaSession.MediaItemsWithStartPosition(
                                        MediaItemFactory.fromSongs(songs), i, C.TIME_UNSET);
                            }
                        }
                    }
                }
                MusicRepository.loadBlocking(PlaybackService.this);
                List<MediaItem> resolved = new ArrayList<>(mediaItems.size());
                for (MediaItem item : mediaItems) {
                    MediaItem playable = MediaItemFactory.resolve(item);
                    if (playable != null) {
                        resolved.add(playable);
                    }
                }
                int index = resolved.isEmpty() ? C.INDEX_UNSET : Math.min(Math.max(startIndex, 0), resolved.size() - 1);
                return new MediaSession.MediaItemsWithStartPosition(resolved, index, startPositionMs);
            });
        }

        @NonNull
        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                @NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller,
                boolean isForPlayback) {
            return executor.submit(() -> {
                PlaybackStateStore.Restored state = PlaybackStateStore.load(PlaybackService.this);
                if (state.isEmpty()) {
                    throw new UnsupportedOperationException("Nothing to resume");
                }
                if (!isForPlayback) {
                    return new MediaSession.MediaItemsWithStartPosition(
                            ImmutableList.of(state.items.get(state.index)), 0, state.positionMs);
                }
                handler.post(() -> {
                    player.setRepeatMode(state.repeatMode);
                    player.setShuffleModeEnabled(state.shuffle);
                    player.setPlaybackSpeed(state.speed);
                    restored = true;
                });
                return new MediaSession.MediaItemsWithStartPosition(state.items, state.index, state.positionMs);
            });
        }

        private boolean allPlayable(List<MediaItem> items) {
            for (MediaItem item : items) {
                if (item.localConfiguration == null) {
                    return false;
                }
            }
            return true;
        }
    }
}
