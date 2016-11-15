package com.simplemobiletools.camera.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem

import com.simplemobiletools.camera.Config
import com.simplemobiletools.camera.R

open class SimpleActivity : AppCompatActivity() {
    lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        config = Config.newInstance(applicationContext)
        var theme = if (config.isDarkTheme) R.style.AppTheme_Base_Dark else R.style.AppTheme_Base
        if (this is MainActivity) {
            theme = if (config.isDarkTheme) R.style.FullScreenTheme_Dark else R.style.FullScreenTheme
        }
        setTheme(theme)
        super.onCreate(savedInstanceState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
