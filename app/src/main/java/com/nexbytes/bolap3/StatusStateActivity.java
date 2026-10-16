package com.nexbytes.bolap3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;

import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.ui.BaseActivity;
import com.nexbytes.bolap3.ui.FolderPicker;

/** One screen for every error/empty state, chosen with {@link #EXTRA_STATE_TYPE}. */
public class StatusStateActivity extends BaseActivity {

    public static final String EXTRA_STATE_TYPE = "state_type";

    public static final String STATE_NO_MUSIC = "no_music_found";
    public static final String STATE_PERMISSION_DENIED = "permission_denied";
    public static final String STATE_FOLDER_UNAVAILABLE = "folder_unavailable";
    public static final String STATE_FILE_MISSING = "file_missing";
    public static final String STATE_PLAYBACK_ERROR = "playback_error";

    private TextView tvStatusIcon;
    private TextView tvStatusTitle;
    private TextView tvStatusMessage;
    private Button btnPrimaryAction;
    private Button btnSecondaryAction;

    private ActivityResultLauncher<Uri> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_state);

        tvStatusIcon = findViewById(R.id.tvStatusIcon);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusMessage = findViewById(R.id.tvStatusMessage);
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
        btnSecondaryAction = findViewById(R.id.btnSecondaryAction);

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        FolderPicker.persist(getContentResolver(), uri);
                        startScan(LibraryPrefs.SCAN_MODE_SAF_FOLDER, uri.toString());
                    }
                });

        renderState(getIntent().getStringExtra(EXTRA_STATE_TYPE));
    }

    private void renderState(String stateType) {
        if (STATE_PERMISSION_DENIED.equals(stateType)) {
            render("!", R.string.status_permission_title, R.string.status_permission_message,
                    R.string.status_pick_folder, this::openFolderPicker,
                    R.string.status_go_home, this::goToWelcome);
        } else if (STATE_FOLDER_UNAVAILABLE.equals(stateType)) {
            render("!", R.string.status_folder_title, R.string.status_folder_message,
                    R.string.status_pick_other_folder, this::openFolderPicker,
                    R.string.status_go_home, this::goToWelcome);
        } else if (STATE_FILE_MISSING.equals(stateType)) {
            render("?", R.string.status_file_title, R.string.status_file_message,
                    R.string.status_go_library, this::goToLibrary,
                    R.string.status_retry_scan, this::retryMediaStoreScan);
        } else if (STATE_PLAYBACK_ERROR.equals(stateType)) {
            render("!", R.string.status_playback_title, R.string.status_playback_message,
                    R.string.status_go_library, this::goToLibrary,
                    R.string.status_retry_scan, this::retryMediaStoreScan);
        } else {
            render("♪", R.string.status_no_music_title, R.string.status_no_music_message,
                    R.string.status_pick_folder, this::openFolderPicker,
                    R.string.status_retry_scan, this::retryMediaStoreScan);
        }
    }

    private void render(String icon, @StringRes int title, @StringRes int message,
                        @StringRes int primary, Runnable primaryAction,
                        @StringRes int secondary, Runnable secondaryAction) {
        tvStatusIcon.setText(icon);
        tvStatusTitle.setText(title);
        tvStatusMessage.setText(message);
        btnPrimaryAction.setText(primary);
        btnSecondaryAction.setText(secondary);
        btnPrimaryAction.setOnClickListener(view -> primaryAction.run());
        btnSecondaryAction.setOnClickListener(view -> secondaryAction.run());
    }

    private void openFolderPicker() {
        folderPickerLauncher.launch(null);
    }

    private void retryMediaStoreScan() {
        startScan(LibraryPrefs.SCAN_MODE_MEDIA_STORE, null);
    }

    private void startScan(String mode, String folderUri) {
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra(LibraryPrefs.EXTRA_SCAN_MODE, mode);
        intent.putExtra(LibraryPrefs.EXTRA_FOLDER_URI, folderUri);
        startActivity(intent);
        finish();
    }

    private void goToWelcome() {
        startActivity(new Intent(this, WelcomeActivity.class));
        finish();
    }

    private void goToLibrary() {
        // Without a library (first scan failed) the launcher sends the user to the welcome screen.
        startActivity(new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
