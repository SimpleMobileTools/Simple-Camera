package com.simplemobiletools.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PhotoProcessor extends AsyncTask<byte[], Void, Void> {
    private static final String TAG = PhotoProcessor.class.getSimpleName();
    private static Context context;

    public PhotoProcessor(Context cxt) {
        context = cxt;
    }

    @Override
    protected Void doInBackground(byte[]... params) {
        final File photoFile = getOutputMediaFile();
        if (photoFile == null) {
            return null;
        }

        try {
            final byte[] data = params[0];
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

        return null;
    }

    private static File getOutputMediaFile() {
        final String appName = context.getResources().getString(R.string.app_name);
        final File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }

    private Bitmap setBitmapRotation(Bitmap bitmap, String path) throws IOException {
        final ExifInterface exif = new ExifInterface(path);
        final String orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
        if (orientation.equalsIgnoreCase("6")) {
            bitmap = rotateImage(bitmap, 90);
        } else if (orientation.equalsIgnoreCase("8")) {
            bitmap = rotateImage(bitmap, 270);
        } else if (orientation.equalsIgnoreCase("3")) {
            bitmap = rotateImage(bitmap, 180);
        } else if (orientation.equalsIgnoreCase("0")) {
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
}
