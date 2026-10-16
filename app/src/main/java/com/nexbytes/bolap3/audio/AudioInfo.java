package com.nexbytes.bolap3.audio;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nexbytes.bolap3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Technical description of a file for the player screen, e.g. "FLAC · 24 bits · 96 kHz · 2304 kbps". */
public final class AudioInfo {

    private AudioInfo() {
    }

    @WorkerThread
    @Nullable
    public static String describe(Context context, Uri uri) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, uri);
            List<String> parts = new ArrayList<>();

            String format = formatName(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
            if (format != null) {
                parts.add(format);
            }

            int bits = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE));
            if (bits > 0) {
                parts.add(context.getString(R.string.audio_bits, bits));
            }

            int sampleRate = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE));
            if (sampleRate > 0) {
                parts.add(context.getString(R.string.audio_khz, formatKhz(sampleRate)));
            }

            int bitrate = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            if (bitrate > 0) {
                parts.add(context.getString(R.string.audio_kbps, Math.round(bitrate / 1000f)));
            }

            return parts.isEmpty() ? null : String.join(" · ", parts);
        } catch (Exception exception) {
            return null;
        }
    }

    static String formatKhz(int sampleRate) {
        float khz = sampleRate / 1000f;
        return khz == Math.floor(khz)
                ? String.valueOf((int) khz)
                : String.format(Locale.getDefault(), "%.1f", khz);
    }

    @Nullable
    static String formatName(@Nullable String mime) {
        if (mime == null) {
            return null;
        }
        switch (mime) {
            case "audio/mpeg":
                return "MP3";
            case "audio/flac":
            case "audio/x-flac":
                return "FLAC";
            case "audio/mp4":
            case "audio/mp4a-latm":
            case "audio/aac":
                return "AAC";
            case "audio/x-wav":
            case "audio/wav":
            case "audio/raw":
                return "WAV";
            case "audio/ogg":
            case "audio/vorbis":
                return "OGG";
            case "audio/opus":
                return "Opus";
            case "audio/alac":
                return "ALAC";
            default:
                int slash = mime.indexOf('/');
                return slash >= 0 ? mime.substring(slash + 1).toUpperCase(Locale.ROOT) : mime;
        }
    }

    private static int parseInt(@Nullable String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
