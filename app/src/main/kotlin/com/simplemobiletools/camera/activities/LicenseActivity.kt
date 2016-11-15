package com.simplemobiletools.camera.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.simplemobiletools.camera.R
import kotlinx.android.synthetic.main.activity_license.*

class LicenseActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        license_butterknife_title.setOnClickListener { openUrl(R.string.butterknife_url) }
    }

    private fun openUrl(id: Int) {
        val url = resources.getString(id)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }
}
