package com.simplemobiletools.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;

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

    public boolean getIsDarkTheme() {
        return mPrefs.getBoolean(Constants.IS_DARK_THEME, false);
    }

    public void setIsDarkTheme(boolean isDarkTheme) {
        mPrefs.edit().putBoolean(Constants.IS_DARK_THEME, isDarkTheme).apply();
    }

    public boolean getUseDCIMFolder() {
        return mPrefs.getBoolean(Constants.USE_DCIM, true);
    }

    public void setUseDCIMFolder(boolean useDCIM) {
        mPrefs.edit().putBoolean(Constants.USE_DCIM, useDCIM).apply();
    }

    public boolean getForceRatioEnabled() {
        return mPrefs.getBoolean(Constants.FORCE_RATIO, true);
    }

    public void setForceRatioEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.FORCE_RATIO, enabled).apply();
    }

    public int getMaxPhotoResolution() {
        return mPrefs.getInt(Constants.MAX_RESOLUTION, 1);
    }

    public void setMaxPhotoResolution(int maxRes) {
        mPrefs.edit().putInt(Constants.MAX_RESOLUTION, maxRes).apply();
    }

    public int getMaxVideoResolution() {
        return mPrefs.getInt(Constants.MAX_VIDEO_RESOLUTION, 1);
    }

    public void setMaxVideoResolution(int maxRes) {
        mPrefs.edit().putInt(Constants.MAX_VIDEO_RESOLUTION, maxRes).apply();
    }

    public boolean getIsSoundEnabled() {
        return mPrefs.getBoolean(Constants.SOUND, true);
    }

    public void setIsSoundEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Constants.SOUND, enabled).apply();
    }

    public int getLastUsedCamera() {
        return mPrefs.getInt(Constants.LAST_USED_CAMERA, Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public void setLastUsedCamera(int cameraId) {
        mPrefs.edit().putInt(Constants.LAST_USED_CAMERA, cameraId).apply();
    }
}
