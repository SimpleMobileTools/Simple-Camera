package com.simplemobiletools.camera.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.camera.*
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.humanizePath
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.LICENSE_GLIDE
import com.simplemobiletools.commons.helpers.LICENSE_KOTLIN
import kotlinx.android.synthetic.main.activity_settings.*
import java.io.File

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupSavePhotosFolder()
        setupShowPreview()
        setupSound()
        setupForceRatio()
        setupMaxPhotoResolution()
        setupMaxVideoResolution()
        updateTextColors(settings_holder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about -> startAboutActivity(R.string.app_name, LICENSE_KOTLIN or LICENSE_GLIDE, BuildConfig.VERSION_NAME)
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupSavePhotosFolder() {
        settings_save_photos.text = getLastPart(config.savePhotosFolder)
        settings_save_photos_holder.setOnClickListener {
            FilePickerDialog(this, config.savePhotosFolder, false) {
                handleSAFDialog(File(it)) {
                    config.savePhotosFolder = it
                    settings_save_photos.text = getLastPart(config.savePhotosFolder)
                }
            }
        }
    }

    private fun getLastPart(path: String): String {
        val humanized = humanizePath(path)
        return humanized.substringAfterLast("/", humanized)
    }

    private fun setupShowPreview() {
        settings_show_preview.isChecked = config.isShowPreviewEnabled
        settings_show_preview_holder.setOnClickListener {
            settings_show_preview.toggle()
            config.isShowPreviewEnabled = settings_show_preview.isChecked
        }
    }

    private fun setupSound() {
        settings_sound.isChecked = config.isSoundEnabled
        settings_sound_holder.setOnClickListener {
            settings_sound.toggle()
            config.isSoundEnabled = settings_sound.isChecked
        }
    }

    private fun setupForceRatio() {
        settings_force_ratio.isChecked = config.forceRatioEnabled
        settings_force_ratio_holder.setOnClickListener {
            settings_force_ratio.toggle()
            config.forceRatioEnabled = settings_force_ratio.isChecked
        }
    }

    private fun setupMaxPhotoResolution() {
        /*settings_max_photo_resolution.setSelection(getMaxPhotoSelection())
        settings_max_photo_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.maxPhotoResolution = getMaxPhotoPx(settings_max_photo_resolution.selectedItemPosition)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }*/
    }

    private fun getMaxPhotoSelection(): Int {
        val maxRes = config.maxPhotoResolution
        return when (maxRes) {
            TWO_MPX -> 0
            FIVE_MPX -> 1
            EIGHT_MPX -> 2
            else -> 3
        }
    }

    private fun getMaxPhotoPx(index: Int): Int {
        return when (index) {
            0 -> TWO_MPX
            1 -> FIVE_MPX
            2 -> EIGHT_MPX
            else -> -1
        }
    }

    private fun setupMaxVideoResolution() {
        /*settings_max_video_resolution.setSelection(getMaxVideoSelection())
        settings_max_video_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.maxVideoResolution = getMaxVideoPx(settings_max_video_resolution.selectedItemPosition)
            }
        }*/
    }

    private fun getMaxVideoSelection(): Int {
        return when (config.maxVideoResolution) {
            P480 -> 0
            P720 -> 1
            P1080 -> 2
            else -> 3
        }
    }

    private fun getMaxVideoPx(index: Int): Int {
        return when (index) {
            0 -> P480
            1 -> P720
            2 -> P1080
            else -> -1
        }
    }
}
