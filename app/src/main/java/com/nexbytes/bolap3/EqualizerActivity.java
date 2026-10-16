package com.nexbytes.bolap3;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.media3.common.Player;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.nexbytes.bolap3.audio.EqualizerManager;
import com.nexbytes.bolap3.playback.PlayerConnection;
import com.nexbytes.bolap3.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in equalizer (bands + presets + bass boost), with a shortcut to the system one. */
public class EqualizerActivity extends BaseActivity {

    private PlayerConnection connection;
    private MaterialSwitch switchEq;
    private View eqControls;
    private TextView tvMessage;
    private ChipGroup chipGroupPresets;
    private LinearLayout eqBands;
    private SeekBar seekBass;
    private final List<SeekBar> bandSeekBars = new ArrayList<>();
    private final List<TextView> bandLabels = new ArrayList<>();
    private boolean built = false;
    private boolean updatingUi = false;

    private ActivityResultLauncher<Intent> systemEqualizerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);
        setupToolbar(R.string.eq_title);

        switchEq = findViewById(R.id.switchEq);
        eqControls = findViewById(R.id.eqControls);
        tvMessage = findViewById(R.id.tvEqMessage);
        chipGroupPresets = findViewById(R.id.chipGroupPresets);
        eqBands = findViewById(R.id.eqBands);
        seekBass = findViewById(R.id.seekBass);

        systemEqualizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> { });

        findViewById(R.id.btnSystemEq).setOnClickListener(view -> openSystemEqualizer());
        findViewById(R.id.btnEqReset).setOnClickListener(view -> {
            EqualizerManager.reset(this);
            refreshValues();
        });

        // Connecting makes sure the playback service (and so the audio session) exists.
        connection = new PlayerConnection(this, new Player.Listener() { });
    }

    @Override
    protected void onStart() {
        super.onStart();
        connection.connect(controller -> buildUi());
        buildUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        connection.disconnect();
    }

    private void buildUi() {
        if (built) {
            return;
        }
        if (!EqualizerManager.isAvailable()) {
            tvMessage.setText(connection.get() == null ? R.string.eq_no_session : R.string.eq_unavailable);
            tvMessage.setVisibility(View.VISIBLE);
            switchEq.setVisibility(View.GONE);
            eqControls.setVisibility(View.GONE);
            return;
        }
        built = true;
        tvMessage.setVisibility(View.GONE);
        switchEq.setVisibility(View.VISIBLE);
        eqControls.setVisibility(View.VISIBLE);

        switchEq.setChecked(EqualizerManager.isEnabled(this));
        switchEq.setOnCheckedChangeListener((button, checked) -> {
            EqualizerManager.setEnabled(this, checked);
            setControlsEnabled(checked);
        });

        buildPresets();
        buildBands();

        boolean bass = EqualizerManager.isBassBoostAvailable();
        findViewById(R.id.tvBassTitle).setVisibility(bass ? View.VISIBLE : View.GONE);
        seekBass.setVisibility(bass ? View.VISIBLE : View.GONE);
        seekBass.setProgress(EqualizerManager.getBassStrength(this));
        seekBass.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    EqualizerManager.setBassStrength(EqualizerActivity.this, progress);
                }
            }
        });

        setControlsEnabled(switchEq.isChecked());
    }

    private void buildPresets() {
        chipGroupPresets.removeAllViews();
        List<String> presets = EqualizerManager.getPresetNames();
        int selected = EqualizerManager.getPreset(this);

        Chip custom = newChip(getString(R.string.eq_preset_custom), -1);
        chipGroupPresets.addView(custom);
        custom.setChecked(selected < 0);

        for (int i = 0; i < presets.size(); i++) {
            Chip chip = newChip(presets.get(i), i);
            chipGroupPresets.addView(chip);
            chip.setChecked(i == selected);
        }
    }

    private Chip newChip(String label, int preset) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(preset);
        chip.setOnClickListener(view -> {
            if (preset >= 0) {
                EqualizerManager.usePreset(this, preset);
                refreshValues();
            }
        });
        return chip;
    }

    private void buildBands() {
        eqBands.removeAllViews();
        bandSeekBars.clear();
        bandLabels.clear();

        short[] range = EqualizerManager.getBandLevelRange();
        int min = range[0];
        int max = range[1];
        LayoutInflater inflater = getLayoutInflater();

        for (int band = 0; band < EqualizerManager.getBandCount(); band++) {
            View row = inflater.inflate(R.layout.item_eq_band, eqBands, false);
            TextView frequency = row.findViewById(R.id.tvBandFreq);
            SeekBar seekBar = row.findViewById(R.id.seekBand);
            TextView level = row.findViewById(R.id.tvBandLevel);

            int hz = EqualizerManager.getCenterFrequencyHz(band);
            frequency.setText(hz >= 1000
                    ? getString(R.string.eq_band_khz, formatNumber(hz / 1000f))
                    : getString(R.string.eq_band_hz, hz));
            seekBar.setContentDescription(frequency.getText());
            seekBar.setMax(max - min);

            int bandIndex = band;
            seekBar.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    int millibels = progress + min;
                    level.setText(getString(R.string.eq_db, formatNumber(millibels / 100f)));
                    if (fromUser && !updatingUi) {
                        EqualizerManager.setBandLevel(EqualizerActivity.this, bandIndex, (short) millibels);
                        checkPresetChip(-1);
                    }
                }
            });

            bandSeekBars.add(seekBar);
            bandLabels.add(level);
            eqBands.addView(row);
        }
        refreshValues();
    }

    private void refreshValues() {
        updatingUi = true;
        int min = EqualizerManager.getBandLevelRange()[0];
        for (int band = 0; band < bandSeekBars.size(); band++) {
            int millibels = EqualizerManager.getBandLevel(band);
            bandSeekBars.get(band).setProgress(millibels - min);
            bandLabels.get(band).setText(getString(R.string.eq_db, formatNumber(millibels / 100f)));
        }
        seekBass.setProgress(EqualizerManager.getBassStrength(this));
        checkPresetChip(EqualizerManager.getPreset(this));
        updatingUi = false;
    }

    private void checkPresetChip(int preset) {
        for (int i = 0; i < chipGroupPresets.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupPresets.getChildAt(i);
            chip.setChecked(Integer.valueOf(preset).equals(chip.getTag()));
        }
    }

    private void setControlsEnabled(boolean enabled) {
        eqControls.setAlpha(enabled ? 1f : 0.4f);
        for (SeekBar seekBar : bandSeekBars) {
            seekBar.setEnabled(enabled);
        }
        seekBass.setEnabled(enabled);
        for (int i = 0; i < chipGroupPresets.getChildCount(); i++) {
            chipGroupPresets.getChildAt(i).setEnabled(enabled);
        }
    }

    private void openSystemEqualizer() {
        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, EqualizerManager.getAttachedSessionId())
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        try {
            systemEqualizerLauncher.launch(intent);
        } catch (Exception exception) {
            Toast.makeText(this, R.string.eq_system_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatNumber(float value) {
        return value == Math.floor(value)
                ? String.valueOf((int) value)
                : String.format(Locale.getDefault(), "%.1f", value);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(@NonNull SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(@NonNull SeekBar seekBar) {
        }
    }
}
