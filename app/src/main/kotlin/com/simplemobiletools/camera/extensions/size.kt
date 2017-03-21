package com.simplemobiletools.camera.extensions

import android.hardware.Camera

private val RATIO_TOLERANCE = 0.2f

fun Camera.Size.isSixteenToNine(): Boolean {
    val selectedRatio = Math.abs(width / height.toFloat())
    val checkedRatio = 16 / 9.toFloat()
    val diff = Math.abs(selectedRatio - checkedRatio)
    return diff < RATIO_TOLERANCE
}
