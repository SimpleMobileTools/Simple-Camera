package com.simplemobiletools.camera.activities

import android.annotation.SuppressLint
import android.os.Bundle
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.models.CaptureMode
import com.simplemobiletools.commons.dialogs.FeatureLockedDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.LICENSE_GLIDE
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupOptionsMenu()
        refreshMenuItems()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupSound()
        setupVolumeButtonsAsShutter()
        setupFlipPhotos()
        setupSavePhotoMetadata()
        setupSavePhotosFolder()
        setupPhotoQuality()
        setupCaptureMode()
        updateTextColors(settings_holder)

        val properPrimaryColor = getProperPrimaryColor()
        arrayListOf(
            settings_color_customization_label,
            settings_general_settings_label,
            settings_shutter_label,
            settings_saving_label
        ).forEach {
            it.setTextColor(properPrimaryColor)
        }

        arrayOf(
            settings_color_customization_holder,
            settings_general_settings_holder,
            settings_shutter_holder,
            settings_saving_holder
        ).forEach {
            it.background.applyColorFilter(getProperBackgroundColor().getContrastColor())
        }
    }

    fun refreshMenuItems() {
        settings_toolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        settings_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())

        // make sure the corners at ripple fit the stroke rounded corners
        if (settings_purchase_thank_you_holder.isGone()) {
            settings_use_english_holder.background = resources.getDrawable(R.drawable.ripple_all_corners, theme)
            settings_language_holder.background = resources.getDrawable(R.drawable.ripple_all_corners, theme)
        }

        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_label.text = getCustomizeColorsString()
        settings_customize_colors_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())

        listOf(settings_general_settings_holder, settings_general_settings_label).forEach {
            it.beGoneIf(settings_use_english_holder.isGone() && settings_purchase_thank_you_holder.isGone() && settings_language_holder.isGone())
        }

        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun getLastPart(path: String): String {
        val humanized = humanizePath(path)
        return humanized.substringAfterLast("/", humanized)
    }

    private fun setupSound() {
        settings_sound.isChecked = config.isSoundEnabled
        settings_sound_holder.setOnClickListener {
            settings_sound.toggle()
            config.isSoundEnabled = settings_sound.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() {
        settings_volume_buttons_as_shutter.isChecked = config.volumeButtonsAsShutter
        settings_volume_buttons_as_shutter_holder.setOnClickListener {
            settings_volume_buttons_as_shutter.toggle()
            config.volumeButtonsAsShutter = settings_volume_buttons_as_shutter.isChecked
        }
    }

    private fun setupFlipPhotos() {
        settings_flip_photos.isChecked = config.flipPhotos
        settings_flip_photos_holder.setOnClickListener {
            settings_flip_photos.toggle()
            config.flipPhotos = settings_flip_photos.isChecked
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
        settings_save_photos_label.text = addLockedLabelIfNeeded(R.string.save_photos)
        settings_save_photos.text = getLastPart(config.savePhotosFolder)
        settings_save_photos_holder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                FilePickerDialog(this, config.savePhotosFolder, false, showFAB = true) {
                    val path = it
                    handleSAFDialog(it) { success ->
                        if (success) {
                            config.savePhotosFolder = path
                            settings_save_photos.text = getLastPart(config.savePhotosFolder)
                        }
                    }
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupPhotoQuality() {
        updatePhotoQuality(config.photoQuality)
        settings_photo_quality_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(100, "100%"),
                RadioItem(95, "95%"),
                RadioItem(90, "90%"),
                RadioItem(85, "85%"),
                RadioItem(80, "80%"),
                RadioItem(75, "75%"),
                RadioItem(70, "70%"),
                RadioItem(65, "65%"),
                RadioItem(60, "60%"),
                RadioItem(55, "55%"),
                RadioItem(50, "50%")
            )

            RadioGroupDialog(this@SettingsActivity, items, config.photoQuality) {
                config.photoQuality = it as Int
                updatePhotoQuality(it)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePhotoQuality(quality: Int) {
        settings_photo_quality.text = "$quality%"
    }

    private fun setupCaptureMode() {
        updateCaptureMode(config.captureMode)
        settings_capture_mode_holder.setOnClickListener {
            val items = CaptureMode.values().mapIndexed { index, captureMode ->
                RadioItem(index, getString(captureMode.stringResId), captureMode)
            }

            RadioGroupDialog(this@SettingsActivity, ArrayList(items), config.captureMode.ordinal) {
                config.captureMode = it as CaptureMode
                updateCaptureMode(it)
            }
        }
    }

    private fun updateCaptureMode(captureMode: CaptureMode) {
        settings_capture_mode.text = getString(captureMode.stringResId)
    }

}
