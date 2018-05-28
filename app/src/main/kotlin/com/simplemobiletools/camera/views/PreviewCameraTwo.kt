package com.simplemobiletools.camera.views

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import com.simplemobiletools.camera.helpers.STATE_PREVIEW
import com.simplemobiletools.camera.interfaces.MyPreview

class PreviewCameraTwo(context: Context) : ViewGroup(context), MyPreview {
    private var mTargetUri: Uri? = null
    private var mIsImageCaptureIntent = false

    override fun setTargetUri(uri: Uri) {
        mTargetUri = uri
    }

    override fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean) {
        mIsImageCaptureIntent = isImageCaptureIntent
    }

    override fun setFlashlightState(state: Int) {
    }

    override fun setCamera(cameraId: Int): Boolean {
        return false
    }

    override fun getCameraState(): Int {
        return STATE_PREVIEW
    }

    override fun releaseCamera() {
    }

    override fun showChangeResolutionDialog() {
    }

    override fun toggleFrontBackCamera() {
    }

    override fun toggleFlashlight() {
    }

    override fun tryTakePicture() {
    }

    override fun toggleRecording(): Boolean {
        return false
    }

    override fun tryInitVideoMode() {
    }

    override fun initPhotoMode() {
    }

    override fun initVideoMode(): Boolean {
        return false
    }

    override fun checkFlashlight() {
    }

    override fun deviceOrientationChanged() {
    }

    override fun resumeCamera(): Boolean {
        return false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
}
