package com.simplemobiletools.camera.extensions

import android.content.Context
import com.simplemobiletools.camera.helpers.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getOutputMediaFile(isPhoto: Boolean): String {
    val mediaStorageDir = File(config.savePhotosFolder)

    if (!mediaStorageDir.exists()) {
        if (!mediaStorageDir.mkdirs()) {
            return ""
        }
    }

    val mediaName = getRandomMediaName(isPhoto)
    return if (isPhoto) {
        "${mediaStorageDir.path}/$mediaName.jpg"
    } else {
        "${mediaStorageDir.path}/$mediaName.mp4"
    }
}

fun getRandomMediaName(isPhoto: Boolean): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return if (isPhoto) {
        "IMG_$timestamp"
    } else {
        "VID_$timestamp"
    }
}
