package com.simplemobiletools.camera.implementations

import android.graphics.Bitmap
import android.net.Uri
import com.simplemobiletools.camera.models.ResolutionOption

interface CameraXPreviewListener {
    fun setCameraAvailable(available: Boolean)
    fun setHasFrontAndBackCamera(hasFrontAndBack: Boolean)
    fun setFlashAvailable(available: Boolean)
    fun onChangeCamera(frontCamera: Boolean)
    fun toggleBottomButtons(enabled: Boolean)
    fun shutterAnimation()
    fun onMediaSaved(uri: Uri)
    fun onImageCaptured(bitmap: Bitmap)
    fun onChangeFlashMode(flashMode: Int)
    fun onVideoRecordingStarted()
    fun onVideoRecordingStopped()
    fun onVideoDurationChanged(durationNanos: Long)
    fun onFocusCamera(xPos: Float, yPos: Float)
    fun onSwipeLeft()
    fun onSwipeRight()
    fun onTouchPreview()
    fun displaySelectedResolution(resolutionOption: ResolutionOption)
    fun showImageSizes(
        selectedResolution: ResolutionOption,
        resolutions: List<ResolutionOption>,
        isPhotoCapture: Boolean,
        isFrontCamera: Boolean,
        onSelect: (index: Int, changed: Boolean) -> Unit,
    )

    fun showFlashOptions(photoCapture: Boolean)
}
