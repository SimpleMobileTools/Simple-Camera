package com.simplemobiletools.camera.extensions

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.helpers.FLASH_ALWAYS_ON
import com.simplemobiletools.camera.helpers.FLASH_AUTO
import com.simplemobiletools.camera.helpers.FLASH_OFF
import com.simplemobiletools.camera.helpers.FLASH_ON

fun Int.toCameraXFlashMode(): Int {
    return when (this) {
        FLASH_ON -> ImageCapture.FLASH_MODE_ON
        FLASH_OFF -> ImageCapture.FLASH_MODE_OFF
        FLASH_AUTO -> ImageCapture.FLASH_MODE_AUTO
        FLASH_ALWAYS_ON -> ImageCapture.FLASH_MODE_OFF
        else -> throw IllegalArgumentException("Unknown mode: $this")
    }
}

fun Int.toAppFlashMode(): Int {
    return when (this) {
        ImageCapture.FLASH_MODE_ON -> FLASH_ON
        ImageCapture.FLASH_MODE_OFF -> FLASH_OFF
        ImageCapture.FLASH_MODE_AUTO -> FLASH_AUTO
        else -> throw IllegalArgumentException("Unknown mode: $this")
    }
}

fun Int.toFlashModeId(): Int {
    return when (this) {
        FLASH_ON -> R.id.flash_on
        FLASH_OFF -> R.id.flash_off
        FLASH_AUTO -> R.id.flash_auto
        FLASH_ALWAYS_ON -> R.id.flash_always_on
        else -> throw IllegalArgumentException("Unknown mode: $this")
    }
}

fun Int.toCameraSelector(): CameraSelector {
    return if (this == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.DEFAULT_FRONT_CAMERA
    } else {
        CameraSelector.DEFAULT_BACK_CAMERA
    }
}

