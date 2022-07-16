package com.simplemobiletools.camera.helpers

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.models.CameraSelectorImageQualities
import com.simplemobiletools.camera.models.MySize
import com.simplemobiletools.commons.extensions.showErrorToast

class ImageQualityManager(
    private val activity: AppCompatActivity,
) {

    companion object {
        private val CAMERA_LENS = arrayOf(CameraCharacteristics.LENS_FACING_FRONT, CameraCharacteristics.LENS_FACING_BACK)
    }

    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val config = activity.config
    private val imageQualities = mutableListOf<CameraSelectorImageQualities>()

    fun initSupportedQualities() {
        if (imageQualities.isEmpty()) {
            for (cameraId in cameraManager.cameraIdList) {
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                    for (lens in CAMERA_LENS) {
                        if (characteristics.get(CameraCharacteristics.LENS_FACING) == lens) {
                            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                            val imageSizes = configMap.getOutputSizes(ImageFormat.JPEG).map { MySize(it.width, it.height) }
                            val cameraSelector = lens.toCameraSelector()
                            imageQualities.add(CameraSelectorImageQualities(cameraSelector, imageSizes))
                        }
                    }
                } catch (e: Exception) {
                    activity.showErrorToast(e)
                }
            }
        }
    }

    private fun Int.toCameraSelector(): CameraSelector {
        return if (this == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun getUserSelectedResolution(cameraSelector: CameraSelector): MySize {
        val index = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) config.frontPhotoResIndex else config.backPhotoResIndex
        return imageQualities.filter { it.camSelector == cameraSelector }
            .flatMap { it.qualities }
            .sortedWith(compareByDescending<MySize> { it.ratio }.thenByDescending { it.pixels })
            .distinctBy { it.pixels }
            .filter { it.megaPixels != "0.0" }[index]
    }

    fun getSupportedResolutions(cameraSelector: CameraSelector): List<MySize> {
        return imageQualities.filter { it.camSelector == cameraSelector }
            .flatMap { it.qualities }
            .sortedWith(compareByDescending<MySize> { it.ratio }.thenByDescending { it.pixels })
            .distinctBy { it.pixels }
            .filter { it.megaPixels != "0.0" }
    }
}
