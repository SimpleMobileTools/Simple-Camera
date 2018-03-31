package com.simplemobiletools.camera.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.humanizePath
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.LICENSE_GLIDE
import com.simplemobiletools.commons.helpers.LICENSE_LEAK_CANARY
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupCustomizeColors()
        setupUseEnglish()
        setupAvoidWhatsNew()
        setupShowPreview()
        setupSound()
        setupFocusBeforeCapture()
        setupVolumeButtonsAsShutter()
        setupTurnFlashOffAtStartup()
        setupFlipPhotos()
        setupKeepSettingsVisible()
        setupAlwaysOpenBackCamera()
        setupSavePhotoMetadata()
        setupSavePhotosFolder()
        setupPhotoQuality()
        updateTextColors(settings_holder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.about -> launchAbout()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    private fun setupAvoidWhatsNew() {
        settings_avoid_whats_new.isChecked = config.avoidWhatsNew
        settings_avoid_whats_new_holder.setOnClickListener {
            settings_avoid_whats_new.toggle()
            config.avoidWhatsNew = settings_avoid_whats_new.isChecked
        }
    }

    private fun launchAbout() {
        val faqItems = arrayListOf(
                FAQItem(R.string.faq_1_title, R.string.faq_1_text),
                FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons)
        )

        startAboutActivity(R.string.app_name, LICENSE_GLIDE or LICENSE_LEAK_CANARY, BuildConfig.VERSION_NAME, faqItems)
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

    private fun setupKeepSettingsVisible() {
        settings_keep_settings_visible.isChecked = config.keepSettingsVisible
        settings_keep_settings_visible_holder.setOnClickListener {
            settings_keep_settings_visible.toggle()
            config.keepSettingsVisible = settings_keep_settings_visible.isChecked
        }
    }

    private fun setupAlwaysOpenBackCamera() {
        settings_always_open_back_camera.isChecked = config.alwaysOpenBackCamera
        settings_always_open_back_camera_holder.setOnClickListener {
            settings_always_open_back_camera.toggle()
            config.alwaysOpenBackCamera = settings_always_open_back_camera.isChecked
        }
    }

    private fun setupSavePhotoMetadata() {
        settings_save_photo_metadata.isChecked = config.savePhotoMetadata
        settings_save_photo_metadata_holder.setOnClickListener {
            settings_save_photo_metadata.toggle()
            config.savePhotoMetadata = settings_save_photo_metadata.isChecked
        }
    }

    private fun setupSavePhotosFolder() {
        settings_save_photos.text = getLastPart(config.savePhotosFolder)
        settings_save_photos_holder.setOnClickListener {
            FilePickerDialog(this, config.savePhotosFolder, false, showFAB = true) {
                handleSAFDialog(it) {
                    config.savePhotosFolder = it
                    settings_save_photos.text = getLastPart(config.savePhotosFolder)
                }
            }
        }
    }

    private fun setupPhotoQuality() {
        settings_photo_quality.text = "${config.photoQuality}%"
        settings_photo_quality_holder.setOnClickListener {
            val items = arrayListOf(
                    RadioItem(50, "50%"),
                    RadioItem(55, "55%"),
                    RadioItem(60, "60%"),
                    RadioItem(65, "65%"),
                    RadioItem(70, "70%"),
                    RadioItem(75, "75%"),
                    RadioItem(80, "80%"),
                    RadioItem(85, "85%"),
                    RadioItem(90, "90%"),
                    RadioItem(95, "95%"),
                    RadioItem(100, "100%"))

            RadioGroupDialog(this@SettingsActivity, items, config.photoQuality) {
                config.photoQuality = it as Int
                settings_photo_quality.text = "${config.photoQuality}%"
            }
        }
    }
}
