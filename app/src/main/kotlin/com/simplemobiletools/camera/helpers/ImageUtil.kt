package com.simplemobiletools.camera.helpers

import android.graphics.*
import androidx.annotation.IntRange
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Utility class for image related operations.
 * @see androidx.camera.core.internal.utils.ImageUtil
 */
object ImageUtil {

    @Throws(CodecFailedException::class)
    fun imageToJpegByteArray(image: ImageProxy, jpegQuality: Int): ByteArray {
        val shouldCropImage = shouldCropImage(image)
        return when (image.format) {
            ImageFormat.JPEG -> {
                if (!shouldCropImage) {
                    // When cropping is unnecessary, the byte array doesn't need to be decoded and
                    // re-encoded again. Therefore, jpegQuality is unnecessary in this case.
                    jpegImageToJpegByteArray(image)
                } else {
                    jpegImageToJpegByteArray(image, image.cropRect, jpegQuality)
                }
            }
            ImageFormat.YUV_420_888 -> {
                yuvImageToJpegByteArray(image, if (shouldCropImage) image.cropRect else null, jpegQuality)
            }
            else -> {
                // Unrecognized image format
                byteArrayOf()
            }
        }
    }

    /**
     * Converts JPEG [ImageProxy] to JPEG byte array.
     */
    fun jpegImageToJpegByteArray(image: ImageProxy): ByteArray {
        require(image.format == ImageFormat.JPEG) { "Incorrect image format of the input image proxy: " + image.format }
        val planes = image.planes
        val buffer = planes[0].buffer
        val data = ByteArray(buffer.capacity())
        buffer.rewind()
        buffer[data]
        return data
    }

    /**
     * Converts JPEG [ImageProxy] to JPEG byte array. The input JPEG image will be cropped
     * by the specified crop rectangle and compressed by the specified quality value.
     */
    @Throws(CodecFailedException::class)
    private fun jpegImageToJpegByteArray(
        image: ImageProxy,
        cropRect: Rect,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
    ): ByteArray {
        require(image.format == ImageFormat.JPEG) { "Incorrect image format of the input image proxy: " + image.format }
        var data = jpegImageToJpegByteArray(image)
        data = cropJpegByteArray(data, cropRect, jpegQuality)
        return data
    }

    /**
     * Converts YUV_420_888 [ImageProxy] to JPEG byte array. The input YUV_420_888 image
     * will be cropped if a non-null crop rectangle is specified. The output JPEG byte array will
     * be compressed by the specified quality value.
     */
    @Throws(CodecFailedException::class)
    private fun yuvImageToJpegByteArray(
        image: ImageProxy,
        cropRect: Rect?,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
    ): ByteArray {
        require(image.format == ImageFormat.YUV_420_888) { "Incorrect image format of the input image proxy: " + image.format }
        return nv21ToJpeg(
            yuv_420_888toNv21(image),
            image.width,
            image.height,
            cropRect,
            jpegQuality
        )
    }

    /**
     * [android.media.Image] to NV21 byte array.
     */
    private fun yuv_420_888toNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()
        val ySize = yBuffer.remaining()
        var position = 0
        val nv21 = ByteArray(ySize + image.width * image.height / 2)

        // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
        for (row in 0 until image.height) {
            yBuffer[nv21, position, image.width]
            position += image.width
            yBuffer.position(
                Math.min(ySize, yBuffer.position() - image.width + yPlane.rowStride)
            )
        }
        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
        // perform faster bulk gets from the byte buffers.
        val vLineBuffer = ByteArray(vRowStride)
        val uLineBuffer = ByteArray(uRowStride)
        for (row in 0 until chromaHeight) {
            vBuffer[vLineBuffer, 0, Math.min(vRowStride, vBuffer.remaining())]
            uBuffer[uLineBuffer, 0, Math.min(uRowStride, uBuffer.remaining())]
            var vLineBufferPosition = 0
            var uLineBufferPosition = 0
            for (col in 0 until chromaWidth) {
                nv21[position++] = vLineBuffer[vLineBufferPosition]
                nv21[position++] = uLineBuffer[uLineBufferPosition]
                vLineBufferPosition += vPixelStride
                uLineBufferPosition += uPixelStride
            }
        }
        return nv21
    }

    /**
     * Crops JPEG byte array with given [android.graphics.Rect].
     */
    @Throws(CodecFailedException::class)
    private fun cropJpegByteArray(
        data: ByteArray,
        cropRect: Rect,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
    ): ByteArray {
        val bitmap: Bitmap
        try {
            val decoder = BitmapRegionDecoder.newInstance(
                data, 0, data.size,
                false
            )
            bitmap = decoder.decodeRegion(cropRect, BitmapFactory.Options())
            decoder.recycle()
        } catch (e: IllegalArgumentException) {
            throw CodecFailedException(
                "Decode byte array failed with illegal argument.$e",
                CodecFailedException.FailureType.DECODE_FAILED
            )
        } catch (e: IOException) {
            throw CodecFailedException(
                "Decode byte array failed.",
                CodecFailedException.FailureType.DECODE_FAILED
            )
        }

        val out = ByteArrayOutputStream()
        val success = bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        if (!success) {
            throw CodecFailedException(
                "Encode bitmap failed.",
                CodecFailedException.FailureType.ENCODE_FAILED
            )
        }
        bitmap.recycle()
        return out.toByteArray()
    }

    @Throws(CodecFailedException::class)
    private fun nv21ToJpeg(
        nv21: ByteArray,
        width: Int,
        height: Int,
        cropRect: Rect?,
        @IntRange(from = 1, to = 100) jpegQuality: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val success = yuv.compressToJpeg(
            cropRect ?: Rect(0, 0, width, height),
            jpegQuality, out
        )
        if (!success) {
            throw CodecFailedException(
                "YuvImage failed to encode jpeg.",
                CodecFailedException.FailureType.ENCODE_FAILED
            )
        }
        return out.toByteArray()
    }

    /**
     * Checks whether the image's crop rectangle is the same as the source image size.
     */
    private fun shouldCropImage(image: ImageProxy): Boolean {
        return shouldCropImage(
            image.width, image.height, image.cropRect.width(),
            image.cropRect.height()
        )
    }

    /**
     * Checks whether the image's crop rectangle is the same as the source image size.
     */
    private fun shouldCropImage(
        sourceWidth: Int, sourceHeight: Int, cropRectWidth: Int,
        cropRectHeight: Int
    ): Boolean {
        return sourceWidth != cropRectWidth || sourceHeight != cropRectHeight
    }

    /**
     * Exception for error during transcoding image.
     */
    class CodecFailedException internal constructor(message: String, val failureType: FailureType) : Exception(message) {
        enum class FailureType {
            ENCODE_FAILED, DECODE_FAILED, UNKNOWN
        }
    }
}
