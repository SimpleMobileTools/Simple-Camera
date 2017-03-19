package com.simplemobiletools.camera

import android.content.Context
import android.hardware.Camera
import android.os.Environment
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var savePhotosFolder: String
        get() = prefs.getString(SAVE_PHOTOS, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString())
        set(path) = prefs.edit().putString(SAVE_PHOTOS, path).apply()

    var isShowPreviewEnabled: Boolean
        get() = prefs.getBoolean(SHOW_PREVIEW, false)
        set(enabled) = prefs.edit().putBoolean(SHOW_PREVIEW, enabled).apply()

    var forceRatioEnabled: Boolean
        get() = prefs.getBoolean(FORCE_RATIO, true)
        set(enabled) = prefs.edit().putBoolean(FORCE_RATIO, enabled).apply()

    var maxPhotoResolution: Int
        get() = prefs.getInt(MAX_PHOTO_RESOLUTION, FIVE_MPX)
        set(maxRes) = prefs.edit().putInt(MAX_PHOTO_RESOLUTION, maxRes).apply()

    var maxVideoResolution: Int
        get() = prefs.getInt(MAX_VIDEO_RESOLUTION, P720)
        set(maxRes) = prefs.edit().putInt(MAX_VIDEO_RESOLUTION, maxRes).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(SOUND, true)
        set(enabled) = prefs.edit().putBoolean(SOUND, enabled).apply()

    var lastUsedCamera: Int
        get() = prefs.getInt(LAST_USED_CAMERA, Camera.CameraInfo.CAMERA_FACING_BACK)
        set(cameraId) = prefs.edit().putInt(LAST_USED_CAMERA, cameraId).apply()

    var lastFlashlightState: Boolean
        get() = prefs.getBoolean(LAST_FLASHLIGHT_STATE, false)
        set(enabled) = prefs.edit().putBoolean(LAST_FLASHLIGHT_STATE, enabled).apply()
}
