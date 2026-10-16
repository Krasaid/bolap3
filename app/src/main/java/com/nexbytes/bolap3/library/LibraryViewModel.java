package com.nexbytes.bolap3.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.data.PlaylistRepository;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.HomeSection;
import com.nexbytes.bolap3.model.Song;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State of the library screen: selected tab, opened album/artist/folder/playlist, search query
 * and sort order. Survives rotation; recomputes when the library or playlists change.
 */
public class LibraryViewModel extends AndroidViewModel {

    public enum Tab {
        HOME(null),
        SONGS(null),
        ALBUMS(GroupItem.TYPE_ALBUM),
        ARTISTS(GroupItem.TYPE_ARTIST),
        FOLDERS(GroupItem.TYPE_FOLDER),
        PLAYLISTS(null);

        @Nullable
        final String groupType;

        Tab(@Nullable String groupType) {
            this.groupType = groupType;
        }
    }

    public static final class ViewState {
        public final Tab tab;
        @Nullable
        public final GroupItem openGroup;
        /** True when {@link #songs} is shown, false when {@link #groups} (or Home) is shown. */
        public final boolean showingSongs;
        /** True when the Home carousels ({@link #home}) are shown. */
        public final boolean showingHome;
        public final List<Song> songs;
        public final List<GroupItem> groups;
        public final List<HomeSection> home;
        public final int librarySize;
        public final String query;
        public final boolean loading;

        ViewState(Tab tab, @Nullable GroupItem openGroup, boolean showingSongs, boolean showingHome,
                  List<Song> songs, List<GroupItem> groups, List<HomeSection> home, int librarySize,
                  String query, boolean loading) {
            this.tab = tab;
            this.openGroup = openGroup;
            this.showingSongs = showingSongs;
            this.showingHome = showingHome;
            this.songs = songs;
            this.groups = groups;
            this.home = home;
            this.librarySize = librarySize;
            this.query = query;
            this.loading = loading;
        }
    }

    private final MediatorLiveData<ViewState> state = new MediatorLiveData<>();

    private Tab tab = Tab.HOME;
    @Nullable
    private GroupItem openGroup;
    private String query = "";
    private SongSort sort;

    private List<Song> library = Collections.emptyList();
    private boolean libraryLoaded = false;
    @Nullable
    private List<Song> sortedLibrary;
    private final Map<String, List<GroupItem>> groupCache = new HashMap<>();
    @Nullable
    private List<GroupItem> playlistGroups;
    @Nullable
    private List<HomeSection> homeSections;
    @Nullable
    private List<Song> openPlaylistSongs;
    @Nullable
    private PendingOpen pendingOpen;

    private static final class PendingOpen {
        final Song song;
        final String type;

        PendingOpen(Song song, String type) {
            this.song = song;
            this.type = type;
        }
    }

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        sort = SettingsPrefs.getSortOrder(application);

        state.addSource(MusicRepository.songs(), songs -> {
            library = songs != null ? songs : Collections.emptyList();
            libraryLoaded = true;
            sortedLibrary = null;
            groupCache.clear();
            if (pendingOpen != null) {
                PendingOpen open = pendingOpen;
                pendingOpen = null;
                openGroupOf(open.song, open.type);
                return;
            }
            reloadPlaylistData();
            recompute();
        });
        state.addSource(PlaylistRepository.changes(), version -> reloadPlaylistData());

