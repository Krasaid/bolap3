package com.nexbytes.bolap3.media;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.material.color.MaterialColors;
import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.library.LibraryGrouping;
import com.nexbytes.bolap3.model.GroupItem;
import com.nexbytes.bolap3.model.Song;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads cover art off the main thread, downsampled to the requested size, with an LRU cache
 * bounded by bytes. Lookup order: MediaStore thumbnail (which already includes embedded art and
 * is cached by the system), embedded picture via MediaMetadataRetriever (folder/SAF songs),
 * then the album's artwork.
 */
public final class AlbumArtLoader {

    public static final int SIZE_THUMB = 192;
    public static final int SIZE_LARGE = 1024;

    public interface Callback {
        void onLoaded(@Nullable Bitmap bitmap);
    }

    private static final int CACHE_BYTES =
            (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024);

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getAllocationByteCount();
        }
    };

    /** Keys already known to have no artwork, so we don't hit the disk again for them. */
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private AlbumArtLoader() {
    }

    /** Asynchronous load; the callback runs on the main thread. */
    public static void load(Context context, @Nullable Song song, int sizePx, Callback callback) {
        if (song == null) {
            callback.onLoaded(null);
            return;
        }
        String key = cacheKey(song, sizePx);
        Bitmap cached = cache.get(key);
        if (cached != null || missing.contains(key)) {
            callback.onLoaded(cached);
            return;
        }
        Context app = context.getApplicationContext();
        AppExecutors.io().execute(() -> {
            Bitmap bitmap = loadBlocking(app, song, sizePx);
            AppExecutors.main(() -> callback.onLoaded(bitmap));
        });
    }

    @WorkerThread
    @Nullable
    public static Bitmap loadBlocking(Context context, @Nullable Song song, int sizePx) {
        if (song == null) {
            return null;
        }
        String key = cacheKey(song, sizePx);
        Bitmap cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (missing.contains(key)) {
            return null;
        }

        int size = bucket(sizePx);
        Uri songUri = Uri.parse(song.getUri());
        ContentResolver resolver = context.getContentResolver();
        Bitmap bitmap = null;

        if (MediaStore.AUTHORITY.equals(songUri.getAuthority())) {
            bitmap = loadThumbnail(resolver, songUri, size);
        }
        if (bitmap == null) {
            bitmap = loadEmbedded(context, songUri, size);
        }
        if (bitmap == null && song.getAlbumId() > 0) {
            bitmap = loadThumbnail(resolver,
                    ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, song.getAlbumId()), size);
        }
        if (bitmap == null && song.getAlbumArtUri() != null) {
            bitmap = loadFromUri(resolver, Uri.parse(song.getAlbumArtUri()), size);
        }

        if (bitmap != null) {
            cache.put(key, bitmap);
        } else {
            missing.add(key);
        }
        return bitmap;
    }

    /** Already-decoded artwork, or null (never touches the disk). */
    @Nullable
    public static Bitmap getCached(@Nullable Song song, int sizePx) {
        return song == null ? null : cache.get(cacheKey(song, sizePx));
    }

    /** Song thumbnail; without artwork it shows the album's generated cover. */
    public static void bindSong(ImageView view, @Nullable Song song) {
        if (song == null) {
            bind(view, null, null, null, R.drawable.ic_album);
            return;
        }
        bind(view, song, albumSeed(song), song.getAlbum(), R.drawable.ic_album);
    }

    /** Seed shared by every song of an album, so they all get the same generated cover. */
    public static String albumSeed(Song song) {
        return LibraryGrouping.keyFor(song, GroupItem.TYPE_ALBUM);
    }

    /**
     * Shows a placeholder immediately, then the cover when loaded. Safe with recycled views:
     * a late result for a previous song is ignored.
     *
     * @param coverSeed  when not null the placeholder is a generated cover with {@code coverLabel}'s
     *                   initials; when null it is {@code placeholderIcon} (smart playlists)
     */
    public static void bind(ImageView view, @Nullable Song song, @Nullable String coverSeed,
                            @Nullable String coverLabel, @DrawableRes int placeholderIcon) {
        String tag = song != null ? song.getUri() : null;
        view.setTag(R.id.tag_cover_uri, tag);
        view.setClipToOutline(true);

        Bitmap cached = song != null ? cache.get(cacheKey(song, SIZE_THUMB)) : null;
        if (cached != null) {
            showBitmap(view, cached);
            return;
        }

        if (coverSeed != null) {
            showGenerated(view, coverSeed, coverLabel);
        } else {
            showPlaceholder(view, placeholderIcon);
        }
        if (song == null) {
            return;
        }

        load(view.getContext(), song, SIZE_THUMB, bitmap -> {
            if (bitmap != null && Objects.equals(tag, view.getTag(R.id.tag_cover_uri))) {
                showBitmap(view, bitmap);
            }
        });
    }

    public static void showPlaceholder(ImageView view, @DrawableRes int icon) {
        int padding = Math.round(12 * view.getResources().getDisplayMetrics().density);
        view.setImageResource(icon);
        view.setImageTintList(ColorStateList.valueOf(
                MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)));
        view.setPadding(padding, padding, padding, padding);
        view.setBackgroundResource(R.drawable.bg_cover_placeholder_dark);
    }

    public static void showGenerated(ImageView view, @Nullable String seed, @Nullable String label) {
        view.setImageTintList(null);
        view.setPadding(0, 0, 0, 0);
        view.setImageDrawable(new GeneratedCoverDrawable(seed, label));
    }

    public static void showBitmap(ImageView view, Bitmap bitmap) {
        view.setImageTintList(null);
        view.setPadding(0, 0, 0, 0);
        view.setImageBitmap(bitmap);
    }

    @Nullable
    private static Bitmap loadThumbnail(ContentResolver resolver, Uri uri, int size) {
        try {
            return resolver.loadThumbnail(uri, new Size(size, size), null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Bitmap loadEmbedded(Context context, Uri songUri, int size) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, songUri);
            byte[] picture = retriever.getEmbeddedPicture();
            if (picture == null || picture.length == 0) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(picture, 0, picture.length, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size);
            return BitmapFactory.decodeByteArray(picture, 0, picture.length, options);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static Bitmap loadFromUri(ContentResolver resolver, Uri uri, int size) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size);
            try (InputStream input = resolver.openInputStream(uri)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2;
        }
        return sample;
    }

    private static int bucket(int sizePx) {
        return sizePx <= SIZE_THUMB ? SIZE_THUMB : SIZE_LARGE;
    }

    private static String cacheKey(Song song, int sizePx) {
        return song.getUri() + "@" + bucket(sizePx);
    }
}
