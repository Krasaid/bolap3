package com.nexbytes.bolap3.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class FolderPathsTest {

    @Test
    public void mediaStorePath_matchesItselfAndSubfolders() {
        assertTrue(FolderPaths.isWithin("Music/Rock/", "Music/Rock/"));
        assertTrue(FolderPaths.isWithin("Music/Rock/Live/", "Music/Rock/"));
    }

    @Test
    public void mediaStorePath_doesNotMatchSiblingWithSamePrefix() {
        assertFalse(FolderPaths.isWithin("Music/Rock2/", "Music/Rock/"));
        assertFalse(FolderPaths.isWithin("Music/Rock2", "Music/Rock"));
        assertFalse(FolderPaths.isWithin("Music/", "Music/Rock/"));
    }

    @Test
    public void pathWithoutTrailingSlash_requiresSeparator() {
        assertTrue(FolderPaths.isWithin("Music/Rock/Live", "Music/Rock"));
        assertFalse(FolderPaths.isWithin("Music/Rockabilly", "Music/Rock"));
    }

    @Test
    public void safDocumentUri_matchesEncodedChildren() {
        String rock = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2FRock";
        assertTrue(FolderPaths.isWithin(rock, rock));
        assertTrue(FolderPaths.isWithin(rock + "%2FLive", rock));
        assertTrue(FolderPaths.isWithin(rock + "%2flive", rock));
        assertFalse(FolderPaths.isWithin(rock + "2", rock));
    }

    @Test
    public void safVolumeRoot_containsEverything() {
        String root = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3A";
        assertTrue(FolderPaths.isWithin(root + "Music", root));
    }

    @Test
    public void nullsAndEmpty_neverMatch() {
        assertFalse(FolderPaths.isWithin(null, "Music/"));
        assertFalse(FolderPaths.isWithin("Music/", null));
        assertFalse(FolderPaths.isWithin("Music/", ""));
    }

    @Test
    public void isWithinAny() {
        assertTrue(FolderPaths.isWithinAny("Podcasts/Show/", Arrays.asList("Music/", "Podcasts/")));
        assertFalse(FolderPaths.isWithinAny("Music/", Collections.emptyList()));
    }
}
