package com.simplemobiletools.camera.helpers

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.models.CameraSelectorImageQualities
import com.simplemobiletools.camera.models.MySize

class ImageQualityManager(
    activity: AppCompatActivity,
) {

    companion object {
        private const val TAG = "ImageQualityManager"
        private const val MAX_VIDEO_WIDTH = 4096
        private const val MAX_VIDEO_HEIGHT = 2160
        private val CAMERA_LENS = arrayOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK)
    }

    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val config = activity.config
    private val imageQualities = mutableListOf<CameraSelectorImageQualities>()

    fun initSupportedQualities() {
        for (cameraId in cameraManager.cameraIdList) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                for (lens in CAMERA_LENS) {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == lens) {
                        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                        val imageSizes = configMap.getOutputSizes(ImageFormat.JPEG).map { MySize(it.width, it.height) }
                        val cameraSelector = lens.toCameraSelector()
                        imageQualities.add(CameraSelectorImageQualities(cameraSelector, imageSizes))
                        Log.i(TAG, "initQualities: imageSizes=$imageSizes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera ID=$cameraId is not supported", e)
            }
        }
    }

    private fun getAvailableVideoSizes(configMap: StreamConfigurationMap): List<Size> {
        return configMap.getOutputSizes(MediaRecorder::class.java).filter {
            it.width <= MAX_VIDEO_WIDTH && it.height <= MAX_VIDEO_HEIGHT
        }
    }

    private fun Int.toCameraSelector(): CameraSelector {
        return if (this == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun getUserSelectedResolution(cameraSelector: CameraSelector): Size? {
        val index = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) config.frontPhotoResIndex else config.backPhotoResIndex
        return imageQualities.filter { it.camSelector == cameraSelector }
            .flatMap { it.qualities }
            .sortedByDescending { it.pixels }
            .distinctBy { it.pixels }
            .map { Size(it.width, it.height) }
            .also {
                Log.i(TAG, "Resolutions: $it, index=$index")
            }
            .getOrNull(index).also {
                Log.i(TAG, "getUserSelectedResolution: $it, index=$index")
            }
    }

    fun getSupportedResolutions(cameraSelector: CameraSelector): List<MySize> {
        return imageQualities.filter { it.camSelector == cameraSelector }
            .flatMap { it.qualities }
            .sortedByDescending { it.pixels }
            .distinctBy { it.pixels }
            .filter { it.megaPixels != "0.0" }
    }
}
