package com.simplemobiletools.camera.implementations

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.util.Size
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.ScaleType
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.load.ImageHeaderParser.UNKNOWN_ORIENTATION
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.*
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.CaptureMode
import com.simplemobiletools.camera.models.MediaOutput
import com.simplemobiletools.camera.models.MySize
import com.simplemobiletools.camera.models.ResolutionOption
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.PERMISSION_ACCESS_FINE_LOCATION
import com.simplemobiletools.commons.helpers.ensureBackgroundThread

class CameraXPreview(
    private val activity: BaseSimpleActivity,
    private val previewView: PreviewView,
    private val mediaSoundHelper: MediaSoundHelper,
    private val mediaOutputHelper: MediaOutputHelper,
    private val cameraErrorHandler: CameraErrorHandler,
    private val listener: CameraXPreviewListener,
    private val isThirdPartyIntent: Boolean,
    initInPhotoMode: Boolean,
) : MyPreview, DefaultLifecycleObserver {

    companion object {
        // Auto focus is 1/6 of the area.
        private const val AF_SIZE = 1.0f / 6.0f
        private const val AE_SIZE = AF_SIZE * 1.5f
        private const val CAMERA_MODE_SWITCH_WAIT_TIME = 500L
    }

    private val config = activity.config
    private val contentResolver = activity.contentResolver
    private val mainExecutor = ContextCompat.getMainExecutor(activity)
    private val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val windowMetricsCalculator = WindowMetricsCalculator.getOrCreate()
    private val videoQualityManager = VideoQualityManager(activity)
    private val imageQualityManager = ImageQualityManager(activity)
    private val mediaSizeStore = MediaSizeStore(config)

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

            if (lastRotation != rotation) {
                preview?.targetRotation = rotation
                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
                lastRotation = rotation
            }
        }
    }
    private val cameraHandler = Handler(Looper.getMainLooper())
    private val photoModeRunnable = Runnable {
        if (imageCapture == null) {
            isPhotoCapture = true
            if (!isThirdPartyIntent) { // we don't want to store the state for 3rd party intents
                config.initPhotoMode = true
            }
            startCamera()
        } else {
            listener.onInitPhotoMode()
        }
    }
    private val videoModeRunnable = Runnable {
        if (videoCapture == null) {
            isPhotoCapture = false
            if (!isThirdPartyIntent) { // we don't want to store the state for 3rd party intents
                config.initPhotoMode = false
            }
            startCamera()
        } else {
            listener.onInitVideoMode()
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
    private var lastRotation = 0
    private var lastCameraStartTime = 0L
    private var simpleLocationManager: SimpleLocationManager? = null

    init {
        bindToLifeCycle()
    }

    private fun bindToLifeCycle() {
        activity.lifecycle.addObserver(this)
    }

    private fun startCamera(switching: Boolean = false) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity.applicationContext)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                imageQualityManager.initSupportedQualities()
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

        val resolution = if (isPhotoCapture) {
            imageQualityManager.getUserSelectedResolution(cameraSelector).also {
                listener.displaySelectedResolution(it.toResolutionOption())
            }
        } else {
            val selectedQuality = videoQualityManager.getUserSelectedQuality(cameraSelector).also {
                listener.displaySelectedResolution(it.toResolutionOption())
            }
            MySize(selectedQuality.width, selectedQuality.height)
        }

        listener.adjustPreviewView(resolution.requiresCentering())

        val isFullSize = resolution.isFullScreen
        previewView.scaleType = if (isFullSize) ScaleType.FILL_CENTER else ScaleType.FIT_CENTER
        val rotation = previewView.display.rotation
        val rotatedResolution = getRotatedResolution(resolution, rotation)

        val previewUseCase = buildPreview(rotatedResolution, rotation)
        val captureUseCase = getCaptureUseCase(rotatedResolution, rotation)

        cameraProvider.unbindAll()
        camera = if (isFullSize) {
            val metrics = windowMetricsCalculator.computeCurrentWindowMetrics(activity).bounds
            val screenWidth = metrics.width()
            val screenHeight = metrics.height()
            val viewPort = ViewPort.Builder(Rational(screenWidth, screenHeight), rotation).build()

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(previewUseCase)
                .addUseCase(captureUseCase)
                .setViewPort(viewPort)
                .build()

            cameraProvider.bindToLifecycle(
                activity,
                cameraSelector,
                useCaseGroup,
            )
        } else {
            cameraProvider.bindToLifecycle(
                activity,
                cameraSelector,
                previewUseCase,
                captureUseCase,
            )
        }
        preview = previewUseCase
        setupZoomAndFocus()
        setFlashlightState(config.flashlightState)
    }

    private fun getRotatedResolution(resolution: MySize, rotationDegrees: Int): Size {
        return if (rotationDegrees == Surface.ROTATION_0 || rotationDegrees == Surface.ROTATION_180) {
            Size(resolution.height, resolution.width)
        } else {
            Size(resolution.width, resolution.height)
        }
    }

    private fun buildPreview(resolution: Size, rotation: Int): Preview {
        return Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetResolution(resolution)
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
    }

    private fun getCaptureUseCase(resolution: Size, rotation: Int): UseCase {
        return if (isPhotoCapture) {
            buildImageCapture(resolution, rotation).also {
                imageCapture = it
                videoCapture = null
            }
        } else {
            buildVideoCapture().also {
                videoCapture = it
                imageCapture = null
            }
        }
    }

    private fun buildImageCapture(resolution: Size, rotation: Int): ImageCapture {
        return Builder()
            .setCaptureMode(getCaptureMode())
            .setFlashMode(flashMode)
            .setJpegQuality(config.photoQuality)
            .setTargetRotation(rotation)
            .setTargetResolution(resolution)
            .build()
    }

    private fun getCaptureMode(): Int {
        return when (config.captureMode) {
            CaptureMode.MINIMIZE_LATENCY -> CAPTURE_MODE_MINIMIZE_LATENCY
            CaptureMode.MAXIMIZE_QUALITY -> CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }

    private fun buildVideoCapture(): VideoCapture<Recorder> {
        val qualitySelector = QualitySelector.from(
            videoQualityManager.getUserSelectedQuality(cameraSelector).toCameraXQuality(),
            FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        return VideoCapture.withOutput(recorder)
    }

    private fun setupCameraObservers() {
        listener.setFlashAvailable(camera?.cameraInfo?.hasFlashUnit() ?: false)
        listener.onChangeCamera(isFrontCameraInUse())
        if (isPhotoCapture) {
            listener.onInitPhotoMode()
        } else {
            listener.onInitVideoMode()
        }
        camera?.cameraInfo?.cameraState?.observe(activity) { cameraState ->
            if (cameraState.error == null) {
                when (cameraState.type) {
                    CameraState.Type.OPENING,
                    CameraState.Type.OPEN -> {
                        listener.setHasFrontAndBackCamera(hasFrontCamera() && hasBackCamera())
                        listener.setCameraAvailable(true)
                    }
                    CameraState.Type.PENDING_OPEN,
                    CameraState.Type.CLOSING,
                    CameraState.Type.CLOSED -> {
                        listener.setCameraAvailable(false)
                    }
                }
            } else {
                listener.setCameraAvailable(false)
                cameraErrorHandler.handleCameraError(cameraState.error)
            }
        }
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
            override fun onDown(event: MotionEvent): Boolean {
                listener.onTouchPreview()
                return super.onDown(event)
            }

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
                    listener.onFocusCamera(event.rawX, event.rawY)
                    true
                } ?: false
            }
        })
        previewView.setOnTouchListener { _, event ->
            val handledGesture = gestureDetector.onTouchEvent(event)
            val handledScaleGesture = scaleGesture?.onTouchEvent(event)
            handledGesture || handledScaleGesture ?: false
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        orientationEventListener.enable()
        previewView.doOnLayout {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startCamera()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (config.savePhotoVideoLocation) {
            if (simpleLocationManager == null) {
                simpleLocationManager = SimpleLocationManager(activity)
            }
            requestLocationUpdates()
        }
    }

    private fun requestLocationUpdates() {
        activity.apply {
            if (checkLocationPermission()) {
                simpleLocationManager?.requestLocationUpdates()
            } else {
                handlePermission(PERMISSION_ACCESS_FINE_LOCATION) { _ ->
                    if (checkLocationPermission()) {
                        simpleLocationManager?.requestLocationUpdates()
                    } else {
                        config.savePhotoVideoLocation = false
                    }
                }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        simpleLocationManager?.dropLocationUpdates()
    }

    override fun onStop(owner: LifecycleOwner) {
        orientationEventListener.disable()
    }

    override fun isInPhotoMode(): Boolean {
        return isPhotoCapture
    }

    override fun showChangeResolution() {
        val selectedResolution = if (isPhotoCapture) {
            imageQualityManager.getUserSelectedResolution(cameraSelector).toResolutionOption()
        } else {
            videoQualityManager.getUserSelectedQuality(cameraSelector).toResolutionOption()
        }

        val resolutions = if (isPhotoCapture) {
            imageQualityManager.getSupportedResolutions(cameraSelector).map { it.toResolutionOption() }
        } else {
            videoQualityManager.getSupportedQualities(cameraSelector).map { it.toResolutionOption() }
        }

        if (resolutions.size > 2) {
            listener.showImageSizes(
                selectedResolution = selectedResolution,
                resolutions = resolutions,
                isPhotoCapture = isPhotoCapture,
                isFrontCamera = isFrontCameraInUse()
            ) { index, changed ->
                mediaSizeStore.storeSize(isPhotoCapture, isFrontCameraInUse(), index)
                if (changed) {
                    currentRecording?.stop()
                    startCamera()
                }
            }
        } else {
            toggleResolutions(resolutions)
        }
    }

    private fun toggleResolutions(resolutions: List<ResolutionOption>) {
        if (resolutions.size >= 2) {
            val currentIndex = mediaSizeStore.getCurrentSizeIndex(isPhotoCapture, isFrontCameraInUse())

            val nextIndex = if (currentIndex >= resolutions.lastIndex) {
                0
            } else {
                currentIndex + 1
            }

            mediaSizeStore.storeSize(isPhotoCapture, isFrontCameraInUse(), nextIndex)
            currentRecording?.stop()
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

    override fun handleFlashlightClick() {
        if (isPhotoCapture) {
            listener.showFlashOptions(true)
        } else {
            toggleFlashlight()
        }
    }

    private fun toggleFlashlight() {
        val newFlashMode = if (isPhotoCapture) {
            when (flashMode) {
                FLASH_MODE_OFF -> FLASH_MODE_ON
                FLASH_MODE_ON -> FLASH_MODE_AUTO
                else -> FLASH_MODE_OFF
            }
        } else {
            when (flashMode) {
                FLASH_MODE_OFF -> FLASH_MODE_ON
                else -> FLASH_MODE_OFF
            }
        }
        setFlashlightState(newFlashMode.toAppFlashMode())
    }

    override fun setFlashlightState(state: Int) {
        var flashState = state
        if (isPhotoCapture) {
            camera?.cameraControl?.enableTorch(flashState == FLASH_ALWAYS_ON)
        } else {
            camera?.cameraControl?.enableTorch(flashState == FLASH_ON || flashState == FLASH_ALWAYS_ON)
            // reset to the FLASH_ON for video capture
            if (flashState == FLASH_ALWAYS_ON) {
                flashState = FLASH_ON
            }
        }
        val newFlashMode = flashState.toCameraXFlashMode()
        flashMode = newFlashMode
        imageCapture?.flashMode = newFlashMode

        config.flashlightState = flashState
        listener.onChangeFlashMode(flashState)
    }

    override fun tryTakePicture() {
        if (imageCapture == null) {
            activity.toast(R.string.camera_open_error)
            return
        }

        val imageCapture = imageCapture

        val metadata = Metadata().apply {
            isReversedHorizontal = isFrontCameraInUse() && config.flipPhotos
            if (config.savePhotoVideoLocation) {
                location = simpleLocationManager?.getLocation()
            }
        }

        val mediaOutput = mediaOutputHelper.getImageMediaOutput()
        imageCapture!!.takePicture(mainExecutor, object : OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                listener.shutterAnimation()
                playShutterSoundIfEnabled()
                ensureBackgroundThread {
                    image.use {
                        if (mediaOutput is MediaOutput.BitmapOutput) {
                            val imageBytes = ImageUtil.jpegImageToJpegByteArray(image)
                            val bitmap = BitmapUtils.makeBitmap(imageBytes)
                            activity.runOnUiThread {
                                listener.onPhotoCaptureEnd()
                                if (bitmap != null) {
                                    listener.onImageCaptured(bitmap)
                                } else {
                                    cameraErrorHandler.handleImageCaptureError(ERROR_CAPTURE_FAILED)
                                }
                            }
                        } else {
                            ImageSaver.saveImage(
                                contentResolver = contentResolver,
                                image = image,
                                mediaOutput = mediaOutput,
                                metadata = metadata,
                                jpegQuality = config.photoQuality,
                                saveExifAttributes = config.savePhotoMetadata,
                                onImageSaved = { savedUri ->
                                    activity.runOnUiThread {
                                        listener.onPhotoCaptureEnd()
                                        listener.onMediaSaved(savedUri)
                                    }
                                },
                                onError = ::handleImageCaptureError
                            )
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                handleImageCaptureError(exception)
            }
        })
    }

    private fun handleImageCaptureError(exception: ImageCaptureException) {
        listener.onPhotoCaptureEnd()
        cameraErrorHandler.handleImageCaptureError(exception.imageCaptureError)
    }

    override fun initPhotoMode() {
        debounceChangeCameraMode(photoModeRunnable)
    }

    override fun initVideoMode() {
        debounceChangeCameraMode(videoModeRunnable)
    }

    private fun debounceChangeCameraMode(cameraModeRunnable: Runnable) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCameraStartTime > CAMERA_MODE_SWITCH_WAIT_TIME) {
            cameraModeRunnable.run()
        } else {
            cameraHandler.removeCallbacks(photoModeRunnable)
            cameraHandler.removeCallbacks(videoModeRunnable)
            cameraHandler.postDelayed(cameraModeRunnable, CAMERA_MODE_SWITCH_WAIT_TIME)
        }
        lastCameraStartTime = currentTime
    }

    override fun toggleRecording() {
        if (currentRecording == null || recordingState is VideoRecordEvent.Finalize) {
            if (config.isSoundEnabled) {
                mediaSoundHelper.playStartVideoRecordingSound(onPlayComplete = {
                    startRecording()
                })
                listener.onVideoRecordingStarted()
            } else {
                startRecording()
            }
        } else {
            currentRecording?.stop()
            currentRecording = null
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun startRecording() {
        if (videoCapture == null) {
            activity.toast(R.string.camera_open_error)
            return
        }

        val videoCapture = videoCapture

        val recording = when (val mediaOutput = mediaOutputHelper.getVideoMediaOutput()) {
            is MediaOutput.FileDescriptorMediaOutput -> {
                FileDescriptorOutputOptions.Builder(mediaOutput.fileDescriptor).apply {
                    if (config.savePhotoVideoLocation) {
                        setLocation(simpleLocationManager?.getLocation())
                    }
                }.build().let { videoCapture!!.output.prepareRecording(activity, it) }
            }
            is MediaOutput.FileMediaOutput -> {
                FileOutputOptions.Builder(mediaOutput.file).apply {
                    if (config.savePhotoVideoLocation) {
                        setLocation(simpleLocationManager?.getLocation())
                    }
                }.build().let { videoCapture!!.output.prepareRecording(activity, it) }
            }
            is MediaOutput.MediaStoreOutput -> {
                MediaStoreOutputOptions.Builder(contentResolver, mediaOutput.contentUri).apply {
                    setContentValues(mediaOutput.contentValues)
                    if (config.savePhotoVideoLocation) {
                        setLocation(simpleLocationManager?.getLocation())
                    }
                }.build().let { videoCapture!!.output.prepareRecording(activity, it) }
            }
        }

        currentRecording = recording.withAudioEnabled()
            .start(mainExecutor) { recordEvent ->
                recordingState = recordEvent
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
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
                            listener.onMediaSaved(recordEvent.outputResults.outputUri)
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

    private fun playStopVideoRecordingSoundIfEnabled() {
        if (config.isSoundEnabled) {
            mediaSoundHelper.playStopVideoRecordingSound()
        }
    }
}
