package com.voiceassistant.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.voiceassistant.services.VoiceListenerService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            if (prefs.getBoolean("background_service", false)) {
                Intent serviceIntent = new Intent(context, VoiceListenerService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
