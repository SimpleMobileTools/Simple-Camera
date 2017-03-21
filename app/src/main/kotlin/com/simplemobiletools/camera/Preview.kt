package com.simplemobiletools.camera

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.hardware.Camera
import android.media.*
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.dialogs.ChangeResolutionDialog
import com.simplemobiletools.camera.extensions.*
import com.simplemobiletools.commons.extensions.getNavBarHeight
import com.simplemobiletools.commons.extensions.needsStupidWritePermissions
import com.simplemobiletools.commons.extensions.scanPath
import com.simplemobiletools.commons.extensions.toast
import java.io.File
import java.io.IOException
import java.util.*

class Preview : ViewGroup, SurfaceHolder.Callback, MediaScannerConnection.OnScanCompletedListener {
    companion object {
        val PHOTO_PREVIEW_LENGTH = 1000L
        var mCamera: Camera? = null
        private val TAG = Preview::class.java.simpleName
        private val FOCUS_AREA_SIZE = 100
        private val RATIO_TOLERANCE = 0.2f

        lateinit var mSurfaceHolder: SurfaceHolder
        lateinit var mSurfaceView: SurfaceView
        lateinit var mActivity: MainActivity
        lateinit var mCallback: PreviewListener
        lateinit var mScreenSize: Point
        lateinit var config: Config
        private var mSupportedPreviewSizes: List<Camera.Size>? = null
        private var mPreviewSize: Camera.Size? = null
        private var mParameters: Camera.Parameters? = null
        private var mRecorder: MediaRecorder? = null
        private var mTargetUri: Uri? = null
        private var mScaleGestureDetector: ScaleGestureDetector? = null
        private var mZoomRatios: List<Int>? = null

        private var mCurrVideoPath = ""
        private var mCanTakePicture = false
        private var mIsFlashEnabled = false
        private var mIsRecording = false
        private var mIsVideoMode = false
        private var mIsSurfaceCreated = false
        private var mSwitchToVideoAsap = false
        private var mSetupPreviewAfterMeasure = false
        private var mIsSixteenToNine = false
        private var mWasZooming = false
        private var mLastClickX = 0
        private var mLastClickY = 0
        private var mInitVideoRotation = 0
        private var mCurrCameraId = 0
        private var mMaxZoom = 0
    }

    constructor(context: Context) : super(context)

    constructor(activity: MainActivity, surfaceView: SurfaceView, previewListener: PreviewListener) : super(activity) {
        mActivity = activity
        mCallback = previewListener
        mSurfaceView = surfaceView
        mSurfaceHolder = mSurfaceView.holder
        mSurfaceHolder.addCallback(this)
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        mCanTakePicture = false
        mIsFlashEnabled = false
        mIsVideoMode = false
        mIsSurfaceCreated = false
        mSetupPreviewAfterMeasure = false
        mCurrVideoPath = ""
        config = activity.config
        mScreenSize = getScreenSize()
        initGestureDetector()

        mSurfaceView.setOnTouchListener { view, event ->
            mLastClickX = event.x.toInt()
            mLastClickY = event.y.toInt()

            if (mMaxZoom > 0)
                mScaleGestureDetector!!.onTouchEvent(event)
            false
        }

        mSurfaceView.setOnClickListener {
            if (!mWasZooming)
                focusArea(false)

            mWasZooming = false
            mSurfaceView.isSoundEffectsEnabled = true
        }
    }

    fun trySwitchToVideo() {
        if (mIsSurfaceCreated) {
            initRecorder()
        } else {
            mSwitchToVideoAsap = true
        }
    }

    fun setCamera(cameraId: Int): Boolean {
        mCurrCameraId = cameraId
        val newCamera: Camera
        try {
            newCamera = Camera.open(cameraId)
            mCallback.setIsCameraAvailable(true)
        } catch (e: Exception) {
            mActivity.toast(R.string.camera_open_error)
            Log.e(TAG, "setCamera open " + e.message)
            mCallback.setIsCameraAvailable(false)
            return false
        }

        if (mCamera === newCamera) {
            return false
        }

        releaseCamera()
        mCamera = newCamera
        if (mCamera != null) {
            mParameters = mCamera!!.parameters
            mMaxZoom = mParameters!!.maxZoom
            mZoomRatios = mParameters!!.zoomRatios
            mSupportedPreviewSizes = mParameters!!.supportedPreviewSizes.sortedByDescending { it.width * it.height }
            mIsSixteenToNine = isSixteenToNine()
            requestLayout()
            invalidate()
            mSetupPreviewAfterMeasure = true

            val focusModes = mParameters!!.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                mParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

            mCamera!!.setDisplayOrientation(mActivity.getPreviewRotation(cameraId))
            mCamera!!.parameters = mParameters

            if (mCanTakePicture) {
                try {
                    mCamera!!.setPreviewDisplay(mSurfaceHolder)
                } catch (e: IOException) {
                    Log.e(TAG, "setCamera setPreviewDisplay " + e.message)
                    return false
                }
            }

            mCallback.setFlashAvailable(hasFlash(mCamera))
        }

        if (mIsVideoMode) {
            initRecorder()
        }

        return true
    }

