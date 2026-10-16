package com.nexbytes.bolap3;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.nexbytes.bolap3.data.AppExecutors;
import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.data.MusicRepository;

/**
 * Routes to the library if one exists, otherwise to the welcome screen. Also receives voice
 * searches ("play X on HiFi Player") and hands them to the player. Has no UI.
 */
public class LauncherActivity extends AppCompatActivity {

    private volatile boolean routed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Keep the splash screen up while the library is checked, instead of a blank window.
        SplashScreen.installSplashScreen(this).setKeepOnScreenCondition(() -> !routed);
        super.onCreate(savedInstanceState);

        if (!LibraryPrefs.isLibraryReady(this)) {
            open(new Intent(this, WelcomeActivity.class));
            return;
        }

        boolean voiceSearch = MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(getIntent().getAction());
        String query = getIntent().getStringExtra(SearchManager.QUERY);

        AppExecutors.db().execute(() -> {
            int songCount = MusicRepository.getSongCount(this);
            if (songCount > 0) {
                MusicRepository.loadBlocking(this);
            }
            AppExecutors.main(() -> {
                if (songCount == 0) {
                    LibraryPrefs.clear(this);
                    open(new Intent(this, WelcomeActivity.class));
                } else if (voiceSearch) {
                    open(new Intent(this, PlayerActivity.class)
                            .putExtra(PlayerActivity.EXTRA_SEARCH_QUERY, query != null ? query : ""));
                } else {
                    open(new Intent(this, LibraryActivity.class));
                }
            });
        });
    }

    private void open(Intent intent) {
        routed = true;
        startActivity(intent);
        finish();
    }
}
