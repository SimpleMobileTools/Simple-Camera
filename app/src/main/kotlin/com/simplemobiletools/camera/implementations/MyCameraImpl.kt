package com.simplemobiletools.camera.implementations

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class MyCameraImpl(val context: Context) {
    fun getFrontCameraId() = CameraCharacteristics.LENS_FACING_FRONT

    fun getBackCameraId() = CameraCharacteristics.LENS_FACING_BACK

    fun getCountOfCameras(): Int? {
        return try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.size
        } catch (e: Exception) {
            null
        }
    }
}
