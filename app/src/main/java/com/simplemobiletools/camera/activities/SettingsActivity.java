package com.simplemobiletools.camera.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;

import com.simplemobiletools.camera.Config;
import com.simplemobiletools.camera.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;

public class SettingsActivity extends AppCompatActivity {
    @BindView(R.id.settings_long_tap) SwitchCompat mLongTapSwitch;
    @BindView(R.id.settings_focus_before_capture) SwitchCompat mFocusBeforeCaptureSwitch;
    @BindView(R.id.settings_sound) SwitchCompat mSoundSwitch;
    @BindView(R.id.settings_force_ratio) SwitchCompat mForceRatioSwitch;
    @BindView(R.id.settings_max_resolution) AppCompatSpinner mMaxResolutionSpinner;

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);

        setupLongTap();
        setupFocusBeforeCapture();
        setupSound();
        setupForceRatio();
        setupMaxResolution();
    }

    private void setupLongTap() {
        mLongTapSwitch.setChecked(mConfig.getLongTapEnabled());
    }

    private void setupFocusBeforeCapture() {
        mFocusBeforeCaptureSwitch.setChecked(mConfig.getFocusBeforeCaptureEnabled());
    }

    private void setupSound() {
        mSoundSwitch.setChecked(mConfig.getIsSoundEnabled());
    }

    private void setupForceRatio() {
        mForceRatioSwitch.setChecked(mConfig.getForceRatioEnabled());
    }

    private void setupMaxResolution() {
        mMaxResolutionSpinner.setSelection(mConfig.getMaxResolution());
    }

    @OnClick(R.id.settings_long_tap_holder)
    public void handleLongTapToTrigger() {
        mLongTapSwitch.setChecked(!mLongTapSwitch.isChecked());
        mConfig.setLongTapEnabled(mLongTapSwitch.isChecked());
    }

    @OnClick(R.id.settings_focus_before_capture_holder)
    public void handleFocusBeforeCapture() {
        mFocusBeforeCaptureSwitch.setChecked(!mFocusBeforeCaptureSwitch.isChecked());
        mConfig.setFocusBeforeCaptureEnabled(mFocusBeforeCaptureSwitch.isChecked());
    }

    @OnClick(R.id.settings_sound_holder)
    public void handleSound() {
        mSoundSwitch.setChecked(!mSoundSwitch.isChecked());
        mConfig.setIsSoundEnabled(mSoundSwitch.isChecked());
    }

    @OnClick(R.id.settings_force_ratio_holder)
    public void handleForceRatio() {
        mForceRatioSwitch.setChecked(!mForceRatioSwitch.isChecked());
        mConfig.setForceRatioEnabled(mForceRatioSwitch.isChecked());
    }

    @OnItemSelected(R.id.settings_max_resolution)
    public void handleMaxResolution() {
        mConfig.setMaxResolution(mMaxResolutionSpinner.getSelectedItemPosition());
    }
}
