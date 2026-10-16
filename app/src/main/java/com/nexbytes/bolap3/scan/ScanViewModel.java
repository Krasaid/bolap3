package com.nexbytes.bolap3.scan;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.data.MusicRepository;
import com.nexbytes.bolap3.model.Song;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs a scan outside the Activity so it survives rotation and other configuration changes.
 * A cancelled or empty scan never touches the existing library.
 */
public class ScanViewModel extends AndroidViewModel {

    public enum Status {
        RUNNING,
        DONE,
        EMPTY,
        PERMISSION_DENIED,
        FOLDER_UNAVAILABLE,
        CANCELLED
    }

    public static final class State {
        public final Status status;
        public final int count;

        State(Status status, int count) {
            this.status = status;
            this.count = count;
        }
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(new State(Status.RUNNING, 0));
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean started = false;

    public ScanViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<State> getState() {
        return state;
    }

    /** Starts the scan once; later calls (e.g. after rotation) are ignored. */
    public void start(@Nullable String scanMode, @Nullable String folderUri) {
        if (started) {
            return;
        }
        started = true;

        Application app = getApplication();
        boolean safFolder = LibraryPrefs.SCAN_MODE_SAF_FOLDER.equals(scanMode) && folderUri != null;

        executor.execute(() -> {
            MusicScanner scanner = new MusicScanner(app, cancelled);
            MusicScanner.Progress progress = count -> state.postValue(new State(Status.RUNNING, count));

            try {
                List<Song> songs = safFolder
                        ? scanner.scanSafFolder(folderUri, progress)
                        : scanner.scanMediaStore(progress);

                if (cancelled.get()) {
                    state.postValue(new State(Status.CANCELLED, songs.size()));
                    return;
                }
                if (songs.isEmpty()) {
                    state.postValue(new State(Status.EMPTY, 0));
                    return;
                }

                MusicRepository.replaceAll(app, songs);
                LibraryPrefs.setLibraryReady(app, true,
                        safFolder ? LibraryPrefs.SCAN_MODE_SAF_FOLDER : LibraryPrefs.SCAN_MODE_MEDIA_STORE,
                        safFolder ? folderUri : null);
                state.postValue(new State(Status.DONE, songs.size()));
            } catch (MusicScanner.ScanException exception) {
                state.postValue(new State(
                        exception.permissionDenied && !safFolder ? Status.PERMISSION_DENIED : Status.FOLDER_UNAVAILABLE,
                        0));
            }
        });
    }

    public void cancel() {
        cancelled.set(true);
    }

    @Override
    protected void onCleared() {
        cancelled.set(true);
        executor.shutdown();
    }
}
