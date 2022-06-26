package com.simplemobiletools.camera.extensions

import androidx.camera.core.CameraSelector

fun CameraSelector.toLensFacing(): Int {
    return if (this == CameraSelector.DEFAULT_FRONT_CAMERA) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }
}
