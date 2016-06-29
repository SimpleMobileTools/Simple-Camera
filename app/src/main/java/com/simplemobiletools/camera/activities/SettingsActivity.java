package com.simplemobiletools.camera.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;

import com.simplemobiletools.camera.Config;
import com.simplemobiletools.camera.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SettingsActivity extends AppCompatActivity {
    @BindView(R.id.settings_long_tap) SwitchCompat mLongTapSwitch;
    @BindView(R.id.settings_focus_before_capture) SwitchCompat mFocusBeforeCaptureSwitch;
    @BindView(R.id.settings_force_ratio) SwitchCompat mForceRatioSwitch;

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);

        setupLongTap();
        setupFocusBeforeCapture();
        setupForceRatio();
    }

    private void setupLongTap() {
        mLongTapSwitch.setChecked(mConfig.getLongTapEnabled());
    }

    private void setupFocusBeforeCapture() {
        mFocusBeforeCaptureSwitch.setChecked(mConfig.getFocusBeforeCaptureEnabled());
    }

    private void setupForceRatio() {
        mForceRatioSwitch.setChecked(mConfig.getForceRatioEnabled());
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

    @OnClick(R.id.settings_force_ratio_holder)
    public void handleForceRatio() {
        mForceRatioSwitch.setChecked(!mForceRatioSwitch.isChecked());
        mConfig.setForceRatioEnabled(mForceRatioSwitch.isChecked());
    }
}
