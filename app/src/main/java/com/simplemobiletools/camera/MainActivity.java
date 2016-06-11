package com.simplemobiletools.camera;

import android.content.Intent;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    @BindView(R.id.viewHolder) RelativeLayout viewHolder;
    @BindView(R.id.toggle_camera) ImageView toggleCameraBtn;
    @BindView(R.id.toggle_flash) ImageView toggleFlashBtn;
    @BindView(R.id.toggle_videocam) ImageView togglePhotoVideoBtn;
    @BindView(R.id.shutter) ImageView shutterBtn;

    public static int orientation;
    private static SensorManager sensorManager;
    private Preview preview;
    private int currCamera;
    private boolean isFlashEnabled;
    private boolean isPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        currCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        preview = new Preview(this, (SurfaceView) findViewById(R.id.surfaceView));
        preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        viewHolder.addView(preview);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        isPhoto = true;
    }

    @OnClick(R.id.toggle_camera)
    public void toggleCamera() {
        if (currCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            currCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
            toggleCameraBtn.setImageResource(R.mipmap.camera_rear);
        } else {
            currCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
            toggleCameraBtn.setImageResource(R.mipmap.camera_front);
        }

        disableFlash();
        preview.releaseCamera();
        preview.setCamera(currCamera);
    }

    @OnClick(R.id.toggle_flash)
    public void toggleFlash() {
        if (isFlashEnabled) {
            disableFlash();
        } else if (preview.enableFlash()) {
            isFlashEnabled = preview.enableFlash();
            toggleFlashBtn.setImageResource(R.mipmap.flash_on);
        }
    }

    private void disableFlash() {
        preview.disableFlash();
        isFlashEnabled = false;
        toggleFlashBtn.setImageResource(R.mipmap.flash_off);
    }

    @OnClick(R.id.shutter)
    public void shutterPressed() {
        if (isPhoto) {
            preview.takePicture();
        } else {

        }
    }

    @OnClick(R.id.about)
    public void launchAbout() {
        final Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
        startActivity(intent);
    }

    @OnClick(R.id.toggle_videocam)
    public void toggleVideo() {
        final Resources res = getResources();
        isPhoto = !isPhoto;

        if (isPhoto) {
            togglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.videocam));
            shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.camera));
        } else {
            togglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.photo));
            shutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
        }
    }

    private void hideNavigationBarIcons() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final int cnt = Camera.getNumberOfCameras();
        if (cnt == 1) {
            toggleCameraBtn.setVisibility(View.INVISIBLE);
        }
        preview.setCamera(currCamera);
        hideNavigationBarIcons();

        if (sensorManager != null) {
            final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        preview.releaseCamera();

        if (sensorManager != null)
            sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[1] < 6.5 && event.values[1] > -6.5) {
            if (event.values[0] > 0) {
                orientation = Constants.ORIENT_LANDSCAPE_LEFT;
            } else {
                orientation = Constants.ORIENT_LANDSCAPE_RIGHT;
            }
        } else {
            orientation = Constants.ORIENT_PORTRAIT;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
