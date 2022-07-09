package com.simplemobiletools.camera.models

import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.OutputStream

sealed class MediaOutput(
    open val uri: Uri?,
) {
    data class MediaStoreOutput(
        val contentValues: ContentValues,
        val contentUri: Uri,
    ) : MediaOutput(null)

    data class OutputStreamMediaOutput(
        val outputStream: OutputStream,
        override val uri: Uri,
    ) : MediaOutput(uri)

    data class FileDescriptorMediaOutput(
        val fileDescriptor: ParcelFileDescriptor,
        override val uri: Uri,
    ) : MediaOutput(uri)

    object BitmapOutput : MediaOutput(null)
}
