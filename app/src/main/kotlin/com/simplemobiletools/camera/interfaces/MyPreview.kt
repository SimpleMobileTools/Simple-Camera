package com.simplemobiletools.camera.interfaces

import android.net.Uri

interface MyPreview {

    fun setTargetUri(uri: Uri) = Unit

    fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean) = Unit

    fun setFlashlightState(state: Int) = Unit

    fun toggleFrontBackCamera()

    fun toggleFlashlight() = Unit

    fun tryTakePicture()

    fun toggleRecording()

    fun initPhotoMode()

    fun initVideoMode()

    fun checkFlashlight() = Unit

    fun showChangeResolution() = Unit
}
