package com.nexbytes.bolap3.tags;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Minimal tag reader for what Android's MediaMetadataRetriever does not expose: lyrics and
 * ReplayGain. Supports ID3v2.2/2.3/2.4 (MP3, some AAC/WAV) and FLAC Vorbis comments.
 * Only the start of the file is read; pictures and other frames are skipped.
 */
public final class TagReader {

    /** Tags larger than this (huge embedded pictures) are skipped rather than buffered. */
    private static final int MAX_ID3_TAG_BYTES = 24 * 1024 * 1024;

    public static final class Tags {
        @Nullable public String lyrics;
        @Nullable public Float trackGainDb;
        @Nullable public Float albumGainDb;
    }

    private TagReader() {
    }

    public static Tags read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        Tags tags = new Tags();
        byte[] magic = new byte[4];
        in.readFully(magic, 0, 3);

        if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') {
            readId3(in, tags);
        } else {
            in.readFully(magic, 3, 1);
            if (magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') {
                readFlac(in, tags);
            }
        }
        return tags;
    }

    // ---------------------------------------------------------------- ID3v2

    private static void readId3(DataInputStream in, Tags tags) throws IOException {
        int major = in.readUnsignedByte();
        in.readUnsignedByte(); // revision
        int flags = in.readUnsignedByte();
        int size = syncSafe(in.readInt());

        if (size <= 0 || size > MAX_ID3_TAG_BYTES || major < 2 || major > 4) {
            return;
        }

        byte[] tag = new byte[size];
        in.readFully(tag);

        // v2.2/v2.3 tag-wide unsynchronisation.
        if ((flags & 0x80) != 0 && major < 4) {
            tag = removeUnsync(tag, 0, tag.length);
        }

        int offset = 0;
        if ((flags & 0x40) != 0 && major >= 3) {
            int extendedSize = readInt(tag, 0);
            offset = major == 4 ? syncSafe(extendedSize) : extendedSize + 4;
        }

        int idLength = major == 2 ? 3 : 4;
        int headerLength = major == 2 ? 6 : 10;

        while (offset + headerLength <= tag.length) {
            if (tag[offset] == 0) {
                break; // padding
            }
            String id = new String(tag, offset, idLength, StandardCharsets.ISO_8859_1);
            int frameSize;
            int frameFlags = 0;
            if (major == 2) {
                frameSize = ((tag[offset + 3] & 0xFF) << 16) | ((tag[offset + 4] & 0xFF) << 8) | (tag[offset + 5] & 0xFF);
            } else {
                int raw = readInt(tag, offset + 4);
                frameSize = major == 4 ? syncSafe(raw) : raw;
                frameFlags = ((tag[offset + 8] & 0xFF) << 8) | (tag[offset + 9] & 0xFF);
            }

            int dataStart = offset + headerLength;
            if (frameSize <= 0 || dataStart + frameSize > tag.length) {
                break;
            }

            byte[] data = frameData(tag, dataStart, frameSize, major, frameFlags);
            if (data != null) {
                handleId3Frame(id, data, tags);
            }
            offset = dataStart + frameSize;
        }
    }

    @Nullable
    private static byte[] frameData(byte[] tag, int start, int size, int major, int flags) {
        int from = start;
        int length = size;
        if (major == 4) {
            if ((flags & 0x000C) != 0) {
                return null; // compressed or encrypted
            }
            if ((flags & 0x0001) != 0) { // data length indicator
                from += 4;
                length -= 4;
            }
            if (length <= 0) {
                return null;
            }
            if ((flags & 0x0002) != 0) {
                return removeUnsync(tag, from, length);
            }
        } else if (major == 3) {
            if ((flags & 0x00C0) != 0) {
                return null; // compressed or encrypted
            }
            if ((flags & 0x0020) != 0) { // grouping identity byte
                from += 1;
                length -= 1;
            }
            if (length <= 0) {
                return null;
            }
        }
        byte[] data = new byte[length];
        System.arraycopy(tag, from, data, 0, length);
        return data;
    }

    private static void handleId3Frame(String id, byte[] data, Tags tags) {
        switch (id) {
            case "USLT":
            case "ULT": {
                // encoding, 3-byte language, descriptor, text
                if (data.length < 5) return;
                int encoding = data[0] & 0xFF;
                int descriptorEnd = findTerminator(data, 4, encoding);
                int textStart = descriptorEnd + terminatorLength(encoding);
                String text = decode(data, textStart, data.length - textStart, encoding);
                if (tags.lyrics == null && !text.trim().isEmpty()) {
                    tags.lyrics = text;
                }
                break;
            }
            case "TXXX":
            case "TXX": {
                if (data.length < 2) return;
                int encoding = data[0] & 0xFF;
                int descriptionEnd = findTerminator(data, 1, encoding);
                String description = decode(data, 1, descriptionEnd - 1, encoding);
                int valueStart = descriptionEnd + terminatorLength(encoding);
                String value = decode(data, valueStart, data.length - valueStart, encoding);
                handleKeyValue(description, value, tags);
                break;
            }
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- FLAC

    private static void readFlac(DataInputStream in, Tags tags) throws IOException {
        boolean last = false;
        while (!last) {
            int header = in.readUnsignedByte();
            last = (header & 0x80) != 0;
            int type = header & 0x7F;
            int length = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();

            if (type == 4) { // VORBIS_COMMENT
                byte[] block = new byte[length];
                in.readFully(block);
                readVorbisComments(block, tags);
                return;
            }
            skipFully(in, length);
        }
    }

    /** Parses a Vorbis comment block (little-endian lengths, "KEY=value" UTF-8 entries). */
    static void readVorbisComments(byte[] block, Tags tags) {
        int offset = 0;
        if (block.length < 8) return;
        int vendorLength = readIntLE(block, offset);
        offset += 4 + vendorLength;
        if (vendorLength < 0 || offset + 4 > block.length) return;
        int count = readIntLE(block, offset);
        offset += 4;

        for (int i = 0; i < count && offset + 4 <= block.length; i++) {
            int length = readIntLE(block, offset);
            offset += 4;
            if (length < 0 || offset + length > block.length) return;
            String comment = new String(block, offset, length, StandardCharsets.UTF_8);
            offset += length;

            int equals = comment.indexOf('=');
            if (equals > 0) {
                handleKeyValue(comment.substring(0, equals), comment.substring(equals + 1), tags);
            }
        }
    }

    // ---------------------------------------------------------------- shared

    private static void handleKeyValue(String key, String value, Tags tags) {
        String upper = key.trim().toUpperCase(Locale.ROOT);
        switch (upper) {
            case "LYRICS":
            case "UNSYNCEDLYRICS":
            case "UNSYNCED LYRICS":
                if (tags.lyrics == null && !value.trim().isEmpty()) {
                    tags.lyrics = value;
                }
                break;
            case "REPLAYGAIN_TRACK_GAIN":
                tags.trackGainDb = parseGain(value);
                break;
            case "REPLAYGAIN_ALBUM_GAIN":
                tags.albumGainDb = parseGain(value);
                break;
            default:
                break;
        }
    }

    /** "-7.25 dB" -> -7.25; null when unparseable. */
    @Nullable
    static Float parseGain(@Nullable String value) {
        if (value == null) return null;
        String cleaned = value.trim().replace(',', '.');
        int end = 0;
        while (end < cleaned.length()) {
            char c = cleaned.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') {
                end++;
            } else {
                break;
            }
        }
        try {
            return end == 0 ? null : Float.parseFloat(cleaned.substring(0, end));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int terminatorLength(int encoding) {
        return encoding == 1 || encoding == 2 ? 2 : 1;
    }

    /** Index of the string terminator starting at {@code from} (or data.length if none). */
    private static int findTerminator(byte[] data, int from, int encoding) {
        if (encoding == 1 || encoding == 2) {
            for (int i = from; i + 1 < data.length; i += 2) {
                if (data[i] == 0 && data[i + 1] == 0) return i;
            }
        } else {
            for (int i = from; i < data.length; i++) {
                if (data[i] == 0) return i;
            }
        }
        return data.length;
    }

    private static String decode(byte[] data, int offset, int length, int encoding) {
        if (length <= 0 || offset >= data.length) {
            return "";
        }
        length = Math.min(length, data.length - offset);
        Charset charset;
        switch (encoding) {
            case 1:
                charset = StandardCharsets.UTF_16; // BOM decides endianness
                break;
            case 2:
                charset = StandardCharsets.UTF_16BE;
                break;
            case 3:
                charset = StandardCharsets.UTF_8;
                break;
            default:
                charset = StandardCharsets.ISO_8859_1;
                break;
        }
        String text = new String(data, offset, length, charset);
        int nul = text.indexOf('\u0000');
        return nul >= 0 ? text.substring(0, nul) : text;
    }

    private static byte[] removeUnsync(byte[] data, int offset, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            out.write(data[i]);
            if ((data[i] & 0xFF) == 0xFF && i + 1 < end && data[i + 1] == 0) {
                i++; // drop the inserted 0x00
            }
        }
        return out.toByteArray();
    }

    private static int syncSafe(int value) {
        return ((value & 0x7F000000) >> 3) | ((value & 0x7F0000) >> 2) | ((value & 0x7F00) >> 1) | (value & 0x7F);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
    }

    private static void skipFully(DataInputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
