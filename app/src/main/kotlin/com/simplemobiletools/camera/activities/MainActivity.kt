package com.simplemobiletools.camera.activities

import android.app.Activity
import android.content.Intent
import android.hardware.Camera
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.view.*
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.navBarHeight
import com.simplemobiletools.camera.helpers.*
import com.simplemobiletools.camera.views.FocusRectView
import com.simplemobiletools.camera.views.Preview
import com.simplemobiletools.camera.views.Preview.PreviewListener
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.BROADCAST_REFRESH_MEDIA
import com.simplemobiletools.commons.helpers.PERMISSION_CAMERA
import com.simplemobiletools.commons.helpers.PERMISSION_RECORD_AUDIO
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.commons.models.Release
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity(), PreviewListener, PhotoProcessor.MediaSavedListener {
    private val FADE_DELAY = 5000

    lateinit var mFocusRectView: FocusRectView
    lateinit var mTimerHandler: Handler
    lateinit var mFadeHandler: Handler

    private var mPreview: Preview? = null
    private var mPreviewUri: Uri? = null
    private var mFlashlightState = FLASH_OFF
    private var mIsInPhotoMode = false
    private var mIsCameraAvailable = false
    private var mIsVideoCaptureIntent = false
    private var mIsHardwareShutterHandled = false
    private var mCurrVideoRecTimer = 0
    private var mCurrCameraId = 0
    var mLastHandledOrientation = 0

    lateinit var mOrientationEventListener: OrientationEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        appLaunched()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (config.alwaysOpenBackCamera)
            config.lastUsedCamera = Camera.CameraInfo.CAMERA_FACING_BACK

        initVariables()
        tryInitCamera()
        supportActionBar?.hide()
        checkWhatsNewDialog()
        setupOrientationEventListener()
    }

    override fun onResume() {
        super.onResume()
        if (hasStorageAndCameraPermissions()) {
            resumeCameraItems()
            setupPreviewImage(mIsInPhotoMode)
            scheduleFadeOut()
            mFocusRectView.setStrokeColor(config.primaryColor)

            if (mIsVideoCaptureIntent && mIsInPhotoMode) {
                handleTogglePhotoVideo()
                checkButtons()
            }
            toggleBottomButtons(false)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (hasStorageAndCameraPermissions()) {
            mOrientationEventListener.enable()
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasStorageAndCameraPermissions() || isAskingPermissions) {
            return
        }

        mFadeHandler.removeCallbacksAndMessages(null)

        hideTimer()
        mPreview?.releaseCamera()
        mOrientationEventListener.disable()

        if (mPreview?.isWaitingForTakePictureCallback == true) {
            toast(R.string.photo_not_saved)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mPreview?.releaseCamera()
        mPreview?.mActivity = null
        mPreview = null
    }

    private fun initVariables() {
        mIsInPhotoMode = false
        mIsCameraAvailable = false
        mIsVideoCaptureIntent = false
        mIsHardwareShutterHandled = false
        mCurrVideoRecTimer = 0
        mCurrCameraId = 0
        mLastHandledOrientation = 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else if (config.volumeButtonsAsShutter && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            shutterPressed()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            mIsHardwareShutterHandled = false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun hideToggleModeAbout() {
        toggle_photo_video.beGone()
        settings.beGone()
    }

    private fun tryInitCamera() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                handlePermission(PERMISSION_WRITE_STORAGE) {
                    if (it) {
                        initializeCamera()
                        handleIntent()
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

    private fun handleIntent() {
        if (isImageCaptureIntent()) {
            hideToggleModeAbout()
            val output = intent.extras?.get(MediaStore.EXTRA_OUTPUT)
            if (output != null && output is Uri) {
                mPreview?.mTargetUri = output
            }
        } else if (intent?.action == MediaStore.ACTION_VIDEO_CAPTURE) {
            mIsVideoCaptureIntent = true
            hideToggleModeAbout()
            shutter.setImageDrawable(resources.getDrawable(R.drawable.ic_video_rec))
        }
        mPreview?.isImageCaptureIntent = isImageCaptureIntent()
    }

    private fun isImageCaptureIntent() = intent?.action == MediaStore.ACTION_IMAGE_CAPTURE || intent?.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE

    private fun initializeCamera() {
        setContentView(R.layout.activity_main)
        initButtons()

        (btn_holder.layoutParams as RelativeLayout.LayoutParams).setMargins(0, 0, 0, (navBarHeight + resources.getDimension(R.dimen.activity_margin)).toInt())

        mCurrCameraId = config.lastUsedCamera
        mPreview = Preview(this, camera_view, this)
        mPreview!!.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view_holder.addView(mPreview)
        toggle_camera.setImageResource(if (mCurrCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) R.drawable.ic_camera_front else R.drawable.ic_camera_rear)

        mFocusRectView = FocusRectView(applicationContext)
        view_holder.addView(mFocusRectView)

        mIsInPhotoMode = true
        mTimerHandler = Handler()
        mFadeHandler = Handler()
        mFlashlightState = if (config.turnFlashOffAtStartup) FLASH_OFF else config.flashlightState
        setupPreviewImage(true)
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener { toggleCamera() }
        last_photo_video_preview.setOnClickListener { showLastMediaPreview() }
        toggle_flash.setOnClickListener { toggleFlash() }
        shutter.setOnClickListener { shutterPressed() }
        settings.setOnClickListener { launchSettings() }
        toggle_photo_video.setOnClickListener { handleTogglePhotoVideo() }
        change_resolution.setOnClickListener { mPreview?.showChangeResolutionDialog() }
    }

    private fun toggleCamera() {
        if (!checkCameraAvailable()) {
            return
        }

        mCurrCameraId = if (mCurrCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }

        config.lastUsedCamera = mCurrCameraId
        var newIconId = R.drawable.ic_camera_front
        mPreview?.releaseCamera()
        if (mPreview?.setCamera(mCurrCameraId) == true) {
            if (mCurrCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                newIconId = R.drawable.ic_camera_rear
            }
            toggle_camera.setImageResource(newIconId)
            disableFlash()
            hideTimer()
        } else {
            toast(R.string.camera_switch_error)
        }
    }

    private fun showLastMediaPreview() {
        if (mPreviewUri != null) {
            val path = applicationContext.getRealPathFromURI(mPreviewUri!!) ?: mPreviewUri!!.toString()
            openPathIntent(path, false, BuildConfig.APPLICATION_ID)
        }
    }

    private fun toggleFlash() {
        if (!checkCameraAvailable()) {
            return
        }

        mFlashlightState = ++mFlashlightState % if (mIsInPhotoMode) 3 else 2
        checkFlash()
    }

    private fun checkFlash() {
        when (mFlashlightState) {
            FLASH_ON -> enableFlash()
            FLASH_AUTO -> autoFlash()
            else -> disableFlash()
        }
    }

    private fun disableFlash() {
        mPreview?.disableFlash()
        toggle_flash.setImageResource(R.drawable.ic_flash_off)
        mFlashlightState = FLASH_OFF
        config.flashlightState = FLASH_OFF
    }

    private fun enableFlash() {
        mPreview?.enableFlash()
        toggle_flash.setImageResource(R.drawable.ic_flash_on)
        mFlashlightState = FLASH_ON
        config.flashlightState = FLASH_ON
    }

    private fun autoFlash() {
        mPreview?.autoFlash()
        toggle_flash.setImageResource(R.drawable.ic_flash_auto)
        mFlashlightState = FLASH_AUTO
        config.flashlightState = FLASH_AUTO
    }

    private fun shutterPressed() {
        if (checkCameraAvailable()) {
            handleShutter()
        }
    }

    private fun handleShutter() {
        if (mIsInPhotoMode) {
            toggleBottomButtons(true)
            mPreview?.tryTakePicture()
        } else {
            if (mPreview?.toggleRecording() == true) {
                shutter.setImageDrawable(resources.getDrawable(R.drawable.ic_video_stop))
                toggle_camera.beInvisible()
                showTimer()
            } else {
                shutter.setImageDrawable(resources.getDrawable(R.drawable.ic_video_rec))
                showToggleCameraIfNeeded()
                hideTimer()
            }
        }
    }

    fun toggleBottomButtons(hide: Boolean) {
        val alpha = if (hide) 0f else 1f
        shutter.animate().alpha(alpha).start()
        toggle_camera.animate().alpha(alpha).start()
        toggle_flash.animate().alpha(alpha).start()

        shutter.isClickable = !hide
        toggle_camera.isClickable = !hide
        toggle_flash.isClickable = !hide
    }

    private fun launchSettings() {
        if (settings.alpha == 1f) {
            val intent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(intent)
        } else {
            fadeInButtons()
        }
    }

    private fun handleTogglePhotoVideo() {
        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                togglePhotoVideo()
            } else {
                toast(R.string.no_audio_permissions)
                if (mIsVideoCaptureIntent) {
                    finish()
                }
            }
        }
    }

    private fun togglePhotoVideo() {
        if (!checkCameraAvailable()) {
            return
        }

        if (mIsVideoCaptureIntent)
            mPreview?.trySwitchToVideo()

        disableFlash()
        hideTimer()
        mIsInPhotoMode = !mIsInPhotoMode
        showToggleCameraIfNeeded()
        checkButtons()
        toggleBottomButtons(false)
    }

    private fun checkButtons() {
        if (mIsInPhotoMode) {
            initPhotoMode()
        } else {
            tryInitVideoMode()
        }
    }

    private fun initPhotoMode() {
        toggle_photo_video.setImageDrawable(resources.getDrawable(R.drawable.ic_video))
        shutter.setImageDrawable(resources.getDrawable(R.drawable.ic_shutter))
        mPreview?.initPhotoMode()
        setupPreviewImage(true)
    }

    private fun tryInitVideoMode() {
        if (mPreview?.initRecorder() == true) {
            initVideoButtons()
        } else {
            if (!mIsVideoCaptureIntent) {
                toast(R.string.video_mode_error)
            }
        }
    }

    private fun initVideoButtons() {
        toggle_photo_video.setImageDrawable(resources.getDrawable(R.drawable.ic_camera))
        showToggleCameraIfNeeded()
        shutter.setImageDrawable(resources.getDrawable(R.drawable.ic_video_rec))
        checkFlash()
        setupPreviewImage(false)
    }

    private fun setupPreviewImage(isPhoto: Boolean) {
        val uri = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val lastMediaId = getLatestMediaId(uri)
        if (lastMediaId == 0L) {
            return
        }

        mPreviewUri = Uri.withAppendedPath(uri, lastMediaId.toString())

        runOnUiThread {
            if (!isActivityDestroyed()) {
                val options = RequestOptions()
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)

                Glide.with(this)
                        .load(mPreviewUri)
                        .apply(options)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(last_photo_video_preview)
            }
        }
    }

    private fun scheduleFadeOut() {
        if (!config.keepSettingsVisible)
            mFadeHandler.postDelayed({ fadeOutButtons() }, FADE_DELAY.toLong())
    }

    private fun fadeOutButtons() {
        fadeAnim(settings, .5f)
        fadeAnim(toggle_photo_video, .0f)
        fadeAnim(change_resolution, .0f)
        fadeAnim(last_photo_video_preview, .0f)
    }

    private fun fadeInButtons() {
        fadeAnim(settings, 1f)
        fadeAnim(toggle_photo_video, 1f)
        fadeAnim(change_resolution, 1f)
        fadeAnim(last_photo_video_preview, 1f)
        scheduleFadeOut()
    }

    private fun fadeAnim(view: View, value: Float) {
        view.animate().alpha(value).start()
        view.isClickable = value != .0f
    }

    private fun hideNavigationBarIcons() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
    }

    private fun hideTimer() {
        video_rec_curr_timer.text = 0.getFormattedDuration()
        video_rec_curr_timer.beGone()
        mCurrVideoRecTimer = 0
        mTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun showTimer() {
        video_rec_curr_timer.beVisible()
        setupTimer()
    }

    private fun setupTimer() {
        runOnUiThread(object : Runnable {
            override fun run() {
                video_rec_curr_timer.text = mCurrVideoRecTimer++.getFormattedDuration()
                mTimerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun resumeCameraItems() {
        showToggleCameraIfNeeded()
        if (mPreview?.setCamera(mCurrCameraId) == true) {
            hideNavigationBarIcons()
            checkFlash()

            if (!mIsInPhotoMode) {
                initVideoButtons()
            }
        } else {
            toast(R.string.camera_switch_error)
        }
    }

    private fun showToggleCameraIfNeeded() {
        toggle_camera?.beInvisibleIf(Camera.getNumberOfCameras() <= 1)
    }

    private fun hasStorageAndCameraPermissions() = hasPermission(PERMISSION_WRITE_STORAGE) && hasPermission(PERMISSION_CAMERA)

    private fun setupOrientationEventListener() {
        mOrientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (isActivityDestroyed()) {
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
                    mPreview?.deviceOrientationChanged()
                }
            }
        }
    }

    private fun animateViews(degrees: Int) {
        val views = arrayOf<View>(toggle_camera, toggle_flash, toggle_photo_video, change_resolution, shutter, settings, last_photo_video_preview)
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

    fun finishActivity() {
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.beVisible()
        } else {
            toggle_flash.beInvisible()
            disableFlash()
        }
    }

    override fun setIsCameraAvailable(available: Boolean) {
        mIsCameraAvailable = available
    }

    override fun videoSaved(uri: Uri) {
        setupPreviewImage(mIsInPhotoMode)
        if (mIsVideoCaptureIntent) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(Activity.RESULT_OK, this)
            }
            finish()
        }
    }

    override fun drawFocusRect(x: Int, y: Int) = mFocusRectView.drawFocusRect(x, y)

    override fun mediaSaved(path: String) {
        scanPath(path) {
            setupPreviewImage(mIsInPhotoMode)
            Intent(BROADCAST_REFRESH_MEDIA).apply {
                `package` = "com.simplemobiletools.gallery"
                sendBroadcast(this)
            }
        }

        if (isImageCaptureIntent()) {
            finishActivity()
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
