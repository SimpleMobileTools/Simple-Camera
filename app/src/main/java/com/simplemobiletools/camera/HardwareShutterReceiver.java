package com.simplemobiletools.camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.simplemobiletools.camera.activities.MainActivity;

public class HardwareShutterReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final Intent mainIntent = new Intent(context.getApplicationContext(), MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(mainIntent);
    }
}
