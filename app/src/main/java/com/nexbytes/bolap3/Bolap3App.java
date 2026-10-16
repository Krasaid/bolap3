package com.nexbytes.bolap3;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheIndexManager;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.scan.LibraryAutoRefresh;

public class Bolap3App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        SettingsPrefs.applyTheme(this, SettingsPrefs.getTheme(this));
        // Material You colors, only when the user turned them on in Settings.
        DynamicColors.applyToActivitiesIfAvailable(this, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> SettingsPrefs.isDynamicColors(activity))
                .build());

        LibraryAutoRefresh.register(this);

        FirebaseApp.initializeApp(this);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings settings =
                new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
                        .setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder().build()
                        )
                        .build();

        db.setFirestoreSettings(settings);

        PersistentCacheIndexManager indexManager = db.getPersistentCacheIndexManager();

        if (indexManager != null) {
            indexManager.enableIndexAutoCreation();
        }
    }
}
