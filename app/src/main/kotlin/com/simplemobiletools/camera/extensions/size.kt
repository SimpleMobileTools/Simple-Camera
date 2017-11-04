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

fun Camera.Size.isFiveToThree(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 5 / 3.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.isFourToThree(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 4 / 3.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}

fun Camera.Size.isThreeToFour(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 3 / 4.toFloat()
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

fun Camera.Size.isOneNineToOne() = Math.abs(1.9 - (width / height.toFloat())) < RATIO_TOLERANCE

fun Camera.Size.isSquare() = width == height

fun Camera.Size.getAspectRatio(context: Context) = when {
    isSixteenToNine() -> "16:9"
    isFiveToThree() -> "5:3"
    isFourToThree() -> "4:3"
    isThreeToFour() -> "3:4"
    isThreeToTwo() -> "3:2"
    isSixToFive() -> "6:5"
    isOneNineToOne() -> "1.9:1"
    isSquare() -> "1:1"
    else -> context.resources.getString(R.string.other)
}
