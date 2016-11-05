package com.simplemobiletools.camera

import android.net.Uri
import android.os.AsyncTask
import android.util.Log

import com.simplemobiletools.camera.activities.MainActivity

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference

class PhotoProcessor(activity: MainActivity, val uri: Uri?) : AsyncTask<ByteArray, Void, String>() {
    companion object {
        private val TAG = PhotoProcessor::class.java.simpleName
        private var mActivity: WeakReference<MainActivity>? = null
    }

    init {
        mActivity = WeakReference(activity)
    }

    override fun doInBackground(vararg params: ByteArray): String {
        var fos: FileOutputStream? = null
        val path: String
        try {
            if (uri != null) {
                path = uri.path
            } else {
                path = Utils.getOutputMediaFile(mActivity?.get(), true)
            }

            if (path.isEmpty()) {
                return ""
            }

            val photoFile = File(path)
            val data = params[0]
            fos = FileOutputStream(photoFile)
            fos.write(data)
            fos.close()
            return photoFile.absolutePath
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "PhotoProcessor file not found: " + e.message)
        } catch (e: IOException) {
            Log.e(TAG, "PhotoProcessor ioexception " + e.message)
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "PhotoProcessor close ioexception " + e.message)
            }
        }

        return ""
    }

    override fun onPostExecute(path: String) {
        super.onPostExecute(path)
        val listener = mActivity?.get()
        listener?.mediaSaved(path)
    }

    interface MediaSavedListener {
        fun mediaSaved(path: String)
    }
}
