package com.simplemobiletools.camera.extensions

import android.hardware.Camera
import com.simplemobiletools.camera.helpers.ORIENT_LANDSCAPE_LEFT
import com.simplemobiletools.camera.helpers.ORIENT_LANDSCAPE_RIGHT

fun Int.compensateDeviceRotation(currCameraId: Int) = when {
    this == ORIENT_LANDSCAPE_LEFT -> 270
    this == ORIENT_LANDSCAPE_RIGHT -> 90
    currCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT -> 180
    else -> 0
}
