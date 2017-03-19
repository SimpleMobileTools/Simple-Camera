package com.simplemobiletools.camera.extensions

import android.hardware.Camera
import com.simplemobiletools.camera.Constants

fun Int.compensateDeviceRotation(currCameraId: Int): Int {
    var degrees = 0
    val isFrontCamera = currCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT
    if (this == Constants.ORIENT_LANDSCAPE_LEFT) {
        degrees += if (isFrontCamera) 90 else 270
    } else if (this == Constants.ORIENT_LANDSCAPE_RIGHT) {
        degrees += if (isFrontCamera) 270 else 90
    }
    return degrees
}
