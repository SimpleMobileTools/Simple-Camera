package com.simplemobiletools.camera.implementations

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.load.ImageHeaderParser.UNKNOWN_ORIENTATION
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.dialogs.ChangeResolutionDialogX
import com.simplemobiletools.camera.extensions.*
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.MediaOutput
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraXPreview(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
    private val mediaOutputHelper: MediaOutputHelper,
    private val cameraErrorHandler: CameraErrorHandler,
    private val listener: CameraXPreviewListener,
    initInPhotoMode: Boolean,
) : MyPreview, DefaultLifecycleObserver {

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        // Auto focus is 1/6 of the area.
        private const val AF_SIZE = 1.0f / 6.0f
        private const val AE_SIZE = AF_SIZE * 1.5f
    }

    private val config = activity.config
    private val contentResolver = activity.contentResolver
    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mediaSoundHelper = MediaSoundHelper()
    private val windowMetricsCalculator = WindowMetricsCalculator.getOrCreate()
    private val videoQualityManager = VideoQualityManager(activity)
    private val imageQualityManager = ImageQualityManager(activity)
    private val exifRemover = ExifRemover(contentResolver)

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

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var currentRecording: Recording? = null
    private var recordingState: VideoRecordEvent? = null
    private var cameraSelector = config.lastUsedCameraLens.toCameraSelector()
    private var flashMode = FLASH_MODE_OFF
    private var isPhotoCapture = initInPhotoMode

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

    private fun startCamera(switching: Boolean = false) {
        imageQualityManager.initSupportedQualities()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                videoQualityManager.initSupportedQualities(provider)
                bindCameraUseCases()
                setupCameraObservers()
            } catch (e: Exception) {
                val errorMessage = if (switching) R.string.camera_switch_error else R.string.camera_open_error
                activity.toast(errorMessage)
            }
        }, mainExecutor)
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val metrics = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds
        val aspectRatio = if (isPhotoCapture) {
            aspectRatio(metrics.width(), metrics.height())
        } else {
            val selectedQuality = videoQualityManager.getUserSelectedQuality(cameraSelector)
            selectedQuality.getAspectRatio()
        }
        val rotation = previewView.display.rotation

        preview = buildPreview(aspectRatio, rotation)
        val captureUseCase = getCaptureUseCase(aspectRatio, rotation)
        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            activity,
            cameraSelector,
            preview,
            captureUseCase,
        )

        preview?.setSurfaceProvider(previewView.surfaceProvider)
        setupZoomAndFocus()
    }

    private fun setupCameraObservers() {
        listener.setFlashAvailable(camera?.cameraInfo?.hasFlashUnit() ?: false)
        listener.onChangeCamera(isFrontCameraInUse())

        camera?.cameraInfo?.cameraState?.observe(activity) { cameraState ->
            when (cameraState.type) {
                CameraState.Type.OPEN,
                CameraState.Type.OPENING -> {
                    listener.setHasFrontAndBackCamera(hasFrontCamera() && hasBackCamera())
                    listener.setCameraAvailable(true)
                }
                CameraState.Type.PENDING_OPEN,
                CameraState.Type.CLOSING,
                CameraState.Type.CLOSED -> {
                    listener.setCameraAvailable(false)
                }
            }

            cameraErrorHandler.handleCameraError(cameraState?.error)
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
        return Builder()
            .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .setJpegQuality(config.photoQuality)
            .setTargetRotation(rotation)
            .apply {
                imageQualityManager.getUserSelectedResolution(cameraSelector)?.let { resolution ->
                    val rotatedResolution = getRotatedResolution(rotation, resolution)
                    setTargetResolution(rotatedResolution)
                } ?: setTargetAspectRatio(aspectRatio)
            }
            .build()
    }

    private fun getRotatedResolution(rotationDegrees: Int, resolution: Size): Size {
        return if (rotationDegrees == Surface.ROTATION_0 || rotationDegrees == Surface.ROTATION_180) {
            Size(resolution.height, resolution.width)
        } else {
            Size(resolution.width, resolution.height)
        }
    }

    private fun buildPreview(aspectRatio: Int, rotation: Int): Preview {
        return Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(aspectRatio)
            .build()
    }

    private fun buildVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.from(
            videoQualityManager.getUserSelectedQuality(cameraSelector),
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
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

    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun isFrontCameraInUse(): Boolean {
        return cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
    }

    @SuppressLint("ClickableViewAccessibility")
    // source: https://stackoverflow.com/a/60095886/10552591
    private fun setupZoomAndFocus() {
        val scaleGesture = camera?.let { ScaleGestureDetector(activity, PinchToZoomOnScaleGestureListener(it.cameraInfo, it.cameraControl)) }
        val gestureDetector = GestureDetector(activity, object : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                return camera?.cameraInfo?.let {
                    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    val width = previewView.width.toFloat()
                    val height = previewView.height.toFloat()
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
                    true
                } ?: false
            }
        })
        previewView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleGesture?.onTouchEvent(event)
            true
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        orientationEventListener.enable()
    }

    override fun onStop(owner: LifecycleOwner) {
        orientationEventListener.disable()
    }

    override fun showChangeResolutionDialog() {
        val oldQuality = videoQualityManager.getUserSelectedQuality(cameraSelector)
        ChangeResolutionDialogX(
            activity,
            isFrontCameraInUse(),
            imageQualityManager.getSupportedResolutions(cameraSelector),
            videoQualityManager.getSupportedQualities(cameraSelector)
        ) {
            if (oldQuality != videoQualityManager.getUserSelectedQuality(cameraSelector)) {
                currentRecording?.stop()
            }
            startCamera()
        }
    }

    override fun toggleFrontBackCamera() {
        val newCameraSelector = if (isFrontCameraInUse()) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        cameraSelector = newCameraSelector
        config.lastUsedCameraLens = newCameraSelector.toLensFacing()
        startCamera(switching = true)
    }

    override fun toggleFlashlight() {
        val newFlashMode = if (isPhotoCapture) {
            when (flashMode) {
                FLASH_MODE_OFF -> FLASH_MODE_ON
                FLASH_MODE_ON -> FLASH_MODE_AUTO
                FLASH_MODE_AUTO -> FLASH_MODE_OFF
                else -> throw IllegalArgumentException("Unknown mode: $flashMode")
            }
        } else {
            when (flashMode) {
                FLASH_MODE_OFF -> FLASH_MODE_ON
                FLASH_MODE_ON -> FLASH_MODE_OFF
                else -> throw IllegalArgumentException("Unknown mode: $flashMode")
            }.also {
                camera?.cameraControl?.enableTorch(it == FLASH_MODE_ON)
            }
        }
        flashMode = newFlashMode
        imageCapture?.flashMode = newFlashMode
        val appFlashMode = flashMode.toAppFlashMode()
        config.flashlightState = appFlashMode
        listener.onChangeFlashMode(appFlashMode)
    }

    override fun tryTakePicture() {
        val imageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        val metadata = Metadata().apply {
            isReversedHorizontal = isFrontCameraInUse() && config.flipPhotos
        }

        val mediaOutput = mediaOutputHelper.getImageMediaOutput()

        if (mediaOutput is MediaOutput.BitmapOutput) {
            imageCapture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    listener.toggleBottomButtons(false)
                    val bitmap = BitmapUtils.makeBitmap(image.toJpegByteArray())
                    if (bitmap != null) {
                        listener.onImageCaptured(bitmap)
                    } else {
                        cameraErrorHandler.handleImageCaptureError(ERROR_CAPTURE_FAILED)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    handleImageCaptureError(exception)
                }
            })
        } else {
            val outputOptionsBuilder = when (mediaOutput) {
                is MediaOutput.MediaStoreOutput -> OutputFileOptions.Builder(contentResolver, mediaOutput.contentUri, mediaOutput.contentValues)
                is MediaOutput.OutputStreamMediaOutput -> OutputFileOptions.Builder(mediaOutput.outputStream)
                is MediaOutput.BitmapOutput -> throw IllegalStateException("Cannot produce an OutputFileOptions for a bitmap output")
                else -> throw IllegalArgumentException("Unexpected option for image ")
            }

            val outputOptions = outputOptionsBuilder.setMetadata(metadata).build()

            imageCapture.takePicture(outputOptions, mainExecutor, object : OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    ensureBackgroundThread {
                        val savedUri = mediaOutput.uri ?: outputFileResults.savedUri!!
                        if (!config.savePhotoMetadata) {
                            exifRemover.removeExif(savedUri)
                        }

                        activity.runOnUiThread {
                            listener.toggleBottomButtons(false)
                            listener.onMediaSaved(savedUri)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    handleImageCaptureError(exception)
                }
            })
        }
        playShutterSoundIfEnabled()
    }

    private fun handleImageCaptureError(exception: ImageCaptureException) {
        listener.toggleBottomButtons(false)
        cameraErrorHandler.handleImageCaptureError(exception.imageCaptureError)
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
        if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
            startRecording()
        } else {
            currentRecording?.stop()
            currentRecording = null
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun startRecording() {
        val videoCapture = videoCapture ?: throw IllegalStateException("Camera initialization failed.")

        val mediaOutput = mediaOutputHelper.getVideoMediaOutput()
        val recording = when (mediaOutput) {
            is MediaOutput.FileDescriptorMediaOutput -> {
                FileDescriptorOutputOptions.Builder(mediaOutput.fileDescriptor).build()
                    .let { videoCapture.output.prepareRecording(activity, it) }
            }
            is MediaOutput.FileMediaOutput -> {
                FileOutputOptions.Builder(mediaOutput.file).build()
                    .let { videoCapture.output.prepareRecording(activity, it) }
            }
            is MediaOutput.MediaStoreOutput -> {
                MediaStoreOutputOptions.Builder(contentResolver, mediaOutput.contentUri).setContentValues(mediaOutput.contentValues).build()
                    .let { videoCapture.output.prepareRecording(activity, it) }
            }
            else -> throw IllegalArgumentException("Unexpected output option for video $mediaOutput")
        }

        currentRecording = recording.withAudioEnabled()
            .start(mainExecutor) { recordEvent ->
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
                            cameraErrorHandler.handleVideoRecordingError(recordEvent.error)
                        } else {
                            listener.onMediaSaved(mediaOutput.uri ?: recordEvent.outputResults.outputUri)
                        }
                    }
                }
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
