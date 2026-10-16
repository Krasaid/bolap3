package com.nexbytes.bolap3.model;

import androidx.annotation.StringRes;

import java.util.Collections;
import java.util.List;

/** A carousel on the Home tab: either songs (tap plays the section) or groups (tap opens it). */
public final class HomeSection {

    @StringRes
    public final int title;
    public final List<Song> songs;
    public final List<GroupItem> groups;

    private HomeSection(@StringRes int title, List<Song> songs, List<GroupItem> groups) {
        this.title = title;
        this.songs = songs;
        this.groups = groups;
    }

    public static HomeSection ofSongs(@StringRes int title, List<Song> songs) {
        return new HomeSection(title, Collections.unmodifiableList(songs), Collections.emptyList());
    }

    public static HomeSection ofGroups(@StringRes int title, List<GroupItem> groups) {
        return new HomeSection(title, Collections.emptyList(), Collections.unmodifiableList(groups));
    }

    public boolean isSongs() {
        return groups.isEmpty();
    }

    public int size() {
        return isSongs() ? songs.size() : groups.size();
    }
}
