package com.simplemobiletools.camera.interfaces

interface MyPreview {

    fun isInPhotoMode(): Boolean

    fun setFlashlightState(state: Int)

    fun toggleFrontBackCamera()

    fun handleFlashlightClick()

    fun tryTakePicture()

    fun toggleRecording()

    fun initPhotoMode()

    fun initVideoMode()

    fun showChangeResolution()
}
