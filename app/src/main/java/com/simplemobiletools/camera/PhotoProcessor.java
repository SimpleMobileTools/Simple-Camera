package com.simplemobiletools.camera;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class PhotoProcessor extends AsyncTask<byte[], Void, Void> {
    private static final String TAG = PhotoProcessor.class.getSimpleName();
    private static Context mContext;

    public PhotoProcessor(Context context) {
        mContext = context;
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
}
