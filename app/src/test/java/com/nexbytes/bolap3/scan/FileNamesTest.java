package com.nexbytes.bolap3.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FileNamesTest {

    @Test
    public void stripsOnlyTrailingAudioExtension() {
        assertEquals("song", FileNames.stripAudioExtension("song.mp3", "x"));
        assertEquals("My.mp3.collection", FileNames.stripAudioExtension("My.mp3.collection.flac", "x"));
        assertEquals("notes.txt", FileNames.stripAudioExtension("notes.txt", "x"));
    }

    @Test
    public void extensionMatchIsCaseInsensitive() {
        assertEquals("LOUD", FileNames.stripAudioExtension("LOUD.MP3", "x"));
        assertEquals("Track 01", FileNames.stripAudioExtension("Track 01.Flac", "x"));
    }

    @Test
    public void blankNamesUseFallback() {
        assertEquals("fallback", FileNames.stripAudioExtension(null, "fallback"));
        assertEquals("fallback", FileNames.stripAudioExtension("  ", "fallback"));
        assertEquals("fallback", FileNames.stripAudioExtension(".mp3", "fallback"));
    }

    @Test
    public void detectsAudioByExtensionOrMime() {
        assertTrue(FileNames.isAudioFile("a.opus", null));
        assertTrue(FileNames.isAudioFile("a.OGG", "application/octet-stream"));
        assertTrue(FileNames.isAudioFile("noext", "audio/flac"));
        assertFalse(FileNames.isAudioFile("cover.jpg", "image/jpeg"));
        assertFalse(FileNames.isAudioFile("list.m3u", "audio/x-mpegurl"));
        assertFalse(FileNames.isAudioFile(".hidden.mp3", null));
    }

    @Test
    public void baseName() {
        assertEquals("song", FileNames.baseName("song.flac"));
        assertEquals("a.b", FileNames.baseName("a.b.mp3"));
        assertEquals("noext", FileNames.baseName("noext"));
    }

    @Test
    public void lastPathSegment() {
        assertEquals("Rock", FileNames.lastPathSegment("Music/Rock/"));
        assertEquals("Music", FileNames.lastPathSegment("Music"));
    }

    @Test
    public void parseLeadingInt() {
        assertEquals(3, FileNames.parseLeadingInt("3/12"));
        assertEquals(7, FileNames.parseLeadingInt(" 07 "));
        assertEquals(0, FileNames.parseLeadingInt("abc"));
        assertEquals(0, FileNames.parseLeadingInt(null));
        assertEquals(0, FileNames.parseLeadingInt("123456"));
    }
}
