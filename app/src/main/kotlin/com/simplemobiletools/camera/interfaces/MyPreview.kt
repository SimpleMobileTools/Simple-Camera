package com.simplemobiletools.camera.interfaces

import android.net.Uri

interface MyPreview {

    fun onResumed() = Unit

    fun onPaused() = Unit

    fun setTargetUri(uri: Uri) = Unit

    fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean) = Unit

    fun setFlashlightState(state: Int) = Unit

    fun getCameraState(): Int = 0

    fun showChangeResolutionDialog()

    fun toggleFrontBackCamera()

    fun toggleFlashlight()

    fun tryTakePicture()

    fun toggleRecording()

    fun initPhotoMode()

    fun initVideoMode()

    fun checkFlashlight() = Unit
}
