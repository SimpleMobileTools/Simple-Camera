package com.simplemobiletools.camera.extensions

import androidx.camera.core.ImageProxy

// Only for JPEG format
fun ImageProxy.toJpegByteArray(): ByteArray {
    val buffer = planes.first().buffer
    val jpegImageData = ByteArray(buffer.remaining())
    buffer[jpegImageData]
    return jpegImageData
}
