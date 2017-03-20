package com.simplemobiletools.camera.dialogs

import android.hardware.Camera
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.activities.SimpleActivity
import com.simplemobiletools.commons.extensions.setupDialogStuff
import kotlinx.android.synthetic.main.dialog_change_resolution.view.*

class ChangeResolutionDialog(val activity: SimpleActivity, val isBackCamera: Boolean, val camera: Camera, val callback: () -> Unit) {
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_change_resolution, null).apply {
            change_resolution_photo_holder.setOnClickListener {

            }

            change_resolution_video_holder.setOnClickListener {

            }
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .create().apply {
            activity.setupDialogStuff(view, this, if (isBackCamera) R.string.back_camera else R.string.front_camera)
        }
    }
}
