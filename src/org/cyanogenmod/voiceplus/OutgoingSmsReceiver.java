package org.cyanogenmod.voiceplus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Created by koush on 7/7/13.
 */
public class OutgoingSmsReceiver extends BroadcastReceiver {
    private static final String LOGTAG = "OutgoingSmsReceiver";

    private boolean canDeliverToAddress(Context context, Intent intent) {
        String address = intent.getStringExtra("destAddr");

        if (address == null)
            return false;
        if (address.startsWith("+") && !address.startsWith("+1"))
            return false;

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String country = tm.getNetworkCountryIso();
        if (country == null)
            country = tm.getSimCountryIso();
        if (country == null)
            return address.startsWith("+1"); /* Should never be reached. */

        if (!country.toUpperCase().equals("US") && !address.startsWith("+1"))
            return false;

        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("account", null) == null)
            return;

        if (!canDeliverToAddress(context, intent)) {
            String destination = intent.getStringExtra("destAddr");
            if (destination == null)
            destination = "(null)";
            Log.d(LOGTAG, "Sending <" + destination + "> via cellular instead of Google Voice.");
            return;
        }

        abortBroadcast();
        setResultCode(Activity.RESULT_CANCELED);

        intent.setClass(context, VoicePlusService.class);
        context.startService(intent);
    }
}
