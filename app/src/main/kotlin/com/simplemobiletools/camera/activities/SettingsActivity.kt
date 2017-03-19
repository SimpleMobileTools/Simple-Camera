package com.simplemobiletools.camera.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.camera.*
import com.simplemobiletools.camera.extensions.config
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.getBasePath
import com.simplemobiletools.commons.extensions.getHumanReadablePath
import com.simplemobiletools.commons.extensions.updateTextColors
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    var mCurrPath = ""
    var mWantedPath = ""

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
        return when (item.itemId) {
            R.id.about -> {
                startActivity(Intent(applicationContext, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupSavePhotosFolder() {
        mCurrPath = config.savePhotosFolder
        settings_save_photos.text = getHumanPath()
        settings_save_photos_holder.setOnClickListener {
            FilePickerDialog(this, mCurrPath, false) {
                /*mWantedPath = pickedPath
                if (!isShowingWritePermissions(File(pickedPath), config.treeUri, OPEN_DOCUMENT_TREE)) {
                    mCurrPath = if (pickedPath.length == 1) pickedPath else pickedPath.trimEnd('/')
                    config.savePhotosFolder = mCurrPath
                    settings_save_photos.text = getHumanPath()
                }*/
            }
        }
    }

    private fun getHumanPath(): String {
        val basePath = mCurrPath.getBasePath(this)
        val path = mCurrPath.replaceFirst(basePath, getStorageName(basePath)).trimEnd('/')

        return if (path.contains('/'))
            path.substring(path.lastIndexOf("/") + 1)
        else
            path
    }

    private fun getStorageName(basePath: String) = "${getHumanReadablePath(basePath)}/"

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
