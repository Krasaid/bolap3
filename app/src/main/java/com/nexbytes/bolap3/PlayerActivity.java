package com.nexbytes.bolap3;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nexbytes.bolap3.audio.AudioInfo;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.media.AccentMath;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.media.GeneratedCoverDrawable;
import com.nexbytes.bolap3.media.NowPlayingAccent;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.playback.PlaybackActions;
import com.nexbytes.bolap3.playback.PlayerConnection;
import com.nexbytes.bolap3.playback.SleepTimer;
import com.nexbytes.bolap3.ui.BaseActivity;
import com.nexbytes.bolap3.ui.Formatters;
import com.nexbytes.bolap3.ui.QueueSheet;
import com.nexbytes.bolap3.ui.SongActionsSheet;
import com.nexbytes.bolap3.ui.RingtoneSetter;
import com.nexbytes.bolap3.ui.SongDeleteHelper;

public class PlayerActivity extends BaseActivity implements SongActionsSheet.Host {

    /** Search text from a voice request; the matching songs start playing. */
    public static final String EXTRA_SEARCH_QUERY = "search_query";
    /** URI of the song being opened, so it shows before the controller connects. */
    public static final String EXTRA_SONG_URI = "song_uri";
    /** Set when opened from the mini player with a shared-element cover transition. */
    public static final String EXTRA_SHARED_COVER = "shared_cover";

    private static final float BACKDROP_BLUR_PX = 90f;
    private static final float COVER_SCALE_PAUSED = 0.86f;
    private static final long ACCENT_ANIMATION_MS = 450;

    private static final long PROGRESS_INTERVAL_MS = 500;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private PlayerConnection connection;
    private SongDeleteHelper deleteHelper;
    private RingtoneSetter ringtoneSetter;

    private ImageView imgBackdrop;
    private View frameCover;
    private ImageView imgAlbumArt;
    private TextView tvPlayerTitle;
    private TextView tvPlayerArtist;
    private TextView tvAudioInfo;
    private TextView tvCurrentTime;
    private TextView tvDuration;
    private TextView tvSleepStatus;
    private TextView btnSpeed;
    private SeekBar seekBarProgress;
    private ImageButton btnPlayPause;
    private ImageButton btnPrevious;
    private ImageButton btnNext;
    private ImageButton btnShuffle;
    private ImageButton btnRepeat;
    private ImageButton btnFavorite;

    private boolean userSeeking = false;
    @Nullable
    private String displayedUri;
    private boolean favorite = false;
    @Nullable
    private Boolean lastPlaying;

    /** Current accent (theme primary or the artwork color) and the color drawn on top of it. */
    private int accentColor;
    private int onAccentColor;
    @Nullable
    private ValueAnimator accentAnimator;
    /** Play button background: a circle when paused, a rounded square while playing. */
    private final GradientDrawable playButtonShape = new GradientDrawable();
    @Nullable
    private ValueAnimator shapeAnimator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            updateSleepStatus();
            handler.postDelayed(this, PROGRESS_INTERVAL_MS);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            updatePlayerView();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            MediaController controller = connection.get();
            Song song = PlaybackActions.currentSong(controller);
            if (controller != null && controller.hasNextMediaItem()) {
                // The service skips to the next song by itself.
                Toast.makeText(PlayerActivity.this,
                        getString(R.string.player_error_toast, song != null ? song.getTitle() : ""),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(PlayerActivity.this, StatusStateActivity.class);
            intent.putExtra(StatusStateActivity.EXTRA_STATE_TYPE, StatusStateActivity.STATE_PLAYBACK_ERROR);
            startActivity(intent);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        boolean sharedCover = getIntent().getBooleanExtra(EXTRA_SHARED_COVER, false);
        if (sharedCover) {
            // Wait until the cover has content so the mini player's artwork can fly into place.
            postponeEnterTransition();
        } else {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_up, R.anim.hold);
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.hold, R.anim.slide_out_down);
        }

        connection = new PlayerConnection(this, playerListener);
        deleteHelper = new SongDeleteHelper(this, connection::get);
        ringtoneSetter = new RingtoneSetter(this);