    private fun isSixteenToNine(): Boolean {
        val selectedSize = getSelectedResolution()
        val selectedRatio = Math.abs(selectedSize.width / selectedSize.height.toFloat())
        val checkedRatio = 16 / 9.toFloat()
        val diff = Math.abs(selectedRatio - checkedRatio)
        return diff < RATIO_TOLERANCE
    }

    private fun getSelectedResolution(): Camera.Size {
        val index = getResolutionIndex()
        val resolutions = if (mIsVideoMode) {
            mParameters!!.supportedVideoSizes ?: mParameters!!.supportedPreviewSizes
        } else {
            mParameters!!.supportedPictureSizes
        }.sortedByDescending { it.width * it.height }

        return resolutions[index]
    }

    private fun getResolutionIndex(): Int {
        val isBackCamera = config.lastUsedCamera == Camera.CameraInfo.CAMERA_FACING_BACK
        return if (mIsVideoMode) {
            if (isBackCamera) config.backVideoResIndex else config.frontVideoResIndex
        } else {
            if (isBackCamera) config.backPhotoResIndex else config.frontPhotoResIndex
        }
    }

    fun setTargetUri(uri: Uri) {
        mTargetUri = uri
    }

    private fun initGestureDetector() {
        mScaleGestureDetector = ScaleGestureDetector(mActivity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val zoomFactor = mParameters!!.zoom
                var zoomRatio = mZoomRatios!![zoomFactor] / 100f
                zoomRatio *= detector.scaleFactor

                var newZoomFactor = zoomFactor
                if (zoomRatio <= 1f) {
                    newZoomFactor = 0
                } else if (zoomRatio >= mZoomRatios!![mMaxZoom] / 100f) {
                    newZoomFactor = mMaxZoom
                } else {
                    if (detector.scaleFactor > 1f) {
                        for (i in zoomFactor..mZoomRatios!!.size - 1) {
                            if (mZoomRatios!![i] / 100.0f >= zoomRatio) {
                                newZoomFactor = i
                                break
                            }
                        }
                    } else {
                        for (i in zoomFactor downTo 0) {
                            if (mZoomRatios!![i] / 100.0f <= zoomRatio) {
                                newZoomFactor = i
                                break
                            }
                        }
                    }
                }

                newZoomFactor = Math.max(newZoomFactor, 0)
                newZoomFactor = Math.min(mMaxZoom, newZoomFactor)

                mParameters!!.zoom = newZoomFactor
                mCamera?.parameters = mParameters
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

    fun takePicture() {
        if (mCanTakePicture) {
            var rotation = mActivity.getMediaRotation(mCurrCameraId)
            rotation += mCallback.getCurrentOrientation().compensateDeviceRotation(mCurrCameraId)

            val selectedResolution = getSelectedResolution()
            mParameters!!.setPictureSize(selectedResolution.width, selectedResolution.height);
            mParameters!!.setRotation(rotation % 360)

            if (config.isSoundEnabled) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
                if (volume != 0) {
                    val mp = MediaPlayer.create(context, Uri.parse("file:///system/media/audio/ui/camera_click.ogg"))
                    mp?.start()
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mCamera!!.enableShutterSound(false)
            }

            mCamera!!.parameters = mParameters
            mCamera!!.takePicture(null, null, takePictureCallback)
        }
        mCanTakePicture = false
    }

    private val takePictureCallback = Camera.PictureCallback { data, cam ->
        if (config.isShowPreviewEnabled) {
            Handler().postDelayed({
                resumePreview()
            }, PHOTO_PREVIEW_LENGTH)
        } else {
            resumePreview()
        }

        PhotoProcessor(mActivity, mTargetUri).execute(data)
    }

    private fun resumePreview() {
        mCamera?.startPreview()
        mCanTakePicture = true
    }

    fun getSupportedVideoSizes(): List<Camera.Size> = mParameters!!.supportedVideoSizes ?: mParameters!!.supportedPreviewSizes

    private fun focusArea(takePictureAfter: Boolean) {
        if (mCamera == null)
            return

        mCamera!!.cancelAutoFocus()
        val focusRect = calculateFocusArea(mLastClickX.toFloat(), mLastClickY.toFloat())
        if (mParameters!!.maxNumFocusAreas > 0) {
            val focusAreas = ArrayList<Camera.Area>(1)
            focusAreas.add(Camera.Area(focusRect, 1000))
            mParameters!!.focusAreas = focusAreas
            mCallback.drawFocusRect(mLastClickX, mLastClickY)
        }

        mCamera!!.parameters = mParameters
        mCamera!!.autoFocus { success, camera ->
            camera.cancelAutoFocus()
            val focusModes = mParameters!!.supportedFocusModes
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                mParameters!!.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

            camera.parameters = mParameters
            if (takePictureAfter) {
                takePicture()
            }
        }
    }

    private fun calculateFocusArea(x: Float, y: Float): Rect {
        var left = java.lang.Float.valueOf(x / mSurfaceView.width * 2000 - 1000)!!.toInt()
        var top = java.lang.Float.valueOf(y / mSurfaceView.height * 2000 - 1000)!!.toInt()

        val tmp = left
        left = top
        top = -tmp

        val rectLeft = Math.max(left - FOCUS_AREA_SIZE / 2, -1000)
        val rectTop = Math.max(top - FOCUS_AREA_SIZE / 2, -1000)
        val rectRight = Math.min(left + FOCUS_AREA_SIZE / 2, 1000)
        val rectBottom = Math.min(top + FOCUS_AREA_SIZE / 2, 1000)
        return Rect(rectLeft, rectTop, rectRight, rectBottom)
    }

    fun showChangeResolutionDialog() {
        if (mCamera != null) {
            ChangeResolutionDialog(mActivity, config.lastUsedCamera == Camera.CameraInfo.CAMERA_FACING_BACK, mCamera!!) {

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
            mCamera?.setPreviewDisplay(mSurfaceHolder)

            if (mSwitchToVideoAsap)
                initRecorder()
        } catch (e: IOException) {
            Log.e(TAG, "surfaceCreated IOException " + e.message)
        }

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mIsSurfaceCreated = true

        if (mIsVideoMode) {
            initRecorder()
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
            mParameters!!.setPreviewSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mCamera!!.parameters = mParameters
            mCamera!!.startPreview()
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

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

    }

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
            if (mIsSixteenToNine || mIsVideoMode) {
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes!!, mScreenSize.y, mScreenSize.x)
            } else {
                val newRatioHeight = (mScreenSize.x * (4.toDouble() / 3)).toInt()
                setMeasuredDimension(mScreenSize.x, newRatioHeight)
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes!!, newRatioHeight, mScreenSize.x)
            }
            val lp = mSurfaceView.layoutParams

            // make sure to occupy whole width in every case
            if (mScreenSize.x > mPreviewSize!!.height) {
                val ratio = mScreenSize.x.toFloat() / mPreviewSize!!.height
                lp.width = (mPreviewSize!!.height * ratio).toInt()
                if (mIsSixteenToNine || mIsVideoMode) {
                    lp.height = mScreenSize.y
                } else {
                    lp.height = (mPreviewSize!!.width * ratio).toInt()
                }
            } else {
                lp.width = mPreviewSize!!.height
                lp.height = mPreviewSize!!.width
            }

            if (mSetupPreviewAfterMeasure) {
                mSetupPreviewAfterMeasure = false
                mCamera?.stopPreview()
                setupPreview()
            }
        }
    }

