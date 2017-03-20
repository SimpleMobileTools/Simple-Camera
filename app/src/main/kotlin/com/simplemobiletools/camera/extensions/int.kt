package com.simplemobiletools.camera.extensions

import android.hardware.Camera
import com.simplemobiletools.camera.ORIENT_LANDSCAPE_LEFT
import com.simplemobiletools.camera.ORIENT_LANDSCAPE_RIGHT

fun Int.compensateDeviceRotation(currCameraId: Int): Int {
    val isFrontCamera = currCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT
    return if (this == ORIENT_LANDSCAPE_LEFT) {
        if (isFrontCamera) 90 else 270
    } else if (this == ORIENT_LANDSCAPE_RIGHT) {
        if (isFrontCamera) 270 else 90
    } else
        0
}
