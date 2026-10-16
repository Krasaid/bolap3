package com.nexbytes.bolap3;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nexbytes.bolap3.adapter.LyricsAdapter;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.playback.PlaybackActions;
import com.nexbytes.bolap3.playback.PlayerConnection;
import com.nexbytes.bolap3.tags.LrcParser;
import com.nexbytes.bolap3.tags.LyricsLoader;
import com.nexbytes.bolap3.ui.BaseActivity;

/** Lyrics of the current song; synced (.lrc) lyrics follow playback and lines can be tapped to seek. */
public class LyricsActivity extends BaseActivity {

    private static final long SYNC_INTERVAL_MS = 250;

    private PlayerConnection connection;
    private LyricsAdapter adapter;
    private RecyclerView rvLyrics;
    private LinearLayoutManager layoutManager;
    private View emptyView;
    private TextView tvStatus;
    private TextView tvHint;
    private TextView tvSong;

    @Nullable
    private String loadedUri;
    @Nullable
    private LrcParser.Lyrics lyrics;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable syncUpdater = new Runnable() {
        @Override
        public void run() {
            syncActiveLine();
            handler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                loadForCurrentSong();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lyrics);
        setupToolbar(R.string.lyrics_title);

        rvLyrics = findViewById(R.id.rvLyrics);
        emptyView = findViewById(R.id.lyricsEmpty);
        tvStatus = findViewById(R.id.tvLyricsStatus);
        tvHint = findViewById(R.id.tvLyricsHint);
        tvSong = findViewById(R.id.tvLyricsSong);

        layoutManager = new LinearLayoutManager(this);
        rvLyrics.setLayoutManager(layoutManager);
        adapter = new LyricsAdapter(line -> {
            MediaController controller = connection.get();
            if (controller != null && line.timeMs >= 0) {
                controller.seekTo(line.timeMs);
            }
        });
        rvLyrics.setAdapter(adapter);

        connection = new PlayerConnection(this, listener);
        com.nexbytes.bolap3.media.NowPlayingAccent.get().observe(this, adapter::setAccentColor);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.connect(controller -> loadForCurrentSong());
        handler.post(syncUpdater);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(syncUpdater);
        connection.disconnect();
    }

    private void loadForCurrentSong() {
        Song song = PlaybackActions.currentSong(connection.get());
        if (song == null) {
            showStatus(getString(R.string.player_no_song_title), false);
            tvSong.setText("");
            return;
        }
        if (song.getUri().equals(loadedUri)) {
            return;
        }
        loadedUri = song.getUri();
        lyrics = null;
        tvSong.setText(getString(R.string.group_subtitle_with_artist, song.getTitle(), song.getArtist()));
        showStatus(getString(R.string.lyrics_loading), false);

        String uri = song.getUri();
        AppExecutors.io().execute(() -> {
            LrcParser.Lyrics result = LyricsLoader.load(this, song);
            AppExecutors.main(() -> {
                if (!uri.equals(loadedUri) || isFinishing()) {
                    return;
                }
                if (result.isEmpty()) {
                    showStatus(getString(R.string.lyrics_not_found), true);
                    return;
                }
                lyrics = result;
                emptyView.setVisibility(View.GONE);
                rvLyrics.setVisibility(View.VISIBLE);
                adapter.submit(result);
                rvLyrics.scrollToPosition(0);
                syncActiveLine();
            });
        });
    }

    private void showStatus(String message, boolean showHint) {
        adapter.submit(LrcParser.parse(null));
        rvLyrics.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        tvStatus.setText(message);
        tvHint.setVisibility(showHint ? View.VISIBLE : View.GONE);
    }

    private void syncActiveLine() {
        MediaController controller = connection.get();
        if (controller == null || lyrics == null || !lyrics.synced) {
            return;
        }
        int index = lyrics.indexAt(controller.getCurrentPosition());
        if (adapter.setActiveIndex(index) && index >= 0) {
            // Keep the active line about a third of the way down the screen.
            int offset = rvLyrics.getHeight() / 3;
            layoutManager.scrollToPositionWithOffset(index, offset);
        }
    }
}
