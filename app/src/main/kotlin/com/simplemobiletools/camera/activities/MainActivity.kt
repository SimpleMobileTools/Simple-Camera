package com.simplemobiletools.camera.activities

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.transition.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.toFlashModeId
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.implementations.CameraXInitializer
import com.simplemobiletools.camera.implementations.CameraXPreviewListener
import com.simplemobiletools.camera.implementations.MyCameraImpl
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.ResolutionOption
import com.simplemobiletools.camera.views.FocusCircleView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.Release
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_flash.flash_auto
import kotlinx.android.synthetic.main.layout_flash.flash_off
import kotlinx.android.synthetic.main.layout_flash.flash_on
import kotlinx.android.synthetic.main.layout_flash.flash_toggle_group
import kotlinx.android.synthetic.main.layout_media_size.media_size_toggle_group
import kotlinx.android.synthetic.main.layout_top.change_resolution
import kotlinx.android.synthetic.main.layout_top.default_icons
import kotlinx.android.synthetic.main.layout_top.settings
import kotlinx.android.synthetic.main.layout_top.toggle_flash

class MainActivity : SimpleActivity(), PhotoProcessor.MediaSavedListener, CameraXPreviewListener {
    private companion object {
        const val CAPTURE_ANIMATION_DURATION = 500L
        const val PHOTO_MODE_INDEX = 1
        const val VIDEO_MODE_INDEX = 0
    }

    lateinit var mTimerHandler: Handler
    private lateinit var defaultScene: Scene
    private lateinit var mediaSizeScene: Scene
    private lateinit var flashModeScene: Scene
    private lateinit var mOrientationEventListener: OrientationEventListener
    private lateinit var mFocusCircleView: FocusCircleView
    private lateinit var mCameraImpl: MyCameraImpl
    private var mPreview: MyPreview? = null
    private var mPreviewUri: Uri? = null
    private var mIsInPhotoMode = true
    private var mIsCameraAvailable = false
    private var mIsHardwareShutterHandled = false
    private var mCurrVideoRecTimer = 0
    var mLastHandledOrientation = 0

