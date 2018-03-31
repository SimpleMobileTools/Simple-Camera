package com.simplemobiletools.camera.dialogs

import android.hardware.Camera
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.SimpleActivity
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.extensions.getAspectRatio
import com.simplemobiletools.camera.helpers.Config
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.dialog_change_resolution.view.*

class ChangeResolutionDialog(val activity: SimpleActivity, val config: Config, val camera: Camera, val callback: () -> Unit) {
    var dialog: AlertDialog
    private val isBackCamera = activity.config.lastUsedCamera == Camera.CameraInfo.CAMERA_FACING_BACK

    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_change_resolution, null).apply {
            setupPhotoResolutionPicker(this)
            setupVideoResolutionPicker(this)
        }

        dialog = AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener { callback() }
                .create().apply {
                    activity.setupDialogStuff(view, this, if (isBackCamera) R.string.back_camera else R.string.front_camera)
                }
    }

    private fun setupPhotoResolutionPicker(view: View) {
        val items = getFormattedResolutions(camera.parameters.supportedPictureSizes)
        var selectionIndex = if (isBackCamera) config.backPhotoResIndex else config.frontPhotoResIndex
        selectionIndex = Math.max(selectionIndex, 0)

        view.change_resolution_photo_holder.setOnClickListener {
            RadioGroupDialog(activity, items, selectionIndex) {
                selectionIndex = it as Int
                view.change_resolution_photo.text = items[selectionIndex].title
                if (isBackCamera) {
                    config.backPhotoResIndex = it
                } else {
                    config.frontPhotoResIndex = it
                }
                dialog.dismiss()
            }
        }
        view.change_resolution_photo.text = items[selectionIndex].title
    }

    private fun setupVideoResolutionPicker(view: View) {
        val items = getFormattedResolutions(camera.parameters.supportedVideoSizes ?: camera.parameters.supportedPreviewSizes)
        var selectionIndex = if (isBackCamera) config.backVideoResIndex else config.frontVideoResIndex

        view.change_resolution_video_holder.setOnClickListener {
            RadioGroupDialog(activity, items, selectionIndex) {
                selectionIndex = it as Int
                view.change_resolution_video.text = items[selectionIndex].title
                if (isBackCamera) {
                    config.backVideoResIndex = it
                } else {
                    config.frontVideoResIndex = it
                }
                dialog.dismiss()
            }
        }
        view.change_resolution_video.text = items[selectionIndex].title
    }

    private fun getFormattedResolutions(resolutions: List<Camera.Size>): ArrayList<RadioItem> {
        val items = ArrayList<RadioItem>(resolutions.size)
        val sorted = resolutions.sortedByDescending { it.width * it.height }
        sorted.forEachIndexed { index, size ->
            val megapixels = String.format("%.1f", (size.width * size.height.toFloat()) / 1000000)
            val aspectRatio = size.getAspectRatio(activity)
            items.add(RadioItem(index, "${size.width} x ${size.height}  ($megapixels MP,  $aspectRatio)"))
        }
        return items
    }
}
