package com.simplemobiletools.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Preview extends ViewGroup implements SurfaceHolder.Callback, View.OnTouchListener {
    private static final String TAG = Preview.class.getSimpleName();
    private static final int FOCUS_AREA_SIZE = 200;
    private static final int PHOTO_PREVIEW_LENGTH = 1000;

    private static SurfaceHolder surfaceHolder;
    private static Camera camera;
    private static List<Camera.Size> supportedPreviewSizes;
    private static SurfaceView surfaceView;
    private static Camera.Size previewSize;
    private static boolean canTakePicture;
    private static Activity activity;
    private static int currCameraId;
    private static boolean isFlashEnabled;
    private static Camera.Parameters parameters;
    private static PreviewListener callback;
    private static MediaRecorder recorder;
    private static boolean isRecording;
    private static boolean isVideoMode;
    private static String curVideoPath;

    public Preview(Context context) {
        super(context);
    }

    public Preview(Activity act, SurfaceView sv, PreviewListener cb) {
        super(act);

        activity = act;
        callback = cb;
        surfaceView = sv;
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        canTakePicture = false;
        surfaceView.setOnTouchListener(this);
        isFlashEnabled = false;
    }

    public void setCamera(int cameraId) {
        currCameraId = cameraId;
        Camera newCamera;
        try {
            newCamera = Camera.open(cameraId);
        } catch (Exception e) {
            Utils.showToast(getContext(), R.string.camera_open_error);
            Log.e(TAG, "setCamera open " + e.getMessage());
            return;
        }

        if (camera == newCamera) {
            return;
        }

        releaseCamera();
        camera = newCamera;
        if (camera != null) {
            parameters = camera.getParameters();
            supportedPreviewSizes = parameters.getSupportedPreviewSizes();
            requestLayout();

            final List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

            camera.setParameters(parameters);
            camera.setDisplayOrientation(getCameraRotation(cameraId));

            if (canTakePicture) {
                try {
                    camera.setPreviewDisplay(surfaceHolder);
                } catch (IOException e) {
                    Log.e(TAG, "setCamera setPreviewDisplay " + e.getMessage());
                }
                setupPreview();
            }

            callback.setFlashAvailable(Utils.hasFlash(camera));
        }

        if (isVideoMode)
            initRecorder();
    }

    public static int getCameraRotation(int cameraId) {
        final Camera.CameraInfo info = Utils.getCameraInfo(cameraId);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public void takePicture() {
        if (canTakePicture) {
            if (isFlashEnabled) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            MediaPlayer.create(getContext(), R.raw.camera_shutter).start();
            camera.setParameters(parameters);
            camera.takePicture(null, null, takePictureCallback);
        }
        canTakePicture = false;
    }

    private Camera.PictureCallback takePictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera cam) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (camera != null) {
                        camera.startPreview();
                    }

                    canTakePicture = true;
                }
            }, PHOTO_PREVIEW_LENGTH);

            final Camera.CameraInfo info = Utils.getCameraInfo(currCameraId);
            new PhotoProcessor(getContext(), info.facing).execute(data);

            if (isFlashEnabled) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
            }
        }
    };

    private void focusArea(MotionEvent event) {
        if (camera == null)
            return;

        camera.cancelAutoFocus();
        final Rect focusRect = calculateFocusArea(event.getX(), event.getY());
        if (parameters.getMaxNumFocusAreas() > 0) {
            final List<Camera.Area> focusAreas = new ArrayList<>(1);
            focusAreas.add(new Camera.Area(focusRect, 1000));
            parameters.setFocusAreas(focusAreas);
        }

        camera.setParameters(parameters);
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                camera.cancelAutoFocus();
                final List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

                camera.setParameters(parameters);
            }
        });
    }

    private Rect calculateFocusArea(float x, float y) {
        int left = Float.valueOf((x / surfaceView.getWidth()) * 2000 - 1000).intValue();
        int top = Float.valueOf((y / surfaceView.getHeight()) * 2000 - 1000).intValue();

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

        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (camera != null) {
                camera.setPreviewDisplay(surfaceHolder);
            }
        } catch (IOException e) {
            Log.e(TAG, "surfaceCreated IOException " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setupPreview();
    }

    private void setupPreview() {
        canTakePicture = true;
        if (camera != null && previewSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);

            requestLayout();

            camera.setParameters(parameters);
            camera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
        }

        cleanupRecorder();
    }

    private void cleanupRecorder() {
        if (recorder != null) {
            if (isRecording) {
                recorder.stop();
            }

            recorder.release();
            recorder = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int height, int width) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) height / width;

        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - height) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - height);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - height) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - height);
                }
            }
        }
        return optimalSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (supportedPreviewSizes != null) {
            previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        focusArea(event);
        return false;
    }

    public void enableFlash() {
        if (isVideoMode) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            camera.setParameters(parameters);
        }

        isFlashEnabled = true;
    }

    public void disableFlash() {
        isFlashEnabled = false;
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(parameters);
    }

    public void initPhotoMode() {
        isRecording = false;
        isVideoMode = false;
        stopRecording();
        cleanupRecorder();
    }

    // VIDEO RECORDING
    public void initRecorder() {
        isRecording = false;
        isVideoMode = true;
        recorder = new MediaRecorder();
        recorder.setCamera(camera);
        recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

        curVideoPath = Utils.getOutputMediaFile(getContext(), false);
        if (curVideoPath.isEmpty()) {
            Utils.showToast(getContext(), R.string.video_creating_error);
            return;
        }

        final CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        recorder.setProfile(cpHigh);
        recorder.setOutputFile(curVideoPath);
        recorder.setPreviewDisplay(surfaceHolder.getSurface());

        if (currCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            recorder.setOrientationHint(270);
        } else {
            recorder.setOrientationHint(getCameraRotation(currCameraId));
        }

        try {
            recorder.prepare();
        } catch (IllegalStateException e) {
            Log.e(TAG, "initRecorder " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "initRecorder " + e.getMessage());
        }
    }

    public boolean toggleRecording() {
        if (isRecording) {
            stopRecording();
            initRecorder();
        } else {
            camera.lock();
            camera.unlock();
            recorder.start();
            isRecording = true;
        }
        return isRecording;
    }

    private void stopRecording() {
        if (recorder != null && isRecording) {
            recorder.stop();
            recorder = null;
            Utils.scanFile(curVideoPath, getContext());
        }

        isRecording = false;
    }

    public interface PreviewListener {
        void setFlashAvailable(boolean available);
    }
}
