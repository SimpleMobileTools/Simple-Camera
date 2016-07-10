package com.simplemobiletools.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.view.Display;
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

        final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        if (isPhoto) {
            return mediaStorageDir.getPath() + File.separator + "IMG_" + timestamp + ".jpg";
        } else {
            return mediaStorageDir.getPath() + File.separator + "VID_" + timestamp + ".mp4";
        }
    }

    private static File getFolderName(Context context, boolean isPhoto) {
        final Resources res = context.getResources();
        String typeDirectory = res.getString(R.string.photo_directory);
        if (!isPhoto) {
            typeDirectory = res.getString(R.string.video_directory);
        }

        return new File(getMainDirectory(isPhoto), typeDirectory);
    }

    private static File getMainDirectory(boolean isPhoto) {
        String type = Environment.DIRECTORY_MOVIES;
        if (isPhoto) {
            type = Environment.DIRECTORY_PICTURES;
        }
        return Environment.getExternalStoragePublicDirectory(type);
    }

    public static String formatSeconds(int duration) {
        final StringBuilder sb = new StringBuilder(8);
        final int hours = duration / (60 * 60);
        final int minutes = (duration % (60 * 60)) / 60;
        final int seconds = ((duration % (60 * 60)) % 60);

        if (duration > 3600000) {
            sb.append(String.format(Locale.getDefault(), "%02d", hours)).append(":");
        }

        sb.append(String.format(Locale.getDefault(), "%02d", minutes));
        sb.append(":").append(String.format(Locale.getDefault(), "%02d", seconds));

        return sb.toString();
    }

    public static Point getScreenSize(Activity activity) {
        final Display display = activity.getWindowManager().getDefaultDisplay();
        final Point size = new Point();
        display.getSize(size);
        size.y += getNavBarHeight(activity.getResources());
        return size;
    }

    public static int getNavBarHeight(Resources res) {
        int id = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (id > 0 && hasNavBar(res)) {
            return res.getDimensionPixelSize(id);
        }

        return 0;
    }

    public static boolean hasNavBar(Resources res) {
        int id = res.getIdentifier("config_showNavigationBar", "bool", "android");
        return id > 0 && res.getBoolean(id);
    }

    public static boolean hasCameraPermission(Context cxt) {
        return ContextCompat.checkSelfPermission(cxt, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasStoragePermission(Context cxt) {
        return ContextCompat.checkSelfPermission(cxt, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAudioPermission(Context cxt) {
        return ContextCompat.checkSelfPermission(cxt, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
}
