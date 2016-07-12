package com.simplemobiletools.camera.activities;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
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
import com.simplemobiletools.camera.FocusRectView;
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

public class MainActivity extends AppCompatActivity
        implements SensorEventListener, PreviewListener, PhotoProcessor.MediaSavedListener, MediaScannerConnection.OnScanCompletedListener {
    @BindView(R.id.view_holder) RelativeLayout mViewHolder;
    @BindView(R.id.toggle_camera) ImageView mToggleCameraBtn;
    @BindView(R.id.toggle_flash) ImageView mToggleFlashBtn;
    @BindView(R.id.toggle_photo_video) ImageView mTogglePhotoVideoBtn;
    @BindView(R.id.shutter) ImageView mShutterBtn;
    @BindView(R.id.video_rec_curr_timer) TextView mRecCurrTimer;
    @BindView(R.id.about) View mAboutBtn;
    @BindView(R.id.last_photo_video_preview) ImageView mLastPhotoVideoPreview;

    private static final int CAMERA_STORAGE_PERMISSION = 1;
    private static final int AUDIO_PERMISSION = 2;

    private static SensorManager mSensorManager;
    private static Preview mPreview;
    private static FocusRectView mFocusRectView;
    private static Handler mTimerHandler;
    private static Uri mPreviewUri;

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
            if (intent.getExtras() != null && intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE) ||
                    intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE_SECURE)) {
                mIsImageCaptureIntent = true;
                hideToggleModeAbout();
                final Object output = intent.getExtras().get(MediaStore.EXTRA_OUTPUT);
                if (output != null && output instanceof Uri) {
                    mPreview.setTargetUri((Uri) output);
                }
            } else if (intent.getAction().equals(MediaStore.ACTION_VIDEO_CAPTURE)) {
                mIsVideoCaptureIntent = true;
                hideToggleModeAbout();
                mShutterBtn.setImageDrawable(getResources().getDrawable(R.mipmap.video_rec));
            }
        }
    }

    private void initializeCamera() {
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mCurrCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        mPreview = new Preview(this, (SurfaceView) findViewById(R.id.camera_view), this);
        mPreview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mViewHolder.addView(mPreview);

        mFocusRectView = new FocusRectView(getApplicationContext());
        mViewHolder.addView(mFocusRectView);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mIsInPhotoMode = true;
        mTimerHandler = new Handler();
        setupPreviewImage(true);
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

    @OnClick(R.id.last_photo_video_preview)
    public void showLastMediaPreview() {
        if (mPreviewUri == null)
            return;

        try {
            final String REVIEW_ACTION = "com.android.camera.action.REVIEW";
            Intent intent = new Intent(REVIEW_ACTION, mPreviewUri);
            this.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, mPreviewUri);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Utils.showToast(getApplicationContext(), R.string.no_gallery_app_available);
            }
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
            tryInitVideoButtons();
        }
    }

    private void initPhotoButtons() {
        final Resources res = getResources();
        mTogglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.videocam));
        mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.camera));
        mPreview.initPhotoMode();
        setupPreviewImage(true);
    }

    private void tryInitVideoButtons() {
        if (mPreview.initRecorder()) {
            initVideoButtons();
        } else {
            if (!mIsVideoCaptureIntent) {
                Utils.showToast(getApplicationContext(), R.string.video_mode_error);
            }
        }
    }

    private void initVideoButtons() {
        final Resources res = getResources();
        mTogglePhotoVideoBtn.setImageDrawable(res.getDrawable(R.mipmap.photo));
        mToggleCameraBtn.setVisibility(View.VISIBLE);
        mShutterBtn.setImageDrawable(res.getDrawable(R.mipmap.video_rec));
        checkFlash();
        setupPreviewImage(false);
    }

    private void setupPreviewImage(boolean isPhoto) {
        final Uri uri = (isPhoto) ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        final long lastMediaId = getLastMediaId(uri);
        if (lastMediaId == 0) {
            return;
        }
        final ContentResolver cr = getContentResolver();
        mPreviewUri = Uri.withAppendedPath(uri, String.valueOf(lastMediaId));
        Bitmap tmb;

        if (isPhoto) {
            tmb = MediaStore.Images.Thumbnails.getThumbnail(cr, lastMediaId, MediaStore.Images.Thumbnails.MICRO_KIND, null);
            final int rotationDegrees = getImageRotation();
            tmb = rotateThumbnail(tmb, rotationDegrees);
        } else {
            tmb = MediaStore.Video.Thumbnails.getThumbnail(cr, lastMediaId, MediaStore.Video.Thumbnails.MICRO_KIND, null);
        }

        setPreviewImage(tmb);
    }

    private int getImageRotation() {
        final String[] projection = {MediaStore.Images.ImageColumns.ORIENTATION};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int orientationIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.ORIENTATION);
                return cursor.getInt(orientationIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    private Bitmap rotateThumbnail(Bitmap tmb, int degrees) {
        if (degrees == 0)
            return tmb;

        final Matrix matrix = new Matrix();
        matrix.setRotate(degrees, tmb.getWidth() / 2, tmb.getHeight() / 2);
        return Bitmap.createBitmap(tmb, 0, 0, tmb.getWidth(), tmb.getHeight(), matrix, true);
    }

    private void setPreviewImage(final Bitmap bmp) {
        if (bmp != null) {
            mLastPhotoVideoPreview.post(new Runnable() {
                @Override
                public void run() {
                    mLastPhotoVideoPreview.setImageBitmap(bmp);
                }
            });
        }
    }

    private long getLastMediaId(Uri uri) {
        final String[] projection = {MediaStore.Images.ImageColumns._ID};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");
            if (cursor != null && cursor.moveToFirst()) {
                final int idIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns._ID);
                return cursor.getLong(idIndex);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
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
            setupPreviewImage(mIsInPhotoMode);

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
                initVideoButtons();
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
        setupPreviewImage(mIsInPhotoMode);
        if (mIsVideoCaptureIntent) {
            final Intent intent = new Intent();
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void drawFocusRect(int x, int y) {
        if (mFocusRectView != null) {
            mFocusRectView.drawFocusRect(x, y);
        }
    }

    @Override
    public void mediaSaved(String path) {
        final String[] paths = {path};
        MediaScannerConnection.scanFile(getApplicationContext(), paths, null, this);

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

    @Override
    public void onScanCompleted(String path, Uri uri) {
        setupPreviewImage(mIsInPhotoMode);
    }
}
