package com.simplemobiletools.camera.extensions

import android.content.Context
import android.hardware.Camera
import com.simplemobiletools.camera.R

val RATIO_TOLERANCE = 0.1f

fun Camera.Size.isSixteenToNine(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 16 / 9.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.isFourToThree(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 4 / 3.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.isThreeToTwo(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 3 / 2.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.isSixToFive(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 6 / 5.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.getAspectRatio(context: Context) = when {
    isSixteenToNine() -> "16:9"
    isFourToThree() -> "4:3"
    isThreeToTwo() -> "3:2"
    isSixToFive() -> "6:5"
    else -> context.resources.getString(R.string.other)
}
