package com.simplemobiletools.camera.extensions

import android.view.View
import com.simplemobiletools.commons.helpers.SHORT_ANIMATION_DURATION

fun View.fadeIn() {
    animate().alpha(1f).setDuration(SHORT_ANIMATION_DURATION).withStartAction { isClickable = true }.start()
}

fun View.fadeOut() {
    animate().alpha(0f).setDuration(SHORT_ANIMATION_DURATION).withEndAction { isClickable = false }.start()
}
