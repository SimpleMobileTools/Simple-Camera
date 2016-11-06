package com.simplemobiletools.camera.extensions

import android.content.Context
import android.net.Uri
import android.os.Build
import android.support.v4.provider.DocumentFile
import com.simplemobiletools.camera.Config
import com.simplemobiletools.filepicker.extensions.getSDCardPath

fun Context.needsStupidWritePermissions(path: String) = isPathOnSD(path) && isKitkat()

fun Context.isPathOnSD(path: String) = path.startsWith(getSDCardPath())

fun Context.isKitkat() = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT

fun Context.getFileDocument(path: String): DocumentFile {
    val relativePath = path.substring(getSDCardPath().length + 1)
    var document = DocumentFile.fromTreeUri(this, Uri.parse(Config.newInstance(this).treeUri))
    val parts = relativePath.split("/")
    for (part in parts) {
        val currDocument = document.findFile(part)
        if (currDocument != null)
            document = currDocument
    }
    return document
}
