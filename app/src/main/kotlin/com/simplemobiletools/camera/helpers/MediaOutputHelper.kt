package com.simplemobiletools.camera.helpers

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFile
import com.simplemobiletools.camera.extensions.getRandomMediaName
import com.simplemobiletools.camera.models.MediaOutput
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import java.io.File
import java.io.OutputStream

class MediaOutputHelper(
    private val activity: BaseSimpleActivity,
    private val errorHandler: CameraErrorHandler,
    private val outputUri: Uri?,
    private val is3rdPartyIntent: Boolean,
) {

    companion object {
        private const val TAG = "MediaOutputHelper"
        private const val MODE = "rw"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val VIDEO_MIME_TYPE = "video/mp4"
    }

    private val mediaStorageDir = activity.config.savePhotosFolder
    private val contentResolver = activity.contentResolver

    fun getImageMediaOutput(): MediaOutput {
        return if (is3rdPartyIntent) {
            if (outputUri != null) {
                val outputStream = openOutputStream(outputUri)
                if (outputStream != null) {
                    MediaOutput.OutputStreamMediaOutput(outputStream, outputUri)
                } else {
                    errorHandler.showSaveToInternalStorage()
                    getMediaStoreOutput(isPhoto = true)
                }
            } else {
                MediaOutput.BitmapOutput
            }
        } else {
            getOutputStreamMediaOutput() ?: getMediaStoreOutput(isPhoto = true)
        }
    }

    fun getVideoMediaOutput(): MediaOutput {
        return if (is3rdPartyIntent) {
            if (outputUri != null) {
                val fileDescriptor = openFileDescriptor(outputUri)
                if (fileDescriptor != null) {
                    MediaOutput.FileDescriptorMediaOutput(fileDescriptor, outputUri)
                } else {
                    errorHandler.showSaveToInternalStorage()
                    getMediaStoreOutput(isPhoto = false)
                }
            } else {
                getMediaStoreOutput(isPhoto = false)
            }
        } else {
            getFileDescriptorMediaOutput() ?: getMediaStoreOutput(isPhoto = false)
        }
    }

    private fun getMediaStoreOutput(isPhoto: Boolean): MediaOutput.MediaStoreOutput {
        val contentValues = getContentValues(isPhoto)
        val contentUri = if (isPhoto) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        return MediaOutput.MediaStoreOutput(contentValues, contentUri)
    }

    private fun getContentValues(isPhoto: Boolean): ContentValues {
        val mimeType = if (isPhoto) IMAGE_MIME_TYPE else VIDEO_MIME_TYPE
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getRandomMediaName(isPhoto))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
    }

    private fun getOutputStreamMediaOutput(): MediaOutput.OutputStreamMediaOutput? {
        var mediaOutput: MediaOutput.OutputStreamMediaOutput? = null
        val canWrite = canWriteToFilePath(mediaStorageDir)
        Log.i(TAG, "getMediaOutput: canWrite=${canWrite}")
        if (canWrite) {
            val path = activity.getOutputMediaFile(true)
            val uri = getUriForFilePath(path)
            val outputStream = activity.getFileOutputStreamSync(path, path.getMimeType())
            if (uri != null && outputStream != null) {
                mediaOutput = MediaOutput.OutputStreamMediaOutput(outputStream, uri)
            }
        }
        Log.i(TAG, "OutputStreamMediaOutput: $mediaOutput")
        return mediaOutput
    }

    private fun openOutputStream(uri: Uri): OutputStream? {
        return try {
            Log.i(TAG, "uri: $uri")
            contentResolver.openOutputStream(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileDescriptorMediaOutput(): MediaOutput.FileDescriptorMediaOutput? {
        var mediaOutput: MediaOutput.FileDescriptorMediaOutput? = null
        val canWrite = canWriteToFilePath(mediaStorageDir)
        Log.i(TAG, "getMediaOutput: canWrite=${canWrite}")
        if (canWrite) {
            val path = activity.getOutputMediaFile(false)
            val uri = getUriForFilePath(path)
            if (uri != null) {
                val fileDescriptor = contentResolver.openFileDescriptor(uri, MODE)
                if (fileDescriptor != null) {
                    mediaOutput = MediaOutput.FileDescriptorMediaOutput(fileDescriptor, uri)
                }
            }
        }
        Log.i(TAG, "FileDescriptorMediaOutput: $mediaOutput")
        return mediaOutput
    }

    private fun openFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            Log.i(TAG, "uri: $uri")
            contentResolver.openFileDescriptor(uri, MODE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun canWriteToFilePath(path: String): Boolean {
        return when {
            activity.isRestrictedSAFOnlyRoot(path) -> activity.hasProperStoredAndroidTreeUri(path)
            activity.needsStupidWritePermissions(path) -> activity.hasProperStoredTreeUri(false)
            activity.isAccessibleWithSAFSdk30(path) -> activity.hasProperStoredFirstParentUri(path)
            else -> File(path).canWrite()
        }
    }

    private fun getUriForFilePath(path: String): Uri? {
        val targetFile = File(path)
        return when {
            activity.isRestrictedSAFOnlyRoot(path) -> activity.getAndroidSAFUri(path)
            activity.needsStupidWritePermissions(path) -> {
                targetFile.parentFile?.let { parentFile ->
                    val documentFile =
                        if (activity.getDoesFilePathExist(parentFile.absolutePath)) {
                            activity.getDocumentFile(parentFile.path)
                        } else {
                            val parentDocumentFile = parentFile.parent?.let {
                                activity.getDocumentFile(it)
                            }
                            parentDocumentFile?.createDirectory(parentFile.name)
                                ?: activity.getDocumentFile(parentFile.absolutePath)
                        }

                    if (documentFile == null) {
                        return Uri.fromFile(targetFile)
                    }

                    try {
                        if (activity.getDoesFilePathExist(path)) {
                            activity.createDocumentUriFromRootTree(path)
                        } else {
                            documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())?.uri
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }
            activity.isAccessibleWithSAFSdk30(path) -> {
                try {
                    activity.createDocumentUriUsingFirstParentTreeUri(path)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } ?: Uri.fromFile(targetFile)
            }
            else -> return Uri.fromFile(targetFile)
        }
    }
}
