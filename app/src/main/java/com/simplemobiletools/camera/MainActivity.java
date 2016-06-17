package com.simplemobiletools.camera;

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

import com.simplemobiletools.camera.Preview.PreviewListener;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SensorEventListener, PreviewListener, PhotoProcessor.MediaSavedListener {
    @BindView(R.id.viewHolder) RelativeLayout viewHolder;
    @BindView(R.id.toggle_camera) ImageView toggleCameraBtn;
    @BindView(R.id.toggle_flash) ImageView toggleFlashBtn;
    @BindView(R.id.toggle_photo_video) ImageView togglePhotoVideoBtn;
    @BindView(R.id.shutter) ImageView shutterBtn;
    @BindView(R.id.video_rec_curr_timer) TextView recCurrTimer;
    @BindView(R.id.about) View aboutBtn;

    private static final int CAMERA_STORAGE_PERMISSION = 1;
    private static final int AUDIO_PERMISSION = 2;
    private static SensorManager sensorManager;
    private Preview preview;
    private boolean isFlashEnabled;
    private boolean isInPhotoMode;
    private boolean isAskingPermissions;
    private boolean isCameraAvailable;
    private boolean isImageCaptureIntent;
    private boolean isVideoCaptureIntent;
    private boolean isHardwareShutterHandled;
    private int currVideoRecTimer;
    private int orientation;
    private int currCamera;
    private Handler timerHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        tryInitCamera();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA && !isHardwareShutterHandled) {
            isHardwareShutterHandled = true;
            shutterPressed();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            isHardwareShutterHandled = false;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void hideToggleModeAbout() {
        if (togglePhotoVideoBtn != null)
            togglePhotoVideoBtn.setVisibility(View.GONE);

        if (aboutBtn != null)
            aboutBtn.setVisibility(View.GONE);
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
        if (intent != null) {
            if (intent.getExtras() != null && intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE)) {
                isImageCaptureIntent = true;
                hideToggleModeAbout();
                final Object output = intent.getExtras().get(MediaStore.EXTRA_OUTPUT);
                if (output != null && output instanceof Uri) {
                    preview.setTargetUri((Uri) output);
                }
            } else if (intent.getAction().equals(MediaStore.ACTION_VIDEO_CAPTURE)) {
                isVideoCaptureIntent = true;
                hideToggleModeAbout();
                preview.setIsVideoCaptureIntent();
                shutterBtn.setImageDrawable(getResources().getDrawable(R.mipmap.video_rec));
            }
        }
    }

    private void initializeCamera() {
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        currCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView), this);
        preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewHolder.addView(preview);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        isInPhotoMode = true;
        timerHandler = new Handler();
    }

    private boolean hasCameraAndStoragePermission() {
        return Utils.hasCameraPermission(getApplicationContext()) && Utils.hasStoragePermission(getApplicationContext());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        isAskingPermissions = false;

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
                toggleVideo();
            } else {
                Utils.showToast(getApplicationContext(), R.string.no_audio_permissions);
                if (isVideoCaptureIntent)
                    finish();
            }
        }
    }

    @OnClick(R.id.toggle_camera)
    public void toggleCamera() {
        if (!checkCameraAvailable()) {
            return;
        }

        if (currCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            currCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            currCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        int newIconId = R.mipmap.camera_front;
        preview.releaseCamera();
        if (preview.setCamera(currCamera)) {
            if (currCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
                newIconId = R.mipmap.camera_rear;
            }
            toggleCameraBtn.setImageResource(newIconId);
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

        if (isFlashEnabled) {
            disableFlash();
        } else {
            enableFlash();
        }
    }

    private void disableFlash() {
        preview.disableFlash();
        isFlashEnabled = false;
        toggleFlashBtn.setImageResource(R.mipmap.flash_off);
    }

    private void enableFlash() {
        preview.enableFlash();
        isFlashEnabled = true;
        toggleFlashBtn.setImageResource(R.mipmap.flash_on);
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
        if (isInPhotoMode) {
            preview.takePicture();
        } else {
            final Resources res = getResources();
            final boolean isRecording = preview.toggleRecording();
            if (isRecording) {
                shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_stop));
                toggleCameraBtn.setVisibility(View.INVISIBLE);
                showTimer();
            } else {
                shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
                toggleCameraBtn.setVisibility(View.VISIBLE);
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
    public void handleToggleVideo() {
        toggleVideo();
    }

    private void toggleVideo() {
        if (!checkCameraAvailable()) {
            return;
        }

        if (!Utils.hasAudioPermission(getApplicationContext())) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
            isAskingPermissions = true;
            return;
        }

        if (isVideoCaptureIntent)
            preview.trySwitchToVideo();

        disableFlash();
        hideTimer();
        isInPhotoMode = !isInPhotoMode;
        toggleCameraBtn.setVisibility(View.VISIBLE);

        if (isInPhotoMode) {
            initPhotoButtons();
        } else {
            initVideoButtons();
        }
    }

    private void initPhotoButtons() {
        final Resources res = getResources();
        togglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.videocam));
        shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.camera));
        preview.initPhotoMode();
    }

    private void initVideoButtons() {
        if (preview.initRecorder()) {
            final Resources res = getResources();
            togglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.photo));
            shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
            toggleCameraBtn.setVisibility(View.VISIBLE);
        } else {
            if (!isVideoCaptureIntent) {
                Utils.showToast(getApplicationContext(), R.string.video_mode_error);
            }
        }
    }

    private void hideNavigationBarIcons() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    private void hideTimer() {
        recCurrTimer.setText(Utils.formatSeconds(0));
        recCurrTimer.setVisibility(View.GONE);
        currVideoRecTimer = 0;
        timerHandler.removeCallbacksAndMessages(null);
    }

    private void showTimer() {
        recCurrTimer.setVisibility(View.VISIBLE);
        setupTimer();
    }

    private void setupTimer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recCurrTimer.setText(Utils.formatSeconds(currVideoRecTimer++));
                timerHandler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraAndStoragePermission()) {
            resumeCameraItems();

            if (isVideoCaptureIntent && isInPhotoMode) {
                toggleVideo();
            }
        }
    }

    private void resumeCameraItems() {
        final int cnt = Camera.getNumberOfCameras();
        if (cnt == 1) {
            toggleCameraBtn.setVisibility(View.INVISIBLE);
        }

        if (preview.setCamera(currCamera)) {
            hideNavigationBarIcons();

            if (sensorManager != null) {
                final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }

            if (!isInPhotoMode) {
                initVideoButtons();
            }
        } else {
            Utils.showToast(getApplicationContext(), R.string.camera_switch_error);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!hasCameraAndStoragePermission() || isAskingPermissions)
            return;

        hideTimer();
        if (preview != null) {
            preview.releaseCamera();
        }

        if (sensorManager != null)
            sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] < 6.5 && event.values[0] > -6.5) {
            orientation = Constants.ORIENT_PORTRAIT;
        } else {
            if (event.values[0] > 0) {
                orientation = Constants.ORIENT_LANDSCAPE_LEFT;
            } else {
                orientation = Constants.ORIENT_LANDSCAPE_RIGHT;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private boolean checkCameraAvailable() {
        if (!isCameraAvailable) {
            Utils.showToast(getApplicationContext(), R.string.camera_unavailable);
        }
        return isCameraAvailable;
    }

    @Override
    public void setFlashAvailable(boolean available) {
        if (available) {
            toggleFlashBtn.setVisibility(View.VISIBLE);
        } else {
            toggleFlashBtn.setVisibility(View.INVISIBLE);
            disableFlash();
        }
    }

    @Override
    public void setIsCameraAvailable(boolean available) {
        isCameraAvailable = available;
    }

    @Override
    public void activateShutter() {
        handleShutter();
    }

    @Override
    public int getCurrentOrientation() {
        return orientation;
    }

    @Override
    public void videoSaved(Uri uri) {
        if (isVideoCaptureIntent) {
            final Intent intent = new Intent();
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void mediaSaved() {
        if (isImageCaptureIntent) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (preview != null)
            preview.releaseCamera();
    }
}
