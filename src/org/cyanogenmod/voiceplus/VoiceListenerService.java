package org.cyanogenmod.voiceplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Created by koush on 8/21/13.
 */
public class VoiceListenerService extends BroadcastReceiver {
    SharedPreferences settings;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (settings == null)
            settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (null == settings.getString("account", null))
            return;

        context.startService(new Intent(context, VoicePlusService.class).setAction(VoicePlusService.ACTION_INCOMING_VOICE));
    }
}
