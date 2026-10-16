package com.nexbytes.bolap3.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AudioInfoTest {

    @Test
    public void formatNames() {
        assertEquals("FLAC", AudioInfo.formatName("audio/flac"));
        assertEquals("MP3", AudioInfo.formatName("audio/mpeg"));
        assertEquals("AAC", AudioInfo.formatName("audio/mp4"));
        assertEquals("X-APE", AudioInfo.formatName("audio/x-ape"));
        assertNull(AudioInfo.formatName(null));
    }

    @Test
    public void sampleRate() {
        assertEquals("48", AudioInfo.formatKhz(48_000));
        assertEquals("96", AudioInfo.formatKhz(96_000));
    }
}
