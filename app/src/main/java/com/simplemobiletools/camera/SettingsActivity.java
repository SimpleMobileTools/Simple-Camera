package com.simplemobiletools.camera;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SettingsActivity extends AppCompatActivity {
    @BindView(R.id.settings_long_tap) SwitchCompat longTapSwitch;

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);
        setupLongTap();
    }

    private void setupLongTap() {
        longTapSwitch.setChecked(mConfig.getLongTapEnabled());
    }

    @OnClick(R.id.settings_long_tap_holder)
    public void handleLongTapToTrigger() {
        longTapSwitch.setChecked(!longTapSwitch.isChecked());
        mConfig.setLongTapEnabled(longTapSwitch.isChecked());
    }
}
