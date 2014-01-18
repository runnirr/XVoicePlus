package com.runnirr.xvoiceplus;

import org.cyanogenmod.voiceplus.VoicePlusService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class IncomingGvReceiver extends BroadcastReceiver {

    public static final String INCOMING_VOICE = "com.runnirr.xvoiceplus.INCOMING_VOICE";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, VoicePlusService.class);
        context.startService(intent);
    }

}
