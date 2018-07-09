package com.simplemobiletools.camera.interfaces

import android.net.Uri

interface MyPreview {
    fun onResumed()

    fun onPaused()

    fun setTargetUri(uri: Uri)

    fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean)

    fun setFlashlightState(state: Int)

    fun getCameraState(): Int

    fun showChangeResolutionDialog()

    fun toggleFrontBackCamera()

    fun toggleFlashlight()

    fun tryTakePicture()

    fun toggleRecording()

    fun tryInitVideoMode()

    fun initPhotoMode()

    fun initVideoMode(): Boolean

    fun checkFlashlight()

    fun deviceOrientationChanged()

    fun resumeCamera(): Boolean

    fun imageSaved()
}
