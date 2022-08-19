package com.simplemobiletools.camera.implementations

import android.graphics.Bitmap
import android.net.Uri

interface CameraXPreviewListener {
    fun setCameraAvailable(available: Boolean)
    fun setHasFrontAndBackCamera(hasFrontAndBack:Boolean)
    fun setFlashAvailable(available: Boolean)
    fun onChangeCamera(frontCamera: Boolean)
    fun toggleBottomButtons(hide:Boolean)
    fun onMediaSaved(uri: Uri)
    fun onImageCaptured(bitmap: Bitmap)
    fun onChangeFlashMode(flashMode: Int)
    fun onVideoRecordingStarted()
    fun onVideoRecordingStopped()
    fun onVideoDurationChanged(durationNanos: Long)
    fun onFocusCamera(xPos: Float, yPos: Float)
    fun onSwipeLeft()
    fun onSwipeRight()
}
