package com.runnirr.xvoiceplus.receivers;

import com.runnirr.xvoiceplus.R;
import com.runnirr.xvoiceplus.XVoicePlusService;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

public class UserPollReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = UserPollReceiver.class.getName();
    public static final String USER_POLL = "com.runnirr.xvoiceplus.USER_POLL";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, XVoicePlusService.class);
        startWakefulService(context, intent); 
    }

    private static PendingIntent getUserPollPendingIntent(Context context) {
        Intent intent = new Intent().setAction(USER_POLL);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static void startAlarmManager(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("settings_enabled", false)) {
            String pollingFreqStr = preferences.getString("settings_polling_frequency",
                    context.getString(R.string.default_polling_frequency));
            long pollingFreq = Long.valueOf(pollingFreqStr);
            Log.i(TAG, "PollingFreq: " + pollingFreq);

            if (pollingFreq > 0) {
                getAlarmManager(context).setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime(), pollingFreq, getUserPollPendingIntent(context));    
            } else {
                getAlarmManager(context).cancel(getUserPollPendingIntent(context));
            }
        }
    }
}
