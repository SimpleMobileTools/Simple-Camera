package com.simplemobiletools.camera.extensions

import android.content.Context
import com.simplemobiletools.camera.helpers.Config
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.helpers.PERMISSION_ACCESS_COARSE_LOCATION
import com.simplemobiletools.commons.helpers.PERMISSION_ACCESS_FINE_LOCATION
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.getOutputMediaFilePath(isPhoto: Boolean): String {
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
fun Context.getOutputMediaFileName(isPhoto: Boolean): String {
    val mediaName = getRandomMediaName(isPhoto)
    return if (isPhoto) {
        "$mediaName.jpg"
    } else {
        "$mediaName.mp4"
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

fun Context.checkLocationPermission(): Boolean {
    return hasPermission(PERMISSION_ACCESS_FINE_LOCATION) || hasPermission(PERMISSION_ACCESS_COARSE_LOCATION)
}
