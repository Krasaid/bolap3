package com.nexbytes.bolap3;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.nexbytes.bolap3.data.LibraryPrefs;
import com.nexbytes.bolap3.ui.BaseActivity;
import com.nexbytes.bolap3.ui.FolderPicker;

public class WelcomeActivity extends BaseActivity {

    private ActivityResultLauncher<String> audioPermissionLauncher;
    private ActivityResultLauncher<Uri> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        goToScan(LibraryPrefs.SCAN_MODE_MEDIA_STORE, null);
                    } else {
                        showPermissionDenied();
                    }
                });

        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        FolderPicker.persist(getContentResolver(), uri);
                        goToScan(LibraryPrefs.SCAN_MODE_SAF_FOLDER, uri.toString());
                    }
                });

        findViewById(R.id.btnScanDevice).setOnClickListener(view -> requestAudioPermission());
        findViewById(R.id.btnPickFolder).setOnClickListener(view -> folderPickerLauncher.launch(null));
    }

    private void requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            goToScan(LibraryPrefs.SCAN_MODE_MEDIA_STORE, null);
        } else {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO);
        }
    }

    private void showPermissionDenied() {
        Intent intent = new Intent(this, StatusStateActivity.class);
        intent.putExtra(StatusStateActivity.EXTRA_STATE_TYPE, StatusStateActivity.STATE_PERMISSION_DENIED);
        startActivity(intent);
    }

    private void goToScan(String scanMode, String folderUri) {
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra(LibraryPrefs.EXTRA_SCAN_MODE, scanMode);
        intent.putExtra(LibraryPrefs.EXTRA_FOLDER_URI, folderUri);
        startActivity(intent);
        finish();
    }
}
