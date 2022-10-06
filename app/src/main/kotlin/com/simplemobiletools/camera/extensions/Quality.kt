package com.simplemobiletools.camera.extensions

import androidx.camera.video.Quality
import com.simplemobiletools.camera.models.VideoQuality

fun Quality.toVideoQuality(): VideoQuality {
    return when (this) {
        Quality.UHD -> VideoQuality.UHD
        Quality.FHD -> VideoQuality.FHD
        Quality.HD -> VideoQuality.HD
        Quality.SD -> VideoQuality.SD
        else -> throw IllegalArgumentException("Unsupported quality: $this")
    }
}

fun VideoQuality.toCameraXQuality(): Quality {
    return when (this) {
        VideoQuality.UHD -> Quality.UHD
        VideoQuality.FHD -> Quality.FHD
        VideoQuality.HD -> Quality.HD
        VideoQuality.SD -> Quality.SD
    }
}
