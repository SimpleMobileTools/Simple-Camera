package com.simplemobiletools.camera.views

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.media.AudioManager
import android.media.CamcorderProfile
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.view.*
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.dialogs.ChangeResolutionDialog
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getMyCamera
import com.simplemobiletools.camera.extensions.getOutputMediaFile
import com.simplemobiletools.camera.extensions.realScreenSize
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.implementations.MyCameraOneImpl
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.MySize
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isJellyBean1Plus
import java.io.File
import java.io.IOException
import java.util.*

class PreviewCameraOne : ViewGroup, SurfaceHolder.Callback, MyPreview {
    private val FOCUS_AREA_SIZE = 100
    private val PHOTO_PREVIEW_LENGTH = 500L
    private val REFOCUS_PERIOD = 3000L

    private lateinit var mSurfaceHolder: SurfaceHolder
    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mScreenSize: Point
    private lateinit var mConfig: Config
    private var mSupportedPreviewSizes: List<Camera.Size>? = null
    private var mPreviewSize: Camera.Size? = null
    private var mParameters: Camera.Parameters? = null
    private var mRecorder: MediaRecorder? = null
    private var mScaleGestureDetector: ScaleGestureDetector? = null
    private var mZoomRatios = ArrayList<Int>()
    private var mFlashlightState = FLASH_OFF
    private var mCamera: Camera? = null
    private var mCameraImpl: MyCameraOneImpl? = null
    private var mAutoFocusHandler = Handler()
    private var mActivity: MainActivity? = null
    private var mTargetUri: Uri? = null
    private var mCameraState = STATE_PREVIEW

    private var mCurrVideoPath = ""
    private var mCanTakePicture = false
    private var mIsRecording = false
    private var mIsInVideoMode = false
    private var mIsSurfaceCreated = false
    private var mSwitchToVideoAsap = false
    private var mSetupPreviewAfterMeasure = false
    private var mIsSixteenToNine = false
    private var mWasZooming = false
    private var mIsPreviewShown = false
    private var mWasCameraPreviewSet = false
    private var mIsImageCaptureIntent = false
    private var mIsFocusingBeforeCapture = false
    private var mLastClickX = 0f
    private var mLastClickY = 0f
    private var mCurrCameraId = 0
    private var mMaxZoom = 0
    private var mRotationAtCapture = 0

    constructor(context: Context) : super(context)

    @SuppressLint("ClickableViewAccessibility")
    constructor(activity: MainActivity, surfaceView: SurfaceView) : super(activity) {
        mActivity = activity
        mSurfaceView = surfaceView
        mSurfaceHolder = mSurfaceView.holder
        mSurfaceHolder.addCallback(this)
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        mCameraImpl = MyCameraOneImpl(activity.applicationContext)
        mConfig = activity.config
        mScreenSize = getScreenSize()
        initGestureDetector()

        mSurfaceView.setOnTouchListener { view, event ->
            mLastClickX = event.x
            mLastClickY = event.y

            if (mMaxZoom > 0 && mParameters?.isZoomSupported == true) {
                mScaleGestureDetector!!.onTouchEvent(event)
            }
            false
        }

        mSurfaceView.setOnClickListener {
            if (mIsPreviewShown) {
                resumePreview()
            } else {
                if (!mWasZooming && !mIsPreviewShown) {
                    focusArea(false)
                }

                mWasZooming = false
                mSurfaceView.isSoundEffectsEnabled = true
            }
        }
    }

    override fun onResumed() {}

    override fun onPaused() {
        releaseCamera()
    }

    override fun tryInitVideoMode() {
        if (mIsSurfaceCreated) {
            initVideoMode()
        } else {
            mSwitchToVideoAsap = true
        }
    }

    override fun resumeCamera(): Boolean {
        val newCamera: Camera
        try {
            newCamera = Camera.open(mCurrCameraId)
            mActivity!!.setIsCameraAvailable(true)
        } catch (e: Exception) {
            mActivity!!.showErrorToast(e)
            mActivity!!.setIsCameraAvailable(false)
            return false
        }

        if (mCamera === newCamera) {
            return false
        }

        releaseCamera()
        mCamera = newCamera
        if (initCamera() && mIsInVideoMode) {
            initVideoMode()
        }

        if (!mWasCameraPreviewSet && mIsSurfaceCreated) {
            mCamera!!.setPreviewDisplay(mSurfaceHolder)
            mWasCameraPreviewSet = true
        }
        return true
    }

