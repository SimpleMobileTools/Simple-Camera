package com.simplemobiletools.camera.activities

import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import com.simplemobiletools.camera.Constants
import com.simplemobiletools.camera.R
import com.simplemobiletools.filepicker.dialogs.FilePickerDialog
import com.simplemobiletools.filepicker.extensions.getBasePath
import com.simplemobiletools.filepicker.extensions.getHumanReadablePath
import com.simplemobiletools.filepicker.extensions.isShowingWritePermissions
import kotlinx.android.synthetic.main.activity_settings.*
import java.io.File

class SettingsActivity : SimpleActivity() {
    val OPEN_DOCUMENT_TREE = 1
    var mCurrPath = ""
    var mWantedPath = ""

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
        settings_dark_theme.isChecked = config.isDarkTheme
        settings_dark_theme_holder.setOnClickListener {
            settings_dark_theme.toggle()
            config.isDarkTheme = settings_dark_theme.isChecked
            restartActivity()
        }
    }

    private fun setupSavePhotosFolder() {
        mCurrPath = config.savePhotosFolder
        settings_save_photos.text = getHumanPath()
        settings_save_photos_holder.setOnClickListener {
            FilePickerDialog(this, mCurrPath, false, false, object : FilePickerDialog.OnFilePickerListener {
                override fun onFail(error: FilePickerDialog.FilePickerResult) {
                }

                override fun onSuccess(pickedPath: String) {
                    mWantedPath = pickedPath
                    if (!isShowingWritePermissions(File(pickedPath), config.treeUri, OPEN_DOCUMENT_TREE)) {
                        mCurrPath = if (pickedPath.length == 1) pickedPath else pickedPath.trimEnd('/')
                        config.savePhotosFolder = mCurrPath
                        settings_save_photos.text = getHumanPath()
                    }
                }
            })
        }
    }

    private fun getHumanPath(): String {
        val basePath = mCurrPath.getBasePath(applicationContext)
        val path = mCurrPath.replaceFirst(basePath, getStorageName(basePath)).trimEnd('/')

        return if (path.contains('/'))
            path.substring(path.lastIndexOf("/") + 1)
        else
            path
    }

    private fun getStorageName(basePath: String) = getHumanReadablePath(basePath) + "/"

    @TargetApi(Build.VERSION_CODES.KITKAT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == OPEN_DOCUMENT_TREE) {
            if (resultCode == Activity.RESULT_OK && resultData != null) {
                mCurrPath = mWantedPath
                config.savePhotosFolder = mCurrPath
                settings_save_photos.text = getHumanPath()
                saveTreeUri(resultData)
            } else {
                mCurrPath = config.savePhotosFolder
                settings_save_photos.text = getHumanPath()
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun saveTreeUri(resultData: Intent) {
        val treeUri = resultData.data
        config.treeUri = treeUri.toString()

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(treeUri, takeFlags)
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
        settings_max_photo_resolution.setSelection(getMaxPhotoSelection())
        settings_max_photo_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.maxPhotoResolution = getMaxPhotoPx(settings_max_photo_resolution.selectedItemPosition)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun getMaxPhotoSelection(): Int {
        val maxRes = config.maxPhotoResolution
        return when (maxRes) {
            Constants.TWO_MPX -> 0
            Constants.FIVE_MPX -> 1
            Constants.EIGHT_MPX -> 2
            else -> 3
        }
    }

    private fun getMaxPhotoPx(index: Int): Int {
        return when (index) {
            0 -> Constants.TWO_MPX
            1 -> Constants.FIVE_MPX
            2 -> Constants.EIGHT_MPX
            else -> -1
        }
    }

    private fun setupMaxVideoResolution() {
        settings_max_video_resolution.setSelection(getMaxVideoSelection())
        settings_max_video_resolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.maxVideoResolution = getMaxVideoPx(settings_max_video_resolution.selectedItemPosition)
            }
        }
    }

    private fun getMaxVideoSelection(): Int {
        val maxRes = config.maxVideoResolution
        return when (maxRes) {
            Constants.P480 -> 0
            Constants.P720 -> 1
            Constants.P1080 -> 2
            else -> 3
        }
    }

    private fun getMaxVideoPx(index: Int): Int {
        return when (index) {
            0 -> Constants.P480
            1 -> Constants.P720
            2 -> Constants.P1080
            else -> -1
        }
    }

    private fun restartActivity() {
        TaskStackBuilder.create(applicationContext).addNextIntentWithParentStack(intent).startActivities()
    }
}