    fun enableFlash() {
        mParameters!!.flashMode = Camera.Parameters.FLASH_MODE_TORCH
        mCamera!!.parameters = mParameters
        mIsFlashEnabled = true
    }

    fun disableFlash() {
        mIsFlashEnabled = false
        mParameters!!.flashMode = Camera.Parameters.FLASH_MODE_OFF
        mCamera!!.parameters = mParameters
    }

    fun initPhotoMode() {
        stopRecording()
        cleanupRecorder()
        mIsRecording = false
        mIsVideoMode = false
    }

    // VIDEO RECORDING
    fun initRecorder(): Boolean {
        if (mCamera == null || mRecorder != null || !mIsSurfaceCreated)
            return false

        mSwitchToVideoAsap = false
        val preferred = mSupportedPreviewSizes!![0]

        mParameters!!.setPreviewSize(preferred.width, preferred.height)
        mCamera!!.parameters = mParameters

        mIsRecording = false
        mIsVideoMode = true
        mRecorder = MediaRecorder().apply {
            setCamera(mCamera)
            setVideoSource(MediaRecorder.VideoSource.DEFAULT)
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        }

        mCurrVideoPath = mActivity.getOutputMediaFile(false)
        if (mCurrVideoPath.isEmpty()) {
            mActivity.toast(R.string.video_creating_error)
            return false
        }

        val resolution = getSelectedResolution()
        CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).apply {
            videoFrameWidth = resolution.width
            videoFrameHeight = resolution.height
            mRecorder!!.setProfile(this)
        }

