package com.simplemobiletools.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Point
import android.hardware.Camera
import android.support.v4.content.ContextCompat
import android.view.Surface
import com.simplemobiletools.commons.extensions.getFileDocument
import com.simplemobiletools.commons.extensions.needsStupidWritePermissions
import com.simplemobiletools.commons.extensions.toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Utils {
    companion object {
        fun getCameraInfo(cameraId: Int): Camera.CameraInfo {
            val info = android.hardware.Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, info)
            return info
        }

        fun showToast(context: Context, resId: Int) = context.toast(resId)

        fun hasFlash(camera: Camera?): Boolean {
            if (camera == null) {
                return false
            }

            if (camera.parameters.flashMode == null) {
                return false
            }

            val supportedFlashModes = camera.parameters.supportedFlashModes
            if (supportedFlashModes == null || supportedFlashModes.isEmpty() ||
                    supportedFlashModes.size == 1 && supportedFlashModes[0] == Camera.Parameters.FLASH_MODE_OFF) {
                return false
            }

            return true
        }

        fun getOutputMediaFile(context: Context, isPhoto: Boolean): String {
            val mediaStorageDir = File(Config.newInstance(context).savePhotosFolder)

            if (!mediaStorageDir.exists()) {
                if (!mediaStorageDir.mkdirs()) {
                    return ""
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            return if (isPhoto) {
                "${mediaStorageDir.path}${File.separator}IMG_$timestamp.jpg"
            } else {
                "${mediaStorageDir.path}${File.separator}VID_$timestamp.mp4"
            }
        }

        fun formatSeconds(duration: Int): String {
            val sb = StringBuilder(8)
            val hours = duration / (60 * 60)
            val minutes = duration % (60 * 60) / 60
            val seconds = duration % (60 * 60) % 60

            if (duration > 3600000) {
                sb.append(String.format(Locale.getDefault(), "%02d", hours)).append(":")
            }

            sb.append(String.format(Locale.getDefault(), "%02d", minutes))
            sb.append(":").append(String.format(Locale.getDefault(), "%02d", seconds))

            return sb.toString()
        }

        fun getScreenSize(activity: Activity): Point {
            val display = activity.windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)
            size.y += getNavBarHeight(activity.resources)
            return size
        }

        fun getNavBarHeight(res: Resources): Int {
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (id > 0 && hasNavBar(res)) {
                res.getDimensionPixelSize(id)
            } else
                0
        }

        fun hasNavBar(res: Resources): Boolean {
            val id = res.getIdentifier("config_showNavigationBar", "bool", "android")
            return id > 0 && res.getBoolean(id)
        }

        fun hasCameraPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        fun hasAudioPermission(cxt: Context) = ContextCompat.checkSelfPermission(cxt, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun needsStupidWritePermissions(context: Context, path: String) = context.needsStupidWritePermissions(path)

        fun getFileDocument(context: Context, path: String, treeUri: String) = context.getFileDocument(path, treeUri)

        fun getRotationDegrees(activity: Activity): Int {
            return when (activity.windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        }

        fun getPreviewRotation(activity: Activity, cameraId: Int): Int {
            val info = Utils.getCameraInfo(cameraId)
            val degrees = Utils.getRotationDegrees(activity)

            var result: Int
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360
                result = 360 - result
            } else {
                result = info.orientation - degrees + 360
            }

            return result % 360
        }

        fun getMediaRotation(activity: Activity, cameraId: Int): Int {
            val degrees = Utils.getRotationDegrees(activity)
            val info = Utils.getCameraInfo(cameraId)
            return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                (360 + info.orientation + degrees) % 360
            } else
                (360 + info.orientation - degrees) % 360
        }

        fun compensateDeviceRotation(currCameraId: Int, deviceOrientation: Int): Int {
            var degrees = 0
            val isFrontCamera = currCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT
            if (deviceOrientation == Constants.ORIENT_LANDSCAPE_LEFT) {
                degrees += if (isFrontCamera) 90 else 270
            } else if (deviceOrientation == Constants.ORIENT_LANDSCAPE_RIGHT) {
                degrees += if (isFrontCamera) 270 else 90
            }
            return degrees
        }

        fun getFinalRotation(activity: Activity, currCameraId: Int, deviceOrientation: Int): Int {
            var rotation = Utils.getMediaRotation(activity, currCameraId)
            rotation += Utils.compensateDeviceRotation(currCameraId, deviceOrientation)
            return rotation % 360
        }
    }
}
