package com.simplemobiletools.camera.helpers

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.simplemobiletools.commons.extensions.removeValues
import java.io.IOException

class ExifRemover(private val contentResolver: ContentResolver) {
    companion object {
        private const val MODE = "rw"
    }

    @WorkerThread
    fun removeExif(uri: Uri) {
        try {
            val fileDescriptor = contentResolver.openFileDescriptor(uri, MODE)
            if (fileDescriptor != null) {
                val exifInterface = ExifInterface(fileDescriptor.fileDescriptor)
                exifInterface.removeValues()
            }
        } catch (e: IOException) {
        }
    }
}
