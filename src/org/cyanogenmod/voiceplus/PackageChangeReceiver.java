package org.cyanogenmod.voiceplus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Created by koush on 7/17/13.
 */
public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null)
            return;

        PackageManager pm = context.getPackageManager();
        if (pm == null)
            return;

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
            SharedPreferences settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
            if (!settings.getBoolean("pestered", false)) {
                Notification.Builder builder = new Notification.Builder(context);
                Notification n = builder
                .setSmallIcon(R.drawable.stat_sys_gvoice)
                .setContentText(context.getString(R.string.enable_voice_plus))
                .setTicker(context.getString(R.string.enable_voice_plus))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, VoicePlusSetup.class), 0))
                .build();
                ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1000, n);
                settings.edit().putBoolean("pestered", true).commit();
            }

            pm.setComponentEnabledSetting(activity, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            pm.setComponentEnabledSetting(service, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
            context.startService(new Intent(context, VoicePlusService.class));
        }
        else {
            pm.setComponentEnabledSetting(activity, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            pm.setComponentEnabledSetting(service, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
            pm.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
    }
}
