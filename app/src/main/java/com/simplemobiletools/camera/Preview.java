package com.simplemobiletools.camera;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;

import com.simplemobiletools.camera.activities.MainActivity;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Preview extends ViewGroup implements SurfaceHolder.Callback, View.OnTouchListener, OnLongClickListener, View.OnClickListener,
        MediaScannerConnection.OnScanCompletedListener {
    private static final String TAG = Preview.class.getSimpleName();
    private static final int FOCUS_AREA_SIZE = 100;
    private static final int PHOTO_PREVIEW_LENGTH = 1000;
    private static final float RATIO_TOLERANCE = 0.1f;

    private static SurfaceHolder mSurfaceHolder;
    private static Camera mCamera;
    private static List<Camera.Size> mSupportedPreviewSizes;
    private static SurfaceView mSurfaceView;
    private static Camera.Size mPreviewSize;
    private static MainActivity mActivity;
    private static Camera.Parameters mParameters;
    private static PreviewListener mCallback;
    private static MediaRecorder mRecorder;
    private static String mCurVideoPath;
    private static Point mScreenSize;
    private static Uri mTargetUri;

    private static boolean mCanTakePicture;
    private static boolean mIsFlashEnabled;
    private static boolean mIsRecording;
    private static boolean mIsVideoMode;
    private static boolean mIsSurfaceCreated;
    private static boolean mSwitchToVideoAsap;
    private static boolean mSetupPreviewAfterMeasure;
    private static boolean mFocusBeforeCapture;
    private static boolean mForceAspectRatio;
    private static int mLastClickX;
    private static int mLastClickY;
    private static int mInitVideoRotation;
    private static int mCurrCameraId;

    public Preview(Context context) {
        super(context);
    }

    public Preview(MainActivity activity, SurfaceView surfaceView, PreviewListener previewListener) {
        super(activity);

        mActivity = activity;
        mCallback = previewListener;
        mSurfaceView = surfaceView;
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mCanTakePicture = false;
        mSurfaceView.setOnTouchListener(this);
        mSurfaceView.setOnClickListener(this);
        mIsFlashEnabled = false;
        mIsVideoMode = false;
        mIsSurfaceCreated = false;
        mSetupPreviewAfterMeasure = false;
        mCurVideoPath = "";
        mScreenSize = Utils.getScreenSize(mActivity);
    }

    public void trySwitchToVideo() {
        if (mIsSurfaceCreated) {
            initRecorder();
        } else {
            mSwitchToVideoAsap = true;
        }
    }

    public boolean setCamera(int cameraId) {
        mCurrCameraId = cameraId;
        Camera newCamera;
        try {
            newCamera = Camera.open(cameraId);
            mCallback.setIsCameraAvailable(true);
        } catch (Exception e) {
            Utils.showToast(getContext(), R.string.camera_open_error);
            Log.e(TAG, "setCamera open " + e.getMessage());
            mCallback.setIsCameraAvailable(false);
            return false;
        }

        if (mCamera == newCamera) {
            return false;
        }

        releaseCamera();
        mCamera = newCamera;
        if (mCamera != null) {
            mParameters = mCamera.getParameters();
            mSupportedPreviewSizes = mParameters.getSupportedPreviewSizes();
            Collections.sort(mSupportedPreviewSizes, new SizesComparator());
            requestLayout();
            invalidate();
            mSetupPreviewAfterMeasure = true;

            final List<String> focusModes = mParameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

            final int rotation = getPreviewRotation(cameraId);
            mCamera.setDisplayOrientation(rotation);
            mCamera.setParameters(mParameters);

            if (mCanTakePicture) {
                try {
                    mCamera.setPreviewDisplay(mSurfaceHolder);
                } catch (IOException e) {
                    Log.e(TAG, "setCamera setPreviewDisplay " + e.getMessage());
                    return false;
                }
            }

            mCallback.setFlashAvailable(Utils.hasFlash(mCamera));
        }

        if (mIsVideoMode) {
            initRecorder();
        }

        final Config config = Config.newInstance(getContext());
        mFocusBeforeCapture = config.getFocusBeforeCaptureEnabled();
        mForceAspectRatio = config.getForceRatioEnabled();
        mSurfaceView.setOnLongClickListener(config.getLongTapEnabled() ? this : null);

        return true;
    }

    public void setTargetUri(Uri uri) {
        mTargetUri = uri;
    }

    private static int getPreviewRotation(int cameraId) {
        final Camera.CameraInfo info = Utils.getCameraInfo(cameraId);
        final int degrees = getRotationDegrees();

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = 360 - result;
        } else {
            result = info.orientation - degrees + 360;
        }

        return result % 360;
    }

    private static int getMediaRotation(int cameraId) {
        final int degrees = getRotationDegrees();
        final Camera.CameraInfo info = Utils.getCameraInfo(cameraId);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 + info.orientation + degrees) % 360;
        }

        return (360 + info.orientation - degrees) % 360;
    }

    private static int getRotationDegrees() {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    public void tryTakePicture() {
        if (mFocusBeforeCapture) {
            focusArea(true);
        } else {
            takePicture();
        }
    }

    private void takePicture() {
        if (mCanTakePicture) {
            if (mIsFlashEnabled) {
                mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            } else {
                mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }

            int rotation = getMediaRotation(mCurrCameraId);
            rotation += compensateDeviceRotation();

            final Camera.Size maxSize = getOptimalPictureSize();
            mParameters.setPictureSize(maxSize.width, maxSize.height);
            mParameters.setRotation(rotation % 360);

            if (Config.newInstance(getContext()).getIsSoundEnabled()) {
                new MediaActionSound().play(MediaActionSound.SHUTTER_CLICK);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mCamera.enableShutterSound(false);
            }

            mCamera.setParameters(mParameters);
            mCamera.takePicture(null, null, takePictureCallback);
        }
        mCanTakePicture = false;
    }

    private Camera.PictureCallback takePictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera cam) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mCamera != null) {
                        mCamera.startPreview();
                    }

                    mCanTakePicture = true;
                }
            }, PHOTO_PREVIEW_LENGTH);

            new PhotoProcessor(mActivity, mTargetUri).execute(data);
            if (mIsFlashEnabled) {
                mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCamera.setParameters(mParameters);
            }
        }
    };

    private Camera.Size getOptimalPictureSize() {
        final int maxResolution = getMaxResolution();
        final List<Camera.Size> sizes = mParameters.getSupportedPictureSizes();
        Collections.sort(sizes, new SizesComparator());
        Camera.Size maxSize = sizes.get(0);
        for (Camera.Size size : sizes) {
            final boolean isProperRatio = isProperRatio(size);
            final boolean isProperResolution = isProperResolution(size, maxResolution);
            if (isProperResolution && isProperRatio) {
                maxSize = size;
                break;
            }
        }
        return maxSize;
    }

    private int getMaxResolution() {
        final int maxRes = Config.newInstance(getContext()).getMaxResolution();
        switch (maxRes) {
            case 0:
                return 6000000;
            case 1:
                return 9000000;
            default:
                return 0;
        }
    }

    private boolean isProperResolution(Camera.Size size, int maxRes) {
        return maxRes == 0 || size.width * size.height < maxRes;
    }

    private boolean isProperRatio(Camera.Size size) {
        final float currRatio = (float) size.height / size.width;
        float wantedRatio = (float) 3 / 4;
        if (mForceAspectRatio || mIsVideoMode)
            wantedRatio = (float) 9 / 16;
        final float diff = Math.abs(currRatio - wantedRatio);
        return diff < RATIO_TOLERANCE;
    }

    private Camera.Size getOptimalVideoSize() {
        final List<Camera.Size> sizes = getSupportedVideoSizes();
        Collections.sort(sizes, new SizesComparator());
        Camera.Size maxSize = sizes.get(0);
        for (Camera.Size size : sizes) {
            final boolean isProperRatio = isProperRatio(size);
            if (isProperRatio) {
                maxSize = size;
                break;
            }
        }
        return maxSize;
    }

    public List<Camera.Size> getSupportedVideoSizes() {
        if (mParameters.getSupportedVideoSizes() != null) {
            return mParameters.getSupportedVideoSizes();
        } else {
            return mParameters.getSupportedPreviewSizes();
        }
    }

    private int compensateDeviceRotation() {
        int degrees = 0;
        boolean isFrontCamera = (mCurrCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT);
        int deviceOrientation = mCallback.getCurrentOrientation();
        if (deviceOrientation == Constants.ORIENT_LANDSCAPE_LEFT) {
            degrees += isFrontCamera ? 90 : 270;
        } else if (deviceOrientation == Constants.ORIENT_LANDSCAPE_RIGHT) {
            degrees += isFrontCamera ? 270 : 90;
        }
        return degrees;
    }

    private int getFinalRotation() {
        int rotation = getMediaRotation(mCurrCameraId);
        rotation += compensateDeviceRotation();
        return rotation % 360;
    }

    private void focusArea(final boolean takePictureAfter) {
        if (mCamera == null)
            return;

        mCamera.cancelAutoFocus();
        final Rect focusRect = calculateFocusArea(mLastClickX, mLastClickY);
        if (mParameters.getMaxNumFocusAreas() > 0) {
            final List<Camera.Area> focusAreas = new ArrayList<>(1);
            focusAreas.add(new Camera.Area(focusRect, 1000));
            mParameters.setFocusAreas(focusAreas);
            mCallback.drawFocusRect(mLastClickX, mLastClickY);
        }

        mCamera.setParameters(mParameters);
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                camera.cancelAutoFocus();
                final List<String> focusModes = mParameters.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

                camera.setParameters(mParameters);
                if (takePictureAfter) {
                    takePicture();
                }
            }
        });
    }

    private Rect calculateFocusArea(float x, float y) {
        int left = Float.valueOf((x / mSurfaceView.getWidth()) * 2000 - 1000).intValue();
        int top = Float.valueOf((y / mSurfaceView.getHeight()) * 2000 - 1000).intValue();

        int tmp = left;
        left = top;
        top = -tmp;

        final int rectLeft = Math.max(left - FOCUS_AREA_SIZE / 2, -1000);
        final int rectTop = Math.max(top - FOCUS_AREA_SIZE / 2, -1000);
        final int rectRight = Math.min(left + FOCUS_AREA_SIZE / 2, 1000);
        final int rectBottom = Math.min(top + FOCUS_AREA_SIZE / 2, 1000);
        return new Rect(rectLeft, rectTop, rectRight, rectBottom);
    }

    public void releaseCamera() {
        stopRecording();

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        cleanupRecorder();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mIsSurfaceCreated = true;
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(mSurfaceHolder);
            }

            if (mSwitchToVideoAsap)
                initRecorder();
        } catch (IOException e) {
            Log.e(TAG, "surfaceCreated IOException " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mIsSurfaceCreated = true;

        if (mIsVideoMode) {
            initRecorder();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mIsSurfaceCreated = false;
        if (mCamera != null) {
            mCamera.stopPreview();
        }

        cleanupRecorder();
    }

    private void setupPreview() {
        mCanTakePicture = true;
        if (mCamera != null && mPreviewSize != null) {
            mParameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            mCamera.setParameters(mParameters);
            mCamera.startPreview();
        }
    }

    private void cleanupRecorder() {
        if (mRecorder != null) {
            if (mIsRecording) {
                stopRecording();
            }

            mRecorder.release();
            mRecorder = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
        Camera.Size result = null;
        for (Camera.Size size : sizes) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea > resultArea) {
                        result = size;
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mScreenSize.x, mScreenSize.y);

        if (mSupportedPreviewSizes != null) {
            // for simplicity lets assume that most displays are 16:9 and the remaining ones are 4:3
            // always set 16:9 for videos as many devices support 4:3 only in low quality
            if (mForceAspectRatio || mIsVideoMode) {
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, mScreenSize.y, mScreenSize.x);
            } else {
                final int newRatioHeight = (int) (mScreenSize.x * ((double) 4 / 3));
                setMeasuredDimension(mScreenSize.x, newRatioHeight);
                mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, newRatioHeight, mScreenSize.x);
            }
            final LayoutParams lp = mSurfaceView.getLayoutParams();

            // make sure to occupy whole width in every case
            if (mScreenSize.x > mPreviewSize.height) {
                final float ratio = (float) mScreenSize.x / mPreviewSize.height;
                lp.width = (int) (mPreviewSize.height * ratio);
                if (mForceAspectRatio || mIsVideoMode) {
                    lp.height = mScreenSize.y;
                } else {
                    lp.height = (int) (mPreviewSize.width * ratio);
                }
            } else {
                lp.width = mPreviewSize.height;
                lp.height = mPreviewSize.width;
            }

            if (mSetupPreviewAfterMeasure) {
                mSetupPreviewAfterMeasure = false;
                mCamera.stopPreview();
                setupPreview();
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mLastClickX = (int) event.getX();
        mLastClickY = (int) event.getY();
        return false;
    }

    public void enableFlash() {
        if (mIsVideoMode) {
            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            mCamera.setParameters(mParameters);
        }

        mIsFlashEnabled = true;
    }

    public void disableFlash() {
        mIsFlashEnabled = false;
        mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(mParameters);
    }

    public void initPhotoMode() {
        stopRecording();
        cleanupRecorder();
        mIsRecording = false;
        mIsVideoMode = false;
        recheckAspectRatio();
    }

    private void recheckAspectRatio() {
        if (!mForceAspectRatio) {
            mSetupPreviewAfterMeasure = true;
            invalidate();
            requestLayout();
        }
    }

    // VIDEO RECORDING
    public boolean initRecorder() {
        if (mCamera == null || mRecorder != null || !mIsSurfaceCreated)
            return false;

        mSwitchToVideoAsap = false;
        Camera.Size preferred = mParameters.getPreferredPreviewSizeForVideo();
        if (preferred == null) {
            final List<Camera.Size> previewSizes = mParameters.getSupportedPreviewSizes();
            Collections.sort(previewSizes, new SizesComparator());
            preferred = previewSizes.get(0);
        }

        mParameters.setPreviewSize(preferred.width, preferred.height);
        mCamera.setParameters(mParameters);

        mIsRecording = false;
        mIsVideoMode = true;
        recheckAspectRatio();
        mRecorder = new MediaRecorder();
        mRecorder.setCamera(mCamera);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);

        mCurVideoPath = Utils.getOutputMediaFile(getContext(), false);
        if (mCurVideoPath.isEmpty()) {
            Utils.showToast(getContext(), R.string.video_creating_error);
            return false;
        }

        final Camera.Size videoSize = getOptimalVideoSize();
        final CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        cpHigh.videoFrameWidth = videoSize.width;
        cpHigh.videoFrameHeight = videoSize.height;
        mRecorder.setProfile(cpHigh);
        mRecorder.setOutputFile(mCurVideoPath);
        mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

        int rotation = getFinalRotation();
        mInitVideoRotation = rotation;
        mRecorder.setOrientationHint(rotation);

        try {
            mRecorder.prepare();
        } catch (Exception e) {
            Utils.showToast(getContext(), R.string.video_setup_error);
            Log.e(TAG, "initRecorder " + e.getMessage());
            releaseCamera();
            return false;
        }
        return true;
    }

    public boolean toggleRecording() {
        if (mIsRecording) {
            stopRecording();
            initRecorder();
        } else {
            startRecording();
        }
        return mIsRecording;
    }

    private void startRecording() {
        if (mInitVideoRotation != getFinalRotation()) {
            cleanupRecorder();
            initRecorder();
        }

        try {
            mCamera.unlock();
            mRecorder.start();
            mIsRecording = true;
        } catch (Exception e) {
            Utils.showToast(getContext(), R.string.video_setup_error);
            Log.e(TAG, "toggleRecording " + e.getMessage());
            releaseCamera();
        }
    }

    private void stopRecording() {
        if (mRecorder != null && mIsRecording) {
            try {
                mRecorder.stop();
                final String[] paths = {mCurVideoPath};
                MediaScannerConnection.scanFile(getContext(), paths, null, this);
            } catch (RuntimeException e) {
                new File(mCurVideoPath).delete();
                Utils.showToast(getContext(), R.string.video_saving_error);
                Log.e(TAG, "stopRecording " + e.getMessage());
                mRecorder = null;
                mIsRecording = false;
                releaseCamera();
            }
        }

        mRecorder = null;
        mIsRecording = false;

        final File file = new File(mCurVideoPath);
        if (file.exists() && file.length() == 0) {
            file.delete();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        mCallback.activateShutter();
        return true;
    }

    @Override
    public void onClick(View v) {
        focusArea(false);
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        mCallback.videoSaved(uri);
    }

    private static class SizesComparator implements Comparator<Camera.Size>, Serializable {
        private static final long serialVersionUID = 5431278455314658485L;

        @Override
        public int compare(final Camera.Size a, final Camera.Size b) {
            return b.width * b.height - a.width * a.height;
        }
    }

    public interface PreviewListener {
        void setFlashAvailable(boolean available);

        void setIsCameraAvailable(boolean available);

        void activateShutter();

        int getCurrentOrientation();

        void videoSaved(Uri uri);

        void drawFocusRect(int x, int y);
    }
}
