package com.simplemobiletools.camera.implementations

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
import androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.FLASH_MODE_ON
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.load.ImageHeaderParser.UNKNOWN_ORIENTATION
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.toAppFlashMode
import com.simplemobiletools.camera.extensions.toCameraXFlashMode
import com.simplemobiletools.camera.helpers.MediaSoundHelper
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraXPreview(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val listener: CameraXPreviewListener,
) : MyPreview, DefaultLifecycleObserver {

    companion object {
        private const val TAG = "CameraXPreview"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        // Auto focus is 1/6 of the area.
        private const val AF_SIZE = 1.0f / 6.0f
        private const val AE_SIZE = AF_SIZE * 1.5f
    }

    private val config = activity.config
    private val contentResolver = activity.contentResolver
    private val mainExecutor = activity.mainExecutor
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mediaSoundHelper = MediaSoundHelper()
    private val windowMetricsCalculator = WindowMetricsCalculator.getOrCreate()

    private val orientationEventListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
        @SuppressLint("RestrictedApi")
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == UNKNOWN_ORIENTATION) {
                return
            }

            val rotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }

            preview?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
            videoCapture?.targetRotation = rotation
        }
    }

    private val hasBackCamera: Boolean
        get() = cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false

    private val hasFrontCamera: Boolean
        get() = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false

    private val cameraCount: Int
        get() = cameraProvider?.availableCameraInfos?.size ?: 0

    private val frontCameraInUse: Boolean
        get() = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var currentRecording: Recording? = null
    private var recordingState: VideoRecordEvent? = null
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = config.flashlightState.toCameraXFlashMode()
    private var isPhotoCapture = config.initPhotoMode

    init {
        bindToLifeCycle()
        mediaSoundHelper.loadSounds()
        previewView.doOnLayout {
            startCamera()
        }
    }

    private fun bindToLifeCycle() {
        activity.lifecycle.addObserver(this)
    }

    private fun startCamera() {
        Log.i(TAG, "startCamera: ")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                setupCameraObservers()
            } catch (e: Exception) {
                Log.e(TAG, "startCamera: ", e)
                activity.showErrorToast(activity.getString(R.string.camera_open_error))
            }
        }, mainExecutor)
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val metrics = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds
        val aspectRatio = aspectRatio(metrics.width(), metrics.height())
        val rotation = previewView.display.rotation

        preview = buildPreview(aspectRatio, rotation)
        val captureUseCase = getCaptureUseCase(aspectRatio, rotation)
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            activity,
            lensFacing,
            preview,
            captureUseCase,
        )
        preview?.setSurfaceProvider(previewView.surfaceProvider)
        setupZoomAndFocus()
    }

    private fun setupCameraObservers() {
        listener.setFlashAvailable(camera?.cameraInfo?.hasFlashUnit() ?: false)
        listener.onChangeCamera(frontCameraInUse)

        camera?.cameraInfo?.cameraState?.observe(activity) { cameraState ->
            when (cameraState.type) {
                CameraState.Type.OPEN,
                CameraState.Type.OPENING -> {
                    listener.setHasFrontAndBackCamera(hasFrontCamera && hasBackCamera)
                    listener.setCameraAvailable(true)
                }
                CameraState.Type.PENDING_OPEN,
                CameraState.Type.CLOSING,
                CameraState.Type.CLOSED -> {
                    listener.setCameraAvailable(false)
                }
            }

            // TODO: Handle errors
            cameraState.error?.let { error ->
                listener.setCameraAvailable(false)
                when (error.code) {
                    CameraState.ERROR_STREAM_CONFIG -> {
                        Log.e(TAG, "ERROR_STREAM_CONFIG")
                        // Make sure to setup the use cases properly
                        activity.toast(R.string.camera_unavailable)
                    }
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        Log.e(TAG, "ERROR_CAMERA_IN_USE")
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        activity.showErrorToast("Camera is in use by another app, please close")
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        Log.e(TAG, "ERROR_MAX_CAMERAS_IN_USE")
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        activity.showErrorToast("Camera is in use by another app, please close")
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Log.e(TAG, "ERROR_OTHER_RECOVERABLE_ERROR")
                        activity.toast(R.string.camera_open_error)
                    }
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        Log.e(TAG, "ERROR_CAMERA_DISABLED")
                        // Ask the user to enable the device's cameras
                        activity.toast(R.string.camera_open_error)
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        Log.e(TAG, "ERROR_CAMERA_FATAL_ERROR")
                        // Ask the user to reboot the device to restore camera function
                        activity.toast(R.string.camera_open_error)
                    }
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Log.e(TAG, "ERROR_DO_NOT_DISTURB_MODE_ENABLED")
                        activity.toast(R.string.camera_open_error)
                    }
                }
            }
        }
    }

    private fun getCaptureUseCase(aspectRatio: Int, rotation: Int): UseCase {
        return if (isPhotoCapture) {
            cameraProvider?.unbind(videoCapture)
            buildImageCapture(aspectRatio, rotation).also {
                imageCapture = it
            }
        } else {
            cameraProvider?.unbind(imageCapture)
            buildVideoCapture().also {
                videoCapture = it
            }
        }
    }

    private fun buildImageCapture(aspectRatio: Int, rotation: Int): ImageCapture {
        return ImageCapture.Builder()
            .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .setJpegQuality(config.photoQuality)
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(rotation)
            .build()
    }

    private fun buildPreview(aspectRatio: Int, rotation: Int): Preview {
        return Preview.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(rotation)
            .build()
    }

    private fun buildVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            //TODO: user control for quality
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        return VideoCapture.withOutput(recorder)
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    @SuppressLint("ClickableViewAccessibility")
    // source: https://stackoverflow.com/a/60095886/10552591
    private fun setupZoomAndFocus() {
        Log.i(TAG, "camera controller: ${previewView.controller}")
        val gestureDetector = GestureDetector(activity, object : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                return camera?.cameraInfo?.let {
                    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    val width = previewView.width.toFloat()
                    val height = previewView.height.toFloat()
                    Log.i(TAG, "onSingleTapConfirmed: width=$width,height=$height")
                    val factory = DisplayOrientedMeteringPointFactory(display, it, width, height)
                    val xPos = event.x
                    val yPos = event.y
                    val autoFocusPoint = factory.createPoint(xPos, yPos, AF_SIZE)
                    val autoExposurePoint = factory.createPoint(xPos, yPos, AE_SIZE)
                    val focusMeteringAction = FocusMeteringAction.Builder(autoFocusPoint, FocusMeteringAction.FLAG_AF)
                        .addPoint(autoExposurePoint, FocusMeteringAction.FLAG_AE)
                        .disableAutoCancel()
                        .build()
                    camera?.cameraControl?.startFocusAndMetering(focusMeteringAction)
                    listener.onFocusCamera(xPos, yPos)
                    Log.i(TAG, "start focus")
                    true
                } ?: false
            }
        })
        previewView.setOnTouchListener { _, event ->
            Log.i(TAG, "setOnTouchListener: x=${event.x}, y=${event.y}")
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        orientationEventListener.enable()
    }

    override fun onStop(owner: LifecycleOwner) {
        orientationEventListener.disable()
    }

    override fun setTargetUri(uri: Uri) {

    }

    override fun showChangeResolutionDialog() {

    }

    override fun toggleFrontBackCamera() {
        lensFacing = if (frontCameraInUse) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        startCamera()
    }

    override fun toggleFlashlight() {
        val newFlashMode = when (flashMode) {
            FLASH_MODE_OFF -> FLASH_MODE_ON
            FLASH_MODE_ON -> FLASH_MODE_AUTO
            FLASH_MODE_AUTO -> FLASH_MODE_OFF
            else -> throw IllegalArgumentException("Unknown mode: $flashMode")
        }

        flashMode = newFlashMode
        imageCapture?.flashMode = newFlashMode
        val appFlashMode = flashMode.toAppFlashMode()
        config.flashlightState = appFlashMode
        listener.onChangeFlashMode(appFlashMode)
    }

    override fun tryTakePicture() {
        Log.i(TAG, "captureImage: ")
        val imageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        val metadata = Metadata().apply {
            isReversedHorizontal = config.flipPhotos
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getRandomMediaName(true))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outputOptions = OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
            .setMetadata(metadata)
            .build()


        imageCapture.takePicture(outputOptions, mainExecutor, object : OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: OutputFileResults) {
                listener.toggleBottomButtons(false)
                listener.onMediaCaptured(outputFileResults.savedUri!!)
            }

            override fun onError(exception: ImageCaptureException) {
                listener.toggleBottomButtons(false)
                activity.showErrorToast("Capture picture $exception")
                Log.e(TAG, "Error", exception)
            }
        })
        playShutterSoundIfEnabled()
    }

    override fun initPhotoMode() {
        isPhotoCapture = true
        startCamera()
    }

    override fun initVideoMode() {
        isPhotoCapture = false
        startCamera()
    }

    override fun toggleRecording() {
        Log.d(TAG, "toggleRecording: currentRecording=$currentRecording, recordingState=$recordingState")
        if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
            startRecording()
        } else {
            currentRecording?.stop()
            currentRecording = null
            Log.d(TAG, "Recording stopped")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val videoCapture = videoCapture ?: throw IllegalStateException("Camera initialization failed.")
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getRandomMediaName(false))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val contentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val outputOptions = MediaStoreOutputOptions.Builder(contentResolver, contentUri)
            .setContentValues(contentValues)
            .build()

        currentRecording = videoCapture.output
            .prepareRecording(activity, outputOptions)
            .withAudioEnabled()
            .start(mainExecutor) { recordEvent ->
                Log.d(TAG, "recordEvent=$recordEvent ")
                recordingState = recordEvent
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        playStartVideoRecordingSoundIfEnabled()
                        listener.onVideoRecordingStarted()
                    }

                    is VideoRecordEvent.Status -> {
                        listener.onVideoDurationChanged(recordEvent.recordingStats.recordedDurationNanos)
                    }

                    is VideoRecordEvent.Finalize -> {
                        playStopVideoRecordingSoundIfEnabled()
                        listener.onVideoRecordingStopped()
                        if (recordEvent.hasError()) {
                            // TODO: Handle errors
                        } else {
                            listener.onMediaCaptured(recordEvent.outputResults.outputUri)
                        }
                    }
                }
            }
        Log.d(TAG, "Recording started")
    }

    private fun getRandomMediaName(isPhoto: Boolean): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return if (isPhoto) {
            "IMG_$timestamp"
        } else {
            "VID_$timestamp"
        }
    }

    private fun playShutterSoundIfEnabled() {
        if (config.isSoundEnabled) {
            mediaSoundHelper.playShutterSound()
        }
    }

    private fun playStartVideoRecordingSoundIfEnabled() {
        if (config.isSoundEnabled) {
            mediaSoundHelper.playStartVideoRecordingSound()
        }
    }

    private fun playStopVideoRecordingSoundIfEnabled() {
        if (config.isSoundEnabled) {
            mediaSoundHelper.playStopVideoRecordingSound()
        }
    }
}
