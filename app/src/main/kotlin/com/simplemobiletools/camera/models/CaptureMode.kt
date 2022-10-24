package com.simplemobiletools.camera.models

import androidx.annotation.StringRes
import com.simplemobiletools.camera.R

enum class CaptureMode(@StringRes val stringResId: Int) {
    MINIMISE_LATENCY(R.string.minimize_latency),
    MAXIMISE_QUALITY(R.string.maximize_quality)
}
