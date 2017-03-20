package com.simplemobiletools.camera.dialogs

import android.hardware.Camera
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import com.simplemobiletools.camera.Preview.Companion.config
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.SimpleActivity
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.dialog_change_resolution.view.*

class ChangeResolutionDialog(val activity: SimpleActivity, val isBackCamera: Boolean, val camera: Camera, val callback: () -> Unit) {
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_change_resolution, null).apply {
            change_resolution_photo_holder.setOnClickListener { showPhotoResolutionPicker() }
            change_resolution_video_holder.setOnClickListener { showVideoResolutionPicker() }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
            activity.setupDialogStuff(view, this, if (isBackCamera) R.string.back_camera else R.string.front_camera)
        }
    }

    private fun showPhotoResolutionPicker() {
        val resolutions = camera.parameters.supportedPictureSizes.sortedByDescending { it.width * it.height }
        val items = ArrayList<RadioItem>(resolutions.size)
        resolutions.forEachIndexed { index, size ->
            items.add(RadioItem(index, "${size.width} x ${size.height}"))
        }

        RadioGroupDialog(activity, items, if (isBackCamera) config.backPhotoResIndex else config.frontPhotoResIndex) {
            if (isBackCamera) config.backPhotoResIndex else config.frontPhotoResIndex = it as Int
        }
    }

    private fun showVideoResolutionPicker() {
        val sizes = camera.parameters.supportedVideoSizes ?: camera.parameters.supportedPreviewSizes
        val resolutions = sizes.sortedByDescending { it.width * it.height }
        val items = ArrayList<RadioItem>(resolutions.size)
        resolutions.forEachIndexed { index, size ->
            items.add(RadioItem(index, "${size.width} x ${size.height}"))
        }

        RadioGroupDialog(activity, items, if (isBackCamera) config.backVideoResIndex else config.frontVideoResIndex) {
            if (isBackCamera) config.backVideoResIndex else config.frontVideoResIndex = it as Int
        }
    }
}
