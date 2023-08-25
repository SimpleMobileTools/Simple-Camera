package com.simplemobiletools.camera.activities

import android.annotation.SuppressLint
import android.os.Bundle
import com.simplemobiletools.camera.BuildConfig
import com.simplemobiletools.camera.R
import com.simplemobiletools.camera.databinding.ActivitySettingsBinding
import com.simplemobiletools.camera.extensions.checkLocationPermission
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.camera.models.CaptureMode
import com.simplemobiletools.commons.dialogs.*
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding.apply {
            setContentView(root)
            setupOptionsMenu()
            refreshMenuItems()

            updateMaterialActivityViews(settingsCoordinator, settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(settingsNestedScrollview, settingsToolbar)
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupSound()
        setupVolumeButtonsAsShutter()
        setupFlipPhotos()
        setupSavePhotoMetadata()
        setupSavePhotoVideoLocation()
        setupSavePhotosFolder()
        setupPhotoQuality()
        setupCaptureMode()
        updateTextColors(binding.settingsHolder)

        val properPrimaryColor = getProperPrimaryColor()
        binding.apply {
            arrayListOf(
                settingsColorCustomizationLabel,
                settingsGeneralSettingsLabel,
                settingsShutterLabel,
                settingsSavingLabel,
            ).forEach {
                it.setTextColor(properPrimaryColor)
            }
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupPurchaseThankYou() {
        binding.settingsPurchaseThankYouHolder.beGoneIf(isOrWasThankYouInstalled())
        binding.settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsCustomizeColorsLabel.text = getCustomizeColorsString()
        binding.settingsColorCustomizationHolder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())

        listOf(settingsGeneralSettingsHolder, settingsGeneralSettingsLabel).forEach {
            it.beGoneIf(settingsUseEnglishHolder.isGone() && settingsPurchaseThankYouHolder.isGone() && settingsLanguageHolder.isGone())
        }

        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text)
        )

        if (!resources.getBoolean(com.simplemobiletools.commons.R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_2_title_commons, com.simplemobiletools.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(com.simplemobiletools.commons.R.string.faq_6_title_commons, com.simplemobiletools.commons.R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun getLastPart(path: String): String {
        val humanized = humanizePath(path)
        return humanized.substringAfterLast("/", humanized)
    }

    private fun setupSound() = binding.apply {
        settingsSound.isChecked = config.isSoundEnabled
        settingsSoundHolder.setOnClickListener {
            settingsSound.toggle()
            config.isSoundEnabled = settingsSound.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() = binding.apply {
        settingsVolumeButtonsAsShutter.isChecked = config.volumeButtonsAsShutter
        settingsVolumeButtonsAsShutterHolder.setOnClickListener {
            settingsVolumeButtonsAsShutter.toggle()
            config.volumeButtonsAsShutter = settingsVolumeButtonsAsShutter.isChecked
        }
    }

    private fun setupFlipPhotos() = binding.apply {
        settingsFlipPhotos.isChecked = config.flipPhotos
        settingsFlipPhotosHolder.setOnClickListener {
            settingsFlipPhotos.toggle()
            config.flipPhotos = settingsFlipPhotos.isChecked
        }
    }

    private fun setupSavePhotoMetadata() = binding.apply {
        settingsSavePhotoMetadata.isChecked = config.savePhotoMetadata
        settingsSavePhotoMetadataHolder.setOnClickListener {
            settingsSavePhotoMetadata.toggle()
            config.savePhotoMetadata = settingsSavePhotoMetadata.isChecked
        }
    }

    private fun setupSavePhotoVideoLocation() = binding.apply {
        settingsSavePhotoVideoLocation.isChecked = config.savePhotoVideoLocation
        settingsSavePhotoVideoLocationHolder.setOnClickListener {
            val willEnableSavePhotoVideoLocation = !config.savePhotoVideoLocation

            if (willEnableSavePhotoVideoLocation) {
                if (checkLocationPermission()) {
                    updateSavePhotoVideoLocationConfig(true)
                } else {
                    handlePermission(PERMISSION_ACCESS_FINE_LOCATION) { _ ->
                        if (checkLocationPermission()) {
                            updateSavePhotoVideoLocationConfig(true)
                        } else {
                            OpenDeviceSettingsDialog(activity = this@SettingsActivity, message = getString(com.simplemobiletools.commons.R.string.allow_location_permission))
                        }
                    }
                }
            } else {
                updateSavePhotoVideoLocationConfig(false)
            }
        }
    }

    private fun updateSavePhotoVideoLocationConfig(enabled: Boolean) {
        binding.settingsSavePhotoVideoLocation.isChecked = enabled
        config.savePhotoVideoLocation = enabled
    }

    private fun setupSavePhotosFolder() = binding.apply {
        settingsSavePhotosLabel.text = addLockedLabelIfNeeded(R.string.save_photos)
        settingsSavePhotos.text = getLastPart(config.savePhotosFolder)
        settingsSavePhotosHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                FilePickerDialog(this@SettingsActivity, config.savePhotosFolder, false, showFAB = true) {
                    val path = it
                    handleSAFDialog(it) { success ->
                        if (success) {
                            config.savePhotosFolder = path
                            settingsSavePhotos.text = getLastPart(config.savePhotosFolder)
                        }
                    }
                }
            } else {
                FeatureLockedDialog(this@SettingsActivity) { }
            }
        }
    }

    private fun setupPhotoQuality() {
        updatePhotoQuality(config.photoQuality)
        binding.settingsPhotoQualityHolder.setOnClickListener {
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
        binding.settingsPhotoQuality.text = "$quality%"
    }

    private fun setupCaptureMode() {
        updateCaptureMode(config.captureMode)
        binding.settingsCaptureModeHolder.setOnClickListener {
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
        binding.settingsCaptureMode.text = getString(captureMode.stringResId)
    }
}
