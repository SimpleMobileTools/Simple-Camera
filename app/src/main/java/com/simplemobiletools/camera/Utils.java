package com.simplemobiletools.camera;

import android.hardware.Camera;

public class Utils {
    public static Camera.CameraInfo getCameraInfo(int cameraId) {
        final Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info;
    }
}
