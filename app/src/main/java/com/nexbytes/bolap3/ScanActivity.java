package com.nexbytes.bolap3;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.ViewModelProvider;

import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.scan.ScanViewModel;
import com.nexbytes.bolap3.ui.BaseActivity;

/**
 * Shows scan progress. The scan itself lives in {@link ScanViewModel}, so rotating the screen
 * does not restart or cancel it. Cancelling keeps the previous library untouched.
 */
public class ScanActivity extends BaseActivity {

    private TextView tvScanCounter;
    private ScanViewModel viewModel;
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        tvScanCounter = findViewById(R.id.tvScanCounter);
        TextView tvScanSubtitle = findViewById(R.id.tvScanSubtitle);

        String scanMode = getIntent().getStringExtra(LibraryPrefs.EXTRA_SCAN_MODE);
        String folderUri = getIntent().getStringExtra(LibraryPrefs.EXTRA_FOLDER_URI);
        tvScanSubtitle.setText(LibraryPrefs.SCAN_MODE_SAF_FOLDER.equals(scanMode)
                ? R.string.scan_subtitle_folder
                : R.string.scan_subtitle_device);

        findViewById(R.id.btnCancelScan).setOnClickListener(view -> cancelScan());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cancelScan();
            }
        });

        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        viewModel.getState().observe(this, this::render);
        viewModel.start(scanMode, folderUri);
    }

    private void render(ScanViewModel.State state) {
        tvScanCounter.setText(getResources().getQuantityString(R.plurals.songs_found, state.count, state.count));

        switch (state.status) {
            case DONE:
                navigate(new Intent(this, LibraryActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                break;
            case EMPTY:
                showStatus(StatusStateActivity.STATE_NO_MUSIC);
                break;
            case PERMISSION_DENIED:
                showStatus(StatusStateActivity.STATE_PERMISSION_DENIED);
                break;
            case FOLDER_UNAVAILABLE:
                showStatus(StatusStateActivity.STATE_FOLDER_UNAVAILABLE);
                break;
            case CANCELLED:
            case RUNNING:
            default:
                break;
        }
    }

    private void cancelScan() {
        viewModel.cancel();
        // Go back to the existing library if there is one; the scan never touched it.
        Class<?> target = LibraryPrefs.isLibraryReady(this) ? LibraryActivity.class : WelcomeActivity.class;
        navigate(new Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void showStatus(String stateType) {
        navigate(new Intent(this, StatusStateActivity.class)
                .putExtra(StatusStateActivity.EXTRA_STATE_TYPE, stateType));
    }

    private void navigate(Intent intent) {
        if (navigated) {
            return;
        }
        navigated = true;
        startActivity(intent);
        finish();
    }
}
