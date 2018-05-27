package com.simplemobiletools.camera.implementations

import android.annotation.TargetApi
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.simplemobiletools.camera.interfaces.MyCamera

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class MyCameraTwoImpl(val context: Context) : MyCamera() {
    override fun getFrontCameraId() = CameraCharacteristics.LENS_FACING_FRONT

    override fun getBackCameraId() = CameraCharacteristics.LENS_FACING_BACK

    override fun getCountOfCameras(): Int {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.size
    }
}
