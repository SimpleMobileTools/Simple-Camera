package com.simplemobiletools.camera.views

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.FocusArea
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit


// based on the Android Camera2 sample at https://github.com/googlesamples/android-Camera2Basic
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PreviewCameraTwo : ViewGroup, TextureView.SurfaceTextureListener, MyPreview {
    private val FOCUS_TAG = "focus_tag"
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080

    private lateinit var mActivity: MainActivity
    private lateinit var mTextureView: AutoFitTextureView

    private var mSensorOrientation = 0
    private var mRotationAtCapture = 0
    private var mLastClickX = 0f
    private var mLastClickY = 0f
    private var mIsFlashSupported = true
    private var mIsImageCaptureIntent = false
    private var mIsInVideoMode = false
    private var mUseFrontCamera = false
    private var mCameraId = ""
    private var mCameraState = STATE_INIT
    private var mFlashlightState = FLASH_OFF

    private var mBackgroundThread: HandlerThread? = null
    private var mBackgroundHandler: Handler? = null
    private var mImageReader: ImageReader? = null
    private var mPreviewSize: Size? = null
    private var mTargetUri: Uri? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequest: CaptureRequest? = null
    private val mCameraToPreviewMatrix = Matrix()
    private val mPreviewToCameraMatrix = Matrix()
    private val mCameraOpenCloseLock = Semaphore(1)

    constructor(context: Context) : super(context)

    @SuppressLint("ClickableViewAccessibility")
    constructor(activity: MainActivity, textureView: AutoFitTextureView) : super(activity) {
        mActivity = activity
        mTextureView = textureView

        mTextureView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                mLastClickX = event.x
                mLastClickY = event.y
            }
            false
        }

        mTextureView.setOnClickListener {
            focusArea()
        }
    }

    override fun onResumed() {
        startBackgroundThread()

        if (mTextureView.isAvailable) {
            openCamera(mTextureView.width, mTextureView.height)
        } else {
            mTextureView.surfaceTextureListener = this
        }
    }

    override fun onPaused() {
        closeCamera()
        stopBackgroundThread()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openCamera(width, height)
    }

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("SimpleCameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        try {
            mBackgroundThread?.quitSafely()
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }

    private fun openCamera(width: Int, height: Int) {
        setupCameraOutputs(width, height)
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            getCameraManager().openCamera(mCameraId, cameraStateCallback, mBackgroundHandler)
        } catch (e: InterruptedException) {
        } catch (e: SecurityException) {
        }
    }

    private fun closeCamera() {
        try {
            mCameraOpenCloseLock.acquire()
            mCaptureSession?.close()
            mCaptureSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            mImageReader?.close()
            mImageReader = null
        } catch (e: Exception) {
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val buffer = reader.acquireNextImage().planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        PhotoProcessor(mActivity, mTargetUri, mRotationAtCapture, mSensorOrientation, getIsUsingFrontCamera()).execute(bytes)
    }

    private fun getIsUsingFrontCamera(): Boolean {
        val characteristics = getCameraManager().getCameraCharacteristics(mCameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    private fun setupCameraOutputs(width: Int, height: Int) {
        val manager = getCameraManager()
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue
                if ((mUseFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) || !mUseFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                val largest = configMap.getOutputSizes(ImageFormat.JPEG).maxBy { it.width * it.height }

                mImageReader = ImageReader.newInstance(largest!!.width, largest.height, ImageFormat.JPEG, 2)
                mImageReader!!.setOnImageAvailableListener(imageAvailableListener, mBackgroundHandler)

                val displaySize = Point()
                mActivity.windowManager.defaultDisplay.getSize(displaySize)
                var rotatedPreviewWidth = width
                var rotatedPreviewHeight = height
                var maxPreviewWidth = displaySize.x
                var maxPreviewHeight = displaySize.y

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    rotatedPreviewWidth = height
                    rotatedPreviewHeight = width
                    maxPreviewWidth = displaySize.y
                    maxPreviewHeight = displaySize.x
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT
                }

                val outputSizes = configMap.getOutputSizes(SurfaceTexture::class.java)
                mPreviewSize = chooseOptimalSize(outputSizes, rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest)

                mTextureView.setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
                mIsFlashSupported = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                mCameraId = cameraId
                return
            }
        } catch (e: Exception) {
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size): Size {
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        val width = aspectRatio.width
        val height = aspectRatio.height
        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * height / width) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        return when {
            bigEnough.size > 0 -> bigEnough.minBy { it.width * it.height }!!
            notBigEnough.size > 0 -> notBigEnough.maxBy { it.width * it.height }!!
            else -> choices[0]
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
            mActivity.setIsCameraAvailable(true)
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            mActivity.setIsCameraAvailable(false)
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            mActivity.setIsCameraAvailable(false)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)

            val surface = Surface(texture)
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)

            val stateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    if (mCameraDevice == null) {
                        return
                    }

                    mCaptureSession = cameraCaptureSession
                    try {
                        mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE, getFlashlightMode(mFlashlightState))
                        mPreviewRequest = mPreviewRequestBuilder!!.build()
                        mCaptureSession!!.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler)
                        mCameraState = STATE_PREVIEW
                    } catch (e: CameraAccessException) {
                    }
                }

                override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {}
            }

            mCameraDevice!!.createCaptureSession(Arrays.asList(surface, mImageReader!!.surface), stateCallback, null)
        } catch (e: CameraAccessException) {
        }
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mCameraState) {
                STATE_WAITING_LOCK -> {
                    val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (autoFocusState == null) {
                        captureStillPicture()
                    } else if (autoFocusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || autoFocusState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mCameraState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            process(partialResult)
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            process(result)
        }
    }

    private fun runPrecaptureSequence() {
        try {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            mCameraState = STATE_WAITING_PRECAPTURE
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

    private fun captureStillPicture() {
        try {
            if (mCameraDevice == null) {
                return
            }

            mCameraState = STATE_PICTURE_TAKEN
            mRotationAtCapture = mActivity.mLastHandledOrientation
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(mImageReader!!.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation)
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    unlockFocus()
                    mActivity.toggleBottomButtons(false)
                }

                override fun onCaptureFailed(session: CameraCaptureSession?, request: CaptureRequest?, failure: CaptureFailure?) {
                    super.onCaptureFailed(session, request, failure)
                    mActivity.toggleBottomButtons(false)
                }
            }

            mCaptureSession!!.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder.build(), captureCallback, null)
            }
        } catch (e: CameraAccessException) {
        }
    }

    // inspired by https://gist.github.com/royshil/8c760c2485257c85a11cafd958548482
    private fun focusArea() {
        mActivity.drawFocusCircle(mLastClickX, mLastClickY)

        val captureCallbackHandler = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                super.onCaptureCompleted(session, request, result)

                if (request.tag == FOCUS_TAG) {
                    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
                    mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(), null, null)
                }
            }
        }

        mCaptureSession!!.stopRepeating()
        mPreviewRequestBuilder!!.apply {
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            mCaptureSession!!.capture(build(), captureCallbackHandler, mBackgroundHandler)

            val characteristics = getCameraManager().getCameraCharacteristics(mCameraId)

            // touch-to-focus inspired by OpenCamera
            if (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1) {
                val focusArea = getFocusArea()
                val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                val meteringRect = convertAreaToMeteringRectangle(sensorRect, focusArea)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRect))
            }

            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            setTag(FOCUS_TAG)
            mCaptureSession!!.capture(build(), captureCallbackHandler, mBackgroundHandler)
        }
    }

    private fun convertAreaToMeteringRectangle(sensorRect: Rect, focusArea: FocusArea): MeteringRectangle {
        val camera2Rect = convertRectToCamera2(sensorRect, focusArea.rect)
        return MeteringRectangle(camera2Rect, focusArea.weight)
    }

    private fun convertRectToCamera2(cropRect: Rect, rect: Rect): Rect {
        val leftF = (rect.left + 1000) / 2000.0
        val topF = (rect.top + 1000) / 2000.0
        val rightF = (rect.right + 1000) / 2000.0
        val bottomF = (rect.bottom + 1000) / 2000.0
        var left = (cropRect.left + leftF * (cropRect.width() - 1)).toInt()
        var right = (cropRect.left + rightF * (cropRect.width() - 1)).toInt()
        var top = (cropRect.top + topF * (cropRect.height() - 1)).toInt()
        var bottom = (cropRect.top + bottomF * (cropRect.height() - 1)).toInt()
        left = Math.max(left, cropRect.left)
        right = Math.max(right, cropRect.left)
        top = Math.max(top, cropRect.top)
        bottom = Math.max(bottom, cropRect.top)
        left = Math.min(left, cropRect.right)
        right = Math.min(right, cropRect.right)
        top = Math.min(top, cropRect.bottom)
        bottom = Math.min(bottom, cropRect.bottom)

        return Rect(left, top, right, bottom)
    }

    private fun getFocusArea(): FocusArea {
        val coords = floatArrayOf(mLastClickX, mLastClickY)
        calculateCameraToPreviewMatrix()
        mPreviewToCameraMatrix.mapPoints(coords)
        val focusX = coords[0].toInt()
        val focusY = coords[1].toInt()

        val focusSize = 50
        val rect = Rect()
        rect.left = focusX - focusSize
        rect.right = focusX + focusSize
        rect.top = focusY - focusSize
        rect.bottom = focusY + focusSize

        if (rect.left < -1000) {
            rect.left = -1000
            rect.right = rect.left + 2 * focusSize
        } else if (rect.right > 1000) {
            rect.right = 1000
            rect.left = rect.right - 2 * focusSize
        }

        if (rect.top < -1000) {
            rect.top = -1000
            rect.bottom = rect.top + 2 * focusSize
        } else if (rect.bottom > 1000) {
            rect.bottom = 1000
            rect.top = rect.bottom - 2 * focusSize
        }

        return FocusArea(rect, MeteringRectangle.METERING_WEIGHT_MAX)
    }

    private fun calculateCameraToPreviewMatrix() {
        val yScale = if (getIsUsingFrontCamera()) -1 else 1
        mCameraToPreviewMatrix.apply {
            reset()
            setScale(1f, yScale.toFloat())
            postRotate(mSensorOrientation.toFloat())
            postScale(mTextureView.width / 2000f, mTextureView.height / 2000f)
            postTranslate(mTextureView.width / 2f, mTextureView.height / 2f)
            invert(mPreviewToCameraMatrix)
        }
    }

    private fun lockFocus() {
        try {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            mCameraState = STATE_WAITING_LOCK
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

    private fun unlockFocus() {
        try {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE, getFlashlightMode(mFlashlightState))
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
            mCameraState = STATE_PREVIEW
            mCaptureSession!!.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

    private fun getCameraManager() = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun takePicture() {
        lockFocus()
    }

    private fun getFlashlightMode(state: Int) = when (state) {
        FLASH_OFF -> CameraMetadata.FLASH_MODE_OFF
        FLASH_ON -> CameraMetadata.FLASH_MODE_TORCH
        else -> CameraMetadata.FLASH_MODE_SINGLE
    }

    override fun setTargetUri(uri: Uri) {
        mTargetUri = uri
    }

    override fun setIsImageCaptureIntent(isImageCaptureIntent: Boolean) {
        mIsImageCaptureIntent = isImageCaptureIntent
    }

    override fun setFlashlightState(state: Int) {
        mFlashlightState = state
        checkFlashlight()
    }

    override fun getCameraState() = mCameraState

    override fun showChangeResolutionDialog() {
    }

    override fun toggleFrontBackCamera() {
        mUseFrontCamera = !mUseFrontCamera
    }

    override fun toggleFlashlight() {
        val newState = ++mFlashlightState % if (mIsInVideoMode) 2 else 3
        setFlashlightState(newState)
    }

    override fun tryTakePicture() {
        takePicture()
    }

    override fun toggleRecording(): Boolean {
        return false
    }

    override fun tryInitVideoMode() {
    }

    override fun initPhotoMode() {
        mIsInVideoMode = false
    }

    override fun initVideoMode(): Boolean {
        mIsInVideoMode = true
        return false
    }

    override fun checkFlashlight() {
        if (mCameraState == STATE_PREVIEW && mIsFlashSupported) {
            mPreviewRequestBuilder!!.set(CaptureRequest.FLASH_MODE, getFlashlightMode(mFlashlightState))
            mCaptureSession!!.setRepeatingRequest(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
            mActivity.updateFlashlightState(mFlashlightState)
        }
    }

    override fun deviceOrientationChanged() {
    }

    override fun resumeCamera() = true

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
}
