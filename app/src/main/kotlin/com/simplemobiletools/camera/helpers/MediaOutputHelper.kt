package com.simplemobiletools.camera.helpers

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFileName
import com.simplemobiletools.camera.extensions.getOutputMediaFilePath
import com.simplemobiletools.camera.extensions.getRandomMediaName
import com.simplemobiletools.camera.models.MediaOutput
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import java.io.File
import java.io.OutputStream

class MediaOutputHelper(
    private val activity: BaseSimpleActivity,
    private val errorHandler: CameraErrorHandler,
    private val outputUri: Uri?,
    private val is3rdPartyIntent: Boolean
) {

    companion object {
        private const val MODE = "rw"
        private const val EXTERNAL_VOLUME = "external"
        private const val IMAGE_MIME_TYPE = "image/jpeg"
        private const val VIDEO_MIME_TYPE = "video/mp4"
    }

    private var config = activity.config
    private val contentResolver = activity.contentResolver

    fun getImageMediaOutput(): MediaOutput.ImageCaptureOutput {
        return try {
            if (is3rdPartyIntent) {
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
        } catch (e: Exception) {
            errorHandler.showSaveToInternalStorage()
            getMediaStoreOutput(isPhoto = true)
        }
    }

    fun getVideoMediaOutput(): MediaOutput.VideoCaptureOutput {
        return try {
            if (is3rdPartyIntent) {
                if (outputUri != null) {
                    if (isOreoPlus()) {
                        val fileDescriptor = openFileDescriptor(outputUri)
                        if (fileDescriptor != null) {
                            MediaOutput.FileDescriptorMediaOutput(fileDescriptor, outputUri)
                        } else {
                            errorHandler.showSaveToInternalStorage()
                            getMediaStoreOutput(isPhoto = false)
                        }
                    } else {
                        val path = activity.getRealPathFromURI(outputUri)
                        if (path != null) {
                            MediaOutput.FileMediaOutput(File(path), outputUri)
                        } else {
                            errorHandler.showSaveToInternalStorage()
                            getMediaStoreOutput(isPhoto = false)
                        }
                    }
                } else {
                    getMediaStoreOutput(isPhoto = false)
                }
            } else {
                if (isOreoPlus()) {
                    getFileDescriptorMediaOutput() ?: getMediaStoreOutput(isPhoto = false)
                } else {
                    getFileMediaOutput() ?: getMediaStoreOutput(isPhoto = false)
                }
            }
        } catch (e: Exception) {
            errorHandler.showSaveToInternalStorage()
            getMediaStoreOutput(isPhoto = false)
        }
    }

    private fun getMediaStoreOutput(isPhoto: Boolean): MediaOutput.MediaStoreOutput {
        val contentValues = getContentValues(isPhoto)
        val contentUri = if (isPhoto) {
            MediaStore.Images.Media.getContentUri(EXTERNAL_VOLUME)
        } else {
            MediaStore.Video.Media.getContentUri(EXTERNAL_VOLUME)
        }
        return MediaOutput.MediaStoreOutput(contentValues, contentUri)
    }

    @Suppress("DEPRECATION")
    private fun getContentValues(isPhoto: Boolean): ContentValues {
        val mimeType = if (isPhoto) IMAGE_MIME_TYPE else VIDEO_MIME_TYPE
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, getRandomMediaName(isPhoto))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (isQPlus()) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            } else {
                put(MediaStore.MediaColumns.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString())
            }
        }
    }

    private fun getOutputStreamMediaOutput(): MediaOutput.OutputStreamMediaOutput? {
        var mediaOutput: MediaOutput.OutputStreamMediaOutput? = null
        val canWrite = canWriteToFilePath(config.savePhotosFolder)
        if (canWrite) {
            val path = activity.getOutputMediaFilePath(true)
            val uri = getUriForFilePath(path)
            val outputStream = activity.getFileOutputStreamSync(path, path.getMimeType())
            if (uri != null && outputStream != null) {
                mediaOutput = MediaOutput.OutputStreamMediaOutput(outputStream, uri)
            }
        }
        return mediaOutput
    }

    private fun openOutputStream(uri: Uri): OutputStream? {
        return try {
            contentResolver.openOutputStream(uri)
        } catch (e: Exception) {
            activity.showErrorToast(e)
            null
        }
    }

    private fun getFileDescriptorMediaOutput(): MediaOutput.FileDescriptorMediaOutput? {
        var mediaOutput: MediaOutput.FileDescriptorMediaOutput? = null
        val canWrite = canWriteToFilePath(config.savePhotosFolder)
        if (canWrite) {
            val parentUri = getUriForFilePath(config.savePhotosFolder) ?: return null
            val videoFileName = activity.getOutputMediaFileName(false)
            val documentUri = DocumentsContract.createDocument(
                contentResolver,
                parentUri,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                videoFileName
            ) ?: return null
            val fileDescriptor = contentResolver.openFileDescriptor(documentUri, MODE) ?: return null
            mediaOutput = MediaOutput.FileDescriptorMediaOutput(fileDescriptor, documentUri)
        }
        return mediaOutput
    }

    private fun getFileMediaOutput(): MediaOutput.FileMediaOutput? {
        var mediaOutput: MediaOutput.FileMediaOutput? = null
        val canWrite = canWriteToFilePath(config.savePhotosFolder)
        if (canWrite) {
            val path = activity.getOutputMediaFilePath(false)
            val uri = getUriForFilePath(path)
            if (uri != null) {
                mediaOutput = MediaOutput.FileMediaOutput(File(path), uri)
            }
        }
        return mediaOutput
    }

    private fun openFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            contentResolver.openFileDescriptor(uri, MODE)
        } catch (e: Exception) {
            activity.showErrorToast(e)
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
                        null
                    }
                }
            }
            activity.isAccessibleWithSAFSdk30(path) -> {
                try {
                    activity.createDocumentUriUsingFirstParentTreeUri(path)
                } catch (e: Exception) {
                    null
                } ?: Uri.fromFile(targetFile)
            }
            else -> return Uri.fromFile(targetFile)
        }
    }
}
