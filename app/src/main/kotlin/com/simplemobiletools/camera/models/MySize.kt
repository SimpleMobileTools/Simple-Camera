package com.simplemobiletools.camera.models

import android.content.Context
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.helpers.RATIO_TOLERANCE

data class MySize(val width: Int, val height: Int) {
    fun isSixteenToNine(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 16 / 9.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isFiveToThree(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 5 / 3.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isFourToThree(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 4 / 3.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isThreeToFour(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 3 / 4.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isThreeToTwo(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 3 / 2.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isSixToFive(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 6 / 5.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isNineteenToNine(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 19 / 9.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isNineteenToEight(): Boolean {
        val selectedRatio = Math.abs(width / height.toFloat())
        val checkedRatio = 19 / 8.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun isOneNineToOne() = Math.abs(1.9 - (width / height.toFloat())) < RATIO_TOLERANCE

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
        else -> context.resources.getString(R.string.other)
    }
}
