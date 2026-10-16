package com.nexbytes.bolap3;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.TaskStackBuilder;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.nexbytes.bolap3.data.LibraryFilterPrefs;
import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.data.SettingsPrefs;
import com.nexbytes.bolap3.ui.BaseActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsActivity extends BaseActivity {

    private static final int[] MIN_DURATIONS = {0, 10, 30, 60};

    private TextView tvExcludedSummary;
    private TextView tvMinDurationSummary;
    private TextView tvThemeSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToolbar(R.string.settings_title);

        tvExcludedSummary = findViewById(R.id.tvExcludedSummary);
        tvMinDurationSummary = findViewById(R.id.tvMinDurationSummary);
        tvThemeSummary = findViewById(R.id.tvThemeSummary);

        setupLibrarySection();
        setupPlaybackSection();
        setupAppearanceSection();

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.tvVersion)).setText(getString(R.string.settings_version, version));
        } catch (PackageManager.NameNotFoundException ignored) {
            // Not possible for our own package.
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSummaries();
    }

    private void setupLibrarySection() {
        boolean folderMode = LibraryPrefs.SCAN_MODE_SAF_FOLDER.equals(LibraryPrefs.getScanMode(this));
        ((TextView) findViewById(R.id.tvRescanSummary)).setText(folderMode
                ? R.string.settings_rescan_summary_folder
                : R.string.settings_rescan_summary_device);

        findViewById(R.id.rowRescan).setOnClickListener(view -> {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra(LibraryPrefs.EXTRA_SCAN_MODE, LibraryPrefs.getScanMode(this));
            intent.putExtra(LibraryPrefs.EXTRA_FOLDER_URI, LibraryPrefs.getFolderUri(this));
            startActivity(intent);
        });
        findViewById(R.id.rowChangeSource).setOnClickListener(view ->
                startActivity(new Intent(this, WelcomeActivity.class)));
        findViewById(R.id.rowExcluded).setOnClickListener(view -> showExcludedFolders());
        findViewById(R.id.rowMinDuration).setOnClickListener(view -> showMinDurationDialog());

        MaterialSwitch autoRefresh = findViewById(R.id.switchAutoRefresh);
        autoRefresh.setChecked(SettingsPrefs.isAutoRefresh(this));
        autoRefresh.setOnCheckedChangeListener((button, checked) -> SettingsPrefs.setAutoRefresh(this, checked));
    }

    private void setupPlaybackSection() {
        MaterialSwitch skipSilence = findViewById(R.id.switchSkipSilence);
        skipSilence.setChecked(SettingsPrefs.isSkipSilence(this));
        skipSilence.setOnCheckedChangeListener((button, checked) -> SettingsPrefs.setSkipSilence(this, checked));

        MaterialSwitch replayGain = findViewById(R.id.switchReplayGain);
        replayGain.setChecked(SettingsPrefs.isReplayGain(this));
        replayGain.setOnCheckedChangeListener((button, checked) -> SettingsPrefs.setReplayGain(this, checked));

        MaterialSwitch resumeHeadphones = findViewById(R.id.switchResumeHeadphones);
        resumeHeadphones.setChecked(SettingsPrefs.isResumeOnHeadphones(this));
        resumeHeadphones.setOnCheckedChangeListener((button, checked) ->
                SettingsPrefs.setResumeOnHeadphones(this, checked));

        findViewById(R.id.rowEqualizer).setOnClickListener(view ->
                startActivity(new Intent(this, EqualizerActivity.class)));
    }

    private void setupAppearanceSection() {
        findViewById(R.id.rowTheme).setOnClickListener(view -> showThemeDialog());

        MaterialSwitch artworkColors = findViewById(R.id.switchArtworkColors);
        artworkColors.setChecked(SettingsPrefs.isArtworkColors(this));
        artworkColors.setOnCheckedChangeListener((button, checked) -> {
            SettingsPrefs.setArtworkColors(this, checked);
            // Screens recompute it on their next song update.
            com.nexbytes.bolap3.media.NowPlayingAccent.invalidate();
        });

        MaterialSwitch dynamicColors = findViewById(R.id.switchDynamicColors);
        dynamicColors.setChecked(SettingsPrefs.isDynamicColors(this));
        dynamicColors.setOnCheckedChangeListener((button, checked) -> {
            SettingsPrefs.setDynamicColors(this, checked);
            // Colors are applied when activities are created: rebuild the library + settings stack.
            TaskStackBuilder.create(this)
                    .addNextIntent(new Intent(this, LibraryActivity.class))
                    .addNextIntent(new Intent(this, SettingsActivity.class))
                    .startActivities();
            finish();
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        });
    }

    private void refreshSummaries() {
        int excluded = LibraryFilterPrefs.getExcludedFolders(this).size();
        tvExcludedSummary.setText(excluded == 0
                ? getString(R.string.settings_excluded_none)
                : getResources().getQuantityString(R.plurals.settings_excluded_count, excluded, excluded));

        int seconds = SettingsPrefs.getMinDurationSeconds(this);
        tvMinDurationSummary.setText(seconds == 0
                ? getString(R.string.settings_min_duration_none)
                : getResources().getQuantityString(R.plurals.settings_seconds, seconds, seconds));

        String theme = SettingsPrefs.getTheme(this);
        tvThemeSummary.setText(SettingsPrefs.THEME_LIGHT.equals(theme) ? R.string.settings_theme_light
                : SettingsPrefs.THEME_SYSTEM.equals(theme) ? R.string.settings_theme_system
                : R.string.settings_theme_dark);
    }

    private void showExcludedFolders() {
        List<String> folders = new ArrayList<>(LibraryFilterPrefs.getExcludedFolders(this));
        if (folders.isEmpty()) {
            Toast.makeText(this, R.string.settings_excluded_none, Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.sort(folders);
        String[] labels = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            labels[i] = displayName(folders.get(i));
        }
        boolean[] checked = new boolean[folders.size()];

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_excluded_dialog)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.settings_excluded_restore, (dialog, which) -> {
                    List<String> restore = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) restore.add(folders.get(i));
                    }
                    if (!restore.isEmpty()) {
                        LibraryFilterPrefs.restoreFolders(this, restore);
                        Toast.makeText(this, R.string.settings_excluded_restored, Toast.LENGTH_LONG).show();
                        refreshSummaries();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** "Music/Rock/" stays as is; SAF document URIs become their readable path. */
    private static String displayName(String folderPath) {
        Uri uri = Uri.parse(folderPath);
        if (DocumentsContract.isTreeUri(uri)) {
            try {
                String documentId = DocumentsContract.getDocumentId(uri);
                int colon = documentId.indexOf(':');
                return colon >= 0 ? documentId.substring(colon + 1) : documentId;
            } catch (IllegalArgumentException ignored) {
                return folderPath;
            }
        }
        return folderPath;
    }

    private void showMinDurationDialog() {
        String[] labels = new String[MIN_DURATIONS.length];
        int current = SettingsPrefs.getMinDurationSeconds(this);
        int checked = 0;
        for (int i = 0; i < MIN_DURATIONS.length; i++) {
            labels[i] = MIN_DURATIONS[i] == 0
                    ? getString(R.string.settings_min_duration_none)
                    : getResources().getQuantityString(R.plurals.settings_seconds, MIN_DURATIONS[i], MIN_DURATIONS[i]);
            if (MIN_DURATIONS[i] == current) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_min_duration)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    SettingsPrefs.setMinDurationSeconds(this, MIN_DURATIONS[which]);
                    refreshSummaries();
                    dialog.dismiss();
                    Toast.makeText(this, R.string.settings_min_duration_changed, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showThemeDialog() {
        String[] values = {SettingsPrefs.THEME_SYSTEM, SettingsPrefs.THEME_LIGHT, SettingsPrefs.THEME_DARK};
        String[] labels = {
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
        };
        String current = SettingsPrefs.getTheme(this);
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    SettingsPrefs.setTheme(this, values[which]);
                    SettingsPrefs.applyTheme(this, values[which]);
                })
                .show();
    }
}
