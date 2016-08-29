package com.simplemobiletools.camera.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuItem;

import com.simplemobiletools.camera.Config;
import com.simplemobiletools.camera.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;

public class SettingsActivity extends SimpleActivity {
    @BindView(R.id.settings_dark_theme) SwitchCompat mDarkThemeSwitch;
    @BindView(R.id.settings_use_dcim) SwitchCompat mUseDCIMSwitch;
    @BindView(R.id.settings_focus_before_capture) SwitchCompat mFocusBeforeCaptureSwitch;
    @BindView(R.id.settings_sound) SwitchCompat mSoundSwitch;
    @BindView(R.id.settings_force_ratio) SwitchCompat mForceRatioSwitch;
    @BindView(R.id.settings_max_photo_resolution) AppCompatSpinner mMaxPhotoResolutionSpinner;
    @BindView(R.id.settings_max_video_resolution) AppCompatSpinner mMaxVideoResolutionSpinner;

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);

        setupDarkTheme();
        setupUseDCIM();
        setupFocusBeforeCapture();
        setupSound();
        setupForceRatio();
        setupMaxPhotoResolution();
        setupMaxVideoResolution();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                final Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupDarkTheme() {
        mDarkThemeSwitch.setChecked(mConfig.getIsDarkTheme());
    }

    private void setupUseDCIM() {
        mUseDCIMSwitch.setChecked(mConfig.getUseDCIMFolder());
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

    private void setupMaxPhotoResolution() {
        mMaxPhotoResolutionSpinner.setSelection(mConfig.getMaxPhotoResolution());
    }

    private void setupMaxVideoResolution() {
        mMaxVideoResolutionSpinner.setSelection(mConfig.getMaxVideoResolution());
    }

    @OnClick(R.id.settings_dark_theme_holder)
    public void handleDarkTheme() {
        mDarkThemeSwitch.setChecked(!mDarkThemeSwitch.isChecked());
        mConfig.setIsDarkTheme(mDarkThemeSwitch.isChecked());
        restartActivity();
    }

    @OnClick(R.id.settings_use_dcim_holder)
    public void handleUseDCIM() {
        mUseDCIMSwitch.setChecked(!mUseDCIMSwitch.isChecked());
        mConfig.setUseDCIMFolder(mUseDCIMSwitch.isChecked());
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

    @OnItemSelected(R.id.settings_max_photo_resolution)
    public void handleMaxPhotoResolution() {
        mConfig.setMaxPhotoResolution(mMaxPhotoResolutionSpinner.getSelectedItemPosition());
    }

    @OnItemSelected(R.id.settings_max_video_resolution)
    public void handleMaxVideoResolution() {
        mConfig.setMaxVideoResolution(mMaxVideoResolutionSpinner.getSelectedItemPosition());
    }

    private void restartActivity() {
        TaskStackBuilder.create(getApplicationContext()).addNextIntentWithParentStack(getIntent()).startActivities();
    }
}
