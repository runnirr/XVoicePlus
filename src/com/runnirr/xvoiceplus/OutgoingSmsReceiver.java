package com.runnirr.xvoiceplus;

import org.cyanogenmod.voiceplus.VoicePlusService;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class OutgoingSmsReceiver extends WakefulBroadcastReceiver {
    
    public static final String OUTGOING_SMS = "com.runnirr.xvoiceplus.OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, VoicePlusService.class);
        startWakefulService(context, intent);
        abortBroadcast();
    }

}
