package com.simplemobiletools.camera.helpers

class MediaSizeStore(private val config: Config) {

    fun storeSize(isPhotoCapture: Boolean, isFrontCamera: Boolean, currentIndex: Int) {
        if (isPhotoCapture) {
            if (isFrontCamera) {
                config.frontPhotoResIndex = currentIndex
            } else {
                config.backPhotoResIndex = currentIndex
            }
        } else {
            if (isFrontCamera) {
                config.frontVideoResIndex = currentIndex
            } else {
                config.backVideoResIndex = currentIndex
            }
        }
    }

    fun getCurrentSizeIndex(isPhotoCapture: Boolean, isFrontCamera: Boolean): Int {
        return if (isPhotoCapture) {
            if (isFrontCamera) {
                config.frontPhotoResIndex
            } else {
                config.backPhotoResIndex
            }
        } else {
            if (isFrontCamera) {
                config.frontVideoResIndex
            } else {
                config.backVideoResIndex
            }
        }
    }
}
