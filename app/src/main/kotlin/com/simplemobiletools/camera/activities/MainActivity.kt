package com.simplemobiletools.camera.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.transition.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.fadeIn
import com.simplemobiletools.camera.extensions.fadeOut
import com.simplemobiletools.camera.extensions.setShadowIcon
import com.simplemobiletools.camera.extensions.toFlashModeId
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.implementations.CameraXInitializer
import com.simplemobiletools.camera.implementations.CameraXPreviewListener
import com.simplemobiletools.camera.interfaces.MyPreview
import com.simplemobiletools.camera.models.ResolutionOption
import com.simplemobiletools.camera.views.FocusCircleView
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.Release
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_flash.*
import kotlinx.android.synthetic.main.layout_top.*

class MainActivity : SimpleActivity(), PhotoProcessor.MediaSavedListener, CameraXPreviewListener {
    private companion object {
        const val CAPTURE_ANIMATION_DURATION = 500L
        const val PHOTO_MODE_INDEX = 1
        const val VIDEO_MODE_INDEX = 0
        private const val MIN_SWIPE_DISTANCE_X = 100
    }

    private lateinit var defaultScene: Scene
    private lateinit var flashModeScene: Scene
    private lateinit var mOrientationEventListener: OrientationEventListener
    private lateinit var mFocusCircleView: FocusCircleView
    private var mPreview: MyPreview? = null
    private var mediaSizeToggleGroup: MaterialButtonToggleGroup? = null
    private var mPreviewUri: Uri? = null
    private var mIsHardwareShutterHandled = false
    private var mLastHandledOrientation = 0

