package com.simplemobiletools.camera.extensions

import android.content.Context
import com.simplemobiletools.camera.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val Context.config: Config get() = Config.newInstance(this)

fun Context.getOutputMediaFile(isPhoto: Boolean): String {
    val mediaStorageDir = File(config.savePhotosFolder)

    if (!mediaStorageDir.exists()) {
        if (!mediaStorageDir.mkdirs()) {
            return ""
        }
    }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return if (isPhoto) {
        "${mediaStorageDir.path}${File.separator}IMG_$timestamp.jpg"
    } else {
        "${mediaStorageDir.path}${File.separator}VID_$timestamp.mp4"
    }
}
