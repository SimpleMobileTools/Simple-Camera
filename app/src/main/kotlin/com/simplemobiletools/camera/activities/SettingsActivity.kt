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
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
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
        arrayListOf(
            binding.settingsColorCustomizationLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsShutterLabel,
            binding.settingsSavingLabel,
        ).forEach {
            it.setTextColor(properPrimaryColor)
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
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

    private fun setupUseEnglish() {
        binding.settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        binding.settingsUseEnglish.isChecked = config.useEnglish
        binding.settingsUseEnglishHolder.setOnClickListener {
            binding.settingsUseEnglish.toggle()
            config.useEnglish = binding.settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        binding.settingsLanguage.text = Locale.getDefault().displayLanguage
        binding.settingsLanguageHolder.beVisibleIf(isTiramisuPlus())

        listOf(binding.settingsGeneralSettingsHolder, binding.settingsGeneralSettingsLabel).forEach {
            it.beGoneIf(binding.settingsUseEnglishHolder.isGone() && binding.settingsPurchaseThankYouHolder.isGone() && binding.settingsLanguageHolder.isGone())
        }

        binding.settingsLanguageHolder.setOnClickListener {
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
        binding.settingsSound.isChecked = config.isSoundEnabled
        binding.settingsSoundHolder.setOnClickListener {
            binding.settingsSound.toggle()
            config.isSoundEnabled = binding.settingsSound.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() {
        binding.settingsVolumeButtonsAsShutter.isChecked = config.volumeButtonsAsShutter
        binding.settingsVolumeButtonsAsShutterHolder.setOnClickListener {
            binding.settingsVolumeButtonsAsShutter.toggle()
            config.volumeButtonsAsShutter = binding.settingsVolumeButtonsAsShutter.isChecked
        }
    }

    private fun setupFlipPhotos() {
        binding.settingsFlipPhotos.isChecked = config.flipPhotos
        binding.settingsFlipPhotosHolder.setOnClickListener {
            binding.settingsFlipPhotos.toggle()
            config.flipPhotos = binding.settingsFlipPhotos.isChecked
        }
    }

    private fun setupSavePhotoMetadata() {
        binding.settingsSavePhotoMetadata.isChecked = config.savePhotoMetadata
        binding.settingsSavePhotoMetadataHolder.setOnClickListener {
            binding.settingsSavePhotoMetadata.toggle()
            config.savePhotoMetadata = binding.settingsSavePhotoMetadata.isChecked
        }
    }

    private fun setupSavePhotoVideoLocation() {
        binding.settingsSavePhotoVideoLocation.isChecked = config.savePhotoVideoLocation
        binding.settingsSavePhotoVideoLocationHolder.setOnClickListener {
            val willEnableSavePhotoVideoLocation = !config.savePhotoVideoLocation

            if (willEnableSavePhotoVideoLocation) {
                if (checkLocationPermission()) {
                    updateSavePhotoVideoLocationConfig(true)
                } else {
                    handlePermission(PERMISSION_ACCESS_FINE_LOCATION) { _ ->
                        if (checkLocationPermission()) {
                            updateSavePhotoVideoLocationConfig(true)
                        } else {
                            OpenDeviceSettingsDialog(activity = this@SettingsActivity, message = getString(R.string.allow_location_permission))
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

    private fun setupSavePhotosFolder() {
        binding.settingsSavePhotosLabel.text = addLockedLabelIfNeeded(R.string.save_photos)
        binding.settingsSavePhotos.text = getLastPart(config.savePhotosFolder)
        binding.settingsSavePhotosHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                FilePickerDialog(this, config.savePhotosFolder, false, showFAB = true) {
                    val path = it
                    handleSAFDialog(it) { success ->
                        if (success) {
                            config.savePhotosFolder = path
                            binding.settingsSavePhotos.text = getLastPart(config.savePhotosFolder)
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
