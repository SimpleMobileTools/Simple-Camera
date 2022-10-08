package com.simplemobiletools.camera.models

import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.OutputStream

sealed class MediaOutput(
    open val uri: Uri?,
) {
    sealed interface ImageCaptureOutput
    sealed interface VideoCaptureOutput

    data class MediaStoreOutput(
        val contentValues: ContentValues,
        val contentUri: Uri,
    ) : MediaOutput(null), ImageCaptureOutput, VideoCaptureOutput

    data class OutputStreamMediaOutput(
        val outputStream: OutputStream,
        override val uri: Uri,
    ) : MediaOutput(uri), ImageCaptureOutput

    data class FileDescriptorMediaOutput(
        val fileDescriptor: ParcelFileDescriptor,
        override val uri: Uri,
    ) : MediaOutput(uri), VideoCaptureOutput

    data class FileMediaOutput(
        val file: File,
        override val uri: Uri,
    ) : MediaOutput(uri), VideoCaptureOutput, ImageCaptureOutput

    object BitmapOutput : MediaOutput(null), ImageCaptureOutput
}
