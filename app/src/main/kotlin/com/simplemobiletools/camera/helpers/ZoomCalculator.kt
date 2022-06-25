package com.simplemobiletools.camera.helpers

import androidx.camera.core.ZoomState

class ZoomCalculator {

    fun calculateZoomRatio(zoomState: ZoomState, pinchToZoomScale: Float): Float {
        val clampedRatio = zoomState.zoomRatio * speedUpZoomBy2X(pinchToZoomScale)
        // Clamp the ratio with the zoom range.
        return clampedRatio.coerceAtLeast(zoomState.minZoomRatio).coerceAtMost(zoomState.maxZoomRatio)
    }

    private fun speedUpZoomBy2X(scaleFactor: Float): Float {
        return if (scaleFactor > 1f) {
            1.0f + (scaleFactor - 1.0f) * 2
        } else {
            1.0f - (1.0f - scaleFactor) * 2
        }
    }
}