        imgBackdrop = findViewById(R.id.imgBackdrop);
        frameCover = findViewById(R.id.frameCover);
        imgAlbumArt = findViewById(R.id.imgAlbumArt);
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle);
        tvPlayerArtist = findViewById(R.id.tvPlayerArtist);
        tvAudioInfo = findViewById(R.id.tvAudioInfo);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        tvSleepStatus = findViewById(R.id.tvSleepStatus);
        btnSpeed = findViewById(R.id.btnSpeed);
        seekBarProgress = findViewById(R.id.seekBarProgress);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnFavorite = findViewById(R.id.btnFavorite);

        imgBackdrop.setRenderEffect(RenderEffect.createBlurEffect(
                BACKDROP_BLUR_PX, BACKDROP_BLUR_PX, Shader.TileMode.CLAMP));
        btnPlayPause.setBackground(playButtonShape);
        playButtonShape.setCornerRadius(dp(37));
        applyAccent(MaterialColors.getColor(btnPlayPause, androidx.appcompat.R.attr.colorPrimary));
        NowPlayingAccent.get().observe(this, color -> animateAccent(color != null
                ? color
                : MaterialColors.getColor(btnPlayPause, androidx.appcompat.R.attr.colorPrimary)));

        setupButtons();
        setupSeekBar();
        setupCoverGestures();

        // Show the song we were opened for right away, before the controller connects.
        Song initial = MusicRepository.findByUri(getIntent().getStringExtra(EXTRA_SONG_URI));
        if (initial != null) {
            showSongInfo(initial);
        } else {
            updatePlayerView();
        }
        if (sharedCover) {
            startPostponedEnterTransition();
        }
    }

    @Override
    protected int getInsetsTargetId() {
        return R.id.playerContent;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSearchIntent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.connect(controller -> {
            handleSearchIntent();
            updatePlayerView();
        });
    }

    /** Voice search forwarded by LauncherActivity: play the matching songs. */
    private void handleSearchIntent() {
        MediaController controller = connection.get();
        Intent intent = getIntent();
        if (controller == null || !intent.hasExtra(EXTRA_SEARCH_QUERY)) {
            return;
        }
        String query = intent.getStringExtra(EXTRA_SEARCH_QUERY);
        intent.removeExtra(EXTRA_SEARCH_QUERY);
        if (!PlaybackActions.playFromSearch(controller, MusicRepository.getSongsImmediate(), query)) {
            Toast.makeText(this, getString(R.string.library_empty_search, query), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(progressUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(progressUpdater);
    }

    @Override
    protected void onStop() {
        super.onStop();
        connection.disconnect();
    }

    // ------------------------------------------------------------------ setup

    private void setupButtons() {
        findViewById(R.id.btnBackToLibrary).setOnClickListener(view -> goBackToLibrary());
        findViewById(R.id.btnMoreOptions).setOnClickListener(view -> showOptions());
        findViewById(R.id.btnQueue).setOnClickListener(view -> {
            MediaController controller = connection.get();
            if (controller != null) {
                QueueSheet.show(this, controller);
            }
        });
        findViewById(R.id.btnLyrics).setOnClickListener(view -> openLyrics());
        btnSpeed.setOnClickListener(view -> showSpeedDialog());

        btnPlayPause.setOnClickListener(view -> PlaybackActions.togglePlayPause(connection.get()));
        btnPrevious.setOnClickListener(view -> {
            MediaController controller = connection.get();
            if (controller != null) controller.seekToPrevious();
        });
        btnNext.setOnClickListener(view -> {
            MediaController controller = connection.get();
            if (controller != null) controller.seekToNext();
        });
        btnShuffle.setOnClickListener(view -> PlaybackActions.toggleShuffle(connection.get()));
        btnRepeat.setOnClickListener(view -> PlaybackActions.cycleRepeat(connection.get()));
        btnFavorite.setOnClickListener(view -> {
            Song song = PlaybackActions.currentSong(connection.get());
            if (song != null) {
                PlaylistRepository.toggleFavorite(this, song.getUri(), nowFavorite -> {
                    if (song.getUri().equals(displayedUri)) {
                        setFavorite(nowFavorite);
                    }
                });
            }
        });
    }

    private void setupSeekBar() {
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(Formatters.position(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                MediaController controller = connection.get();
                if (controller != null) {
                    controller.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void setupCoverGestures() {
        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_DISTANCE = 120;
            private static final int SWIPE_VELOCITY = 120;

            @Override
            public boolean onDown(@NonNull MotionEvent event) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent event) {
                btnPlayPause.performClick();
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent down, @NonNull MotionEvent move, float velocityX, float velocityY) {
                if (down == null) {
                    return false;
                }
                float diffX = move.getX() - down.getX();
                float diffY = move.getY() - down.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_DISTANCE && Math.abs(velocityX) > SWIPE_VELOCITY) {
                        (diffX < 0 ? btnNext : btnPrevious).performClick();
                        return true;
                    }
                } else if (diffY > SWIPE_DISTANCE && Math.abs(velocityY) > SWIPE_VELOCITY) {
                    finishAfterTransition();
                    return true;
                }
                return false;
            }
        });

        View.OnTouchListener touch = (view, event) -> {
            boolean handled = detector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP && !handled) {
                view.performClick();
            }
            return true;
        };
        imgAlbumArt.setOnTouchListener(touch);
    }

    // ------------------------------------------------------------------ rendering

    private void updatePlayerView() {
        MediaController controller = connection.get();
        Song song = PlaybackActions.currentSong(controller);

        if (controller == null || song == null) {
            tvPlayerTitle.setText(R.string.player_no_song_title);
            tvPlayerArtist.setText(R.string.player_no_song_artist);
            tvDuration.setText(Formatters.position(0));
            tvCurrentTime.setText(Formatters.position(0));
            seekBarProgress.setProgress(0);
            tvAudioInfo.setVisibility(View.GONE);
            btnFavorite.setVisibility(View.INVISIBLE);
            showCover(null, null);
            displayedUri = null;
            NowPlayingAccent.update(this, null);
            setPlayingState(false);
            return;
        }

        showSongInfo(song);

        setPlayingState(controller.getPlayWhenReady()
                && controller.getPlaybackState() != Player.STATE_ENDED
                && controller.getPlaybackState() != Player.STATE_IDLE);
        updateModeButtons(controller);
        btnSpeed.setText(getString(R.string.speed_value, Formatters.speed(controller.getPlaybackParameters().speed)));
        updateProgress();
    }

    private void showSongInfo(Song song) {
        tvPlayerTitle.setText(song.getTitle());
        tvPlayerArtist.setText(song.getArtist());
        btnFavorite.setVisibility(View.VISIBLE);
        if (!song.getUri().equals(displayedUri)) {
            displayedUri = song.getUri();
            onSongChanged(song);
        }
    }

    private void onSongChanged(Song song) {
        String uri = song.getUri();
        NowPlayingAccent.update(this, song);

        // Instant first frame (cached thumbnail or generated cover), then the full-size artwork.
        showCover(AlbumArtLoader.getCached(song, AlbumArtLoader.SIZE_THUMB), song);
        AlbumArtLoader.load(this, song, AlbumArtLoader.SIZE_LARGE, bitmap -> {
            if (uri.equals(displayedUri) && bitmap != null) {
                showCover(bitmap, song);
            }
        });

        tvAudioInfo.setVisibility(View.GONE);
        AppExecutors.io().execute(() -> {
            String info = AudioInfo.describe(this, Uri.parse(uri));
            AppExecutors.main(() -> {
                if (uri.equals(displayedUri) && info != null) {
                    tvAudioInfo.setText(info);
                    tvAudioInfo.setVisibility(View.VISIBLE);
                }
            });
        });

        setFavorite(false);
        PlaylistRepository.isFavorite(this, uri, isFavorite -> {
            if (uri.equals(displayedUri)) {
                setFavorite(isFavorite);
            }
        });
    }

    /** Shows the artwork (or the album's generated cover) and its blurred copy behind the player. */
    private void showCover(@Nullable Bitmap bitmap, @Nullable Song song) {
        String seed = song != null ? AlbumArtLoader.albumSeed(song) : null;
        String label = song != null ? song.getAlbum() : null;
        if (bitmap != null) {
            imgAlbumArt.setImageBitmap(bitmap);
        } else {
            imgAlbumArt.setImageDrawable(new GeneratedCoverDrawable(seed, label));
        }
        Bitmap backdrop = bitmap != null ? bitmap : GeneratedCoverDrawable.toBitmap(seed, label, 128);
        boolean firstImage = imgBackdrop.getDrawable() == null;
        imgBackdrop.setImageBitmap(backdrop);
        imgBackdrop.animate().cancel();
        if (firstImage) {
            imgBackdrop.setAlpha(0f);
        }
        imgBackdrop.animate().alpha(1f).setDuration(400).start();
    }

    /** Pause/play icon, plus the cover and play button animations, when the state changes. */
    private void setPlayingState(boolean playing) {
        setPlayPauseIcon(playing);
        if (lastPlaying != null && lastPlaying == playing) {
            return;
        }
        boolean animate = lastPlaying != null;
        lastPlaying = playing;

        float scale = playing ? 1f : COVER_SCALE_PAUSED;
        frameCover.animate().cancel();
        if (animate) {
            frameCover.animate()
                    .scaleX(scale).scaleY(scale)
                    .setDuration(playing ? 420 : 300)
                    .setInterpolator(playing ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator())
                    .start();
        } else {
            frameCover.setScaleX(scale);
            frameCover.setScaleY(scale);
        }

        float targetRadius = playing ? dp(22) : dp(37);
        if (shapeAnimator != null) {
            shapeAnimator.cancel();
        }
        if (animate) {
            shapeAnimator = ValueAnimator.ofFloat(playButtonShape.getCornerRadius(), targetRadius);
            shapeAnimator.setDuration(320);
            shapeAnimator.setInterpolator(new OvershootInterpolator(1.2f));
            shapeAnimator.addUpdateListener(a -> playButtonShape.setCornerRadius((float) a.getAnimatedValue()));
            shapeAnimator.start();
        } else {
            playButtonShape.setCornerRadius(targetRadius);
        }
    }

    private void animateAccent(int target) {
        if (accentAnimator != null) {
            accentAnimator.cancel();
        }
        accentAnimator = ValueAnimator.ofArgb(accentColor, target);
        accentAnimator.setDuration(ACCENT_ANIMATION_MS);
        accentAnimator.addUpdateListener(a -> applyAccent((int) a.getAnimatedValue()));
        accentAnimator.start();
    }

    /** Colors every accent-tinted control: play button, progress, active modes, favorite, timer. */
    private void applyAccent(int color) {
        accentColor = color;
        onAccentColor = AccentMath.onColor(color);
        playButtonShape.setColor(color);
        btnPlayPause.setImageTintList(ColorStateList.valueOf(onAccentColor));
        ColorStateList accentList = ColorStateList.valueOf(color);
        seekBarProgress.setProgressTintList(accentList);
        seekBarProgress.setThumbTintList(accentList);
        tvSleepStatus.setTextColor(color);
        tvSleepStatus.setCompoundDrawableTintList(accentList);
        MediaController controller = connection != null ? connection.get() : null;
        if (controller != null) {
            updateModeButtons(controller);
        }
        setFavorite(favorite);
    }

    /** Active shuffle/repeat background: translucent accent fill with an accent outline. */
    private GradientDrawable activeModeBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor((accentColor & 0x00FFFFFF) | 0x33000000);
        shape.setStroke(Math.round(dp(1)), accentColor);
        return shape;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void setPlayPauseIcon(boolean playing) {
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnPlayPause.setContentDescription(getString(playing ? R.string.cd_pause : R.string.cd_play));
    }

    private void setFavorite(boolean isFavorite) {
        favorite = isFavorite;
        btnFavorite.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        int color = isFavorite ? accentColor : getColor(R.color.app_text_secondary);
        btnFavorite.setImageTintList(ColorStateList.valueOf(color));
        btnFavorite.setContentDescription(getString(isFavorite
                ? R.string.action_favorite_remove : R.string.action_favorite_add));
    }

    private void updateModeButtons(MediaController controller) {
        int accent = accentColor;
        int inactive = getColor(R.color.app_text_secondary);

        boolean shuffle = controller.getShuffleModeEnabled();
        btnShuffle.setImageTintList(ColorStateList.valueOf(shuffle ? accent : inactive));
        if (shuffle) {
            btnShuffle.setBackground(activeModeBackground());
        } else {
            btnShuffle.setBackgroundResource(R.drawable.bg_icon_button_secondary);
        }
        btnShuffle.setContentDescription(getString(shuffle ? R.string.cd_shuffle_on : R.string.cd_shuffle_off));

        int repeat = controller.getRepeatMode();
        boolean repeatOn = repeat != Player.REPEAT_MODE_OFF;
        btnRepeat.setImageResource(repeat == Player.REPEAT_MODE_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        btnRepeat.setImageTintList(ColorStateList.valueOf(repeatOn ? accent : inactive));
        if (repeatOn) {
            btnRepeat.setBackground(activeModeBackground());
        } else {
            btnRepeat.setBackgroundResource(R.drawable.bg_icon_button_secondary);
        }
        btnRepeat.setContentDescription(getString(repeat == Player.REPEAT_MODE_ONE
                ? R.string.cd_repeat_one
                : repeat == Player.REPEAT_MODE_ALL ? R.string.cd_repeat_all : R.string.cd_repeat_off));
    }

    private void updateProgress() {
        MediaController controller = connection.get();
        if (controller == null) {
            return;
        }
        long duration = controller.getDuration();
        long position = controller.getCurrentPosition();
        if (duration > 0) {
            seekBarProgress.setMax((int) duration);
            if (!userSeeking) {
                seekBarProgress.setProgress((int) position);
                tvCurrentTime.setText(Formatters.position(position));
            }
            tvDuration.setText(Formatters.position(duration));
        }
    }

    private void updateSleepStatus() {
        if (!SleepTimer.isActive()) {
            tvSleepStatus.setVisibility(View.GONE);
            return;
        }
        tvSleepStatus.setVisibility(View.VISIBLE);
        tvSleepStatus.setText(SleepTimer.isEndOfTrack()
                ? getString(R.string.sleep_remaining_end_of_track)
                : getString(R.string.sleep_remaining, Formatters.position(SleepTimer.getRemainingMs())));
    }

    // ------------------------------------------------------------------ options

    private void showOptions() {
        Song song = PlaybackActions.currentSong(connection.get());
        if (song == null) {
            return;
        }
        SongActionsSheet.show(this, song, this, -1, false, sheet -> {
            sheet.add(R.drawable.ic_equalizer, getString(R.string.action_equalizer),
                    () -> startActivity(new Intent(this, EqualizerActivity.class)));
            sheet.add(R.drawable.ic_timer, getString(R.string.action_sleep_timer), this::showSleepTimerDialog);
            sheet.add(R.drawable.ic_lyrics, getString(R.string.action_lyrics), this::openLyrics);
            sheet.add(R.drawable.ic_speed, getString(R.string.action_speed), this::showSpeedDialog);
        });
    }

    private void openLyrics() {
        startActivity(new Intent(this, LyricsActivity.class));
    }

    private void showSpeedDialog() {
        MediaController controller = connection.get();
        if (controller == null) {
            return;
        }
        float current = controller.getPlaybackParameters().speed;
        String[] labels = new String[SPEEDS.length];
        int checked = 2;
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = getString(R.string.speed_value, Formatters.speed(SPEEDS[i]));
            if (Math.abs(SPEEDS[i] - current) < 0.01f) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_speed)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    MediaController c = connection.get();
                    if (c != null) {
                        c.setPlaybackParameters(new PlaybackParameters(SPEEDS[which]));
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showSleepTimerDialog() {
        int[] minutes = {15, 30, 45, 60};
        int extra = SleepTimer.isActive() ? 2 : 1;
        String[] options = new String[minutes.length + extra];
        for (int i = 0; i < minutes.length; i++) {
            options[i] = getResources().getQuantityString(R.plurals.minutes, minutes[i], minutes[i]);
        }
        options[minutes.length] = getString(R.string.sleep_end_of_track);
        if (SleepTimer.isActive()) {
            options[minutes.length + 1] = getString(R.string.sleep_cancel);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sleep_title)
                .setItems(options, (dialog, which) -> {
                    if (which < minutes.length) {
                        SleepTimer.start(minutes[which]);
                        Toast.makeText(this, getString(R.string.sleep_set, options[which]), Toast.LENGTH_SHORT).show();
                    } else if (which == minutes.length) {
                        if (SleepTimer.startEndOfTrack()) {
                            Toast.makeText(this, R.string.sleep_set_end_of_track, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        SleepTimer.cancel();
                        Toast.makeText(this, R.string.sleep_cancelled, Toast.LENGTH_SHORT).show();
                    }
                    updateSleepStatus();
                })
                .show();
    }

    private void goBackToLibrary() {
        if (isTaskRoot()) {
            // Opened from the notification or widget with nothing underneath.
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
            return;
        }
        finishAfterTransition();
    }

    // ------------------------------------------------------------------ SongActionsSheet.Host

    @Nullable
    @Override
    public Player player() {
        return connection.get();
    }

    @Override
    public SongDeleteHelper deleteHelper() {
        return deleteHelper;
    }

    @Override
    public RingtoneSetter ringtoneSetter() {
        return ringtoneSetter;
    }

    @Override
    public void openGroupOf(Song song, String type) {
        Intent intent = new Intent(this, LibraryActivity.class)
                .putExtra(LibraryActivity.EXTRA_OPEN_GROUP_TYPE, type)
                .putExtra(LibraryActivity.EXTRA_OPEN_GROUP_SONG_URI, song.getUri())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
