package com.runnirr.xvoiceplus;


import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;

import android.annotation.TargetApi;
import android.app.AndroidAppHelper;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = XVoicePlus.class.getName();

    public static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final String XVOICE_PLUS_PACKAGE = "com.runnirr.xvoiceplus";

    private static final String PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS";

    private SharedPreferences mUserPreferences;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
        if (lpparam.packageName.equals(GOOGLE_VOICE_PACKAGE)) {
            hookGoogleVoice(lpparam);
        }
    }

    private void hookGoogleVoice(LoadPackageParam lpparam) {
        findAndHookMethod(GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader,
                "onReceive", Context.class, Intent.class,
                new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d(TAG, "Received incoming Google Voice notification");
                Context context = (Context) param.args[0];
                Intent gvIntent = (Intent) param.args[1];

                Intent intent = new Intent()
                    .setAction(MessageEventReceiver.INCOMING_VOICE)
                    .putExtras(gvIntent.getExtras());

                context.sendOrderedBroadcast(intent, null);
            }
        });
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        XResources.setSystemWideReplacement("android", "bool", "config_sms_capable", true);
        mUserPreferences = new XSharedPreferences("com.runnirr.xvoiceplus");

        hookSendSms();
        hookXVoicePlusPermission();
        hookSmsMessage();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            hookAppOps();
        }
    }

    @TargetApi(19)
    private void hookAppOps() {
        Log.d(TAG, "Hooking app ops");

        XposedBridge.hookAllConstructors(findClass("com.android.server.AppOpsService.Op", null),
                new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(XVOICE_PLUS_PACKAGE.equals((String) param.args[1]) &&
                        (Integer) param.args[2] == SmsUtils.OP_WRITE_SMS) {

                    setIntField(param.thisObject, "mode", AppOpsManager.MODE_ALLOWED);
                }
            }

        });
    }

    private void hookSmsMessage(){
        findAndHookMethod(SmsMessage.class, "createFromPdu", byte[].class, String.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(!SmsUtils.FORMAT_3GPP.equals((String) param.args[1])) {
                    try {
                        Log.d(TAG, "Trying to parse pdu in GSM 3GPP");
                        SmsMessage result = (SmsMessage) callStaticMethod(SmsMessage.class, "createFromPdu", param.args[0], SmsUtils.FORMAT_3GPP);
                        if (result.getServiceCenterAddress().equals(SmsUtils.SERVICE_CENTER)) {
                            param.setResult(result);
                        } else {
                            Log.w(TAG, "Expected service center " + SmsUtils.SERVICE_CENTER + " for Google Voice message. Falling back to default format");
                        }
                    } catch (Throwable e) {
                        Log.w(TAG, "Error parsing in GSM 3GPP. Falling back to default behavior");
                    }
                }
            }
        });
    }

    private void hookXVoicePlusPermission(){
        final Class<?> pmServiceClass = findClass("com.android.server.pm.PackageManagerService", null);

        findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                "android.content.pm.PackageParser.Package", boolean.class, new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final String pkgName = (String) getObjectField(param.args[0], "packageName");

                if (XVOICE_PLUS_PACKAGE.equals(pkgName)) {
                    final Object extras = getObjectField(param.args[0], "mExtras");
                    final HashSet<String> grantedPerms = 
                            (HashSet<String>) getObjectField(extras, "grantedPermissions");
                    final Object settings = getObjectField(param.thisObject, "mSettings");
                    final Object permissions = getObjectField(settings, "mPermissions");

                    // Add android.permission.BROADCAST_SMS to xvoiceplus
                    if (!grantedPerms.contains(PERM_BROADCAST_SMS)) {
                        final Object pAccessBroadcastSms = callMethod(permissions, "get",
                                PERM_BROADCAST_SMS);
                        grantedPerms.add(PERM_BROADCAST_SMS);
                        int[] gpGids = (int[]) getObjectField(extras, "gids");
                        int[] bpGids = (int[]) getObjectField(pAccessBroadcastSms, "gids");
                        gpGids = (int[]) callStaticMethod(param.thisObject.getClass(), 
                                "appendInts", gpGids, bpGids);
                    }
                }
            }
        });
    }


    private void hookSendSms(){
        findAndHookMethod(SmsManager.class, "sendTextMessage",
                String.class, String.class, String.class, PendingIntent.class, PendingIntent.class,
                new XSmsMethodHook());

        findAndHookMethod(SmsManager.class, "sendMultipartTextMessage",
                String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
                new XSmsMethodHook());
    }

    class XSmsMethodHook extends XC_MethodHook {

        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            switch (getOutgoingStrategy()) {

            // TODO: Find a way to make prompt work
            case (R.string.outgoing_prompt):
            case (R.string.outgoing_voice):
                Log.d(TAG, "Sending via Google Voice based on settings");
                attemptSendViaGoogleVoice(param);
                return;
            case (R.string.outgoing_carrier):
            default:
                // Stop the hooks and let it go through carrier
                Log.d(TAG, "Sending via carrier based on settings");
                return;
            }
        }

        private void attemptSendViaGoogleVoice(final MethodHookParam param) {
            String destAddr = (String) param.args[0];
            String scAddr = (String) param.args[1];

            ArrayList<String> texts;
            if (param.args[2] instanceof String) {
                texts = new ArrayList<String>(Collections.singletonList((String) param.args[2]));
            } else {
                texts = (ArrayList<String>) param.args[2];
            }

            ArrayList<PendingIntent> sentIntents;
            if (param.args[3] instanceof PendingIntent) {
                sentIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[3]));
            } else {
                sentIntents = (ArrayList<PendingIntent>) param.args[3];
            }

            ArrayList<PendingIntent> deliveryIntents;
            if (param.args[4] instanceof PendingIntent) {
                deliveryIntents = new ArrayList<PendingIntent>(Collections.singletonList((PendingIntent) param.args[4]));
            } else {
                deliveryIntents = (ArrayList<PendingIntent>) param.args[4];
            }
            try {
                if (sendText(destAddr, scAddr, texts, sentIntents, deliveryIntents)) {
                    // If we sent via Google Voice, stop the system from sending its sms
                    param.setResult(null);
                } else {
                    Log.i(TAG, "Send text failed. Using regluar number");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error attempting to send message via Google Voice", e);
            }
        }

        private boolean sendText(String destAddr, String scAddr, ArrayList<String> texts,
                final ArrayList<PendingIntent> sentIntents, final ArrayList<PendingIntent> deliveryIntents) throws IOException {

            Context context = getContext();
            if (context != null) {
                Intent outgoingSms = new Intent()
                        .setAction(MessageEventReceiver.OUTGOING_SMS)
                        .putExtra("destAddr", destAddr)
                        .putExtra("scAddr", scAddr)
                        .putStringArrayListExtra("parts", texts)
                        .putParcelableArrayListExtra("sentIntents", sentIntents)
                        .putParcelableArrayListExtra("deliveryIntents", deliveryIntents);

                context.sendOrderedBroadcast(outgoingSms, null);
                return true;

            } else {
                Log.e(TAG, "Unable to find a context to send the outgoingSms intent");
                return false;
            }
        }

        private Context getContext(){
            // Try to get a context in one way or another from system
            Context context = null;

            // Seems to work for 4.4
            Log.i(TAG, "Trying to get context from AndroidAppHelper");
            context = AndroidAppHelper.currentApplication();

            // Seems to work for 4.2
            if (context == null) {
                Log.i(TAG, "Trying to get context from mSystemContext");
                Object systemContext = getStaticObjectField(findClass("android.app.ActivityThread", null), "mSystemContext");
                if (systemContext != null) {
                    context = (Context) systemContext;
                }
            }

            // Seems to work for 4.1 and 4.0
            if (context == null) {
                Log.i(TAG, "Trying to get activityThread from systemMain");
                Object activityThread = callStaticMethod(findClass("android.app.ActivityThread", null), "systemMain");
                if (activityThread != null){
                    Log.i(TAG, "Trying to get context from getSystemContext");
                    context = (Context) callMethod(activityThread, "getSystemContext");
                }
            }

            return context;
        }
    }

    private int getOutgoingStrategy() {
        String outboundPref = mUserPreferences.getString("settings_outbound_messages", "0");
        if (!mUserPreferences.getBoolean("settings_enabled", false) ||
                mUserPreferences.getString("account", null) == null ||
                outboundPref.equals("0")) {
            // Not enabled or no account selected, or selected to send via carrier
            return R.string.outgoing_carrier;
        } else if (outboundPref.equals("1")) {
            // User selected to send via Google Voice
            return R.string.outgoing_voice;
        } else if (outboundPref.equals("2")) {
            // User selected to prompt before sending
            return R.string.outgoing_prompt;
        } else {
            // Default to carrier if something is wrong in the settings
            return R.string.outgoing_carrier;
        }
    }
}
