package com.simplemobiletools.camera.helpers

import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import com.simplemobiletools.camera.R
import com.simplemobiletools.commons.extensions.toast

class CameraErrorHandler(
    private val context: Context,
) {

    fun handleCameraError(error: CameraState.StateError?) {
        when (error?.code) {
            CameraState.ERROR_MAX_CAMERAS_IN_USE,
            CameraState.ERROR_CAMERA_IN_USE -> context.toast(R.string.camera_in_use_error, Toast.LENGTH_LONG)
            CameraState.ERROR_CAMERA_FATAL_ERROR -> context.toast(R.string.camera_unavailable)
            CameraState.ERROR_STREAM_CONFIG -> context.toast(R.string.camera_configure_error)
            CameraState.ERROR_CAMERA_DISABLED -> context.toast(R.string.camera_disabled_by_admin_error)
            CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> context.toast(R.string.camera_dnd_error, Toast.LENGTH_LONG)
            CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {}
        }
    }

    fun handleImageCaptureError(imageCaptureError: Int) {
        when (imageCaptureError) {
            ImageCapture.ERROR_FILE_IO -> context.toast(R.string.photo_not_saved)
            else -> context.toast(R.string.photo_capture_failed)
        }
    }

    fun handleVideoRecordingError(error: Int) {
        when (error) {
            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> context.toast(R.string.video_capture_insufficient_storage_error)
            VideoRecordEvent.Finalize.ERROR_NONE -> {}
            else -> context.toast(R.string.video_recording_failed)
        }
    }

    fun showSaveToInternalStorage() {
        context.toast(R.string.save_error_internal_storage)
    }
}
