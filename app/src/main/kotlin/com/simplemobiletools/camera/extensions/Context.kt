package com.simplemobiletools.camera.extensions

import android.content.Context
import android.os.Build
import com.simplemobiletools.filepicker.extensions.getSDCardPath

fun Context.isPathOnSD(path: String) = path.startsWith(getSDCardPath())

fun Context.isKitkat() = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT
