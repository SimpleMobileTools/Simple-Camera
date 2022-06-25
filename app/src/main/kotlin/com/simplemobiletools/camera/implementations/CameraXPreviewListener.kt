package com.simplemobiletools.camera.implementations

import android.net.Uri

interface CameraXPreviewListener {
    fun setCameraAvailable(available: Boolean)
    fun setHasFrontAndBackCamera(hasFrontAndBack:Boolean)
    fun setFlashAvailable(available: Boolean)
    fun onChangeCamera(frontCamera: Boolean)
    fun toggleBottomButtons(hide:Boolean)
    fun onMediaCaptured(uri: Uri)
    fun onChangeFlashMode(flashMode: Int)
    fun onVideoRecordingStarted()
    fun onVideoRecordingStopped()
    fun onVideoDurationChanged(durationNanos: Long)
}
