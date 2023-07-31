package com.simplemobiletools.camera.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.MainActivity
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFilePath
import com.simplemobiletools.commons.extensions.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream

class PhotoProcessor(
    val activity: MainActivity, val saveUri: Uri?, val deviceOrientation: Int, val previewRotation: Int, val isUsingFrontCamera: Boolean,
    val isThirdPartyIntent: Boolean
) :
    AsyncTask<ByteArray, Void, String>() {

    override fun doInBackground(vararg params: ByteArray): String {
        var fos: OutputStream? = null
        val path: String
        try {
            path = if (saveUri != null) {
                saveUri.path!!
            } else {
                activity.getOutputMediaFilePath(true)
            }

            if (path.isEmpty()) {
                return ""
            }

            val data = params[0]
            val tempFile = File.createTempFile("simple_temp_exif", "")
            val tempFOS = FileOutputStream(tempFile)
            tempFOS.use {
                tempFOS.write(data)
            }
            val tempExif = ExifInterface(tempFile.absolutePath)

            val photoFile = File(path)
            if (activity.needsStupidWritePermissions(path)) {
                if (!activity.hasProperStoredTreeUri(activity.isPathOnOTG(path))) {
                    activity.toast(R.string.save_error_internal_storage)
                    activity.config.savePhotosFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
                    return ""
                }

                var document = activity.getDocumentFile(path.getParentPath())
                document = document?.createFile("", path.substring(path.lastIndexOf('/') + 1)) ?: activity.getDocumentFile(path)
                if (document == null) {
                    activity.toast(R.string.save_error_internal_storage)
                    return ""
                }

                fos = activity.contentResolver.openOutputStream(document.uri)
            } else {
                fos = if (saveUri == null) {
                    FileOutputStream(photoFile)
                } else {
                    activity.contentResolver.openOutputStream(saveUri)
                }
            }

            val exif = try {
                ExifInterface(path)
            } catch (e: Exception) {
                null
            }

            val orient = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                ?: ExifInterface.ORIENTATION_UNDEFINED

            val imageRot = orient.degreesFromOrientation()

            val deviceRot = compensateDeviceRotation(deviceOrientation)
            var image = BitmapFactory.decodeByteArray(data, 0, data.size)
            val totalRotation = (imageRot + deviceRot + previewRotation) % 360

            if (isThirdPartyIntent) {
                // make sure the image itself is rotated at third party intents
                image = rotate(image, totalRotation)
            }

            if (isUsingFrontCamera) {
                if (activity.config.flipPhotos || deviceRot != 0) {
                    val matrix = Matrix()
                    val isPortrait = image.width < image.height
                    matrix.preScale(if (isPortrait) -1f else 1f, if (isPortrait) 1f else -1f)

                    try {
                        image = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, false)
                    } catch (e: OutOfMemoryError) {
                        activity.toast(com.simplemobiletools.commons.R.string.out_of_memory_error)
                    }
                }
            }

            try {
                if (fos != null) {
                    image.compress(Bitmap.CompressFormat.JPEG, activity.config.photoQuality, fos)
                }
                if (!isThirdPartyIntent) {
                    activity.saveImageRotation(path, totalRotation)
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
                return ""
            }

            if (activity.config.savePhotoMetadata && !isThirdPartyIntent) {
                val exifInterface = if (path.startsWith(activity.internalStoragePath)) {
                    ExifInterface(path)
                } else {
                    val documentFile = activity.getSomeDocumentFile(path)
                    if (documentFile != null) {
                        val parcelFileDescriptor = activity.contentResolver.openFileDescriptor(documentFile.uri, "rw")
                        val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
                        ExifInterface(fileDescriptor)
                    } else {
                        null
                    }
                }

                if (exifInterface != null) {
                    tempExif.copyTo(exifInterface)
                }
            }

            return photoFile.absolutePath
        } catch (e: FileNotFoundException) {
            activity.showErrorToast(e)
        } finally {
            fos?.close()
        }

        return ""
    }

    private fun rotate(bitmap: Bitmap, degree: Int): Bitmap? {
        if (degree == 0) {
            return bitmap
        }

        val width = bitmap.width
        val height = bitmap.height

        val matrix = Matrix()
        matrix.setRotate(degree.toFloat())

        try {
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        } catch (e: OutOfMemoryError) {
            activity.showErrorToast(e.toString())
        }
        return null
    }

    override fun onPostExecute(path: String) {
        super.onPostExecute(path)
        if (path.isNotEmpty()) {
            activity.mediaSaved(path)
        }
    }

    interface MediaSavedListener {
        fun mediaSaved(path: String)
    }
}
