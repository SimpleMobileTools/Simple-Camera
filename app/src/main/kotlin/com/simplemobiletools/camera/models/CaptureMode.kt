package com.simplemobiletools.camera.models

import androidx.annotation.StringRes
import com.simplemobiletools.camera.R

enum class CaptureMode(@StringRes val stringResId: Int) {
    MINIMIZE_LATENCY(R.string.minimize_latency),
    MAXIMIZE_QUALITY(R.string.maximize_quality)
}