    private val tabSelectedListener = object : TabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            handleTogglePhotoVideo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        appLaunched(BuildConfig.APPLICATION_ID)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        initVariables()
        tryInitCamera()
        supportActionBar?.hide()
        checkWhatsNewDialog()
        setupOrientationEventListener()

        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.statusBars())

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

    }

    private fun selectPhotoTab(triggerListener: Boolean = false) {
        if (!triggerListener) {
            removeTabListener()
        }
        camera_mode_tab.getTabAt(PHOTO_MODE_INDEX)?.select()
        setTabListener()
    }

    private fun selectVideoTab(triggerListener: Boolean = false) {
        if (!triggerListener) {
            removeTabListener()
        }
        camera_mode_tab.getTabAt(VIDEO_MODE_INDEX)?.select()
        setTabListener()
    }

    private fun setTabListener() {
        camera_mode_tab.addOnTabSelectedListener(tabSelectedListener)
    }

    private fun removeTabListener() {
        camera_mode_tab.removeOnTabSelectedListener(tabSelectedListener)
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAndCameraPermissions()) {
            resumeCameraItems()
            setupPreviewImage(mIsInPhotoMode)
            mFocusCircleView.setStrokeColor(getProperPrimaryColor())

            if (isVideoCaptureIntent() && mIsInPhotoMode) {
                handleTogglePhotoVideo()
                checkButtons()
            }
            toggleBottomButtons(false)
            mOrientationEventListener.enable()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureTransparentNavigationBar()
    }

    private fun ensureTransparentNavigationBar() {
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasStorageAndCameraPermissions() || isAskingPermissions) {
            return
        }

        hideTimer()
        mOrientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        mPreview = null
    }

    override fun onBackPressed() {
        if (!closeOptions()) {
            super.onBackPressed()
        }
    }

    private fun initVariables() {
        mIsInPhotoMode = if (isVideoCaptureIntent()) {
            false
        } else if (isImageCaptureIntent()) {
            true
        } else {
            config.initPhotoMode
        }
        mIsCameraAvailable = false
        mIsHardwareShutterHandled = false
        mCurrVideoRecTimer = 0
        mLastHandledOrientation = 0
        mCameraImpl = MyCameraImpl(applicationContext)
        config.lastUsedCamera = mCameraImpl.getBackCameraId().toString()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else if (!mIsHardwareShutterHandled && config.volumeButtonsAsShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mIsHardwareShutterHandled = false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun hideIntentButtons() {
        camera_mode_tab.beGone()
        settings.beGone()
        last_photo_video_preview.beInvisible()
    }

    private fun tryInitCamera() {
        handlePermission(PERMISSION_CAMERA) { grantedCameraPermission ->
            if (grantedCameraPermission) {
                handleStoragePermission { grantedStoragePermission ->
                    if (grantedStoragePermission) {
                        if (mIsInPhotoMode) {
                            initializeCamera()
                        } else {
                            handlePermission(PERMISSION_RECORD_AUDIO) { grantedRecordAudioPermission ->
                                if (grantedRecordAudioPermission) {
                                    initializeCamera()
                                } else {
                                    toast(R.string.no_audio_permissions)
                                    togglePhotoVideoMode()
                                    tryInitCamera()
                                }
                            }
                        }
                    } else {
                        toast(R.string.no_storage_permissions)
                        finish()
                    }
                }
            } else {
                toast(R.string.no_camera_permissions)
                finish()
            }
        }
    }

    private fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
        if (isTiramisuPlus()) {
            handlePermission(PERMISSION_READ_MEDIA_IMAGES) { grantedReadImages ->
                if (grantedReadImages) {
                    handlePermission(PERMISSION_READ_MEDIA_VIDEO) { grantedReadVideos ->
                        callback.invoke(grantedReadVideos)
                    }
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE, callback)
        }
    }

    private fun is3rdPartyIntent() = isVideoCaptureIntent() || isImageCaptureIntent()

    private fun isImageCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_IMAGE_CAPTURE || intent?.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE

    private fun isVideoCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_VIDEO_CAPTURE

    private fun checkImageCaptureIntent() {
        if (isImageCaptureIntent()) {
            hideIntentButtons()
            val output = intent.extras?.get(MediaStore.EXTRA_OUTPUT)
            if (output != null && output is Uri) {
                mPreview?.setTargetUri(output)
            }
        }
    }

    private fun checkVideoCaptureIntent() {
        if (isVideoCaptureIntent()) {
            mIsInPhotoMode = false
            hideIntentButtons()
            shutter.setImageResource(R.drawable.ic_video_rec)
        }
    }

    private fun initializeCamera() {
        setContentView(R.layout.activity_main)
        initButtons()

        defaultScene = Scene(top_options, default_icons)
        mediaSizeScene = Scene(top_options, media_size_toggle_group)
        flashModeScene = Scene(top_options, flash_toggle_group)

        ViewCompat.setOnApplyWindowInsetsListener(view_holder) { _, windowInsets ->
            val safeInsetBottom = windowInsets.displayCutout?.safeInsetBottom ?: 0
            val safeInsetTop = windowInsets.displayCutout?.safeInsetTop ?: 0

            top_options.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = safeInsetTop
            }

            val marginBottom = safeInsetBottom + navigationBarHeight + resources.getDimensionPixelSize(R.dimen.bigger_margin)
            (shutter.layoutParams as ConstraintLayout.LayoutParams).goneBottomMargin = marginBottom

            video_rec_curr_timer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = marginBottom
            }

            WindowInsetsCompat.CONSUMED
        }

        checkVideoCaptureIntent()
        if (mIsInPhotoMode) {
            selectPhotoTab()
        } else {
            selectVideoTab()
        }

        val outputUri = intent.extras?.get(MediaStore.EXTRA_OUTPUT) as? Uri
        val is3rdPartyIntent = is3rdPartyIntent()
        mPreview = CameraXInitializer(this).createCameraXPreview(
            preview_view,
            listener = this,
            outputUri = outputUri,
            is3rdPartyIntent = is3rdPartyIntent,
            initInPhotoMode = mIsInPhotoMode,
        )
        checkImageCaptureIntent()
        mPreview?.setIsImageCaptureIntent(isImageCaptureIntent())

        val imageDrawable =
            if (config.lastUsedCamera == mCameraImpl.getBackCameraId().toString()) R.drawable.ic_camera_front_vector else R.drawable.ic_camera_rear_vector
        toggle_camera.setImageResource(imageDrawable)

        mFocusCircleView = FocusCircleView(applicationContext)
        view_holder.addView(mFocusCircleView)

        mTimerHandler = Handler(Looper.getMainLooper())
        setupPreviewImage(true)

        val initialFlashlightState = FLASH_OFF
        mPreview!!.setFlashlightState(initialFlashlightState)
        updateFlashlightState(initialFlashlightState)
        initFlashModeTransitionNames()
    }

    private fun initFlashModeTransitionNames() {
        val baseName = getString(R.string.toggle_flash)
        flash_auto.transitionName = "$baseName$FLASH_AUTO"
        flash_off.transitionName = "$baseName$FLASH_OFF"
        flash_on.transitionName = "$baseName$FLASH_ON"
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener { toggleCamera() }
        last_photo_video_preview.setOnClickListener { showLastMediaPreview() }
        toggle_flash.setOnClickListener { toggleFlash() }
        shutter.setOnClickListener { shutterPressed() }
        settings.setOnClickListener { launchSettings() }
        change_resolution.setOnClickListener { mPreview?.showChangeResolution() }
        flash_on.setOnClickListener { selectFlashMode(FLASH_ON) }
        flash_off.setOnClickListener { selectFlashMode(FLASH_OFF) }
        flash_auto.setOnClickListener { selectFlashMode(FLASH_AUTO) }
    }

    private fun selectFlashMode(flashMode: Int) {
        closeOptions()
        mPreview?.setFlashlightState(flashMode)
    }

    private fun toggleCamera() {
        if (checkCameraAvailable()) {
            mPreview!!.toggleFrontBackCamera()
        }
    }

    private fun showLastMediaPreview() {
        if (mPreviewUri != null) {
            val path = applicationContext.getRealPathFromURI(mPreviewUri!!) ?: mPreviewUri!!.toString()
            openPathIntent(path, false, BuildConfig.APPLICATION_ID)
        }
    }

    private fun toggleFlash() {
        if (checkCameraAvailable()) {
            if (mIsInPhotoMode) {
                showFlashOptions(mIsInPhotoMode)
            } else {
                mPreview?.toggleFlashlight()
            }
        }
    }

    fun updateFlashlightState(state: Int) {
        config.flashlightState = state
        val flashDrawable = when (state) {
            FLASH_OFF -> R.drawable.ic_flash_off_vector
            FLASH_ON -> R.drawable.ic_flash_on_vector
            else -> R.drawable.ic_flash_auto_vector
        }
        toggle_flash.icon = AppCompatResources.getDrawable(this, flashDrawable)
        toggle_flash.transitionName = "${getString(R.string.toggle_flash)}$state"
    }

    private fun shutterPressed() {
        if (checkCameraAvailable()) {
            handleShutter()
        }
    }

    private fun handleShutter() {
        if (mIsInPhotoMode) {
            toggleBottomButtons(true)
            change_resolution.isEnabled = true
            mPreview?.tryTakePicture()
            shutter_animation.alpha = 1.0f
            shutter_animation.animate().alpha(0f).setDuration(CAPTURE_ANIMATION_DURATION).start()
        } else {
            mPreview?.toggleRecording()
        }
    }

    private fun launchSettings() {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun handleTogglePhotoVideo() {
        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                togglePhotoVideo()
            } else {
                toast(R.string.no_audio_permissions)
                selectPhotoTab()
                if (isVideoCaptureIntent()) {
                    finish()
                }
            }
        }
    }

    private fun togglePhotoVideo() {
        if (!checkCameraAvailable()) {
            return
        }

        if (isVideoCaptureIntent()) {
            mPreview?.initVideoMode()
        }

        mPreview?.setFlashlightState(FLASH_OFF)
        hideTimer()
        togglePhotoVideoMode()
        checkButtons()
        toggleBottomButtons(false)
    }

    private fun togglePhotoVideoMode() {
        mIsInPhotoMode = !mIsInPhotoMode
        config.initPhotoMode = mIsInPhotoMode
    }

    private fun checkButtons() {
        if (mIsInPhotoMode) {
            initPhotoMode()
        } else {
            tryInitVideoMode()
        }
    }

    private fun initPhotoMode() {
        shutter.setImageResource(R.drawable.ic_shutter_animated)
        mPreview?.initPhotoMode()
        setupPreviewImage(true)
        selectPhotoTab()
    }

    private fun tryInitVideoMode() {
        try {
            mPreview?.initVideoMode()
            initVideoButtons()
        } catch (e: Exception) {
            if (!isVideoCaptureIntent()) {
                toast(R.string.video_mode_error)
            }
        }
    }

    private fun initVideoButtons() {
        shutter.setImageResource(R.drawable.ic_video_rec_animated)
        setupPreviewImage(false)
        mPreview?.checkFlashlight()
        selectVideoTab()
    }

    private fun setupPreviewImage(isPhoto: Boolean) {
        val uri = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val lastMediaId = getLatestMediaId(uri)
        if (lastMediaId == 0L) {
            return
        }

        mPreviewUri = Uri.withAppendedPath(uri, lastMediaId.toString())


        loadLastTakenMedia(mPreviewUri)
    }

    private fun loadLastTakenMedia(uri: Uri?) {
        mPreviewUri = uri
        runOnUiThread {
            if (!isDestroyed) {
                val options = RequestOptions()
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)

                Glide.with(this)
                    .load(uri)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(last_photo_video_preview)
            }
        }
    }

    private fun showTimer() {
        video_rec_curr_timer.beVisible()
        setupTimer()
    }

    private fun hideTimer() {
        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()
        mCurrVideoRecTimer = 0
        mTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun setupTimer() {
        runOnUiThread(object : Runnable {
            override fun run() {
                video_rec_curr_timer.text = mCurrVideoRecTimer++.getFormattedDuration()
                mTimerHandler.postDelayed(this, 1000L)
            }
        })
    }

    private fun resumeCameraItems() {
        if (!mIsInPhotoMode) {
            initVideoButtons()
        }
    }

    private fun hasStorageAndCameraPermissions(): Boolean {
        return if (mIsInPhotoMode) hasPhotoModePermissions() else hasVideoModePermissions()
    }

    private fun hasPhotoModePermissions(): Boolean {
        return if (isTiramisuPlus()) {
            hasPermission(PERMISSION_READ_MEDIA_IMAGES) && hasPermission(PERMISSION_CAMERA)
        } else {
            hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA)
        }
    }

    private fun hasVideoModePermissions(): Boolean {
        return if (isTiramisuPlus()) {
            hasPermission(PERMISSION_READ_MEDIA_VIDEO) && hasPermission(PERMISSION_CAMERA) && hasPermission(PERMISSION_RECORD_AUDIO)
        } else {
            hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA) && hasPermission(PERMISSION_RECORD_AUDIO)
        }
    }

    private fun setupOrientationEventListener() {
        mOrientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (isDestroyed) {
                    mOrientationEventListener.disable()
                    return
                }

                val currOrient = when (orientation) {
                    in 75..134 -> ORIENT_LANDSCAPE_RIGHT
                    in 225..289 -> ORIENT_LANDSCAPE_LEFT
                    else -> ORIENT_PORTRAIT
                }

                if (currOrient != mLastHandledOrientation) {
                    val degrees = when (currOrient) {
                        ORIENT_LANDSCAPE_LEFT -> 90
                        ORIENT_LANDSCAPE_RIGHT -> -90
                        else -> 0
                    }

                    animateViews(degrees)
                    mLastHandledOrientation = currOrient
                }
            }
        }
    }

    private fun animateViews(degrees: Int) {
        val views = arrayOf<View>(toggle_camera, toggle_flash, change_resolution, shutter, settings, last_photo_video_preview)
        for (view in views) {
            rotate(view, degrees)
        }
    }

    private fun rotate(view: View, degrees: Int) = view.animate().rotation(degrees.toFloat()).start()

    private fun checkCameraAvailable(): Boolean {
        if (!mIsCameraAvailable) {
            toast(R.string.camera_unavailable)
        }
        return mIsCameraAvailable
    }

    override fun setCameraAvailable(available: Boolean) {
        mIsCameraAvailable = available
    }

    override fun setHasFrontAndBackCamera(hasFrontAndBack: Boolean) {
        toggle_camera?.beVisibleIf(hasFrontAndBack)
    }

    override fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.beVisible()
        } else {
            toggle_flash.beInvisible()
            toggle_flash.icon = AppCompatResources.getDrawable(this, R.drawable.ic_flash_off_vector)
            mPreview?.setFlashlightState(FLASH_OFF)
        }
    }

    override fun onChangeCamera(frontCamera: Boolean) {
        toggle_camera.setImageResource(if (frontCamera) R.drawable.ic_camera_rear_vector else R.drawable.ic_camera_front_vector)
    }

    override fun toggleBottomButtons(hide: Boolean) {
        runOnUiThread {
            shutter.isClickable = !hide
            toggle_camera.isClickable = !hide
            toggle_flash.isClickable = !hide
        }
    }

    override fun onMediaSaved(uri: Uri) {
        change_resolution.isEnabled = true
        loadLastTakenMedia(uri)
        if (isImageCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        } else if (isVideoCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onImageCaptured(bitmap: Bitmap) {
        if (isImageCaptureIntent()) {
            Intent().apply {
                putExtra("data", bitmap)
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onChangeFlashMode(flashMode: Int) {
        updateFlashlightState(flashMode)
    }

    override fun onVideoRecordingStarted() {
        camera_mode_tab.beInvisible()
        shutter.isSelected = true
        toggle_camera.beInvisible()
        change_resolution.isEnabled = false
        video_rec_curr_timer.beVisible()
    }

    override fun onVideoRecordingStopped() {
        camera_mode_tab.beVisible()
        shutter.isSelected = false
        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()
        change_resolution.isEnabled = true
        toggle_camera.beVisible()
    }

    override fun onVideoDurationChanged(durationNanos: Long) {
        val seconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos).toInt()
        video_rec_curr_timer.text = seconds.getFormattedDuration()
    }

    override fun onFocusCamera(xPos: Float, yPos: Float) {
        mFocusCircleView.drawFocusCircle(xPos, yPos)
    }

    override fun onSwipeLeft() {
        if (!is3rdPartyIntent() && camera_mode_tab.isVisible()) {
            selectPhotoTab(triggerListener = true)
        }
    }

    override fun onSwipeRight() {
        if (!is3rdPartyIntent() && camera_mode_tab.isVisible()) {
            selectVideoTab(triggerListener = true)
        }
    }

    override fun onTouchPreview() {
        closeOptions()
    }

    private fun closeOptions(): Boolean {
        if (media_size_toggle_group.isVisible() ||
            flash_toggle_group.isVisible()
        ) {
            val transitionSet = createTransition()
            TransitionManager.go(defaultScene, transitionSet)
            media_size_toggle_group.beGone()
            flash_toggle_group.beGone()
            return true
        }

        return false
    }

    override fun displaySelectedResolution(resolutionOption: ResolutionOption) {
        val imageRes = resolutionOption.imageDrawableResId
        change_resolution.icon = AppCompatResources.getDrawable(this, imageRes)
        change_resolution.transitionName = "${resolutionOption.buttonViewId}"
    }

    override fun showImageSizes(
        selectedResolution: ResolutionOption,
        resolutions: List<ResolutionOption>,
        isPhotoCapture: Boolean,
        isFrontCamera: Boolean,
        onSelect: (index: Int, changed: Boolean) -> Unit
    ) {

        media_size_toggle_group.removeAllViews()
        media_size_toggle_group.clearChecked()

        val onItemClick = { clickedViewId: Int ->
            closeOptions()
            val index = resolutions.indexOfFirst { it.buttonViewId == clickedViewId }
            onSelect.invoke(index, selectedResolution.buttonViewId != clickedViewId)
        }

        resolutions.map {
            createButton(it, onItemClick)
        }.forEach { button ->
            media_size_toggle_group.addView(button)
        }

        media_size_toggle_group.check(selectedResolution.buttonViewId)
        showResolutionOptions()
    }

    private fun createButton(resolutionOption: ResolutionOption, onClick: (clickedViewId: Int) -> Unit): MaterialButton {
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }
        return (layoutInflater.inflate(R.layout.layout_button, null) as MaterialButton).apply {
            layoutParams = params
            icon = AppCompatResources.getDrawable(context, resolutionOption.imageDrawableResId)
            id = resolutionOption.buttonViewId
            transitionName = "${resolutionOption.buttonViewId}"
            setOnClickListener {
                onClick.invoke(id)
            }
        }
    }

    private fun showResolutionOptions() {
        val transitionSet = createTransition()
        TransitionManager.go(mediaSizeScene, transitionSet)
        media_size_toggle_group.beVisible()
        media_size_toggle_group.children.map { it as MaterialButton }.forEach(::setButtonColors)
    }

    private fun createTransition(): Transition {
        val fadeTransition = Fade()
        return TransitionSet().apply {
            addTransition(fadeTransition)
            this.duration = resources.getInteger(R.integer.icon_anim_duration).toLong()
        }
    }

    override fun showFlashOptions(photoCapture: Boolean) {
        val transitionSet = createTransition()
        TransitionManager.go(flashModeScene, transitionSet)
        flash_auto.beVisibleIf(photoCapture)
        flash_toggle_group.check(config.flashlightState.toFlashModeId())

        flash_toggle_group.beVisible()
        flash_toggle_group.children.map { it as MaterialButton }.forEach(::setButtonColors)
    }

    private fun setButtonColors(button: MaterialButton) {
        val primaryColor = getProperPrimaryColor()
        val states = arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked))
        val iconColors = intArrayOf(ContextCompat.getColor(this, R.color.md_grey_white), primaryColor)
        button.iconTint = ColorStateList(states, iconColors)
    }

    fun setRecordingState(isRecording: Boolean) {
        runOnUiThread {
            if (isRecording) {
                shutter.isSelected = true
                toggle_camera.beInvisible()
                showTimer()
            } else {
                shutter.isSelected = false
                hideTimer()
            }
        }
    }

    fun videoSaved(uri: Uri) {
        setupPreviewImage(false)
        if (isVideoCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    fun drawFocusCircle(x: Float, y: Float) = mFocusCircleView.drawFocusCircle(x, y)

    override fun mediaSaved(path: String) {
        rescanPaths(arrayListOf(path)) {
            setupPreviewImage(true)
            Intent(BROADCAST_REFRESH_MEDIA).apply {
                putExtra(REFRESH_PATH, path)
                `package` = "com.simplemobiletools.gallery"
                sendBroadcast(this)
            }
        }

        if (isImageCaptureIntent()) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(33, R.string.release_33))
            add(Release(35, R.string.release_35))
            add(Release(39, R.string.release_39))
            add(Release(44, R.string.release_44))
            add(Release(46, R.string.release_46))
            add(Release(52, R.string.release_52))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
