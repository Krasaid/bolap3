package com.nexbytes.bolap3.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormattersTest {

    @Test
    public void duration() {
        assertEquals("--:--", Formatters.duration(0, "--:--"));
        assertEquals("0:05", Formatters.duration(5_400, "--:--"));
        assertEquals("3:45", Formatters.duration(225_000, "--:--"));
        assertEquals("1:02:03", Formatters.duration(3_723_000, "--:--"));
    }

    @Test
    public void position() {
        assertEquals("0:00", Formatters.position(0));
        assertEquals("0:00", Formatters.position(-10));
        assertEquals("1:00", Formatters.position(60_000));
    }

    @Test
    public void speed() {
        assertEquals("1", Formatters.speed(1f));
        assertEquals("1.25", Formatters.speed(1.25f));
        assertEquals("0.5", Formatters.speed(0.5f));
    }
}
