package com.simplemobiletools.camera.models

import androidx.camera.core.CameraSelector

data class CameraSelectorVideoQualities(
    val camSelector: CameraSelector,
    val qualities: List<VideoQuality>,
)
