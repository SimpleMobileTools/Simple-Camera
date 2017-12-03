package com.simplemobiletools.camera.extensions

import android.app.Activity
import android.hardware.Camera
import android.view.Surface

fun Activity.getPreviewRotation(cameraId: Int): Int {
    val info = getCameraInfo(cameraId)
    val degrees = getDeviceRotationDegrees()

    var result: Int
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        result = (info.orientation + degrees) % 360
        result = 360 - result
    } else {
        result = info.orientation - degrees + 360
    }

    return result % 360
}

fun Activity.getDeviceRotationDegrees() = when (windowManager.defaultDisplay.rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}

private fun getCameraInfo(cameraId: Int): Camera.CameraInfo {
    val info = android.hardware.Camera.CameraInfo()
    Camera.getCameraInfo(cameraId, info)
    return info
}
