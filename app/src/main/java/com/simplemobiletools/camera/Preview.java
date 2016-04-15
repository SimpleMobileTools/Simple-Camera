package com.simplemobiletools.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Preview extends ViewGroup implements SurfaceHolder.Callback {
    private static final String TAG = Preview.class.getSimpleName();
    private static final int MEDIA_TYPE_IMAGE = 1;

    private static Context context;
    private static SurfaceHolder surfaceHolder;
    private static Camera camera;
    private static List<Camera.Size> supportedPreviewSizes;
    private static SurfaceView surfaceView;
    private static Camera.Size previewSize;

    public Preview(Context cxt) {
        super(cxt);
        context = cxt;
    }

    public Preview(Context cxt, SurfaceView sv) {
        super(cxt);
        context = cxt;

        surfaceView = sv;
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setCamera(Camera newCamera) {
        if (camera == newCamera) {
            return;
        }

        releaseCamera();
        camera = newCamera;

        if (camera != null) {
            supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();
            requestLayout();

            final Camera.Parameters params = camera.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(params);
        }
    }

    public void takePicture() {
        camera.takePicture(null, null, takePictureCallback);
    }

    private Camera.PictureCallback takePictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            final File photoFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (photoFile == null) {
                return;
            }

            try {
                final FileOutputStream fos = new FileOutputStream(photoFile);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                bitmap = setBitmapRotation(bitmap, photoFile.toString());
                bitmap = setAspectRatio(bitmap);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();
                scanPhoto(photoFile);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "onPictureTaken file not found: " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "onPictureTaken ioexception " + e.getMessage());
            }
        }
    };

    private static File getOutputMediaFile(int type) {
        final String appName = context.getResources().getString(R.string.app_name);
        final File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (type == MEDIA_TYPE_IMAGE) {
            return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
        }

        return null;
    }

    private Bitmap setBitmapRotation(Bitmap bitmap, String path) throws IOException {
        final ExifInterface exif = new ExifInterface(path);
        if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("6")) {
            bitmap = rotateImage(bitmap, 90);
        } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("8")) {
            bitmap = rotateImage(bitmap, 270);
        } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("3")) {
            bitmap = rotateImage(bitmap, 180);
        } else if (exif.getAttribute(ExifInterface.TAG_ORIENTATION).equalsIgnoreCase("0")) {
            bitmap = rotateImage(bitmap, 90);
        }
        return bitmap;
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        final Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap setAspectRatio(Bitmap bitmap) {
        final double wantedAspect = (double) 16 / (double) 9;
        final double bmpWidth = bitmap.getWidth();
        final double bmpHeight = bitmap.getHeight();

        if (bmpHeight / bmpWidth < wantedAspect) {
            final double extraWidth = bmpWidth - (bmpHeight / wantedAspect);
            final int startX = (int) (extraWidth / 2);
            return Bitmap.createBitmap(bitmap, startX, 0, (int) (bmpWidth - extraWidth), (int) bmpHeight);
        }
        return bitmap;
    }

    private void scanPhoto(File photo) {
        final String[] photoPath = {photo.getAbsolutePath()};
        MediaScannerConnection.scanFile(context, photoPath, null, null);
    }

    public void releaseCamera() {
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
                camera.setPreviewDisplay(holder);
            }
        } catch (IOException e) {
            Log.e(TAG, "surfaceCreated IOException " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            final Camera.Parameters parameters = camera.getParameters();
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
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) h / w;

        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
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
}
