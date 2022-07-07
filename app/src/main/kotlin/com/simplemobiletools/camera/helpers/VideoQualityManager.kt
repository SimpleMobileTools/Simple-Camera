package com.simplemobiletools.camera.helpers

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import com.simplemobiletools.camera.extensions.toCameraXQuality
import com.simplemobiletools.camera.extensions.toVideoQuality
import com.simplemobiletools.camera.models.CameraSelectorVideoQualities
import com.simplemobiletools.camera.models.VideoQuality

class VideoQualityManager(private val config: Config) {

    companion object {
        private const val TAG = "VideoQualityHelper"
        private val QUALITIES = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
        private val CAMERA_SELECTORS = arrayOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    private val videoQualities = mutableListOf<CameraSelectorVideoQualities>()

    fun initSupportedQualities(
        cameraProvider: ProcessCameraProvider,
        camera: Camera,
    ) {
        if (videoQualities.isEmpty()) {
            for (camSelector in CAMERA_SELECTORS) {
                try {
                    if (cameraProvider.hasCamera(camSelector)) {
                        QualitySelector.getSupportedQualities(camera.cameraInfo)
                            .filter(QUALITIES::contains)
                            .also { allQualities ->
                                val qualities = allQualities.map { it.toVideoQuality() }
                                videoQualities.add(CameraSelectorVideoQualities(camSelector, qualities))
                            }
                        Log.i(TAG, "bindCameraUseCases: videoQualities=$videoQualities")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera Face $camSelector is not supported", e)
                }
            }
        }
    }

    fun getUserSelectedQuality(cameraSelector: CameraSelector): Quality {
        return if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            config.frontVideoQuality.toCameraXQuality()
        } else {
            config.backVideoQuality.toCameraXQuality()
        }
    }

    fun getSupportedQualities(cameraSelector: CameraSelector): List<VideoQuality> {
        return videoQualities.filter { it.camSelector == cameraSelector }
            .flatMap { it.qualities }
            .sortedByDescending { it.pixels }
    }
}
