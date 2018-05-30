package com.simplemobiletools.camera.views

import android.annotation.TargetApi
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.interfaces.MyPreview
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// based on the Android Camera2 sample at https://github.com/googlesamples/android-Camera2Basic
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class PreviewCameraTwo : ViewGroup, TextureView.SurfaceTextureListener, MyPreview {
    private val MAX_PREVIEW_WIDTH = 1920
    private val MAX_PREVIEW_HEIGHT = 1080

    private lateinit var mActivity: MainActivity
    private lateinit var mTextureView: AutoFitTextureView

    private var mSensorOrientation = 0
    private var mRotationAtCapture = 0
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
    private val mCameraOpenCloseLock = Semaphore(1)

    constructor(context: Context) : super(context)

    constructor(activity: MainActivity, textureView: AutoFitTextureView) : super(activity) {
        mActivity = activity
        mTextureView = textureView
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
        mBackgroundThread?.quitSafely()
        try {
            mBackgroundThread?.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
        }
    }

    private fun openCamera(width: Int, height: Int) {
        setupCameraOutputs(width, height)
        val manager = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(mCameraId, cameraStateCallback, mBackgroundHandler)
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
        PhotoProcessor(mActivity, mTargetUri, 0, mRotationAtCapture, mActivity.config.flipPhotos && getIsFrontCamera()).execute(bytes)
    }

    private fun getIsFrontCamera(): Boolean {
        val manager = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = manager.getCameraCharacteristics(mCameraId)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    private fun setupCameraOutputs(width: Int, height: Int) {
        val manager = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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

                mPreviewSize = chooseOptimalSize(configMap.getOutputSizes(SurfaceTexture::class.java),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest)

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

            mCameraDevice!!.createCaptureSession(Arrays.asList(surface, mImageReader!!.surface),
                    object : CameraCaptureSession.StateCallback() {
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

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        }
                    }, null
            )
        } catch (e: CameraAccessException) {
        }
    }

    private val mCaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mCameraState) {
                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == null) {
                        captureStillPicture()
                    } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mCameraState = STATE_PICTURE_TAKEN
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
                        mCameraState = STATE_PICTURE_TAKEN
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

            mRotationAtCapture = mActivity.mLastHandledOrientation
            val captureBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(mImageReader!!.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation)
            }

            val CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    unlockFocus()
                }
            }

            mCaptureSession!!.apply {
                stopRepeating()
                abortCaptures()
                capture(captureBuilder.build(), CaptureCallback, null)
            }
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

    private fun lockFocus() {
        try {
            mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            mCameraState = STATE_WAITING_LOCK
            mCaptureSession!!.capture(mPreviewRequestBuilder!!.build(), mCaptureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
        }
    }

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

    override fun setCamera(cameraId: Int): Boolean {
        return false
    }

    override fun getCameraState() = mCameraState

    override fun releaseCamera() {
    }

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
