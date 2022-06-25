package com.simplemobiletools.camera.helpers

import android.view.ScaleGestureDetector
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo

class PinchToZoomOnScaleGestureListener(
    private val cameraInfo: CameraInfo,
    private val cameraControl: CameraControl,
) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private val zoomCalculator = ZoomCalculator()

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val zoomState = cameraInfo.zoomState.value ?: return false
        val zoomRatio = zoomCalculator.calculateZoomRatio(zoomState, detector.scaleFactor)
        cameraControl.setZoomRatio(zoomRatio)
        return true
    }
}
