package com.runnirr.xvoiceplus;


import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.cyanogenmod.voiceplus.Helper;
import org.cyanogenmod.voiceplus.OutgoingSmsReceiver;
import org.cyanogenmod.voiceplus.VoicePlusService;

import android.app.AndroidAppHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XVoicePlus implements IXposedHookLoadPackage, IXposedHookZygoteInit {
	private static final String TAG = "XVoicePlus";
	
	private static final String PERM_BROADCAST_SMS = "android.permission.BROADCAST_SMS";
	
	private boolean HOOKED_GV = false;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws ClassNotFoundException {
		if (lpparam.packageName.equals(Helper.GOOGLE_VOICE_PACKAGE)) {
			hookGoogleVoice(lpparam);
		}
	}
	
	private void hookGoogleVoice(LoadPackageParam lpparam) {
		if (!HOOKED_GV){
			findAndHookMethod(Helper.GOOGLE_VOICE_PACKAGE + ".PushNotificationReceiver", lpparam.classLoader,
					"onReceive", Context.class, Intent.class,
					new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							Log.d(TAG, "Received incoming Google Voice notification");
							Context c = (Context) param.args[0];
							Intent incomingGvIntent = new Intent().setAction(VoicePlusService.ACTION_INCOMING_VOICE);
							c.sendOrderedBroadcast(incomingGvIntent, null);
						}
			});
		HOOKED_GV= true;
		}
	}
	
	@Override
	public void initZygote(StartupParam startupParam) {
		hookSendSms();
		hookXVoicePlusPermission();
		hookSmsMessage();
	}
	
	private void hookSmsMessage(){
		findAndHookMethod(android.telephony.SmsMessage.class, "createFromPdu", byte[].class, String.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if(!param.args[1].equals(SmsUtils.FORMAT_3GPP)){
					// Set to 3GPP format (GSM) since that is the format of the generated sms
					// This likely breaks any incoming sms in CDMA format
					param.args[1] = SmsUtils.FORMAT_3GPP;
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

                if ("com.runnirr.xvoiceplus".equals(pkgName) || "org.cyanogenmod.voiceplus".equals(pkgName)) {
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
		findAndHookMethod(findClass("android.telephony.SmsManager", null), "sendTextMessage",
				String.class, String.class, String.class, PendingIntent.class, PendingIntent.class,
				new XSmsMethodHook());

		findAndHookMethod(findClass("android.telephony.SmsManager", null), "sendMultipartTextMessage",
				 String.class, String.class, ArrayList.class, ArrayList.class, ArrayList.class,
				 new XSmsMethodHook());
	}
	
    class XSmsMethodHook extends XC_MethodHook {

		@Override
    	protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
    		if (sendText(destAddr, scAddr, texts, sentIntents, deliveryIntents)) {
    			// If we sent via Google Voice, stop the system from sending its sms
    			Log.i(TAG, "Sending message via Google Voice");
        		param.setResult(null);    			
    		}
    	}

	    private boolean sendText(String destAddr, String scAddr, ArrayList<String> texts,
	    		final ArrayList<PendingIntent> sentIntents, final ArrayList<PendingIntent> deliveryIntents) throws IOException {
	
	    	Intent outgoingSms = new Intent().setAction(OutgoingSmsReceiver.NEW_OUTGOING_SMS)
	    									 .putExtra("destAddr", destAddr)
	    									 .putExtra("scAddr", scAddr)
	    									 .putStringArrayListExtra("parts", texts)
	    									 .putParcelableArrayListExtra("sentIntents", sentIntents)
	    									 .putParcelableArrayListExtra("deliveryIntents", deliveryIntents);

	    	Context context = getContext();
    		if (context != null) {
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
    		
        	// Seems to work for 4.2.2
    		if (context == null) {
    			Log.i(TAG, "Trying to get context from mSystemContext");
    			Object systemContext = getStaticObjectField(findClass("android.app.ActivityThread", null), "mSystemContext");
    			if (systemContext != null) {
    				context = (Context) systemContext;
    			}
    		}
    		    		
    		return context;
	    }
    }

}
