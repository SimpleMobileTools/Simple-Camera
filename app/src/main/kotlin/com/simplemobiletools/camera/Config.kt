package com.simplemobiletools.camera

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Camera
import android.os.Environment

class Config(context: Context) {
    private val mPrefs: SharedPreferences

    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    init {
        mPrefs = context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE)
    }

    var isFirstRun: Boolean
        get() = mPrefs.getBoolean(Constants.IS_FIRST_RUN, true)
        set(firstRun) = mPrefs.edit().putBoolean(Constants.IS_FIRST_RUN, firstRun).apply()

    var isDarkTheme: Boolean
        get() = mPrefs.getBoolean(Constants.IS_DARK_THEME, false)
        set(isDarkTheme) = mPrefs.edit().putBoolean(Constants.IS_DARK_THEME, isDarkTheme).apply()

    var savePhotosFolder: String
        get() = mPrefs.getString(Constants.SAVE_PHOTOS, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString())
        set(path) = mPrefs.edit().putString(Constants.SAVE_PHOTOS, path).apply()

    var forceRatioEnabled: Boolean
        get() = mPrefs.getBoolean(Constants.FORCE_RATIO, true)
        set(enabled) = mPrefs.edit().putBoolean(Constants.FORCE_RATIO, enabled).apply()

    // todo: delete this
    val maxResolution: Int
        get() = mPrefs.getInt(Constants.MAX_RESOLUTION, -1)

    var maxPhotoResolution: Int
        get() = mPrefs.getInt(Constants.MAX_PHOTO_RESOLUTION, oldDefaultResolution)
        set(maxRes) = mPrefs.edit().putInt(Constants.MAX_PHOTO_RESOLUTION, maxRes).apply()

    private val oldDefaultResolution: Int
        get() {
            return when (maxResolution) {
                1 -> Constants.EIGHT_MPX
                2 -> 0
                else -> Constants.FIVE_MPX
            }
        }

    var maxVideoResolution: Int
        get() {
            val maxRes = mPrefs.getInt(Constants.MAX_VIDEO_RESOLUTION, Constants.P720)
            return when (maxRes) {
                0 -> Constants.P480
                2 -> Constants.P1080
                else -> Constants.P720
            }
        }
        set(maxRes) = mPrefs.edit().putInt(Constants.MAX_VIDEO_RESOLUTION, maxRes).apply()

    var isSoundEnabled: Boolean
        get() = mPrefs.getBoolean(Constants.SOUND, true)
        set(enabled) = mPrefs.edit().putBoolean(Constants.SOUND, enabled).apply()

    var lastUsedCamera: Int
        get() = mPrefs.getInt(Constants.LAST_USED_CAMERA, Camera.CameraInfo.CAMERA_FACING_BACK)
        set(cameraId) = mPrefs.edit().putInt(Constants.LAST_USED_CAMERA, cameraId).apply()

    var lastFlashlightState: Boolean
        get() = mPrefs.getBoolean(Constants.LAST_FLASHLIGHT_STATE, false)
        set(enabled) = mPrefs.edit().putBoolean(Constants.LAST_FLASHLIGHT_STATE, enabled).apply()

    var treeUri: String
        get() = mPrefs.getString(Constants.TREE_URI, "")
        set(uri) = mPrefs.edit().putString(Constants.TREE_URI, uri).apply()
}
