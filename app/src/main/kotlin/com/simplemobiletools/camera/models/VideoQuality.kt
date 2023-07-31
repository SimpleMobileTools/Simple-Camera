package com.simplemobiletools.camera.models

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.simplemobiletools.camera.R

enum class VideoQuality(val width: Int, val height: Int) {
    UHD(3840, 2160),
    FHD(1920, 1080),
    HD(1280, 720),
    SD(720, 480);

    val pixels: Int = width * height

    val megaPixels: String = String.format("%.1f", (width * height.toFloat()) / VideoQuality.ONE_MEGA_PIXEL)

    val ratio = width / height.toFloat()

    private fun isSixteenToNine() = ratio == 16 / 9f

    private fun isFiveToThree() = ratio == 5 / 3f

    private fun isFourToThree() = ratio == 4 / 3f

    private fun isTwoToOne() = ratio == 2f

    private fun isThreeToFour() = ratio == 3 / 4f

    private fun isThreeToTwo() = ratio == 3 / 2f

    private fun isSixToFive() = ratio == 6 / 5f

    private fun isNineteenToNine() = ratio == 19 / 9f

    private fun isNineteenToEight() = ratio == 19 / 8f

    private fun isOneNineToOne() = ratio == 1.9f

    private fun isSquare() = width == height

    fun getAspectRatio(context: Context) = when {
        isSixteenToNine() -> "16:9"
        isFiveToThree() -> "5:3"
        isFourToThree() -> "4:3"
        isThreeToFour() -> "3:4"
        isThreeToTwo() -> "3:2"
        isSixToFive() -> "6:5"
        isOneNineToOne() -> "1.9:1"
        isNineteenToNine() -> "19:9"
        isNineteenToEight() -> "19:8"
        isSquare() -> "1:1"
        isTwoToOne() -> "2:1"
        else -> context.resources.getString(com.simplemobiletools.commons.R.string.other)
    }

    @DrawableRes
    fun getImageResId(): Int = when (this) {
        UHD -> R.drawable.ic_video_uhd_vector
        FHD -> R.drawable.ic_video_fhd_vector
        HD -> R.drawable.ic_video_hd_vector
        SD -> R.drawable.ic_video_sd_vector
    }

    @IdRes
    fun getButtonId(): Int = when (this) {
        UHD -> R.id.video_uhd
        FHD -> R.id.video_fhd
        HD -> R.id.video_hd
        SD -> R.id.video_sd
    }

    fun toResolutionOption(): ResolutionOption {
        return ResolutionOption(buttonViewId = getButtonId(), imageDrawableResId = getImageResId())
    }

    companion object {
        private const val ONE_MEGA_PIXEL = 1000000
    }
}
