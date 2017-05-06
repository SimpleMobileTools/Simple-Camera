package com.simplemobiletools.camera

import android.content.Context
import android.hardware.Camera
import android.os.Environment
import com.simplemobiletools.commons.helpers.BaseConfig
import java.io.File

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var savePhotosFolder: String
        get(): String {
            var path = prefs.getString(SAVE_PHOTOS, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString())
            if (!File(path).exists() || !File(path).isDirectory) {
                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                savePhotosFolder = path
            }
            return path
        }
        set(path) = prefs.edit().putString(SAVE_PHOTOS, path).apply()

    var isShowPreviewEnabled: Boolean
        get() = prefs.getBoolean(SHOW_PREVIEW, false)
        set(enabled) = prefs.edit().putBoolean(SHOW_PREVIEW, enabled).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(SOUND, true)
        set(enabled) = prefs.edit().putBoolean(SOUND, enabled).apply()

    var focusBeforeCapture: Boolean
        get() = prefs.getBoolean(FOCUS_BEFORE_CAPTURE, true)
        set(focus) = prefs.edit().putBoolean(FOCUS_BEFORE_CAPTURE, focus).apply()

    var lastUsedCamera: Int
        get() = prefs.getInt(LAST_USED_CAMERA, Camera.CameraInfo.CAMERA_FACING_BACK)
        set(cameraId) = prefs.edit().putInt(LAST_USED_CAMERA, cameraId).apply()

    var flashlightState: Int
        get() = prefs.getInt(FLASHLIGHT_STATE, FLASH_OFF)
        set(state) = prefs.edit().putInt(FLASHLIGHT_STATE, state).apply()

    var backPhotoResIndex: Int
        get() = prefs.getInt(BACK_PHOTO_RESOLUTION_INDEX, -1)
        set(backPhotoResIndex) = prefs.edit().putInt(BACK_PHOTO_RESOLUTION_INDEX, backPhotoResIndex).apply()

    var backVideoResIndex: Int
        get() = prefs.getInt(BACK_VIDEO_RESOLUTION_INDEX, 0)
        set(backVideoResIndex) = prefs.edit().putInt(BACK_VIDEO_RESOLUTION_INDEX, backVideoResIndex).apply()

    var frontPhotoResIndex: Int
        get() = prefs.getInt(FRONT_PHOTO_RESOLUTION_INDEX, 0)
        set(frontPhotoResIndex) = prefs.edit().putInt(FRONT_PHOTO_RESOLUTION_INDEX, frontPhotoResIndex).apply()

    var frontVideoResIndex: Int
        get() = prefs.getInt(FRONT_VIDEO_RESOLUTION_INDEX, 0)
        set(frontVideoResIndex) = prefs.edit().putInt(FRONT_VIDEO_RESOLUTION_INDEX, frontVideoResIndex).apply()
}