    private val tabSelectedListener = object : TabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) {
            handlePermission(PERMISSION_RECORD_AUDIO) {
                if (it) {
                    when (tab.position) {
                        VIDEO_MODE_INDEX -> mPreview?.initVideoMode()
                        PHOTO_MODE_INDEX -> mPreview?.initPhotoMode()
                        else -> throw IllegalStateException("Unsupported  tab position ${tab.position}")
                    }
                } else {
                    toast(R.string.no_audio_permissions)
                    selectPhotoTab()
                    if (isVideoCaptureIntent()) {
                        finish()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        appLaunched(BuildConfig.APPLICATION_ID)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
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
            val isInPhotoMode = isInPhotoMode()
            setupPreviewImage(isInPhotoMode)
            mFocusCircleView.setStrokeColor(getProperPrimaryColor())
            toggleBottomButtons(enabled = true)
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
        mIsHardwareShutterHandled = false
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
                        val isInPhotoMode = isInPhotoMode()
                        if (isInPhotoMode) {
                            initializeCamera(true)
                        } else {
                            handlePermission(PERMISSION_RECORD_AUDIO) { grantedRecordAudioPermission ->
                                if (grantedRecordAudioPermission) {
                                    initializeCamera(false)
                                } else {
                                    toast(R.string.no_audio_permissions)
                                    if (isThirdPartyIntent()) {
                                        finish()
                                    } else {
                                        // re-initialize in photo mode
                                        config.initPhotoMode = true
                                        tryInitCamera()
                                    }
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

    private fun isInPhotoMode(): Boolean {
        return mPreview?.isInPhotoMode()
            ?: if (isVideoCaptureIntent()) {
                false
            } else if (isImageCaptureIntent()) {
                true
            } else {
                config.initPhotoMode
            }
    }

    private fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
        if (isTiramisuPlus()) {
            handlePermission(PERMISSION_READ_MEDIA_IMAGES) { grantedReadImages ->
                if (grantedReadImages) {
                    handlePermission(PERMISSION_READ_MEDIA_VIDEO, callback)
                } else {
                    callback.invoke(false)
                }
            }
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE, callback)
        }
    }

    private fun isThirdPartyIntent() = isVideoCaptureIntent() || isImageCaptureIntent()

    private fun isImageCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_IMAGE_CAPTURE || intent?.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE

    private fun isVideoCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_VIDEO_CAPTURE

    private fun createToggleGroup(): MaterialButtonToggleGroup {
        return MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun initializeCamera(isInPhotoMode: Boolean) {
        setContentView(R.layout.activity_main)
        initButtons()
        initModeSwitcher()
        defaultScene = Scene(top_options, default_icons)
        flashModeScene = Scene(top_options, flash_toggle_group)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(view_holder) { _, windowInsets ->
            val safeInsetBottom = windowInsets.displayCutout?.safeInsetBottom ?: 0
            val safeInsetTop = windowInsets.displayCutout?.safeInsetTop ?: 0

            top_options.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = safeInsetTop
            }

            val marginBottom = safeInsetBottom + navigationBarHeight + resources.getDimensionPixelSize(R.dimen.bigger_margin)

            shutter.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = marginBottom
            }

            WindowInsetsCompat.CONSUMED
        }

        if (isInPhotoMode) {
            selectPhotoTab()
        } else {
            selectVideoTab()
        }

        val outputUri = intent.extras?.get(MediaStore.EXTRA_OUTPUT) as? Uri
        val isThirdPartyIntent = isThirdPartyIntent()
        mPreview = CameraXInitializer(this).createCameraXPreview(
            preview_view,
            listener = this,
            outputUri = outputUri,
            isThirdPartyIntent = isThirdPartyIntent,
            initInPhotoMode = isInPhotoMode,
        )

        mFocusCircleView = FocusCircleView(this)
        view_holder.addView(mFocusCircleView)

        setupPreviewImage(true)
        initFlashModeTransitionNames()

        if (isThirdPartyIntent) {
            hideIntentButtons()
        }
    }

    private fun initFlashModeTransitionNames() {
        val baseName = getString(R.string.toggle_flash)
        flash_auto.transitionName = "$baseName$FLASH_AUTO"
        flash_off.transitionName = "$baseName$FLASH_OFF"
        flash_on.transitionName = "$baseName$FLASH_ON"
        flash_always_on.transitionName = "$baseName$FLASH_ALWAYS_ON"
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener { mPreview!!.toggleFrontBackCamera() }
        last_photo_video_preview.setOnClickListener { showLastMediaPreview() }
        toggle_flash.setOnClickListener { mPreview!!.handleFlashlightClick() }
        shutter.setOnClickListener { shutterPressed() }

        settings.setShadowIcon(R.drawable.ic_settings_vector)
        settings.setOnClickListener { launchSettings() }

        change_resolution.setOnClickListener { mPreview?.showChangeResolution() }

        flash_on.setShadowIcon(R.drawable.ic_flash_on_vector)
        flash_on.setOnClickListener { selectFlashMode(FLASH_ON) }

        flash_off.setShadowIcon(R.drawable.ic_flash_off_vector)
        flash_off.setOnClickListener { selectFlashMode(FLASH_OFF) }

        flash_auto.setShadowIcon(R.drawable.ic_flash_auto_vector)
        flash_auto.setOnClickListener { selectFlashMode(FLASH_AUTO) }

        flash_always_on.setShadowIcon(R.drawable.ic_flashlight_vector)
        flash_always_on.setOnClickListener { selectFlashMode(FLASH_ALWAYS_ON) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initModeSwitcher() {
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                // we have to return true here so ACTION_UP
                // (and onFling) can be dispatched
                return true
            }

            override fun onFling(event1: MotionEvent, event2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // these can be null even if the docs say they cannot, getting event1.x in itself can cause crashes
                try {
                    if (event1 == null || event2 == null || event1.x == null || event2.x == null) {
                        return true
                    }
                } catch (e: NullPointerException) {
                    return true
                }

                val deltaX = event1.x - event2.x
                val deltaXAbs = abs(deltaX)

                if (deltaXAbs >= MIN_SWIPE_DISTANCE_X) {
                    if (deltaX > 0) {
                        onSwipeLeft()
                    } else {
                        onSwipeRight()
                    }
                }

                return true
            }
        })

        camera_mode_tab.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun onSwipeLeft() {
        if (!isThirdPartyIntent() && camera_mode_tab.isVisible()) {
            selectPhotoTab(triggerListener = true)
        }
    }

    private fun onSwipeRight() {
        if (!isThirdPartyIntent() && camera_mode_tab.isVisible()) {
            selectVideoTab(triggerListener = true)
        }
    }

    private fun selectFlashMode(flashMode: Int) {
        closeOptions()
        mPreview?.setFlashlightState(flashMode)
    }


    private fun showLastMediaPreview() {
        if (mPreviewUri != null) {
            val path = applicationContext.getRealPathFromURI(mPreviewUri!!) ?: mPreviewUri!!.toString()
            openPathIntent(path, false, BuildConfig.APPLICATION_ID)
        }
    }

    private fun shutterPressed() {
        if (isInPhotoMode()) {
            toggleBottomButtons(enabled = false)
            change_resolution.isEnabled = true
            mPreview?.tryTakePicture()
        } else {
            mPreview?.toggleRecording()
        }
    }

    private fun launchSettings() {
        val intent = Intent(applicationContext, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onInitPhotoMode() {
        shutter.setImageResource(R.drawable.ic_shutter_animated)
        setupPreviewImage(true)
        selectPhotoTab()
    }

    override fun onInitVideoMode() {
        shutter.setImageResource(R.drawable.ic_video_rec_animated)
        setupPreviewImage(false)
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


    private fun hasStorageAndCameraPermissions(): Boolean {
        return if (isInPhotoMode()) hasPhotoModePermissions() else hasVideoModePermissions()
    }

    private fun hasPhotoModePermissions(): Boolean {
        return if (isTiramisuPlus()) {
            hasPermission(PERMISSION_READ_MEDIA_IMAGES) && hasPermission(PERMISSION_READ_MEDIA_VIDEO) && hasPermission(PERMISSION_CAMERA)
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

    override fun setHasFrontAndBackCamera(hasFrontAndBack: Boolean) {
        toggle_camera?.beVisibleIf(hasFrontAndBack)
    }

    override fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.beVisible()
        } else {
            toggle_flash.beInvisible()
            toggle_flash.setShadowIcon(R.drawable.ic_flash_off_vector)
            mPreview?.setFlashlightState(FLASH_OFF)
        }
    }

    override fun onChangeCamera(frontCamera: Boolean) {
        toggle_camera.setImageResource(if (frontCamera) R.drawable.ic_camera_rear_vector else R.drawable.ic_camera_front_vector)
    }

    override fun toggleBottomButtons(enabled: Boolean) {
        runOnUiThread {
            shutter.isClickable = enabled
            preview_view.isEnabled = enabled
            toggle_camera.isClickable = enabled
            toggle_flash.isClickable = enabled
        }
    }

    override fun shutterAnimation() {
        shutter_animation.alpha = 1.0f
        shutter_animation.animate().alpha(0f).setDuration(CAPTURE_ANIMATION_DURATION).start()
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
        val flashDrawable = when (flashMode) {
            FLASH_OFF -> R.drawable.ic_flash_off_vector
            FLASH_ON -> R.drawable.ic_flash_on_vector
            FLASH_AUTO -> R.drawable.ic_flash_auto_vector
            else -> R.drawable.ic_flashlight_vector
        }
        toggle_flash.setShadowIcon(flashDrawable)
        toggle_flash.transitionName = "${getString(R.string.toggle_flash)}$flashMode"
    }

    override fun onVideoRecordingStarted() {
        camera_mode_tab.beInvisible()
        video_rec_curr_timer.beVisible()

        toggle_camera.fadeOut()
        last_photo_video_preview.fadeOut()

        change_resolution.isEnabled = false
        settings.isEnabled = false
        shutter.isSelected = true
    }

    override fun onVideoRecordingStopped() {
        camera_mode_tab.beVisible()

        toggle_camera.fadeIn()
        last_photo_video_preview.fadeIn()

        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()

        shutter.isSelected = false
        change_resolution.isEnabled = true
        settings.isEnabled = true
    }

    override fun onVideoDurationChanged(durationNanos: Long) {
        val seconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos).toInt()
        video_rec_curr_timer.text = seconds.getFormattedDuration()
    }

    override fun onFocusCamera(xPos: Float, yPos: Float) {
        mFocusCircleView.drawFocusCircle(xPos, yPos)
    }

    override fun onTouchPreview() {
        closeOptions()
    }

    private fun closeOptions(): Boolean {
        if (mediaSizeToggleGroup?.isVisible() == true ||
            flash_toggle_group.isVisible()
        ) {
            val transitionSet = createTransition()
            TransitionManager.go(defaultScene, transitionSet)
            mediaSizeToggleGroup?.beGone()
            flash_toggle_group.beGone()
            default_icons.beVisible()
            return true
        }

        return false
    }

    override fun displaySelectedResolution(resolutionOption: ResolutionOption) {
        val imageRes = resolutionOption.imageDrawableResId
        change_resolution.setShadowIcon(imageRes)
        change_resolution.transitionName = "${resolutionOption.buttonViewId}"
    }

    override fun showImageSizes(
        selectedResolution: ResolutionOption,
        resolutions: List<ResolutionOption>,
        isPhotoCapture: Boolean,
        isFrontCamera: Boolean,
        onSelect: (index: Int, changed: Boolean) -> Unit
    ) {

        top_options.removeView(mediaSizeToggleGroup)
        val mediaSizeToggleGroup = createToggleGroup().apply {
            mediaSizeToggleGroup = this
        }
        top_options.addView(mediaSizeToggleGroup)

        val onItemClick = { clickedViewId: Int ->
            closeOptions()
            val index = resolutions.indexOfFirst { it.buttonViewId == clickedViewId }
            onSelect.invoke(index, selectedResolution.buttonViewId != clickedViewId)
        }

        resolutions.forEach {
            val button = createButton(it, onItemClick)
            mediaSizeToggleGroup.addView(button)
        }

        mediaSizeToggleGroup.check(selectedResolution.buttonViewId)

        val transitionSet = createTransition()
        val mediaSizeScene = Scene(top_options, mediaSizeToggleGroup)
        TransitionManager.go(mediaSizeScene, transitionSet)
        default_icons.beGone()
        mediaSizeToggleGroup.beVisible()
        mediaSizeToggleGroup.children.map { it as MaterialButton }.forEach(::setButtonColors)
    }

    private fun createButton(resolutionOption: ResolutionOption, onClick: (clickedViewId: Int) -> Unit): MaterialButton {
        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
        }
        return (layoutInflater.inflate(R.layout.layout_button, null) as MaterialButton).apply {
            layoutParams = params
            setShadowIcon(resolutionOption.imageDrawableResId)
            id = resolutionOption.buttonViewId
            transitionName = "${resolutionOption.buttonViewId}"
            setOnClickListener {
                onClick.invoke(id)
            }
        }
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
        flash_always_on.beVisibleIf(photoCapture)
        flash_toggle_group.check(config.flashlightState.toFlashModeId())

        flash_toggle_group.beVisible()
        flash_toggle_group.children.forEach { setButtonColors(it as MaterialButton) }
    }

    private fun setButtonColors(button: MaterialButton) {
        val primaryColor = getProperPrimaryColor()
        val states = arrayOf(intArrayOf(-android.R.attr.state_checked), intArrayOf(android.R.attr.state_checked))
        val iconColors = intArrayOf(ContextCompat.getColor(this, R.color.md_grey_white), primaryColor)
        button.iconTint = ColorStateList(states, iconColors)
    }

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
