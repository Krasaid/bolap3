package com.nexbytes.bolap3.tags;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TagReaderTest {

    @Test
    public void id3v23_readsUtf16LyricsAndReplayGain() throws IOException {
        byte[] uslt = concat(
                new byte[]{1}, ascii("eng"),
                utf16WithBom(""), new byte[]{0, 0},
                utf16WithBom("Line one\nLine two"));
        byte[] txxx = concat(
                new byte[]{0}, ascii("REPLAYGAIN_TRACK_GAIN"), new byte[]{0}, ascii("-6.50 dB"));
        byte[] apic = new byte[5000]; // a "picture" that must be skipped
        byte[] frames = concat(frameV23("APIC", apic), frameV23("USLT", uslt), frameV23("TXXX", txxx), new byte[64]);

        TagReader.Tags tags = TagReader.read(new ByteArrayInputStream(id3Header(3, frames)));

        assertEquals("Line one\nLine two", tags.lyrics);
        assertEquals(-6.5f, tags.trackGainDb, 0.0001f);
        assertNull(tags.albumGainDb);
    }

    @Test
    public void id3v24_usesSyncsafeFrameSizes() throws IOException {
        StringBuilder longLyrics = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longLyrics.append("[00:").append(String.format("%02d", i)).append(".00]linea ").append(i).append('\n');
        }
        byte[] uslt = concat(new byte[]{3}, ascii("spa"), new byte[]{0},
                longLyrics.toString().getBytes(StandardCharsets.UTF_8));
        // > 127 bytes, so a non-syncsafe reader would get the size wrong.
        byte[] tag = id3Header(4, concat(frameV24("USLT", uslt), new byte[16]));

        TagReader.Tags tags = TagReader.read(new ByteArrayInputStream(tag));

        assertEquals(longLyrics.toString(), tags.lyrics);
        assertEquals(40, LrcParser.parse(tags.lyrics).lines.size());
    }

    @Test
    public void flac_readsVorbisComments() throws IOException {
        byte[] comments = vorbisBlock("reference libFLAC",
                "TITLE=Song", "LYRICS=Hola mundo", "REPLAYGAIN_ALBUM_GAIN=+1.20 dB");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ascii("fLaC"));
        out.write(blockHeader(false, 0, 34)); // STREAMINFO
        out.write(new byte[34]);
        out.write(blockHeader(true, 4, comments.length));
        out.write(comments);

        TagReader.Tags tags = TagReader.read(new ByteArrayInputStream(out.toByteArray()));

        assertEquals("Hola mundo", tags.lyrics);
        assertEquals(1.2f, tags.albumGainDb, 0.0001f);
    }

    @Test
    public void unknownFormat_returnsEmptyTags() throws IOException {
        TagReader.Tags tags = TagReader.read(new ByteArrayInputStream(ascii("OggS0000000000")));
        assertNull(tags.lyrics);
        assertNull(tags.trackGainDb);
    }

    @Test
    public void parseGain() {
        assertEquals(-7.25f, TagReader.parseGain(" -7.25 dB"), 0.0001f);
        assertEquals(3f, TagReader.parseGain("+3,0 dB"), 0.0001f);
        assertNull(TagReader.parseGain("loud"));
    }

    @Test
    public void replayGainToVolume() {
        assertEquals(1f, ReplayGain.volumeFor(null, null), 0f);
        assertEquals(0.5012f, ReplayGain.volumeFor(-6f, null), 0.001f);
        assertEquals(1f, ReplayGain.volumeFor(4f, null), 0f); // never boost above full volume
        assertEquals(0.7943f, ReplayGain.volumeFor(null, -2f), 0.001f);
    }

    // ------------------------------------------------------------------ builders

    private static byte[] id3Header(int major, byte[] frames) {
        int size = frames.length;
        byte[] header = {'I', 'D', '3', (byte) major, 0, 0,
                (byte) ((size >> 21) & 0x7F), (byte) ((size >> 14) & 0x7F),
                (byte) ((size >> 7) & 0x7F), (byte) (size & 0x7F)};
        return concat(header, frames);
    }

    private static byte[] frameV23(String id, byte[] data) {
        int size = data.length;
        byte[] header = concat(ascii(id), new byte[]{
                (byte) (size >> 24), (byte) (size >> 16), (byte) (size >> 8), (byte) size, 0, 0});
        return concat(header, data);
    }

    private static byte[] frameV24(String id, byte[] data) {
        int size = data.length;
        byte[] header = concat(ascii(id), new byte[]{
                (byte) ((size >> 21) & 0x7F), (byte) ((size >> 14) & 0x7F),
                (byte) ((size >> 7) & 0x7F), (byte) (size & 0x7F), 0, 0});
        return concat(header, data);
    }

    private static byte[] blockHeader(boolean last, int type, int length) {
        return new byte[]{(byte) ((last ? 0x80 : 0) | type), (byte) (length >> 16), (byte) (length >> 8), (byte) length};
    }

    private static byte[] vorbisBlock(String vendor, String... comments) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] vendorBytes = vendor.getBytes(StandardCharsets.UTF_8);
        out.write(le(vendorBytes.length));
        out.write(vendorBytes);
        out.write(le(comments.length));
        for (String comment : comments) {
            byte[] bytes = comment.getBytes(StandardCharsets.UTF_8);
            out.write(le(bytes.length));
            out.write(bytes);
        }
        return out.toByteArray();
    }

    private static byte[] le(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    private static byte[] utf16WithBom(String text) {
        return concat(new byte[]{(byte) 0xFF, (byte) 0xFE}, text.getBytes(StandardCharsets.UTF_16LE));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }
}
