package com.simplemobiletools.camera.activities

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.hardware.*
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.view.*
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.simplemobiletools.camera.*
import com.simplemobiletools.camera.Preview.PreviewListener
import com.simplemobiletools.camera.views.FocusRectView
import com.simplemobiletools.filepicker.extensions.hasStoragePermission
import com.simplemobiletools.filepicker.extensions.toast
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : SimpleActivity(), SensorEventListener, PreviewListener, PhotoProcessor.MediaSavedListener, MediaScannerConnection.OnScanCompletedListener {
    companion object {

        private val CAMERA_STORAGE_PERMISSION = 1
        private val AUDIO_PERMISSION = 2
        private val FADE_DELAY = 5000

        lateinit var mSensorManager: SensorManager
        lateinit var mFocusRectView: FocusRectView
        lateinit var mTimerHandler: Handler
        lateinit var mFadeHandler: Handler
        lateinit var mRes: Resources

        private var mPreview: Preview? = null
        private var mPreviewUri: Uri? = null
        private var mIsFlashEnabled = false
        private var mIsInPhotoMode = false
        private var mIsAskingPermissions = false
        private var mIsCameraAvailable = false
        private var mIsImageCaptureIntent = false
        private var mIsVideoCaptureIntent = false
        private var mIsHardwareShutterHandled = false
        private var mCurrVideoRecTimer = 0
        private var mOrientation = 0
        private var mCurrCamera = 0
        private var mLastHandledOrientation = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        mRes = resources
        tryInitCamera()

        supportActionBar?.hide()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
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
        toggle_photo_video.visibility = View.GONE
        settings.visibility = View.GONE
    }

    private fun tryInitCamera() {
        if (hasCameraAndStoragePermission()) {
            initializeCamera()
            handleIntent()
        } else {
            val permissions = ArrayList<String>(2)
            if (!Utils.hasCameraPermission(applicationContext)) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if (!hasStoragePermission()) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), CAMERA_STORAGE_PERMISSION)
        }
    }

    private fun handleIntent() {
        if (intent != null && intent.action != null) {
            if (intent.extras != null && intent.action == MediaStore.ACTION_IMAGE_CAPTURE || intent.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE) {
                mIsImageCaptureIntent = true
                hideToggleModeAbout()
                val output = intent.extras.get(MediaStore.EXTRA_OUTPUT)
                if (output != null && output is Uri) {
                    mPreview?.setTargetUri(output)
                }
            } else if (intent.action == MediaStore.ACTION_VIDEO_CAPTURE) {
                mIsVideoCaptureIntent = true
                hideToggleModeAbout()
                shutter.setImageDrawable(mRes.getDrawable(R.mipmap.video_rec))
            }
        }
    }

    private fun initializeCamera() {
        setContentView(R.layout.activity_main)
        initButtons()

        if (Utils.hasNavBar(mRes) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val lp = btn_holder.layoutParams as RelativeLayout.LayoutParams
            lp.setMargins(0, 0, 0, lp.bottomMargin + Utils.getNavBarHeight(mRes))
        }

        mCurrCamera = config.lastUsedCamera
        mPreview = Preview(this, camera_view, this)
        mPreview!!.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view_holder.addView(mPreview)
        toggle_camera.setImageResource(if (mCurrCamera == Camera.CameraInfo.CAMERA_FACING_BACK) R.mipmap.camera_front else R.mipmap.camera_back)

        mFocusRectView = FocusRectView(applicationContext)
        view_holder.addView(mFocusRectView)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mIsInPhotoMode = true
        mTimerHandler = Handler()
        mFadeHandler = Handler()
        mIsFlashEnabled = config.lastFlashlightState
        setupPreviewImage(true)
    }

    private fun initButtons() {
        toggle_camera.setOnClickListener { toggleCamera() }
        last_photo_video_preview.setOnClickListener { showLastMediaPreview() }
        toggle_flash.setOnClickListener { toggleFlash() }
        shutter.setOnClickListener { shutterPressed() }
        settings.setOnClickListener { launchSettings() }
        toggle_photo_video.setOnClickListener { handleTogglePhotoVideo() }
    }

    private fun hasCameraAndStoragePermission() = Utils.hasCameraPermission(applicationContext) && hasStoragePermission()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mIsAskingPermissions = false

        if (requestCode == CAMERA_STORAGE_PERMISSION) {
            if (hasCameraAndStoragePermission()) {
                initializeCamera()
                handleIntent()
            } else {
                toast(R.string.no_permissions)
                finish()
            }
        } else if (requestCode == AUDIO_PERMISSION) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                togglePhotoVideo()
            } else {
                toast(R.string.no_audio_permissions)
                if (mIsVideoCaptureIntent)
                    finish()
            }
        }
    }

    fun toggleCamera() {
        if (!checkCameraAvailable()) {
            return
        }

        if (mCurrCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCurrCamera = Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            mCurrCamera = Camera.CameraInfo.CAMERA_FACING_BACK
        }

        config.lastUsedCamera = mCurrCamera
        var newIconId = R.mipmap.camera_front
        mPreview?.releaseCamera()
        if (mPreview?.setCamera(mCurrCamera) == true) {
            if (mCurrCamera == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                newIconId = R.mipmap.camera_back
            }
            toggle_camera.setImageResource(newIconId)
            disableFlash()
            hideTimer()
        } else {
            toast(R.string.camera_switch_error)
        }
    }

    fun showLastMediaPreview() {
        if (mPreviewUri == null)
            return

        try {
            val REVIEW_ACTION = "com.android.camera.action.REVIEW"
            val intent = Intent(REVIEW_ACTION, mPreviewUri)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, mPreviewUri)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                toast(R.string.no_gallery_app_available)
            }
        }
    }

    fun toggleFlash() {
        if (!checkCameraAvailable()) {
            return
        }

        mIsFlashEnabled = !mIsFlashEnabled
        checkFlash()
    }

    private fun checkFlash() {
        if (mIsFlashEnabled) {
            enableFlash()
        } else {
            disableFlash()
        }
    }

    private fun disableFlash() {
        mPreview?.disableFlash()
        toggle_flash.setImageResource(R.mipmap.flash_off)
        mIsFlashEnabled = false
        config.lastFlashlightState = mIsFlashEnabled
    }

    private fun enableFlash() {
        mPreview?.enableFlash()
        toggle_flash.setImageResource(R.mipmap.flash_on)
        mIsFlashEnabled = true
        config.lastFlashlightState = mIsFlashEnabled
    }

    private fun shutterPressed() {
        if (checkCameraAvailable()) {
            handleShutter()
        }
    }

    private fun handleShutter() {
        if (mIsInPhotoMode) {
            toggleBottomButtons(true)
            mPreview?.takePicture()
            Handler().postDelayed({ toggleBottomButtons(false) }, Preview.PHOTO_PREVIEW_LENGTH.toLong())
        } else {
            if (mPreview?.toggleRecording() == true) {
                shutter.setImageDrawable(mRes.getDrawable(R.mipmap.video_stop))
                toggle_camera.visibility = View.INVISIBLE
                showTimer()
            } else {
                shutter.setImageDrawable(mRes.getDrawable(R.mipmap.video_rec))
                toggle_camera.visibility = View.VISIBLE
                hideTimer()
            }
        }
    }

    private fun toggleBottomButtons(hide: Boolean) {
        val alpha = if (hide) 0f else 1f
        shutter.animate().alpha(alpha).start()
        toggle_camera.animate().alpha(alpha).start()
        toggle_flash.animate().alpha(alpha).start()
    }

    fun launchSettings() {
        if (settings.alpha == 1f) {
            val intent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(intent)
        } else {
            fadeInButtons()
        }
    }

    fun handleTogglePhotoVideo() {
        togglePhotoVideo()
        checkButtons()
    }

    private fun togglePhotoVideo() {
        if (!checkCameraAvailable()) {
            return
        }

        if (!Utils.hasAudioPermission(applicationContext)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
            mIsAskingPermissions = true
            return
        }

        if (mIsVideoCaptureIntent)
            mPreview?.trySwitchToVideo()

        disableFlash()
        hideTimer()
        mIsInPhotoMode = !mIsInPhotoMode
        toggle_camera.visibility = View.VISIBLE
    }

    private fun checkButtons() {
        if (mIsInPhotoMode) {
            initPhotoButtons()
        } else {
            tryInitVideoButtons()
        }
    }

    private fun initPhotoButtons() {
        toggle_photo_video.setImageDrawable(mRes.getDrawable(R.mipmap.videocam))
        shutter.setImageDrawable(mRes.getDrawable(R.mipmap.camera))
        mPreview?.initPhotoMode()
        setupPreviewImage(true)
    }

    private fun tryInitVideoButtons() {
        if (mPreview?.initRecorder() == true) {
            initVideoButtons()
        } else {
            if (!mIsVideoCaptureIntent) {
                toast(R.string.video_mode_error)
            }
        }
    }

    private fun initVideoButtons() {
        toggle_photo_video.setImageDrawable(mRes.getDrawable(R.mipmap.photo))
        toggle_camera.visibility = View.VISIBLE
        shutter.setImageDrawable(mRes.getDrawable(R.mipmap.video_rec))
        checkFlash()
        setupPreviewImage(false)
    }

    private fun setupPreviewImage(isPhoto: Boolean) {
        val uri = if (isPhoto) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val lastMediaId = getLastMediaId(uri)
        if (lastMediaId == 0L) {
            return
        }
        mPreviewUri = Uri.withAppendedPath(uri, lastMediaId.toString())

        runOnUiThread {
            Glide.with(this).load(mPreviewUri).centerCrop().diskCacheStrategy(DiskCacheStrategy.NONE).crossFade().into(last_photo_video_preview)
        }
    }

    private fun getLastMediaId(uri: Uri): Long {
        val projection = arrayOf(MediaStore.Images.ImageColumns._ID)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC")
            if (cursor != null && cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID)
                return cursor.getLong(idIndex)
            }
        } finally {
            cursor?.close()
        }
        return 0
    }

    private fun scheduleFadeOut() = mFadeHandler.postDelayed({ fadeOutButtons() }, FADE_DELAY.toLong())

    private fun fadeOutButtons() {
        fadeAnim(settings, .5f)
        fadeAnim(toggle_photo_video, .0f)
        fadeAnim(last_photo_video_preview, .0f)
    }

    private fun fadeInButtons() {
        fadeAnim(settings, 1f)
        fadeAnim(toggle_photo_video, 1f)
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
        video_rec_curr_timer.text = Utils.formatSeconds(0)
        video_rec_curr_timer.visibility = View.GONE
        mCurrVideoRecTimer = 0
        mTimerHandler.removeCallbacksAndMessages(null)
    }

    private fun showTimer() {
        video_rec_curr_timer.visibility = View.VISIBLE
        setupTimer()
    }

    private fun setupTimer() {
        runOnUiThread(object : Runnable {
            override fun run() {
                video_rec_curr_timer.text = Utils.formatSeconds(mCurrVideoRecTimer++)
                mTimerHandler.postDelayed(this, 1000)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraAndStoragePermission()) {
            resumeCameraItems()
            setupPreviewImage(mIsInPhotoMode)
            scheduleFadeOut()

            if (mIsVideoCaptureIntent && mIsInPhotoMode) {
                togglePhotoVideo()
                checkButtons()
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun resumeCameraItems() {
        val cnt = Camera.getNumberOfCameras()
        if (cnt == 1) {
            toggle_camera.visibility = View.INVISIBLE
        }

        if (mPreview?.setCamera(mCurrCamera) == true) {
            hideNavigationBarIcons()
            checkFlash()

            val accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

            if (!mIsInPhotoMode) {
                initVideoButtons()
            }
        } else {
            toast(R.string.camera_switch_error)
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!hasCameraAndStoragePermission() || mIsAskingPermissions)
            return

        mFadeHandler.removeCallbacksAndMessages(null)

        hideTimer()
        mPreview?.releaseCamera()
        mSensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values[0] < 6.5 && event.values[0] > -6.5) {
            mOrientation = Constants.ORIENT_PORTRAIT
        } else {
            if (event.values[0] > 0) {
                mOrientation = Constants.ORIENT_LANDSCAPE_LEFT
            } else {
                mOrientation = Constants.ORIENT_LANDSCAPE_RIGHT
            }
        }

        if (mOrientation != mLastHandledOrientation) {
            val degrees = when (mOrientation) {
                Constants.ORIENT_LANDSCAPE_LEFT -> 90
                Constants.ORIENT_LANDSCAPE_RIGHT -> -90
                else -> 0
            }

            animateViews(degrees)
            mLastHandledOrientation = mOrientation
        }
    }

    private fun animateViews(degrees: Int) {
        val views = arrayOf<View>(toggle_camera, toggle_flash, toggle_photo_video, shutter, settings, last_photo_video_preview)
        for (view in views) {
            rotate(view, degrees)
        }
    }

    private fun rotate(view: View, degrees: Int) = view.animate().rotation(degrees.toFloat()).start()

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
    }

    private fun checkCameraAvailable(): Boolean {
        if (!mIsCameraAvailable) {
            toast(R.string.camera_unavailable)
        }
        return mIsCameraAvailable
    }

    override fun setFlashAvailable(available: Boolean) {
        if (available) {
            toggle_flash.visibility = View.VISIBLE
        } else {
            toggle_flash.visibility = View.INVISIBLE
            disableFlash()
        }
    }

    override fun setIsCameraAvailable(available: Boolean) {
        mIsCameraAvailable = available
    }

    override fun getCurrentOrientation() = mOrientation

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
        val paths = arrayOf(path)
        MediaScannerConnection.scanFile(applicationContext, paths, null, this)

        if (mIsImageCaptureIntent) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        config.isFirstRun = false
        mPreview?.releaseCamera()
    }

    override fun onScanCompleted(path: String, uri: Uri) = setupPreviewImage(mIsInPhotoMode)
}
