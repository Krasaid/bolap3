package com.nexbytes.bolap3.tags;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nexbytes.bolap3.model.Song;
import com.nexbytes.bolap3.scan.FileNames;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Finds lyrics for a song: first a ".lrc" file with the same name in the same folder (only
 * possible for folder/SAF libraries, MediaStore does not expose non-audio files), then lyrics
 * embedded in the file's tags.
 */
public final class LyricsLoader {

    private static final int MAX_LRC_BYTES = 512 * 1024;

    private LyricsLoader() {
    }

    @WorkerThread
    public static LrcParser.Lyrics load(Context context, Song song) {
        ContentResolver resolver = context.getContentResolver();

        String sidecar = readSidecarLrc(resolver, song);
        if (sidecar != null) {
            LrcParser.Lyrics lyrics = LrcParser.parse(sidecar);
            if (!lyrics.isEmpty()) {
                return lyrics;
            }
        }

        TagReader.Tags embedded = readTags(context, Uri.parse(song.getUri()));
        return LrcParser.parse(embedded != null ? embedded.lyrics : null);
    }

    @WorkerThread
    @Nullable
    public static TagReader.Tags readTags(Context context, Uri songUri) {
        try (InputStream input = context.getContentResolver().openInputStream(songUri)) {
            if (input == null) {
                return null;
            }
            return TagReader.read(new java.io.BufferedInputStream(input, 64 * 1024));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    private static String readSidecarLrc(ContentResolver resolver, Song song) {
        Uri songUri = Uri.parse(song.getUri());
        String folderPath = song.getFolderPath();
        // Only folder (SAF) libraries store the folder as a tree document URI.
        if (folderPath == null || !DocumentsContract.isTreeUri(Uri.parse(folderPath))) {
            return null;
        }

        try {
            Uri folderUri = Uri.parse(folderPath);
            String songDocId = DocumentsContract.getDocumentId(songUri);
            int nameStart = Math.max(songDocId.lastIndexOf('/'), songDocId.lastIndexOf(':')) + 1;
            String songName = songDocId.substring(nameStart);
            String wanted = FileNames.baseName(songName) + ".lrc";

            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    folderUri, DocumentsContract.getDocumentId(folderUri));
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null) {
                    return null;
                }
                while (cursor.moveToNext()) {
                    if (wanted.equalsIgnoreCase(cursor.getString(1))) {
                        Uri lrcUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, cursor.getString(0));
                        return readText(resolver, lrcUri);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Not a tree document URI or permission gone.
        }
        return null;
    }

    @Nullable
    private static String readText(ContentResolver resolver, Uri uri) {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0 && out.size() < MAX_LRC_BYTES) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

}
