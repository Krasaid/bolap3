package com.nexbytes.bolap3.scan;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.FolderPaths;
import com.nexbytes.bolap3.data.LibraryFilterPrefs;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.model.Song;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds songs either through MediaStore (whole device) or by walking a folder the user picked
 * with the Storage Access Framework. Runs on a background thread; checks {@code cancelled}
 * between items so a scan can be stopped quickly.
 */
public final class MusicScanner {

    private static final Uri ALBUM_ART_BASE = Uri.parse("content://media/external/audio/albumart");

    public interface Progress {
        void onSongFound(int count);
    }

    /** The folder or the MediaStore could not be read (permission revoked, folder deleted...). */
    public static final class ScanException extends Exception {
        public final boolean permissionDenied;

        ScanException(Throwable cause, boolean permissionDenied) {
            super(cause);
            this.permissionDenied = permissionDenied;
        }
    }

    private final Context context;
    private final ContentResolver resolver;
    private final AtomicBoolean cancelled;
    private final Set<String> excludedFolders;
    private final long minDurationMs;

    public MusicScanner(Context context, AtomicBoolean cancelled) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
        this.cancelled = cancelled;
        this.excludedFolders = LibraryFilterPrefs.getExcludedFolders(context);
        this.minDurationMs = SettingsPrefs.getMinDurationSeconds(context) * 1000L;
    }

    @WorkerThread
    public List<Song> scanMediaStore(@Nullable Progress progress) throws ScanException {
        List<Song> found = new ArrayList<>();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.TRACK
        };

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] selectionArgs = null;
        if (minDurationMs > 0) {
            selection += " AND " + MediaStore.Audio.Media.DURATION + " >= ?";
            selectionArgs = new String[]{String.valueOf(minDurationMs)};
        }

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs,
                MediaStore.Audio.Media.TITLE + " ASC")) {
            if (cursor == null) {
                return found;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
            int trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);

            while (cursor.moveToNext() && !cancelled.get()) {
                String folderPath = orFallback(cursor.getString(pathColumn), R.string.storage_root);
                if (FolderPaths.isWithinAny(folderPath, excludedFolders)) {
                    continue;
                }

                long albumId = cursor.getLong(albumIdColumn);
                String albumArtUri = albumId > 0
                        ? ContentUris.withAppendedId(ALBUM_ART_BASE, albumId).toString()
                        : null;
                Uri songUri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn));

                found.add(new Song(
                        songUri.toString(),
                        orFallback(cursor.getString(titleColumn), R.string.unknown_title),
                        orFallback(cleanArtist(cursor.getString(artistColumn)), R.string.unknown_artist),
                        orFallback(cursor.getString(albumColumn), R.string.unknown_album),
                        albumId,
                        albumArtUri,
                        FileNames.lastPathSegment(folderPath),
                        folderPath,
                        cursor.getLong(durationColumn),
                        cursor.getLong(dateColumn) * 1000L,
                        cursor.getInt(trackColumn)
                ));

                if (progress != null) {
                    progress.onSongFound(found.size());
                }
            }
        } catch (SecurityException exception) {
            throw new ScanException(exception, true);
        } catch (RuntimeException exception) {
            throw new ScanException(exception, false);
        }

        return found;
    }

    /**
     * Walks the picked folder with DocumentsContract queries (much faster than DocumentFile,
     * which issues one query per file property). Folders containing ".nomedia" are skipped
     * together with their subfolders, like MediaStore does.
     */
    @WorkerThread
    public List<Song> scanSafFolder(String treeUriString, @Nullable Progress progress) throws ScanException {
        List<Song> found = new ArrayList<>();

        try {
            Uri treeUri = Uri.parse(treeUriString);
            String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            String rootName = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId));

            Deque<String[]> pending = new ArrayDeque<>(); // {documentId, displayName}
            pending.push(new String[]{rootDocumentId, rootName});

            while (!pending.isEmpty() && !cancelled.get()) {
                String[] folder = pending.pop();
                scanSafDirectory(treeUri, folder[0], folder[1], pending, found, progress);
            }
        } catch (SecurityException exception) {
            throw new ScanException(exception, true);
        } catch (RuntimeException exception) {
            throw new ScanException(exception, false);
        }

        return found;
    }

    private void scanSafDirectory(Uri treeUri, String documentId, @Nullable String displayName,
                                  Deque<String[]> pending, List<Song> found, @Nullable Progress progress) {
        Uri folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
        String folderPath = folderUri.toString();
        if (FolderPaths.isWithinAny(folderPath, excludedFolders)) {
            return;
        }

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        List<String[]> subfolders = new ArrayList<>();
        List<Object[]> audioFiles = new ArrayList<>(); // {documentId, name, lastModified}

        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long lastModified = cursor.isNull(3) ? 0 : cursor.getLong(3);

                if (".nomedia".equals(name)) {
                    return;
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    subfolders.add(new String[]{childId, name});
                } else if (FileNames.isAudioFile(name, mime)) {
                    audioFiles.add(new Object[]{childId, name, lastModified});
                }
            }
        }

        String folderName = displayName != null ? displayName : context.getString(R.string.selected_folder);

        for (Object[] file : audioFiles) {
            if (cancelled.get()) {
                return;
            }
            Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, (String) file[0]);
            Song song = readSafSong(fileUri, (String) file[1], (long) file[2], folderName, folderPath);
            if (song == null) {
                continue;
            }
            found.add(song);
            if (progress != null) {
                progress.onSongFound(found.size());
            }
        }

        for (int i = subfolders.size() - 1; i >= 0; i--) {
            pending.push(subfolders.get(i));
        }
    }

    /** Reads tags with MediaMetadataRetriever; falls back to the file name when there are none. */
    @Nullable
    private Song readSafSong(Uri fileUri, String fileName, long lastModified,
                             String folderName, String folderPath) {
        String title = null;
        String artist = null;
        String album = null;
        long duration = 0;
        int track = 0;

        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, fileUri);
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (isBlank(artist)) {
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            duration = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            track = FileNames.parseLeadingInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER))
                    + 1000 * FileNames.parseLeadingInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));
        } catch (Exception ignored) {
            // Unreadable tags: keep the file with name-based metadata, playback may still work.
        }

        if (minDurationMs > 0 && duration > 0 && duration < minDurationMs) {
            return null;
        }

        return new Song(
                fileUri.toString(),
                isBlank(title) ? FileNames.stripAudioExtension(fileName, context.getString(R.string.unknown_title)) : title,
                orFallback(cleanArtist(artist), R.string.unknown_artist),
                orFallback(album, R.string.unknown_album),
                -1,
                null,
                folderName,
                folderPath,
                duration,
                lastModified,
                track
        );
    }

    @Nullable
    private String queryDisplayName(Uri documentUri) {
        try (Cursor cursor = resolver.query(documentUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException ignored) {
            // Name is cosmetic only.
        }
        return null;
    }

    private String orFallback(@Nullable String value, int fallbackRes) {
        return isBlank(value) ? context.getString(fallbackRes) : value;
    }

    /** MediaStore reports missing artists as "<unknown>". */
    @Nullable
    private static String cleanArtist(@Nullable String artist) {
        return "<unknown>".equals(artist) ? null : artist;
    }

    private static long parseLong(@Nullable String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
