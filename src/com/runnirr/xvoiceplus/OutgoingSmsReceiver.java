package com.runnirr.xvoiceplus;

import org.cyanogenmod.voiceplus.VoicePlusService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OutgoingSmsReceiver extends BroadcastReceiver {
    
    public static final String OUTGOING_SMS = "com.runnirr.xvoiceplus.OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, VoicePlusService.class);
        context.startService(intent);
    }

}
