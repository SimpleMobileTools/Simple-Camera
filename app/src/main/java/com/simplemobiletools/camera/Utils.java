package com.simplemobiletools.camera;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Camera;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Utils {
    public static Camera.CameraInfo getCameraInfo(int cameraId) {
        final Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info;
    }

    public static void showToast(Context context, int resId) {
        Toast.makeText(context, context.getResources().getString(resId), Toast.LENGTH_SHORT).show();
    }

    public static boolean hasFlash(Camera camera) {
        if (camera == null) {
            return false;
        }

        final Camera.Parameters parameters = camera.getParameters();

        if (parameters.getFlashMode() == null) {
            return false;
        }

        final List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        if (supportedFlashModes == null || supportedFlashModes.isEmpty() ||
                supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF)) {
            return false;
        }

        return true;
    }

    public static String getOutputMediaFile(Context context, boolean isPhoto) {
        final File mediaStorageDir = getFolderName(context, isPhoto);

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return "";
            }
        }

        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        if (isPhoto) {
            return mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg";
        } else {
            return mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4";
        }
    }

    private static File getFolderName(Context context, boolean isPhoto) {
        final Resources res = context.getResources();
        final String appName = res.getString(R.string.app_name);
        final String sharedPath = new File(Environment.getExternalStorageDirectory(), appName).getAbsolutePath();
        String typeDirectory = res.getString(R.string.photo_directory);
        if (!isPhoto) {
            typeDirectory = res.getString(R.string.video_directory);
        }

        return new File(sharedPath, typeDirectory);
    }

    public static void scanFile(String path, Context context) {
        final String[] paths = {path};
        MediaScannerConnection.scanFile(context, paths, null, null);
    }
}
