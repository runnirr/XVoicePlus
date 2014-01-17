package org.cyanogenmod.voiceplus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Created by koush on 7/17/13.
 */
public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        Log.d("XVoicePlus.PackageChangeReceiver", "Handling intent " + intent.getAction());

        PackageManager pm = context.getPackageManager();
        if (pm == null)
            return;

        ComponentName activity = new ComponentName(context, VoicePlusSetup.class);

        int ENABLED_STATE;
        try {
            pm.getPackageInfo(Helper.GOOGLE_VOICE_PACKAGE, 0);
            ENABLED_STATE =  PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        }
        catch (Exception e) {
            ENABLED_STATE =  PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }

        pm.setComponentEnabledSetting(activity, ENABLED_STATE, 0);
    }
}
