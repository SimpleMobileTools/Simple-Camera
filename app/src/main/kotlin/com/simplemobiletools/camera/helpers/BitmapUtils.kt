package com.simplemobiletools.camera.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

//inspired by https://android.googlesource.com/platform/packages/apps/Camera2/+/refs/heads/master/src/com/android/camera/util/CameraUtil.java#244
object BitmapUtils {
    private const val INLINE_BITMAP_MAX_PIXEL_NUM = 50 * 1024

    fun makeBitmap(jpegData: ByteArray, maxNumOfPixels: Int = INLINE_BITMAP_MAX_PIXEL_NUM): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true

            BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, options)

            if (options.mCancel || options.outWidth == -1 || options.outHeight == -1) {
                return null
            }
            options.inSampleSize = computeSampleSize(options, -1, maxNumOfPixels)
            options.inJustDecodeBounds = false
            options.inDither = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeByteArray(
                jpegData, 0, jpegData.size,
                options
            )
        } catch (ex: OutOfMemoryError) {
            null
        }
    }

    private fun computeSampleSize(options: BitmapFactory.Options, minSideLength: Int, maxNumOfPixels: Int): Int {
        val initialSize = computeInitialSampleSize(
            options, minSideLength,
            maxNumOfPixels
        )
        var roundedSize: Int
        if (initialSize <= 8) {
            roundedSize = 1
            while (roundedSize < initialSize) {
                roundedSize = roundedSize shl 1
            }
        } else {
            roundedSize = (initialSize + 7) / 8 * 8
        }
        return roundedSize
    }

    private fun computeInitialSampleSize(options: BitmapFactory.Options, minSideLength: Int, maxNumOfPixels: Int): Int {
        val w = options.outWidth.toDouble()
        val h = options.outHeight.toDouble()
        val lowerBound = if (maxNumOfPixels < 0) 1 else ceil(sqrt(w * h / maxNumOfPixels)).toInt()
        val upperBound = if (minSideLength < 0) 128 else floor(w / minSideLength).coerceAtMost(floor(h / minSideLength)).toInt()
        if (upperBound < lowerBound) {
            // return the larger one when there is no overlapping zone.
            return lowerBound
        }
        return if (maxNumOfPixels < 0 && minSideLength < 0) {
            1
        } else if (minSideLength < 0) {
            lowerBound
        } else {
            upperBound
        }
    }
}