    override fun imageSaved() {}

    private fun initCamera(): Boolean {
        if (mCamera == null)
            return false

        mParameters = mCamera!!.parameters
        mMaxZoom = mParameters!!.maxZoom

        if (mParameters!!.isZoomSupported)
            mZoomRatios = mParameters!!.zoomRatios as ArrayList<Int>

        mSupportedPreviewSizes = mParameters!!.supportedPreviewSizes.sortedByDescending { it.width * it.height }
        refreshPreview()

        // hackfix for slow photo preview, more info at https://github.com/SimpleMobileTools/Simple-Camera/issues/120
        if (Build.MODEL == "Nexus 4") {
            mParameters!!.setRecordingHint(true)
        }

        val focusModes = mParameters!!.supportedFocusModes
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        }

        mCamera!!.setDisplayOrientation(getPreviewRotation(mCurrCameraId))
        mParameters!!.zoom = 0
        updateCameraParameters()

        if (mCanTakePicture) {
            try {
                mCamera!!.setPreviewDisplay(mSurfaceHolder)
            } catch (e: IOException) {
                mActivity!!.showErrorToast(e)
                return false
            }
        }

        mActivity!!.setFlashAvailable(hasFlash(mCamera))
        return true
    }

    override fun toggleFrontBackCamera() {
        mCurrCameraId = if (mCurrCameraId == mCameraImpl!!.getBackCameraId()) {
            mCameraImpl!!.getFrontCameraId()
        } else {
            mCameraImpl!!.getBackCameraId()
        }

        mConfig.lastUsedCamera = mCurrCameraId.toString()
        releaseCamera()
        if (resumeCamera()) {
            setFlashlightState(FLASH_OFF)
            mActivity?.updateCameraIcon(mCurrCameraId == mCameraImpl!!.getFrontCameraId())
            mActivity?.toggleTimer(false)
        } else {
            mActivity?.toast(R.string.camera_switch_error)
        }
    }

    override fun getCameraState() = mCameraState

    private fun refreshPreview() {
        mIsSixteenToNine = getSelectedResolution().isSixteenToNine()
        mSetupPreviewAfterMeasure = true
        requestLayout()
        invalidate()
        rescheduleAutofocus()
    }

    private fun getSelectedResolution(): MySize {
        if (mParameters == null) {
            mParameters = mCamera!!.parameters
        }

        var index = getResolutionIndex()
        val resolutions = if (mIsInVideoMode) {
            mParameters!!.supportedVideoSizes ?: mParameters!!.supportedPreviewSizes
        } else {
            mParameters!!.supportedPictureSizes
        }.map { MySize(it.width, it.height) }.sortedByDescending { it.width * it.height }

        if (index == -1) {
            index = getDefaultFullscreenResolution(resolutions) ?: 0
        }

        return resolutions[index]
    }

    private fun getResolutionIndex(): Int {
        val isBackCamera = mConfig.lastUsedCamera == Camera.CameraInfo.CAMERA_FACING_BACK.toString()
        return if (mIsInVideoMode) {
            if (isBackCamera) mConfig.backVideoResIndex else mConfig.frontVideoResIndex
        } else {
            if (isBackCamera) mConfig.backPhotoResIndex else mConfig.frontPhotoResIndex
        }
    }

    private fun getDefaultFullscreenResolution(resolutions: List<MySize>): Int? {
        val screenAspectRatio = mActivity!!.realScreenSize.y / mActivity!!.realScreenSize.x.toFloat()
        resolutions.forEachIndexed { index, size ->
            val diff = screenAspectRatio - (size.width / size.height.toFloat())
            if (Math.abs(diff) < 0.1f) {
                mConfig.backPhotoResIndex = index
                return index
            }
        }
        return null
    }

    private fun initGestureDetector() {
        mScaleGestureDetector = ScaleGestureDetector(mActivity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomFactor = mParameters!!.zoom
                var zoomRatio = mZoomRatios[zoomFactor] / 100f
                zoomRatio *= detector.scaleFactor

                var newZoomFactor = zoomFactor
                if (zoomRatio <= 1f) {
                    newZoomFactor = 0
                } else if (zoomRatio >= mZoomRatios[mMaxZoom] / 100f) {
                    newZoomFactor = mMaxZoom
                } else {
                    if (detector.scaleFactor > 1f) {
                        for (i in zoomFactor until mZoomRatios.size) {
                            if (mZoomRatios[i] / 100.0f >= zoomRatio) {
                                newZoomFactor = i
                                break
                            }
                        }
                    } else {
                        for (i in zoomFactor downTo 0) {
                            if (mZoomRatios[i] / 100.0f <= zoomRatio) {
                                newZoomFactor = i
                                break
                            }
                        }
                    }
                }

                newZoomFactor = Math.max(newZoomFactor, 0)
                newZoomFactor = Math.min(mMaxZoom, newZoomFactor)

                mParameters!!.zoom = newZoomFactor
                updateCameraParameters()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                super.onScaleEnd(detector)
                mWasZooming = true
                mSurfaceView.isSoundEffectsEnabled = false
                mParameters!!.focusAreas = null
            }
        })
    }

    override fun tryTakePicture() {
        if (mConfig.focusBeforeCapture) {
            focusArea(true)
        } else {
            takePicture()
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun takePicture() {
        if (mCanTakePicture) {
            val selectedResolution = getSelectedResolution()
            mParameters!!.setPictureSize(selectedResolution.width, selectedResolution.height)
            val pictureSize = mParameters!!.pictureSize
            if (selectedResolution.width != pictureSize.width || selectedResolution.height != pictureSize.height) {
                mActivity!!.toast(R.string.setting_resolution_failed)
            }

            if (isJellyBean1Plus()) {
                mCamera!!.enableShutterSound(false)
            }

            mRotationAtCapture = mActivity!!.mLastHandledOrientation
            updateCameraParameters()
            mCameraState = STATE_PICTURE_TAKEN
            mIsPreviewShown = true
            try {
                Thread {
                    mCamera!!.takePicture(null, null, takePictureCallback)

                    if (mConfig.isSoundEnabled) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val volume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                        if (volume != 0) {
                            val mp = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"))
                            mp?.start()
                        }
                    }
                }.start()
            } catch (ignored: Exception) {
            }
        }
        mCanTakePicture = false
        mIsFocusingBeforeCapture = false
    }

    private val takePictureCallback = Camera.PictureCallback { data, cam ->
        if (data.isEmpty()) {
            mActivity!!.toast(R.string.unknown_error_occurred)
            return@PictureCallback
        }

        mCameraState = STATE_PREVIEW
        if (!mIsImageCaptureIntent) {
            handlePreview()
        }

        if (mIsImageCaptureIntent) {
            if (mTargetUri != null) {
                storePhoto(data)
            } else {
                mActivity!!.apply {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
        } else {
            storePhoto(data)
        }
    }

    private fun storePhoto(data: ByteArray) {
        val previewRotation = getPreviewRotation(mCurrCameraId)
        PhotoProcessor(mActivity!!, mTargetUri, mRotationAtCapture, previewRotation, getIsUsingFrontCamera(), mIsImageCaptureIntent).execute(data)
    }

    private fun getIsUsingFrontCamera() = mCurrCameraId == mActivity!!.getMyCamera().getFrontCameraId()

    private fun handlePreview() {
        if (mConfig.isShowPreviewEnabled) {
            if (!mConfig.wasPhotoPreviewHintShown) {
                mActivity!!.toast(R.string.click_to_resume_preview)
                mConfig.wasPhotoPreviewHintShown = true
            }
        } else {
            Handler().postDelayed({
                mIsPreviewShown = false
                resumePreview()
            }, PHOTO_PREVIEW_LENGTH)
        }
    }

    private fun resumePreview() {
        mIsPreviewShown = false
        mActivity!!.toggleBottomButtons(false)
        try {
            mCamera?.startPreview()
        } catch (ignored: Exception) {
        }
        mCanTakePicture = true
        focusArea(false, false)
    }

    private fun focusArea(takePictureAfter: Boolean, showFocusRect: Boolean = true) {
        if (mCamera == null || (mIsFocusingBeforeCapture && !takePictureAfter)) {
            return
        }

        if (takePictureAfter) {
            mIsFocusingBeforeCapture = true
        }

        mCamera!!.cancelAutoFocus()
        if (mParameters!!.maxNumFocusAreas > 0) {
            if (mLastClickX == 0f && mLastClickY == 0f) {
                mLastClickX = width / 2.toFloat()
                mLastClickY = height / 2.toFloat()
            }

            val focusRect = calculateFocusArea(mLastClickX, mLastClickY)
            val focusAreas = ArrayList<Camera.Area>(1)
            focusAreas.add(Camera.Area(focusRect, 1000))
            mParameters!!.focusAreas = focusAreas

            if (showFocusRect) {
                mActivity!!.drawFocusCircle(mLastClickX, mLastClickY)
            }
        }

        try {
            val focusModes = mParameters!!.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                mParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }

            updateCameraParameters()
            mCamera!!.autoFocus { success, camera ->
                if (camera == null || mCamera == null) {
                    return@autoFocus
                }

                mCamera!!.cancelAutoFocus()
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                }

                updateCameraParameters()

                if (takePictureAfter) {
                    takePicture()
                } else {
                    rescheduleAutofocus()
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun calculateFocusArea(x: Float, y: Float): Rect {
        var left = x / mSurfaceView.width * 2000 - 1000
        var top = y / mSurfaceView.height * 2000 - 1000

        val tmp = left
        left = top
        top = -tmp

        val rectLeft = Math.max(left.toInt() - FOCUS_AREA_SIZE / 2, -1000)
        val rectTop = Math.max(top.toInt() - FOCUS_AREA_SIZE / 2, -1000)
        val rectRight = Math.min(left.toInt() + FOCUS_AREA_SIZE / 2, 1000)
        val rectBottom = Math.min(top.toInt() + FOCUS_AREA_SIZE / 2, 1000)
        return Rect(rectLeft, rectTop, rectRight, rectBottom)
    }

    private fun rescheduleAutofocus() {
        mAutoFocusHandler.removeCallbacksAndMessages(null)
        mAutoFocusHandler.postDelayed({
            if (!mIsInVideoMode || !mIsRecording) {
                focusArea(false, false)
            }
        }, REFOCUS_PERIOD)
    }

    override fun showChangeResolutionDialog() {
        if (mCamera != null) {
            val oldResolution = getSelectedResolution()
            val photoResolutions = mCamera!!.parameters.supportedPictureSizes.map { MySize(it.width, it.height) } as ArrayList<MySize>
            val videoSizes = mCamera!!.parameters.supportedVideoSizes ?: mCamera!!.parameters.supportedPreviewSizes
            val videoResolutions = videoSizes.map { MySize(it.width, it.height) } as ArrayList<MySize>
            ChangeResolutionDialog(mActivity!!, getIsUsingFrontCamera(), photoResolutions, videoResolutions, false) {
                if (oldResolution != getSelectedResolution()) {
                    refreshPreview()
                }
            }
        }
    }

    fun releaseCamera() {
        stopRecording()
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
        cleanupRecorder()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mIsSurfaceCreated = true
        try {
            mWasCameraPreviewSet = mCamera != null
            mCamera?.setPreviewDisplay(mSurfaceHolder)

            if (mSwitchToVideoAsap)
                initVideoMode()
        } catch (e: IOException) {
            mActivity!!.showErrorToast(e)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mIsSurfaceCreated = true

        if (mIsInVideoMode) {
            initVideoMode()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mIsSurfaceCreated = false
        mCamera?.stopPreview()
        cleanupRecorder()
    }

    private fun setupPreview() {
        mCanTakePicture = true
        if (mCamera != null && mPreviewSize != null) {
            if (mParameters == null)
                mParameters = mCamera!!.parameters

            mParameters!!.setPreviewSize(mPreviewSize!!.width, mPreviewSize!!.height)
            updateCameraParameters()
            try {
                mCamera!!.startPreview()
            } catch (e: RuntimeException) {
                mActivity!!.showErrorToast(e)
            }
        }
    }

    private fun cleanupRecorder() {
        if (mRecorder != null) {
            if (mIsRecording) {
                stopRecording()
            }

            mRecorder!!.release()
            mRecorder = null
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    private fun getOptimalPreviewSize(sizes: List<Camera.Size>, width: Int, height: Int): Camera.Size {
        var result: Camera.Size? = null
        for (size in sizes) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size
                } else {
                    val resultArea = result.width * result.height
                    val newArea = size.width * size.height

                    if (newArea > resultArea) {
                        result = size
                    }
                }
            }
        }

        return result!!
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(mScreenSize.x, mScreenSize.y)

        if (mSupportedPreviewSizes != null) {
            // for simplicity lets assume that most displays are 16:9 and the remaining ones are 4:3
            // always set 16:9 for videos as many devices support 4:3 only in low quality
            mPreviewSize = if (mIsSixteenToNine || mIsInVideoMode) {
                getOptimalPreviewSize(mSupportedPreviewSizes!!, mScreenSize.y, mScreenSize.x)
            } else {
                val newRatioHeight = (mScreenSize.x * (4.toDouble() / 3)).toInt()
                setMeasuredDimension(mScreenSize.x, newRatioHeight)
                getOptimalPreviewSize(mSupportedPreviewSizes!!, newRatioHeight, mScreenSize.x)
            }
            val lp = mSurfaceView.layoutParams

            // make sure to occupy whole width in every case
            if (mScreenSize.x > mPreviewSize!!.height) {
                val ratio = mScreenSize.x.toFloat() / mPreviewSize!!.height
                lp.width = (mPreviewSize!!.height * ratio).toInt()
                if (mIsSixteenToNine || mIsInVideoMode) {
                    lp.height = mScreenSize.y
                } else {
                    lp.height = (mPreviewSize!!.width * ratio).toInt()
                }
            } else {
                lp.width = mPreviewSize!!.height
                lp.height = mPreviewSize!!.width
            }

            if (mSetupPreviewAfterMeasure) {
                if (mCamera != null) {
                    mSetupPreviewAfterMeasure = false
                    mCamera!!.stopPreview()
                    setupPreview()
                }
            }
        }
    }

    override fun setFlashlightState(state: Int) {
        mFlashlightState = state
        checkFlashlight()
    }

    override fun toggleFlashlight() {
        val newState = ++mFlashlightState % if (mIsInVideoMode) 2 else 3
        setFlashlightState(newState)
    }

    override fun checkFlashlight() {
        when (mFlashlightState) {
            FLASH_OFF -> disableFlash()
            FLASH_ON -> enableFlash()
            FLASH_AUTO -> setAutoFlash()
        }
        mActivity?.updateFlashlightState(mFlashlightState)
    }

    private fun disableFlash() {
        mFlashlightState = FLASH_OFF
        mParameters?.flashMode = Camera.Parameters.FLASH_MODE_OFF
        updateCameraParameters()
    }

    private fun enableFlash() {
        mFlashlightState = FLASH_ON
        mParameters?.flashMode = Camera.Parameters.FLASH_MODE_TORCH
        updateCameraParameters()
    }

    private fun setAutoFlash() {
        mFlashlightState = FLASH_AUTO
        mParameters?.flashMode = Camera.Parameters.FLASH_MODE_OFF
        updateCameraParameters()

        Handler().postDelayed({
            mActivity?.runOnUiThread {
                mParameters?.flashMode = Camera.Parameters.FLASH_MODE_AUTO
            }
        }, 1000)
    }

    override fun initPhotoMode() {
        stopRecording()
        cleanupRecorder()
        mIsRecording = false
        mIsInVideoMode = false
        refreshPreview()
    }

    // VIDEO RECORDING
    override fun initVideoMode(): Boolean {
        if (mCamera == null || mRecorder != null || !mIsSurfaceCreated) {
            return false
        }

        refreshPreview()
        mSwitchToVideoAsap = false

        mIsRecording = false
        mIsInVideoMode = true
        mRecorder = MediaRecorder().apply {
            setCamera(mCamera)
            setVideoSource(MediaRecorder.VideoSource.DEFAULT)
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        }

        mCurrVideoPath = mActivity!!.getOutputMediaFile(false)
        if (mCurrVideoPath.isEmpty()) {
            mActivity?.toast(R.string.video_creating_error)
            return false
        }

        if (mRecorder == null) {
            mActivity?.toast(R.string.unknown_error_occurred)
            return false
        }

        val resolution = getSelectedResolution()
        val profile = if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
            CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
        } else {
            CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        }

        if (profile == null) {
            mActivity?.toast(R.string.unknown_error_occurred)
            return false
        }

        profile.apply {
            videoFrameWidth = resolution.width
            videoFrameHeight = resolution.height
            mRecorder!!.setProfile(this)
        }

        checkPermissions()
        if (mRecorder == null) {
            return false
        }
        mRecorder!!.setPreviewDisplay(mSurfaceHolder.surface)

        val rotation = getVideoRotation()
        mRecorder!!.setOrientationHint(rotation)

        try {
            mRecorder!!.prepare()
        } catch (e: Exception) {
            setupFailed(e)
            return false
        }

        return true
    }

    private fun checkPermissions(): Boolean {
        if (mActivity!!.needsStupidWritePermissions(mCurrVideoPath)) {
            if (mConfig.treeUri.isEmpty()) {
                mActivity!!.toast(R.string.save_error_internal_storage)
                mConfig.savePhotosFolder = Environment.getExternalStorageDirectory().toString()
                releaseCamera()
                return false
            }

            try {
                var document = mActivity!!.getDocumentFile(mCurrVideoPath.getParentPath())
                if (document == null) {
                    mActivity!!.toast(R.string.unknown_error_occurred)
                    return false
                }

                document = document.createFile("video/mp4", mCurrVideoPath.substring(mCurrVideoPath.lastIndexOf('/') + 1))
                val fileDescriptor = context.contentResolver.openFileDescriptor(document.uri, "rw")
                mRecorder!!.setOutputFile(fileDescriptor!!.fileDescriptor)
            } catch (e: Exception) {
                setupFailed(e)
            }
        } else {
            mRecorder!!.setOutputFile(mCurrVideoPath)
        }
        return true
    }

    private fun setupFailed(e: Exception) {
        mActivity!!.showErrorToast(e)
        releaseCamera()
    }

    private fun updateCameraParameters() {
        try {
            mCamera?.parameters = mParameters
        } catch (e: RuntimeException) {
        }
    }

    override fun setTargetUri(uri: Uri) {
        mTargetUri = uri
    }

    override fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean) {
        mIsImageCaptureIntent = isImageCaptureIntent
    }

    override fun toggleRecording() {
        if (mIsRecording) {
            stopRecording()
            initVideoMode()
        } else {
            startRecording()
        }
    }

    private fun getVideoRotation(): Int {
        val deviceRot = compensateDeviceRotation(mActivity!!.mLastHandledOrientation, getIsUsingFrontCamera())
        val previewRot = getPreviewRotation(mCurrCameraId)
        return (deviceRot + previewRot) % 360
    }

    override fun deviceOrientationChanged() {
        if (mIsInVideoMode && !mIsRecording) {
            mRecorder = null
            initVideoMode()
        }
    }

    private fun startRecording() {
        try {
            mCamera!!.unlock()
            toggleShutterSound(true)
            mRecorder!!.start()
            toggleShutterSound(false)
            mIsRecording = true
            mActivity!!.setRecordingState(true)
        } catch (e: Exception) {
            mActivity!!.showErrorToast(e)
            releaseCamera()
        }
    }

    private fun stopRecording() {
        if (mRecorder != null && mIsRecording) {
            try {
                toggleShutterSound(true)
                mRecorder!!.stop()
                mActivity!!.rescanPaths(arrayListOf(mCurrVideoPath)) {
                    mActivity!!.videoSaved(Uri.fromFile(File(mCurrVideoPath)))
                    toggleShutterSound(false)
                }
            } catch (e: RuntimeException) {
                mActivity!!.showErrorToast(e)
                toggleShutterSound(false)
                File(mCurrVideoPath).delete()
                mRecorder = null
                mIsRecording = false
                releaseCamera()
            }
        }

        mRecorder = null
        if (mIsRecording) {
            mActivity!!.setRecordingState(false)
        }
        mIsRecording = false

        val file = File(mCurrVideoPath)
        if (file.exists() && file.length() == 0L) {
            file.delete()
        }
    }

    private fun toggleShutterSound(mute: Boolean?) {
        if (!mConfig.isSoundEnabled) {
            (mActivity!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamMute(AudioManager.STREAM_SYSTEM, mute!!)
        }
    }

    private fun hasFlash(camera: Camera?): Boolean {
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

    private fun getScreenSize(): Point {
        val display = mActivity!!.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        size.y += mActivity!!.resources.getNavBarHeight()
        return size
    }

    private fun getPreviewRotation(cameraId: Int): Int {
        val info = getCameraInfo(cameraId)
        val degrees = when (mActivity!!.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = 360 - result
        } else {
            result = info.orientation - degrees + 360
        }

        return result % 360
    }

    private fun getCameraInfo(cameraId: Int): Camera.CameraInfo {
        val info = android.hardware.Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        return info
    }
}
