package com.simplemobiletools.camera.helpers

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getOutputMediaFile
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.createAndroidSAFFile
import com.simplemobiletools.commons.extensions.createDocumentUriFromRootTree
import com.simplemobiletools.commons.extensions.createDocumentUriUsingFirstParentTreeUri
import com.simplemobiletools.commons.extensions.createSAFFileSdk30
import com.simplemobiletools.commons.extensions.getAndroidSAFUri
import com.simplemobiletools.commons.extensions.getDocumentFile
import com.simplemobiletools.commons.extensions.getDoesFilePathExist
import com.simplemobiletools.commons.extensions.getFileOutputStreamSync
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getMimeType
import com.simplemobiletools.commons.extensions.hasProperStoredAndroidTreeUri
import com.simplemobiletools.commons.extensions.hasProperStoredFirstParentUri
import com.simplemobiletools.commons.extensions.hasProperStoredTreeUri
import com.simplemobiletools.commons.extensions.isAccessibleWithSAFSdk30
import com.simplemobiletools.commons.extensions.isRestrictedSAFOnlyRoot
import com.simplemobiletools.commons.extensions.needsStupidWritePermissions
import com.simplemobiletools.commons.extensions.showFileCreateError
import java.io.File
import java.io.OutputStream

class MediaOutputHelper(private val activity: BaseSimpleActivity) {

    companion object {
        private const val TAG = "MediaOutputHelper"
        private const val MODE = "rw"
    }

    private val mediaStorageDir = activity.config.savePhotosFolder

    fun getOutputStreamMediaOutput(): MediaOutput.OutputStreamMediaOutput? {
        val canWrite = activity.canWrite(mediaStorageDir)
        Log.i(TAG, "getMediaOutput: canWrite=${canWrite}")
        return if (canWrite) {
            val path = activity.getOutputMediaFile(true)
            val uri = activity.getUri(path)
            uri?.let {
                activity.getFileOutputStreamSync(path, path.getMimeType())?.let {
                    MediaOutput.OutputStreamMediaOutput(it, uri)
                }
            }
        } else {
            null
        }.also {
            Log.i(TAG, "output stream: $it")
        }
    }

    fun getFileDescriptorMediaOutput(): MediaOutput.FileDescriptorMediaOutput? {
        val canWrite = activity.canWrite(mediaStorageDir)
        Log.i(TAG, "getMediaOutput: canWrite=${canWrite}")
        return if (canWrite) {
            val path = activity.getOutputMediaFile(false)
            val uri = activity.getUri(path)
            uri?.let {
                activity.getFileDescriptorSync(path, path.getMimeType())?.let {
                    MediaOutput.FileDescriptorMediaOutput(it, uri)
                }
            }
        } else {
            null
        }.also {
            Log.i(TAG, "descriptor: $it")
        }
    }

    private fun BaseSimpleActivity.canWrite(path: String): Boolean {
        return when {
            isRestrictedSAFOnlyRoot(path) -> hasProperStoredAndroidTreeUri(path)
            needsStupidWritePermissions(path) -> hasProperStoredTreeUri(false)
            isAccessibleWithSAFSdk30(path) -> hasProperStoredFirstParentUri(path)
            else -> File(path).canWrite()
        }
    }

    private fun BaseSimpleActivity.getUri(path: String): Uri? {
        val targetFile = File(path)
        return when {
            isRestrictedSAFOnlyRoot(path) -> {
                getAndroidSAFUri(path)
            }
            needsStupidWritePermissions(path) -> {
                val parentFile = targetFile.parentFile ?: return null
                val documentFile =
                    if (getDoesFilePathExist(parentFile.absolutePath ?: return null)) {
                        getDocumentFile(parentFile.path)
                    } else {
                        val parentDocumentFile = parentFile.parent?.let { getDocumentFile(it) }
                        parentDocumentFile?.createDirectory(parentFile.name) ?: getDocumentFile(parentFile.absolutePath)
                    }

                if (documentFile == null) {
                    return Uri.fromFile(targetFile)
                }

                try {
                    if (getDoesFilePathExist(path)) {
                        createDocumentUriFromRootTree(path)
                    } else {
                        documentFile.createFile(path.getMimeType(), path.getFilenameFromPath())!!.uri
                    }
                } catch (e: Exception) {
                    null
                }
            }
            isAccessibleWithSAFSdk30(path) -> {
                try {
                    createDocumentUriUsingFirstParentTreeUri(path)
                } catch (e: Exception) {
                    null
                } ?: Uri.fromFile(targetFile)
            }
            else -> return Uri.fromFile(targetFile)
        }
    }

    private fun BaseSimpleActivity.getFileDescriptorSync(path: String, mimeType: String): ParcelFileDescriptor? {
        val targetFile = File(path)

        return when {
            isRestrictedSAFOnlyRoot(path) -> {
                val uri = getAndroidSAFUri(path)
                if (!getDoesFilePathExist(path)) {
                    createAndroidSAFFile(path)
                }
                applicationContext.contentResolver.openFileDescriptor(uri, MODE)
            }
            needsStupidWritePermissions(path) -> {
                val parentFile = targetFile.parentFile ?: return null
                val documentFile =
                    if (getDoesFilePathExist(parentFile.absolutePath ?: return null)) {
                        getDocumentFile(parentFile.path)
                    } else {
                        val parentDocumentFile = parentFile.parent?.let { getDocumentFile(it) }
                        parentDocumentFile?.createDirectory(parentFile.name) ?: getDocumentFile(parentFile.absolutePath)
                    }


                if (documentFile == null) {
                    val casualOutputStream = createCasualFileDescriptor(targetFile)
                    return if (casualOutputStream == null) {
                        showFileCreateError(parentFile.path)
                        null
                    } else {
                        casualOutputStream
                    }
                }

                try {
                    val uri = if (getDoesFilePathExist(path)) {
                        createDocumentUriFromRootTree(path)
                    } else {
                        documentFile.createFile(mimeType, path.getFilenameFromPath())!!.uri
                    }
                    applicationContext.contentResolver.openFileDescriptor(uri, MODE)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            isAccessibleWithSAFSdk30(path) -> {
                try {
                    val uri = createDocumentUriUsingFirstParentTreeUri(path)
                    if (!getDoesFilePathExist(path)) {
                        createSAFFileSdk30(path)
                    }
                    applicationContext.contentResolver.openFileDescriptor(uri, MODE)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                } ?: createCasualFileDescriptor(targetFile)
            }
            else -> return createCasualFileDescriptor(targetFile)
        }
    }

    private fun BaseSimpleActivity.createCasualFileDescriptor(targetFile: File): ParcelFileDescriptor? {
        if (targetFile.parentFile?.exists() == false) {
            targetFile.parentFile?.mkdirs()
        }

        return try {
            contentResolver.openFileDescriptor(Uri.fromFile(targetFile), MODE)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    sealed class MediaOutput(
        open val uri: Uri,
    ) {
        data class OutputStreamMediaOutput(
            val outputStream: OutputStream,
            override val uri: Uri,
        ) : MediaOutput(uri)

        data class FileDescriptorMediaOutput(
            val fileDescriptor: ParcelFileDescriptor,
            override val uri: Uri,
        ) : MediaOutput(uri)
    }
}
