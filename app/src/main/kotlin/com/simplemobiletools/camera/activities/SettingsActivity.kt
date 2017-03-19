package com.simplemobiletools.camera.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
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

        setupCustomizeColors()
        setupSavePhotosFolder()
        setupShowPreview()
        setupSound()
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

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
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

    private fun setupMaxPhotoResolution() {
        settings_max_photo_resolution.text = ""
        settings_max_photo_resolution_holder.setOnClickListener {

        }
    }

    private fun setupMaxVideoResolution() {
        settings_max_photo_resolution.text = ""
        settings_max_photo_resolution_holder.setOnClickListener {

        }
    }
}