        MusicRepository.ensureLoaded(application);
    }

    public LiveData<ViewState> getState() {
        return state;
    }

    public SongSort getSort() {
        return sort;
    }

    public void selectTab(Tab newTab) {
        tab = newTab;
        openGroup = null;
        openPlaylistSongs = null;
        reloadPlaylistData();
        recompute();
    }

    public void openGroup(GroupItem group) {
        openGroup = group;
        openPlaylistSongs = null;
        if (group.isPlaylistLike()) {
            loadOpenPlaylist();
        }
        recompute();
    }

    /** @return true if a group was open (used for back navigation). */
    public boolean closeGroup() {
        if (openGroup == null) {
            return false;
        }
        openGroup = null;
        openPlaylistSongs = null;
        reloadPlaylistData();
        recompute();
        return true;
    }

    /** Opens the album or artist containing {@code song} (from the player's options). */
    public void openGroupOf(Song song, String type) {
        if (!libraryLoaded) {
            pendingOpen = new PendingOpen(song, type);
            return;
        }
        tab = GroupItem.TYPE_ARTIST.equals(type) ? Tab.ARTISTS : Tab.ALBUMS;
        query = "";
        String key = LibraryGrouping.keyFor(song, type);
        for (GroupItem group : groups(type)) {
            if (group.getKey().equals(key)) {
                openGroup(group);
                return;
            }
        }
        openGroup = null;
        recompute();
    }

    public void setQuery(String newQuery) {
        String normalized = newQuery == null ? "" : newQuery;
        if (normalized.equals(query)) {
            return;
        }
        query = normalized;
        recompute();
    }

    public void setSort(SongSort newSort) {
        sort = newSort;
        sortedLibrary = null;
        SettingsPrefs.setSortOrder(getApplication(), newSort);
        recompute();
    }

    private void reloadPlaylistData() {
        if (tab == Tab.HOME && openGroup == null) {
            HomeLoader.load(getApplication(), sections -> {
                homeSections = sections;
                recompute();
            });
        }
        if (tab == Tab.PLAYLISTS && openGroup == null) {
            PlaylistRepository.loadPlaylistGroups(getApplication(), groups -> {
                playlistGroups = groups;
                recompute();
            });
        }
        if (openGroup != null && openGroup.isPlaylistLike()) {
            loadOpenPlaylist();
        }
    }

    private void loadOpenPlaylist() {
        GroupItem requested = openGroup;
        if (requested == null) {
            return;
        }
        PlaylistRepository.loadSongs(getApplication(), requested, songs -> {
            if (openGroup != null && openGroup.getKey().equals(requested.getKey())) {
                openPlaylistSongs = songs;
                recompute();
            }
        });
    }

    private void recompute() {
        if (openGroup != null) {
            List<Song> songs;
            boolean loading = false;
            if (openGroup.isPlaylistLike()) {
                loading = openPlaylistSongs == null;
                songs = openPlaylistSongs != null ? openPlaylistSongs : Collections.emptyList();
            } else {
                songs = LibraryGrouping.songsOf(library, openGroup);
            }
            emit(true, SearchFilter.filterSongs(songs, query), Collections.emptyList(), loading);
            return;
        }

        switch (tab) {
            case HOME:
                if (!SearchFilter.isBlank(query)) {
                    // Searching from Home searches every song.
                    if (sortedLibrary == null) {
                        sortedLibrary = sort.sorted(library);
                    }
                    emit(true, SearchFilter.filterSongs(sortedLibrary, query), Collections.emptyList(), !libraryLoaded);
                } else {
                    emitHome();
                }
                break;
            case SONGS:
                if (sortedLibrary == null) {
                    sortedLibrary = sort.sorted(library);
                }
                emit(true, SearchFilter.filterSongs(sortedLibrary, query), Collections.emptyList(), !libraryLoaded);
                break;
            case PLAYLISTS:
                emit(false, Collections.emptyList(),
                        playlistGroups != null ? SearchFilter.filterGroups(playlistGroups, query) : Collections.emptyList(),
                        playlistGroups == null);
                break;
            default:
                emit(false, Collections.emptyList(),
                        SearchFilter.filterGroups(groups(tab.groupType), query), !libraryLoaded);
                break;
        }
    }

    private List<GroupItem> groups(String type) {
        List<GroupItem> cached = groupCache.get(type);
        if (cached == null) {
            cached = LibraryGrouping.group(library, type, getApplication().getString(R.string.unknown_group));
            groupCache.put(type, cached);
        }
        return cached;
    }

    private void emit(boolean showingSongs, List<Song> songs, List<GroupItem> groups, boolean loading) {
        state.setValue(new ViewState(tab, openGroup, showingSongs, false, songs, groups,
                Collections.emptyList(), library.size(), query, loading));
    }

    private void emitHome() {
        state.setValue(new ViewState(tab, openGroup, false, true, Collections.emptyList(),
                Collections.emptyList(), homeSections != null ? homeSections : Collections.emptyList(),
                library.size(), query, homeSections == null));
    }
}
