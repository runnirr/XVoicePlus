package org.cyanogenmod.voiceplus;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
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

        ComponentName listenerservice = new ComponentName(context, VoiceListenerService.class);
        ComponentName service = new ComponentName(context, VoicePlusService.class);
        ComponentName receiver = new ComponentName(context, OutgoingSmsReceiver.class);
        ComponentName activity = new ComponentName(context, VoicePlusSetup.class);

        PackageInfo pkg;
        try {
            pkg = pm.getPackageInfo(Helper.GOOGLE_VOICE_PACKAGE, 0);
        }
        catch (Exception e) {
            pkg = null;
        }

        if (pkg != null) {
            pm.setComponentEnabledSetting(activity, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            pm.setComponentEnabledSetting(service, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            pm.setComponentEnabledSetting(listenerservice, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            context.startService(new Intent(context, VoicePlusService.class));
        }
        else {
            pm.setComponentEnabledSetting(activity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            pm.setComponentEnabledSetting(service, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            pm.setComponentEnabledSetting(listenerservice, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
    }
}
