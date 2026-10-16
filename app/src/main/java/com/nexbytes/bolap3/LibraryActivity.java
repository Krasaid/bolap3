package com.nexbytes.bolap3;

import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.nexbytes.bolap3.adapter.GroupAdapter;
import com.nexbytes.bolap3.adapter.HomeAdapter;
import com.nexbytes.bolap3.adapter.SongAdapter;
import com.nexbytes.bolap3.library.LibraryViewModel;
import com.nexbytes.bolap3.library.SongSort;
import com.nexbytes.bolap3.media.AccentMath;
import com.nexbytes.bolap3.media.AlbumArtLoader;
import com.nexbytes.bolap3.media.NowPlayingAccent;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.HomeSection;
import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.playback.PlaybackActions;
import com.nexbytes.bolap3.playback.PlayerConnection;
import com.nexbytes.bolap3.scan.LibraryAutoRefresh;
import com.nexbytes.bolap3.ui.BaseActivity;
import com.nexbytes.bolap3.ui.PlaylistDialogs;
import com.nexbytes.bolap3.ui.SongActionsSheet;
import com.nexbytes.bolap3.ui.RingtoneSetter;
import com.nexbytes.bolap3.ui.SongDeleteHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class LibraryActivity extends BaseActivity implements SongActionsSheet.Host {

    /** Extras to open the album/artist of a song (sent by the player screen). */
    public static final String EXTRA_OPEN_GROUP_TYPE = "open_group_type";
    public static final String EXTRA_OPEN_GROUP_SONG_URI = "open_group_song_uri";

    private static final long MINI_PROGRESS_INTERVAL_MS = 1000;
    private static boolean refreshedThisProcess = false;

    private LibraryViewModel viewModel;
    private PlayerConnection connection;
    private SongDeleteHelper deleteHelper;
    private RingtoneSetter ringtoneSetter;
    private final List<Consumer<MediaController>> pendingActions = new ArrayList<>();

    private RecyclerView rvSongs;
    private TextView tvEmpty;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvSectionBack;
    private TextView tvSectionCount;
    private ImageButton btnSort;
    private ImageButton btnPlayAll;
    private ImageButton btnShuffleAll;
    private ImageButton btnPlaylistNew;
    private ImageButton btnLayoutToggle;
    private ImageButton btnReorder;
    private View titleRow;
    private View selectionTitleRow;
    private TextView tvSelectionCount;
    private View selectionActions;
    private OnBackPressedCallback selectionCallback;
    private ItemTouchHelper reorderHelper;
    private boolean dragging = false;
    /** Tab + opened group last rendered; selection and reorder mode end when it changes. */
    @Nullable
    private String renderedListKey;
    private TextView[] tabs;

    private View miniPlayer;
    private ImageView imgMiniCover;
    private TextView tvMiniTitle;
    private TextView tvMiniArtist;
    private ImageButton btnMiniPlayPause;
    private LinearProgressIndicator progressMini;
    @Nullable
    private String miniCoverUri;

    private SongAdapter songAdapter;
    private GroupAdapter groupAdapter;
    private HomeAdapter homeAdapter;
    private LinearLayoutManager listLayoutManager;
    private GridLayoutManager gridLayoutManager;
    @Nullable
    private LibraryViewModel.ViewState currentState;
    private OnBackPressedCallback closeGroupCallback;
    /** Accent taken from the current song's artwork (or the theme accent). */
    private int accentColor;
    @Nullable
    private ValueAnimator accentAnimator;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable miniProgressUpdater = new Runnable() {
        @Override
        public void run() {
            updateMiniProgress();
            handler.postDelayed(this, MINI_PROGRESS_INTERVAL_MS);
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
            updateMiniPlayer();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        connection = new PlayerConnection(this, playerListener);
        deleteHelper = new SongDeleteHelper(this, connection::get);
        ringtoneSetter = new RingtoneSetter(this);

        bindViews();
        setupList();
        setupTabs();
        setupSearch();
        setupHeader();
        setupMiniPlayer();

        closeGroupCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewModel.closeGroup();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeGroupCallback);
        // Added last, so it runs first: back leaves the selection before closing the group.
        selectionCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                songAdapter.clearSelection();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, selectionCallback);
        setupSelectionActions();

        viewModel.getState().observe(this, this::render);

        int themeAccent = MaterialColors.getColor(btnPlayAll, androidx.appcompat.R.attr.colorPrimary);
        applyAccent(themeAccent);
        NowPlayingAccent.get().observe(this, color -> animateAccent(color != null ? color : themeAccent));

        if (savedInstanceState == null) {
            handleOpenGroupIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenGroupIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.connect(controller -> {
            for (Consumer<MediaController> action : pendingActions) {
                action.accept(controller);
            }
            pendingActions.clear();
            updateMiniPlayer();
        });
        handler.post(miniProgressUpdater);

        if (!refreshedThisProcess) {
            refreshedThisProcess = true;
            LibraryAutoRefresh.requestRefresh();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(miniProgressUpdater);
        connection.disconnect();
    }

    // ------------------------------------------------------------------ setup

    private void bindViews() {
        rvSongs = findViewById(R.id.rvSongs);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        tvSectionBack = findViewById(R.id.tvSectionBack);
        tvSectionCount = findViewById(R.id.tvSectionCount);
        btnSort = findViewById(R.id.btnSort);
        btnPlayAll = findViewById(R.id.btnPlayAll);
        btnShuffleAll = findViewById(R.id.btnShuffleAll);
        btnPlaylistNew = findViewById(R.id.btnPlaylistNew);
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle);
        btnReorder = findViewById(R.id.btnReorder);
        titleRow = findViewById(R.id.titleRow);
        selectionTitleRow = findViewById(R.id.selectionTitleRow);
        tvSelectionCount = findViewById(R.id.tvSelectionCount);
        selectionActions = findViewById(R.id.selectionActions);
        tabs = new TextView[]{
                findViewById(R.id.tabHome),
                findViewById(R.id.tabSongs),
                findViewById(R.id.tabAlbums),
                findViewById(R.id.tabArtists),
                findViewById(R.id.tabFolders),
                findViewById(R.id.tabPlaylists)
        };

        miniPlayer = findViewById(R.id.miniPlayer);
        imgMiniCover = miniPlayer.findViewById(R.id.imgMiniCover);
        tvMiniTitle = miniPlayer.findViewById(R.id.tvMiniTitle);
        tvMiniArtist = miniPlayer.findViewById(R.id.tvMiniArtist);
        btnMiniPlayPause = miniPlayer.findViewById(R.id.btnMiniPlayPause);
        progressMini = miniPlayer.findViewById(R.id.progressMini);

        findViewById(R.id.btnSettings).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void setupList() {
        listLayoutManager = new LinearLayoutManager(this);
        // About 170dp per column: 2 on phones, more on tablets and in landscape.
        int columns = Math.max(2, getResources().getConfiguration().screenWidthDp / 170);
        gridLayoutManager = new GridLayoutManager(this, columns);
        rvSongs.setLayoutManager(listLayoutManager);
        rvSongs.setHasFixedSize(true);

        homeAdapter = new HomeAdapter(new HomeAdapter.Listener() {
            @Override
            public void onSongClick(HomeSection section, int index) {
                if (index < 0 || index >= section.songs.size()) {
                    return;
                }
                List<Song> queue = new ArrayList<>(section.songs);
                withController(controller -> PlaybackActions.playSongs(controller, queue, index));
                openPlayer(queue.get(index), false);
            }

            @Override
            public void onSongLongClick(Song song) {
                SongActionsSheet.show(LibraryActivity.this, song, LibraryActivity.this, -1, true, null);
            }

            @Override
            public void onGroupClick(GroupItem group) {
                viewModel.openGroup(group);
                rvSongs.scrollToPosition(0);
            }
        });

        songAdapter = new SongAdapter(new SongAdapter.OnSongClickListener() {
            @Override
            public void onSongClick(Song song, int position) {
                if (currentState == null) {
                    return;
                }
                // The visible list (album, playlist, search results...) becomes the queue.
                List<Song> queue = new ArrayList<>(currentState.songs);
                withController(controller -> PlaybackActions.playSongs(controller, queue, position));
                openPlayer(song, false);
            }

            @Override
            public void onSongOptionsClick(Song song) {
                GroupItem open = currentState != null ? currentState.openGroup : null;
                long playlistId = open != null && open.isUserPlaylist() ? open.getPlaylistId() : -1;
                SongActionsSheet.show(LibraryActivity.this, song, LibraryActivity.this, playlistId, true, null);
            }

            @Override
            public void onSelectionChanged(int count) {
                renderSelection(count);
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder holder) {
                reorderHelper.startDrag(holder);
            }
        });
        setupReorder();

        groupAdapter = new GroupAdapter(new GroupAdapter.OnGroupClickListener() {
            @Override
            public void onGroupClick(GroupItem group) {
                viewModel.openGroup(group);
                rvSongs.scrollToPosition(0);
            }

            @Override
            public void onGroupOptionsClick(GroupItem group) {
                PlaylistDialogs.showPlaylistOptions(LibraryActivity.this, group, () -> { });
            }
        });
    }

    private void setupTabs() {
        LibraryViewModel.Tab[] values = LibraryViewModel.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            LibraryViewModel.Tab tab = values[i];
            tabs[i].setOnClickListener(view -> {
                viewModel.selectTab(tab);
                rvSongs.scrollToPosition(0);
            });
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                viewModel.setQuery(s.toString());
            }
        });
        btnClearSearch.setOnClickListener(view -> etSearch.setText(""));
    }

    private void setupHeader() {
        tvSectionBack.setOnClickListener(view -> viewModel.closeGroup());
        btnSort.setOnClickListener(view -> showSortDialog());
        btnPlayAll.setOnClickListener(view -> {
            if (currentState != null && !currentState.songs.isEmpty()) {
                List<Song> queue = new ArrayList<>(currentState.songs);
                withController(controller -> {
                    controller.setShuffleModeEnabled(false);
                    PlaybackActions.playSongs(controller, queue, 0);
                });
                openPlayer(queue.get(0), false);
            }
        });
        btnShuffleAll.setOnClickListener(view -> {
            if (currentState != null && !currentState.songs.isEmpty()) {
                List<Song> queue = new ArrayList<>(currentState.songs);
                withController(controller -> PlaybackActions.shuffleSongs(controller, queue));
                openPlayer(null, false);
            }
        });
        btnPlaylistNew.setOnClickListener(view -> PlaylistDialogs.createPlaylist(this));
        btnLayoutToggle.setOnClickListener(view -> {
            SettingsPrefs.setGroupsAsGrid(this, !SettingsPrefs.isGroupsAsGrid(this));
            if (currentState != null) {
                render(currentState);
            }
        });
    }

    private void setupMiniPlayer() {
        miniPlayer.findViewById(R.id.miniPlayerBody).setOnClickListener(view ->
                openPlayer(PlaybackActions.currentSong(connection.get()), true));
        btnMiniPlayPause.setOnClickListener(view -> PlaybackActions.togglePlayPause(connection.get()));
        miniPlayer.findViewById(R.id.btnMiniNext).setOnClickListener(view -> {
            MediaController controller = connection.get();
            if (controller != null) {
                controller.seekToNext();
            }
        });
    }

    // ------------------------------------------------------------------ rendering

    private void render(LibraryViewModel.ViewState state) {
        currentState = state;
        renderTabs(state.tab);

        String listKey = state.tab + "|" + (state.openGroup != null ? state.openGroup.getKey() : "");
        if (!Objects.equals(listKey, renderedListKey)) {
            renderedListKey = listKey;
            songAdapter.clearSelection();
            songAdapter.setReorderMode(false);
        }
        boolean canReorder = state.openGroup != null && state.openGroup.isUserPlaylist()
                && state.query.trim().isEmpty() && state.songs.size() > 1;
        if (!canReorder && songAdapter.isReorderMode()) {
            songAdapter.setReorderMode(false);
        }
        btnReorder.setVisibility(canReorder ? View.VISIBLE : View.GONE);
        btnReorder.setImageResource(songAdapter.isReorderMode() ? R.drawable.ic_check : R.drawable.ic_drag_handle);
        btnReorder.setContentDescription(getString(songAdapter.isReorderMode()
                ? R.string.playlist_reorder_done : R.string.playlist_reorder));

        boolean groupOpen = state.openGroup != null;
        closeGroupCallback.setEnabled(groupOpen);

        tvSectionBack.setVisibility(groupOpen ? View.VISIBLE : View.GONE);
        if (groupOpen) {
            tvSectionBack.setText(getString(R.string.library_back_to, state.openGroup.getTitle()));
            tvSectionBack.setContentDescription(getString(R.string.action_back));
        }

        boolean songsVisible = state.showingSongs && !state.songs.isEmpty();
        tvSectionCount.setVisibility(state.showingSongs ? View.VISIBLE : View.GONE);
        tvSectionCount.setText(getResources().getQuantityString(R.plurals.song_count,
                state.songs.size(), state.songs.size()));
        btnSort.setVisibility(state.tab == LibraryViewModel.Tab.SONGS && !groupOpen ? View.VISIBLE : View.GONE);
        btnPlayAll.setVisibility(songsVisible && !songAdapter.isReorderMode() ? View.VISIBLE : View.GONE);
        btnShuffleAll.setVisibility(songsVisible && !songAdapter.isReorderMode() ? View.VISIBLE : View.GONE);
        boolean newPlaylistVisible = state.tab == LibraryViewModel.Tab.PLAYLISTS && !groupOpen;
        btnPlaylistNew.setVisibility(newPlaylistVisible ? View.VISIBLE : View.GONE);

        // Albums, artists and folders can be shown as a grid of covers.
        boolean gridCapable = !state.showingSongs && !state.showingHome && !groupOpen
                && state.tab != LibraryViewModel.Tab.PLAYLISTS;
        boolean grid = gridCapable && SettingsPrefs.isGroupsAsGrid(this);
        btnLayoutToggle.setVisibility(gridCapable ? View.VISIBLE : View.GONE);
        btnLayoutToggle.setImageResource(grid ? R.drawable.ic_view_list : R.drawable.ic_grid_view);
        btnLayoutToggle.setContentDescription(getString(grid ? R.string.library_view_list : R.string.library_view_grid));

        findViewById(R.id.sectionHeader).setVisibility(
                groupOpen || state.showingSongs || newPlaylistVisible || gridCapable ? View.VISIBLE : View.GONE);

        RecyclerView.LayoutManager wanted = grid ? gridLayoutManager : listLayoutManager;
        if (rvSongs.getLayoutManager() != wanted) {
            rvSongs.setLayoutManager(wanted);
        }

        if (state.showingHome) {
            if (rvSongs.getAdapter() != homeAdapter) {
                rvSongs.setAdapter(homeAdapter);
            }
            homeAdapter.submit(state.home, state.librarySize);
        } else if (state.showingSongs) {
            if (rvSongs.getAdapter() != songAdapter) {
                rvSongs.setAdapter(songAdapter);
            }
            if (!dragging) {
                songAdapter.submitList(state.songs);
            }
        } else {
            groupAdapter.setGridMode(grid);
            if (rvSongs.getAdapter() != groupAdapter) {
                rvSongs.setAdapter(groupAdapter);
            }
            groupAdapter.submitList(state.groups);
        }

        boolean empty = !state.loading && (state.showingHome
                ? state.home.isEmpty()
                : state.showingSongs ? state.songs.isEmpty() : state.groups.isEmpty());
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvEmpty.setText(state.showingHome
                ? getString(R.string.home_empty)
                : state.query.trim().isEmpty()
                ? getString(R.string.library_empty_list)
                : getString(R.string.library_empty_search, state.query.trim()));

        updateCurrentHighlight();
    }

    private void renderTabs(LibraryViewModel.Tab active) {
        int onAccent = AccentMath.onColor(accentColor);
        int secondary = getColor(R.color.app_text_secondary);
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = LibraryViewModel.Tab.values()[i] == active;
            tabs[i].setBackgroundResource(selected ? R.drawable.bg_primary_button : R.drawable.bg_secondary_button);
            tabs[i].setBackgroundTintList(selected ? ColorStateList.valueOf(accentColor) : null);
            tabs[i].setTextColor(selected ? onAccent : secondary);
            tabs[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setSelected(selected);
        }
    }

    private void updateMiniPlayer() {
        MediaController controller = connection.get();
        Song song = PlaybackActions.currentSong(controller);

        NowPlayingAccent.update(this, song);
        if (song == null || songAdapter.isSelectionMode()) {
            miniPlayer.setVisibility(View.GONE);
            updateCurrentHighlight();
            return;
        }

        miniPlayer.setVisibility(View.VISIBLE);
        tvMiniTitle.setText(song.getTitle());
        tvMiniArtist.setText(song.getArtist());

        if (!song.getUri().equals(miniCoverUri)) {
            miniCoverUri = song.getUri();
            AlbumArtLoader.bindSong(imgMiniCover, song);
        }

        boolean playing = controller.isPlaying();
        btnMiniPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        btnMiniPlayPause.setContentDescription(getString(playing ? R.string.cd_pause : R.string.cd_play));
        updateMiniProgress();
        updateCurrentHighlight();
    }

    private void updateMiniProgress() {
        MediaController controller = connection.get();
        if (controller == null || miniPlayer.getVisibility() != View.VISIBLE) {
            return;
        }
        long duration = controller.getDuration();
        if (duration > 0) {
            progressMini.setMax(1000);
            progressMini.setProgressCompat((int) (controller.getCurrentPosition() * 1000 / duration), false);
        } else {
            progressMini.setProgressCompat(0, false);
        }
    }

    private void updateCurrentHighlight() {
        Song current = PlaybackActions.currentSong(connection.get());
        songAdapter.setCurrentUri(current != null ? current.getUri() : null);
    }

    // ------------------------------------------------------------------ actions

    // ------------------------------------------------------------------ selection & reorder

    private void renderSelection(int count) {
        boolean selecting = count > 0;
        selectionCallback.setEnabled(selecting);
        titleRow.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionTitleRow.setVisibility(selecting ? View.VISIBLE : View.GONE);
        selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        tvSelectionCount.setText(getResources().getQuantityString(R.plurals.selection_count, count, count));
        updateMiniPlayer();
    }

    private void setupSelectionActions() {
        findViewById(R.id.btnSelectionClose).setOnClickListener(view -> songAdapter.clearSelection());
        findViewById(R.id.btnSelectAll).setOnClickListener(view -> songAdapter.selectAll());

        findViewById(R.id.actionSelPlay).setOnClickListener(view -> {
            List<Song> songs = songAdapter.getSelectedSongs();
            if (songs.isEmpty()) return;
            withController(controller -> {
                controller.setShuffleModeEnabled(false);
                PlaybackActions.playSongs(controller, songs, 0);
            });
            songAdapter.clearSelection();
            openPlayer(songs.get(0), false);
        });
        findViewById(R.id.actionSelQueue).setOnClickListener(view -> {
            List<Song> songs = songAdapter.getSelectedSongs();
            if (songs.isEmpty()) return;
            withController(controller -> PlaybackActions.addToQueue(controller, songs));
            android.widget.Toast.makeText(this, getResources().getQuantityString(
                    R.plurals.added_many_to_queue, songs.size(), songs.size()), android.widget.Toast.LENGTH_SHORT).show();
            songAdapter.clearSelection();
        });
        findViewById(R.id.actionSelPlaylist).setOnClickListener(view -> {
            List<Song> songs = songAdapter.getSelectedSongs();
            if (songs.isEmpty()) return;
            PlaylistDialogs.pickAndAdd(this, songs);
            songAdapter.clearSelection();
        });
        findViewById(R.id.actionSelFavorite).setOnClickListener(view -> {
            List<Song> songs = songAdapter.getSelectedSongs();
            if (songs.isEmpty()) return;
            List<String> uris = new ArrayList<>();
            for (Song song : songs) {
                uris.add(song.getUri());
            }
            PlaylistRepository.addFavorites(this, uris, () -> android.widget.Toast.makeText(this,
                    getResources().getQuantityString(R.plurals.favorites_added_many, uris.size(), uris.size()),
                    android.widget.Toast.LENGTH_SHORT).show());
            songAdapter.clearSelection();
        });
        findViewById(R.id.actionSelDelete).setOnClickListener(view -> {
            List<Song> songs = songAdapter.getSelectedSongs();
            if (songs.isEmpty()) return;
            deleteHelper.confirmAndDelete(songs);
            songAdapter.clearSelection();
        });
    }

    /** Drag by the handle to reorder a user playlist; the order is saved when the drag ends. */
    private void setupReorder() {
        btnReorder.setOnClickListener(view -> {
            songAdapter.setReorderMode(!songAdapter.isReorderMode());
            if (currentState != null) {
                render(currentState);
            }
        });
        reorderHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private boolean moved = false;

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                return rvSongs.getAdapter() == songAdapter && songAdapter.isReorderMode()
                        ? super.getDragDirs(recyclerView, holder) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                dragging = true;
                moved = true;
                songAdapter.moveItem(from.getBindingAdapterPosition(), to.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(recyclerView, holder);
                dragging = false;
                GroupItem open = currentState != null ? currentState.openGroup : null;
                if (moved && open != null && open.isUserPlaylist()) {
                    List<String> order = new ArrayList<>();
                    for (Song song : songAdapter.getCurrentList()) {
                        order.add(song.getUri());
                    }
                    PlaylistRepository.reorderPlaylist(LibraryActivity.this, open.getPlaylistId(), order);
                }
                moved = false;
            }
        });
        reorderHelper.attachToRecyclerView(rvSongs);
    }

    private void showSortDialog() {
        SongSort[] sorts = SongSort.values();
        String[] labels = {
                getString(R.string.sort_title),
                getString(R.string.sort_artist),
                getString(R.string.sort_album),
                getString(R.string.sort_duration),
                getString(R.string.sort_date_added)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(labels, viewModel.getSort().ordinal(), (dialog, which) -> {
                    viewModel.setSort(sorts[which]);
                    rvSongs.scrollToPosition(0);
                    dialog.dismiss();
                })
                .show();
    }

    private void handleOpenGroupIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_OPEN_GROUP_TYPE)) {
            return;
        }
        String type = intent.getStringExtra(EXTRA_OPEN_GROUP_TYPE);
        String uri = intent.getStringExtra(EXTRA_OPEN_GROUP_SONG_URI);
        intent.removeExtra(EXTRA_OPEN_GROUP_TYPE);

        Song song = com.nexbytes.bolap3.data.MusicRepository.findByUri(uri);
        if (song != null && type != null) {
            etSearch.setText("");
            viewModel.openGroupOf(song, type);
        }
    }

    private void withController(Consumer<MediaController> action) {
        MediaController controller = connection.get();
        if (controller != null) {
            action.accept(controller);
        } else {
            pendingActions.add(action);
        }
    }

    /**
     * @param song      shown immediately by the player (before its controller connects), may be null
     * @param fromMini  animate the mini player's cover into the player's cover
     */
    private void openPlayer(@Nullable Song song, boolean fromMini) {
        Intent intent = new Intent(this, PlayerActivity.class);
        if (song != null) {
            intent.putExtra(PlayerActivity.EXTRA_SONG_URI, song.getUri());
        }
        if (fromMini && miniPlayer.getVisibility() == View.VISIBLE) {
            intent.putExtra(PlayerActivity.EXTRA_SHARED_COVER, true);
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                    this, imgMiniCover, getString(R.string.transition_cover));
            startActivity(intent, options.toBundle());
        } else {
            startActivity(intent);
        }
    }

    private void animateAccent(int target) {
        if (accentAnimator != null) {
            accentAnimator.cancel();
        }
        accentAnimator = ValueAnimator.ofArgb(accentColor, target);
        accentAnimator.setDuration(450);
        accentAnimator.addUpdateListener(a -> applyAccent((int) a.getAnimatedValue()));
        accentAnimator.start();
    }

    /** Active tab, "play all" and the mini player follow the artwork color. */
    private void applyAccent(int color) {
        accentColor = color;
        ColorStateList accent = ColorStateList.valueOf(color);
        ColorStateList onAccent = ColorStateList.valueOf(AccentMath.onColor(color));
        btnPlayAll.setBackgroundTintList(accent);
        btnPlayAll.setImageTintList(onAccent);
        btnMiniPlayPause.setBackgroundTintList(accent);
        btnMiniPlayPause.setImageTintList(onAccent);
        progressMini.setIndicatorColor(color);
        if (currentState != null) {
            renderTabs(currentState.tab);
        }
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
        etSearch.setText("");
        viewModel.openGroupOf(song, type);
        rvSongs.scrollToPosition(0);
    }
}
