package com.simplemobiletools.camera.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.simplemobiletools.camera.Config;
import com.simplemobiletools.camera.Constants;
import com.simplemobiletools.camera.PhotoProcessor;
import com.simplemobiletools.camera.Preview;
import com.simplemobiletools.camera.Preview.PreviewListener;
import com.simplemobiletools.camera.R;
import com.simplemobiletools.camera.Utils;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SensorEventListener, PreviewListener, PhotoProcessor.MediaSavedListener {
    @BindView(R.id.viewHolder) RelativeLayout mViewHolder;
    @BindView(R.id.toggle_camera) ImageView mToggleCameraBtn;
    @BindView(R.id.toggle_flash) ImageView mToggleFlashBtn;
    @BindView(R.id.toggle_photo_video) ImageView mTogglePhotoVideoBtn;
    @BindView(R.id.shutter) ImageView mShutterBtn;
    @BindView(R.id.video_rec_curr_timer) TextView mRecCurrTimer;
    @BindView(R.id.about) View mAboutBtn;

    private static final int CAMERA_STORAGE_PERMISSION = 1;
    private static final int AUDIO_PERMISSION = 2;

    private static SensorManager mSensorManager;
    private static Preview mPreview;
    private static Handler mTimerHandler;

    private static boolean mIsFlashEnabled;
    private static boolean mIsInPhotoMode;
    private static boolean mIsAskingPermissions;
    private static boolean mIsCameraAvailable;
    private static boolean mIsImageCaptureIntent;
    private static boolean mIsVideoCaptureIntent;
    private static boolean mIsHardwareShutterHandled;
    private static int mCurrVideoRecTimer;
    private static int mOrientation;
    private static int mCurrCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        tryInitCamera();

        final ActionBar actionbar = getSupportActionBar();
        if (actionbar != null)
            actionbar.hide();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true;
            shutterPressed();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            mIsHardwareShutterHandled = false;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void hideToggleModeAbout() {
        if (mTogglePhotoVideoBtn != null)
            mTogglePhotoVideoBtn.setVisibility(View.GONE);

        if (mAboutBtn != null)
            mAboutBtn.setVisibility(View.GONE);
    }

    private void tryInitCamera() {
        if (hasCameraAndStoragePermission()) {
            initializeCamera();
            handleIntent();
        } else {
            final List<String> permissions = new ArrayList<>(2);
            if (!Utils.hasCameraPermission(getApplicationContext())) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (!Utils.hasStoragePermission(getApplicationContext())) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), CAMERA_STORAGE_PERMISSION);
        }
    }

    private void handleIntent() {
        final Intent intent = getIntent();
        if (intent != null && intent.getAction() != null) {
            if (intent.getExtras() != null && intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE)) {
                mIsImageCaptureIntent = true;
                hideToggleModeAbout();
                final Object output = intent.getExtras().get(MediaStore.EXTRA_OUTPUT);
                if (output != null && output instanceof Uri) {
                    mPreview.setTargetUri((Uri) output);
                }
            } else if (intent.getAction().equals(MediaStore.ACTION_VIDEO_CAPTURE)) {
                mIsVideoCaptureIntent = true;
                hideToggleModeAbout();
                mPreview.setIsVideoCaptureIntent();
                mShutterBtn.setImageDrawable(getResources().getDrawable(R.mipmap.video_rec));
            }
        }
    }

    private void initializeCamera() {
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mCurrCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        mPreview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView), this);
        mPreview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mViewHolder.addView(mPreview);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mIsInPhotoMode = true;
        mTimerHandler = new Handler();
    }

    private boolean hasCameraAndStoragePermission() {
        return Utils.hasCameraPermission(getApplicationContext()) && Utils.hasStoragePermission(getApplicationContext());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mIsAskingPermissions = false;

        if (requestCode == CAMERA_STORAGE_PERMISSION) {
            if (hasCameraAndStoragePermission()) {
                initializeCamera();
                handleIntent();
            } else {
                Utils.showToast(getApplicationContext(), R.string.no_permissions);
                finish();
            }
        } else if (requestCode == AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                togglePhotoVideo();
            } else {
                Utils.showToast(getApplicationContext(), R.string.no_audio_permissions);
                if (mIsVideoCaptureIntent)
                    finish();
            }
        }
    }

    @OnClick(R.id.toggle_camera)
    public void toggleCamera() {
        if (!checkCameraAvailable()) {
            return;
        }

        if (mCurrCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCurrCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            mCurrCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        int newIconId = R.mipmap.camera_front;
        mPreview.releaseCamera();
        if (mPreview.setCamera(mCurrCamera)) {
            if (mCurrCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
                newIconId = R.mipmap.camera_rear;
            }
            mToggleCameraBtn.setImageResource(newIconId);
            disableFlash();
            hideTimer();
        } else {
            Utils.showToast(getApplicationContext(), R.string.camera_switch_error);
        }
    }

    @OnClick(R.id.toggle_flash)
    public void toggleFlash() {
        if (!checkCameraAvailable()) {
            return;
        }

        mIsFlashEnabled = !mIsFlashEnabled;
        checkFlash();
    }

    private void checkFlash() {
        if (mIsFlashEnabled) {
            enableFlash();
        } else {
            disableFlash();
        }
    }

    private void disableFlash() {
        mPreview.disableFlash();
        mToggleFlashBtn.setImageResource(R.mipmap.flash_off);
    }

    private void enableFlash() {
        mPreview.enableFlash();
        mToggleFlashBtn.setImageResource(R.mipmap.flash_on);
    }

    @OnClick(R.id.shutter)
    public void handleShutterPressed() {
        shutterPressed();
    }

    private void shutterPressed() {
        if (!checkCameraAvailable()) {
            return;
        }

        handleShutter();
    }

    private void handleShutter() {
        if (mIsInPhotoMode) {
            mPreview.tryTakePicture();
        } else {
            final Resources res = getResources();
            final boolean isRecording = mPreview.toggleRecording();
            if (isRecording) {
                mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_stop));
                mToggleCameraBtn.setVisibility(View.INVISIBLE);
                showTimer();
            } else {
                mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
                mToggleCameraBtn.setVisibility(View.VISIBLE);
                hideTimer();
            }
        }
    }

    @OnClick(R.id.about)
    public void launchAbout() {
        final Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.toggle_photo_video)
    public void handleTogglePhotoVideo() {
        togglePhotoVideo();
        checkButtons();
    }

    private void togglePhotoVideo() {
        if (!checkCameraAvailable()) {
            return;
        }

        if (!Utils.hasAudioPermission(getApplicationContext())) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
            mIsAskingPermissions = true;
            return;
        }

        if (mIsVideoCaptureIntent)
            mPreview.trySwitchToVideo();

        disableFlash();
        hideTimer();
        mIsInPhotoMode = !mIsInPhotoMode;
        mToggleCameraBtn.setVisibility(View.VISIBLE);
    }

    private void checkButtons() {
        if (mIsInPhotoMode) {
            initPhotoButtons();
        } else {
            initVideoButtons();
        }
    }

    private void initPhotoButtons() {
        final Resources res = getResources();
        mTogglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.videocam));
        mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.camera));
        mPreview.initPhotoMode();
    }

    private void initVideoButtons() {
        if (mPreview.initRecorder()) {
            setupVideoIcons();
        } else {
            if (!mIsVideoCaptureIntent) {
                Utils.showToast(getApplicationContext(), R.string.video_mode_error);
            }
        }
    }

    private void setupVideoIcons() {
        final Resources res = getResources();
        mTogglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.photo));
        mToggleCameraBtn.setVisibility(View.VISIBLE);
        mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
        checkFlash();
    }

    private void hideNavigationBarIcons() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    private void hideTimer() {
        mRecCurrTimer.setText(Utils.formatSeconds(0));
        mRecCurrTimer.setVisibility(View.GONE);
        mCurrVideoRecTimer = 0;
        mTimerHandler.removeCallbacksAndMessages(null);
    }

    private void showTimer() {
        mRecCurrTimer.setVisibility(View.VISIBLE);
        setupTimer();
    }

    private void setupTimer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecCurrTimer.setText(Utils.formatSeconds(mCurrVideoRecTimer++));
                mTimerHandler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraAndStoragePermission()) {
            resumeCameraItems();

            if (mIsVideoCaptureIntent && mIsInPhotoMode) {
                togglePhotoVideo();
                checkButtons();
            }
        }
    }

    private void resumeCameraItems() {
        final int cnt = Camera.getNumberOfCameras();
        if (cnt == 1) {
            mToggleCameraBtn.setVisibility(View.INVISIBLE);
        }

        if (mPreview.setCamera(mCurrCamera)) {
            hideNavigationBarIcons();

            if (mSensorManager != null) {
                final Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }

            if (!mIsInPhotoMode) {
                setupVideoIcons();
            }
        } else {
            Utils.showToast(getApplicationContext(), R.string.camera_switch_error);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!hasCameraAndStoragePermission() || mIsAskingPermissions)
            return;

        hideTimer();
        if (mPreview != null) {
            mPreview.releaseCamera();
        }

        if (mSensorManager != null)
            mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] < 6.5 && event.values[0] > -6.5) {
            mOrientation = Constants.ORIENT_PORTRAIT;
        } else {
            if (event.values[0] > 0) {
                mOrientation = Constants.ORIENT_LANDSCAPE_LEFT;
            } else {
                mOrientation = Constants.ORIENT_LANDSCAPE_RIGHT;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private boolean checkCameraAvailable() {
        if (!mIsCameraAvailable) {
            Utils.showToast(getApplicationContext(), R.string.camera_unavailable);
        }
        return mIsCameraAvailable;
    }

    @Override
    public void setFlashAvailable(boolean available) {
        if (available) {
            mToggleFlashBtn.setVisibility(View.VISIBLE);
        } else {
            mToggleFlashBtn.setVisibility(View.INVISIBLE);
            disableFlash();
        }
    }

    @Override
    public void setIsCameraAvailable(boolean available) {
        mIsCameraAvailable = available;
    }

    @Override
    public void activateShutter() {
        handleShutter();
    }

    @Override
    public int getCurrentOrientation() {
        return mOrientation;
    }

    @Override
    public void videoSaved(Uri uri) {
        if (mIsVideoCaptureIntent) {
            final Intent intent = new Intent();
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void mediaSaved() {
        if (mIsImageCaptureIntent) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Config.newInstance(getApplicationContext()).setIsFirstRun(false);
        if (mPreview != null)
            mPreview.releaseCamera();
    }
}
