package com.nexbytes.bolap3.tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LrcParserTest {

    @Test
    public void parsesSyncedLyricsInTimeOrder() {
        LrcParser.Lyrics lyrics = LrcParser.parse(
                "[ar:Someone]\n[00:12.50]Second\n[00:01.00]First\n[01:02.345]Third");
        assertTrue(lyrics.synced);
        assertEquals(3, lyrics.lines.size());
        assertEquals("First", lyrics.lines.get(0).text);
        assertEquals(1_000, lyrics.lines.get(0).timeMs);
        assertEquals(12_500, lyrics.lines.get(1).timeMs);
        assertEquals(62_345, lyrics.lines.get(2).timeMs);
    }

    @Test
    public void lineWithSeveralTimestampsIsRepeated() {
        LrcParser.Lyrics lyrics = LrcParser.parse("[00:10.00][00:30.00]Chorus");
        assertEquals(2, lyrics.lines.size());
        assertEquals(30_000, lyrics.lines.get(1).timeMs);
        assertEquals("Chorus", lyrics.lines.get(1).text);
    }

    @Test
    public void offsetShiftsLinesEarlier() {
        LrcParser.Lyrics lyrics = LrcParser.parse("[offset:+500]\n[00:02.00]Line");
        assertEquals(1_500, lyrics.lines.get(0).timeMs);
    }

    @Test
    public void stripsWordTimestamps() {
        LrcParser.Lyrics lyrics = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world");
        assertEquals("Hello world", lyrics.lines.get(0).text);
    }

    @Test
    public void plainTextIsUnsynced() {
        LrcParser.Lyrics lyrics = LrcParser.parse("\n\nVerse one\nVerse two\n\n");
        assertFalse(lyrics.synced);
        assertEquals(2, lyrics.lines.size());
        assertEquals(-1, lyrics.indexAt(5_000));
    }

    @Test
    public void indexAtFindsCurrentLine() {
        LrcParser.Lyrics lyrics = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c");
        assertEquals(-1, lyrics.indexAt(500));
        assertEquals(0, lyrics.indexAt(1_000));
        assertEquals(1, lyrics.indexAt(8_999));
        assertEquals(2, lyrics.indexAt(60_000));
    }

    @Test
    public void emptyInput() {
        assertTrue(LrcParser.parse(null).isEmpty());
        assertTrue(LrcParser.parse("  ").isEmpty());
    }
}
