package com.simplemobiletools.camera;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {
    private SharedPreferences mPrefs;

    public static Config newInstance(Context context) {
        return new Config(context);
    }

    public Config(Context context) {
        mPrefs = context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE);
    }

    public boolean getIsFirstRun() {
        return mPrefs.getBoolean(Constants.IS_FIRST_RUN, true);
    }

    public void setIsFirstRun(boolean firstRun) {
        mPrefs.edit().putBoolean(Constants.IS_FIRST_RUN, firstRun).apply();
    }

    public boolean getLongTapEnabled() {
        return mPrefs.getBoolean(Constants.LONG_TAP, true);
    }

    public void setLongTapEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.LONG_TAP, enabled).apply();
    }

    public boolean getFocusBeforeCaptureEnabled() {
        return mPrefs.getBoolean(Constants.FOCUS_BEFORE_CAPTURE, false);
    }

    public void setFocusBeforeCaptureEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.FOCUS_BEFORE_CAPTURE, enabled).apply();
    }

    public boolean getForceRatioEnabled() {
        return mPrefs.getBoolean(Constants.FORCE_RATIO, true);
    }

    public void setForceRatioEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.FORCE_RATIO, enabled).apply();
    }

    public int getMaxResolution() {
        return mPrefs.getInt(Constants.MAX_RESOLUTION, 1);
    }

    public void setMaxResolution(int maxRes) {
        mPrefs.edit().putInt(Constants.MAX_RESOLUTION, maxRes).apply();
    }

    public boolean getIsSoundEnabled() {
        return mPrefs.getBoolean(Constants.SOUND, true);
    }

    public void setIsSoundEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.SOUND, enabled).apply();
    }
}
