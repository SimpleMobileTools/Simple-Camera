package com.simplemobiletools.camera.dialogs

import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.SimpleActivity
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.models.MySize
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.dialog_change_resolution.view.*

class ChangeResolutionDialog(val activity: SimpleActivity, val isFrontCamera: Boolean, val photoResolutions: ArrayList<MySize>,
                             val videoResolutions: ArrayList<MySize>, val openVideoResolutions: Boolean, val callback: () -> Unit) {
    private var dialog: AlertDialog
    private val config = activity.config

    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_change_resolution, null).apply {
            setupPhotoResolutionPicker(this)
            setupVideoResolutionPicker(this)
        }

        dialog = AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener { callback() }
                .create().apply {
                    activity.setupDialogStuff(view, this, if (isFrontCamera) R.string.front_camera else R.string.back_camera) {
                        if (openVideoResolutions) {
                            view.change_resolution_video_holder.performClick()
                        }
                    }
                }
    }

    private fun setupPhotoResolutionPicker(view: View) {
        val items = getFormattedResolutions(photoResolutions)
        var selectionIndex = if (isFrontCamera) config.frontPhotoResIndex else config.backPhotoResIndex
        selectionIndex = Math.max(selectionIndex, 0)

        view.change_resolution_photo_holder.setOnClickListener {
            RadioGroupDialog(activity, items, selectionIndex) {
                selectionIndex = it as Int
                view.change_resolution_photo.text = items[selectionIndex].title
                if (isFrontCamera) {
                    config.frontPhotoResIndex = it
                } else {
                    config.backPhotoResIndex = it
                }
                dialog.dismiss()
            }
        }
        view.change_resolution_photo.text = items.getOrNull(selectionIndex)?.title
    }

    private fun setupVideoResolutionPicker(view: View) {
        val items = getFormattedResolutions(videoResolutions)
        var selectionIndex = if (isFrontCamera) config.frontVideoResIndex else config.backVideoResIndex

        view.change_resolution_video_holder.setOnClickListener {
            RadioGroupDialog(activity, items, selectionIndex) {
                selectionIndex = it as Int
                view.change_resolution_video.text = items[selectionIndex].title
                if (isFrontCamera) {
                    config.frontVideoResIndex = it
                } else {
                    config.backVideoResIndex = it
                }
                dialog.dismiss()
            }
        }
        view.change_resolution_video.text = items.getOrNull(selectionIndex)?.title
    }

    private fun getFormattedResolutions(resolutions: List<MySize>): ArrayList<RadioItem> {
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
