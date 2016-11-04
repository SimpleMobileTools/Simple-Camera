package com.simplemobiletools.camera.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import com.simplemobiletools.camera.R
import com.simplemobiletools.filepicker.dialogs.FilePickerDialog
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupDarkTheme()
        setupSavePhotosFolder()
        setupSound()
        setupForceRatio()
        setupMaxPhotoResolution()
        setupMaxVideoResolution()
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

    private fun setupDarkTheme() {
        settings_dark_theme.isChecked = mConfig.isDarkTheme
        settings_dark_theme_holder.setOnClickListener {
            settings_dark_theme.toggle()
            mConfig.isDarkTheme = settings_dark_theme.isChecked
            restartActivity()
        }
    }

    private fun setupSavePhotosFolder() {
        var currPath = mConfig.savePhotosFolder
        settings_save_photos.text = currPath.substring(currPath.lastIndexOf("/") + 1)
        settings_save_photos_holder.setOnClickListener {
            FilePickerDialog(this, currPath, false, false, true, object: FilePickerDialog.OnFilePickerListener {
                override fun onFail(error: FilePickerDialog.FilePickerResult) {
                }

                override fun onSuccess(pickedPath: String) {
                    currPath = pickedPath.trimEnd('/')
                    mConfig.savePhotosFolder = currPath
                    settings_save_photos.text = currPath.substring(currPath.lastIndexOf("/") + 1)
                }
            })
        }
    }

    private fun setupSound() {
        settings_sound.isChecked = mConfig.isSoundEnabled
        settings_sound_holder.setOnClickListener {
            settings_sound.toggle()
            mConfig.isSoundEnabled = settings_sound.isChecked
        }
    }

    private fun setupForceRatio() {
        settings_force_ratio.isChecked = mConfig.forceRatioEnabled
        settings_force_ratio_holder.setOnClickListener {
            settings_force_ratio.toggle()
            mConfig.forceRatioEnabled = settings_force_ratio.isChecked
        }
    }

    private fun setupMaxPhotoResolution() {
        settings_max_photo_resolution.setSelection(mConfig.maxPhotoResolution)
        settings_max_photo_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mConfig.maxPhotoResolution = settings_max_photo_resolution.selectedItemPosition
            }
        }
    }

    private fun setupMaxVideoResolution() {
        settings_max_video_resolution.setSelection(mConfig.maxVideoResolution)
        settings_max_video_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mConfig.maxVideoResolution = settings_max_video_resolution.selectedItemPosition
            }
        }
    }

    private fun restartActivity() {
        TaskStackBuilder.create(applicationContext).addNextIntentWithParentStack(intent).startActivities()
    }
}
