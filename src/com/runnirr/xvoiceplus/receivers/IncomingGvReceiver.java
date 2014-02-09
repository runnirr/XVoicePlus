package com.runnirr.xvoiceplus.receivers;

import com.runnirr.xvoiceplus.XVoicePlusService;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class IncomingGvReceiver extends WakefulBroadcastReceiver {

    public static final String INCOMING_VOICE = "com.runnirr.xvoiceplus.INCOMING_VOICE";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, XVoicePlusService.class);
        startWakefulService(context, intent);
    }

}
