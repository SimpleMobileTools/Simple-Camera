package com.simplemobiletools.camera.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.activities.BaseSimpleActivity

open class SplashActivity : BaseSimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
