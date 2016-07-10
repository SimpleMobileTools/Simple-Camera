package com.simplemobiletools.camera;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.simplemobiletools.camera.activities.MainActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class PhotoProcessor extends AsyncTask<byte[], Void, String> {
    private static final String TAG = PhotoProcessor.class.getSimpleName();
    private static WeakReference<MainActivity> mActivity;
    private static Uri mUri;

    public PhotoProcessor(MainActivity activity, Uri uri) {
        mActivity = new WeakReference<>(activity);
        mUri = uri;
    }

    @Override
    protected String doInBackground(byte[]... params) {
        FileOutputStream fos = null;
        String path;
        try {
            if (mUri != null) {
                path = mUri.getPath();
            } else {
                path = Utils.getOutputMediaFile(mActivity.get(), true);
            }

            if (path.isEmpty()) {
                return "";
            }

            final File photoFile = new File(path);
            final byte[] data = params[0];
            fos = new FileOutputStream(photoFile);
            fos.write(data);
            fos.close();
            return photoFile.getAbsolutePath();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "PhotoProcessor file not found: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "PhotoProcessor ioexception " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "PhotoProcessor close ioexception " + e.getMessage());
                }
            }
        }

        return "";
    }

    @Override
    protected void onPostExecute(String path) {
        super.onPostExecute(path);
        final MediaSavedListener listener = mActivity.get();
        if (listener != null) {
            listener.mediaSaved(path);
        }
    }

    public interface MediaSavedListener {
        void mediaSaved(String path);
    }
}
