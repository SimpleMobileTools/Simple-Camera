package com.simplemobiletools.camera

import android.app.Application
import com.simplemobiletools.camera.extensions.config
import java.util.*

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (config.useEnglish) {
            val conf = resources.configuration
            conf.locale = Locale.ENGLISH
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
    }
}
