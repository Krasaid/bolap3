package com.nexbytes.bolap3.tags;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses lyrics text. Text containing LRC timestamps ("[01:23.45] line") becomes synced lyrics;
 * anything else is shown as plain text. Supports several timestamps per line, the [offset:] tag
 * and strips enhanced-LRC word timestamps ("&lt;01:23.45&gt;").
 */
public final class LrcParser {

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA = Pattern.compile("^\\[[a-zA-Z#]+:.*]$");
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    public static final class Line {
        /** Start time in ms, or -1 for unsynced lyrics. */
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    public static final class Lyrics {
        public final List<Line> lines;
        public final boolean synced;

        Lyrics(List<Line> lines, boolean synced) {
            this.lines = Collections.unmodifiableList(lines);
            this.synced = synced;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }

        /** Index of the line playing at {@code positionMs}, or -1 before the first line. */
        public int indexAt(long positionMs) {
            if (!synced) return -1;
            int low = 0;
            int high = lines.size() - 1;
            int result = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (lines.get(mid).timeMs <= positionMs) {
                    result = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return result;
        }
    }

    private LrcParser() {
    }

    public static Lyrics parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new Lyrics(new ArrayList<>(), false);
        }
        // Drop a UTF-8 byte-order mark left at the start of .lrc files.
        String text = raw.charAt(0) == BYTE_ORDER_MARK ? raw.substring(1) : raw;
        String[] rawLines = text.split("\\r\\n|\\r|\\n");

        long offset = 0;
        Matcher offsetMatcher = OFFSET.matcher(text);
        if (offsetMatcher.find()) {
            offset = Long.parseLong(offsetMatcher.group(1).replace("+", ""));
        }

        List<Line> synced = new ArrayList<>();
        for (String line : rawLines) {
            Matcher matcher = TIMESTAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find() && matcher.start() == textStart) {
                times.add(toMillis(matcher));
                textStart = matcher.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String lyric = WORD_TIMESTAMP.matcher(line.substring(textStart)).replaceAll("").trim();
            for (long time : times) {
                // A positive offset makes lyrics appear sooner.
                synced.add(new Line(Math.max(0, time - offset), lyric));
            }
        }

        if (!synced.isEmpty()) {
            synced.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
            return new Lyrics(synced, true);
        }

        List<Line> plain = new ArrayList<>();
        for (String line : rawLines) {
            if (!METADATA.matcher(line.trim()).matches()) {
                plain.add(new Line(-1, line.trim()));
            }
        }
        // Drop leading/trailing blank lines.
        while (!plain.isEmpty() && plain.get(0).text.isEmpty()) plain.remove(0);
        while (!plain.isEmpty() && plain.get(plain.size() - 1).text.isEmpty()) plain.remove(plain.size() - 1);
        return new Lyrics(plain, false);
    }

    private static long toMillis(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        long fraction = 0;
        String fractionText = matcher.group(3);
        if (fractionText != null) {
            fraction = Long.parseLong(fractionText);
            if (fractionText.length() == 1) fraction *= 100;
            else if (fractionText.length() == 2) fraction *= 10;
        }
        return minutes * 60_000 + seconds * 1000 + fraction;
    }
}
