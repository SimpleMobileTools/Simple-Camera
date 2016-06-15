package com.simplemobiletools.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class PhotoProcessor extends AsyncTask<byte[], Void, Void> {
    private static final String TAG = PhotoProcessor.class.getSimpleName();
    private static Context mContext;
    private static int mCameraId;

    public PhotoProcessor(Context context, int cameraId) {
        mContext = context;
        mCameraId = cameraId;
    }

    @Override
    protected Void doInBackground(byte[]... params) {
        final String photoPath = Utils.getOutputMediaFile(mContext, true);
        if (photoPath.isEmpty()) {
            return null;
        }

        try {
            final File photoFile = new File(photoPath);
            final byte[] data = params[0];
            final FileOutputStream fos = new FileOutputStream(photoFile);
            /*Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            bitmap = setBitmapRotation(bitmap, photoFile.toString());
            bitmap = setAspectRatio(bitmap);
            bitmap = checkLandscape(bitmap);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);*/

            fos.write(data);
            fos.close();
            Utils.scanFile(photoPath, mContext);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "onPictureTaken file not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "onPictureTaken ioexception " + e.getMessage());
        }

        return null;
    }

    private Bitmap setBitmapRotation(Bitmap bitmap, String path) throws IOException {
        final ExifInterface exif = new ExifInterface(path);
        final String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);

        float angle = 0f;
        if (orientation.equalsIgnoreCase("6")) {
            angle = 90;
        } else if (orientation.equalsIgnoreCase("8")) {
            angle = 270;
        } else if (orientation.equalsIgnoreCase("3")) {
            angle = 180;
        } else if (orientation.equalsIgnoreCase("0")) {
            angle = 90;
        }

        return rotateImage(bitmap, angle);
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        final Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        final Camera.CameraInfo info = Utils.getCameraInfo(mCameraId);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            matrix.preScale(-1.f, 1.f);

        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap checkLandscape(Bitmap bitmap) {
        int angle = 0;
        if (MainActivity.orientation == Constants.ORIENT_LANDSCAPE_LEFT) {
            angle = -90;
        } else if (MainActivity.orientation == Constants.ORIENT_LANDSCAPE_RIGHT) {
            angle = 90;
        }

        if (angle != 0) {
            Matrix matrix = new Matrix();
            matrix.setRotate(angle);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        }

        return bitmap;
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
}
