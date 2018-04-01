package com.simplemobiletools.camera.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.camera.activities.MainActivity

class HardwareShutterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Intent(context.applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(this)
        }
    }
}
