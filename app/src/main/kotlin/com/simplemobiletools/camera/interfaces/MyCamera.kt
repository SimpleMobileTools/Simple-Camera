package com.simplemobiletools.camera.interfaces

abstract class MyCamera {
    abstract fun getFrontCameraId(): Int

    abstract fun getBackCameraId(): Int

    abstract fun getCountOfCameras(): Int
}
