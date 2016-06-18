package com.simplemobiletools.camera;

import android.content.Context;
import android.content.SharedPreferences;

public class Config {
    private SharedPreferences prefs;

    public static Config newInstance(Context context) {
        return new Config(context);
    }

    public Config(Context context) {
        prefs = context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE);
    }

    public boolean getLongTapEnabled() {
        return prefs.getBoolean(Constants.LONG_TAP, true);
    }

    public void setLongTapEnabled(boolean enabled) {
        prefs.edit().putBoolean(Constants.LONG_TAP, enabled).apply();
    }
}