        if (mActivity.needsStupidWritePermissions(mCurrVideoPath)) {
            if (config.treeUri.isEmpty()) {
                mActivity.toast(R.string.save_error_internal_storage)
                config.savePhotosFolder = Environment.getExternalStorageDirectory().toString()
                releaseCamera()
                return false
            }
            try {
                /*var document: DocumentFile = Utils.getFileDocument(context, mCurrVideoPath!!, mConfig!!.treeUri)
                document = document.createFile("", mCurrVideoPath!!.substring(mCurrVideoPath!!.lastIndexOf('/') + 1))
                val uri = document.uri
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "rw")
                mRecorder!!.setOutputFile(fileDescriptor!!.fileDescriptor)*/
            } catch (e: Exception) {
                setupFailed(e)
            }

        } else {
            mRecorder!!.setOutputFile(mCurrVideoPath)
        }
        mRecorder!!.setPreviewDisplay(mSurfaceHolder.surface)

        val rotation = mActivity.getFinalRotation(mCurrCameraId, mCallback.getCurrentOrientation())
        mInitVideoRotation = rotation
        mRecorder!!.setOrientationHint(rotation)

        try {
            mRecorder!!.prepare()
        } catch (e: Exception) {
            setupFailed(e)
            return false
        }

        return true
    }

    private fun setupFailed(e: Exception) {
        mActivity.toast(R.string.video_setup_error)
        Log.e(TAG, "initRecorder " + e.message)
        releaseCamera()
    }

    fun toggleRecording(): Boolean {
        if (mIsRecording) {
            stopRecording()
            initRecorder()
        } else {
            startRecording()
        }
        return mIsRecording
    }

    private fun startRecording() {
        if (mInitVideoRotation != mActivity.getFinalRotation(mCurrCameraId, mCallback.getCurrentOrientation())) {
            cleanupRecorder()
            initRecorder()
        }

        try {
            mCamera!!.unlock()
            toggleShutterSound(true)
            mRecorder!!.start()
            toggleShutterSound(false)
            mIsRecording = true
        } catch (e: Exception) {
            mActivity.toast(R.string.video_setup_error)
            Log.e(TAG, "toggleRecording " + e.message)
            releaseCamera()
        }

    }

    private fun stopRecording() {
        if (mRecorder != null && mIsRecording) {
            try {
                toggleShutterSound(true)
                mRecorder!!.stop()
                mActivity.scanPath(mCurrVideoPath) {}
            } catch (e: RuntimeException) {
                toggleShutterSound(false)
                File(mCurrVideoPath).delete()
                mActivity.toast(R.string.video_saving_error)
                Log.e(TAG, "stopRecording " + e.message)
                mRecorder = null
                mIsRecording = false
                releaseCamera()
            }

        }

        mRecorder = null
        mIsRecording = false

        val file = File(mCurrVideoPath)
        if (file.exists() && file.length() == 0L) {
            file.delete()
        }
    }

    private fun toggleShutterSound(mute: Boolean?) {
        if (!config.isSoundEnabled) {
            (mActivity.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamMute(AudioManager.STREAM_SYSTEM, mute!!)
        }
    }

    override fun onScanCompleted(path: String, uri: Uri) {
        mCallback.videoSaved(uri)
        toggleShutterSound(false)
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
        val display = mActivity.windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        size.y += mActivity.resources.getNavBarHeight()
        return size
    }

    interface PreviewListener {
        fun setFlashAvailable(available: Boolean)

        fun setIsCameraAvailable(available: Boolean)

        fun getCurrentOrientation(): Int

        fun videoSaved(uri: Uri)

        fun drawFocusRect(x: Int, y: Int)
    }
}
