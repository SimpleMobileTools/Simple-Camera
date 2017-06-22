package com.simplemobiletools.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.extensions.compensateDeviceRotation
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFile
import com.simplemobiletools.camera.extensions.getPreviewRotation
import com.simplemobiletools.commons.extensions.getFileDocument
import com.simplemobiletools.commons.extensions.needsStupidWritePermissions
import com.simplemobiletools.commons.extensions.toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.ref.WeakReference

class PhotoProcessor(val activity: MainActivity, val uri: Uri?, val currCameraId: Int, val deviceOrientation: Int) : AsyncTask<ByteArray, Void, String>() {
    companion object {
        private val TAG = PhotoProcessor::class.java.simpleName
        private var mActivity: WeakReference<MainActivity>? = null
    }

    init {
        mActivity = WeakReference(activity)
    }

    override fun doInBackground(vararg params: ByteArray): String {
        var fos: OutputStream? = null
        val path: String
        try {
            if (uri != null) {
                path = uri.path
            } else {
                path = activity.getOutputMediaFile(true)
            }

            if (path.isEmpty()) {
                return ""
            }

            val data = params[0]
            val photoFile = File(path)
            if (activity.needsStupidWritePermissions(path)) {
                if (activity.config.treeUri.isEmpty()) {
                    activity.runOnUiThread {
                        activity.toast(R.string.save_error_internal_storage)
                    }
                    activity.config.savePhotosFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                    return ""
                }
                var document = activity.getFileDocument(path)
                document = document?.createFile("", path.substring(path.lastIndexOf('/') + 1))
                fos = activity.contentResolver.openOutputStream(document?.uri)
            } else {
                fos = FileOutputStream(photoFile)
            }

            var image = BitmapFactory.decodeByteArray(data, 0, data.size)
            val exif = ExifInterface(photoFile.toString())

            val deviceRot = deviceOrientation.compensateDeviceRotation(currCameraId)
            val previewRot = activity.getPreviewRotation(currCameraId)
            val imageRot = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            image = rotate(image, imageRot + deviceRot + previewRot) ?: return ""
            image.compress(Bitmap.CompressFormat.JPEG, 80, fos)
            fos?.close()
            return photoFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "PhotoProcessor file not found: $e")
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "PhotoProcessor close ioexception $e")
            }
        }

        return ""
    }

    private fun rotate(bitmap: Bitmap, degree: Int): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.setRotate((degree % 360).toFloat())
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "PhotoProcessor rotate OutOfMemoryError $e")
            activity.runOnUiThread {
                activity.toast(R.string.photo_not_saved)
            }
        }
        return null
    }

    override fun onPostExecute(path: String) {
        super.onPostExecute(path)

        mActivity?.get()?.mediaSaved(path)
    }

    interface MediaSavedListener {
        fun mediaSaved(path: String)
    }
}
