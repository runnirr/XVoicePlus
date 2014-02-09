package com.runnirr.xvoiceplus.receivers;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.runnirr.xvoiceplus.XVoicePlusService;

public class MessageEventReceiver extends WakefulBroadcastReceiver {

    public static final String INCOMING_VOICE = "com.runnirr.xvoiceplus.INCOMING_VOICE";
    public static final String OUTGOING_SMS = "com.runnirr.xvoiceplus.OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, XVoicePlusService.class);
        startWakefulService(context, intent);
    }
}
