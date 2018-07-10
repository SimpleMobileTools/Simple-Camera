package com.simplemobiletools.camera.models

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.Size
import com.simplemobiletools.camera.R

data class MySize(val width: Int, val height: Int) {
    val ratio = width / height.toFloat()
    fun isSixteenToNine() = ratio == 16 / 9f

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
        else -> context.resources.getString(R.string.other)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun toSize() = Size(width, height)
}
