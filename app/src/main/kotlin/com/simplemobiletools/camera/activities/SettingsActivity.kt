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
        setupFocusBeforeCapture()
        setupVolumeButtonsAsShutter()
        setupTurnFlashOffAtStartup()
        setupFlipPhotos()
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
            FilePickerDialog(this, config.savePhotosFolder, false, showFAB = true) {
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

    private fun setupFocusBeforeCapture() {
        settings_focus_before_capture.isChecked = config.focusBeforeCapture
        settings_focus_before_capture_holder.setOnClickListener {
            settings_focus_before_capture.toggle()
            config.focusBeforeCapture = settings_focus_before_capture.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() {
        settings_volume_buttons_as_shutter.isChecked = config.volumeButtonsAsShutter
        settings_volume_buttons_as_shutter_holder.setOnClickListener {
            settings_volume_buttons_as_shutter.toggle()
            config.volumeButtonsAsShutter = settings_volume_buttons_as_shutter.isChecked
        }
    }

    private fun setupTurnFlashOffAtStartup() {
        settings_turn_flash_off_at_startup.isChecked = config.turnFlashOffAtStartup
        settings_turn_flash_off_at_startup_holder.setOnClickListener {
            settings_turn_flash_off_at_startup.toggle()
            config.turnFlashOffAtStartup = settings_turn_flash_off_at_startup.isChecked
        }
    }

    private fun setupFlipPhotos() {
        settings_flip_photos.isChecked = config.flipPhotos
        settings_flip_photos_holder.setOnClickListener {
            settings_flip_photos.toggle()
            config.flipPhotos = settings_flip_photos.isChecked
        }
    }
}
