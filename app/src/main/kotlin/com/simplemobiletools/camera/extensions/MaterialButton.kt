package com.simplemobiletools.camera.extensions

import androidx.annotation.DrawableRes
import com.google.android.material.button.MaterialButton
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.views.ShadowDrawable

fun MaterialButton.setShadowIcon(@DrawableRes drawableResId: Int) {
    icon = ShadowDrawable(context, drawableResId, R.style.TopIconShadow)
}
