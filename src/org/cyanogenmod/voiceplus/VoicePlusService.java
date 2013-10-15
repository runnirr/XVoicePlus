package org.cyanogenmod.voiceplus;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ISms;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;

/**
 * Created by koush on 7/5/13.
 */
public class VoicePlusService extends Service {
    public static final String ACTION_INCOMING_VOICE = VoicePlusService.class.getPackage().getName() + ".INCOMING_VOICE";
    private static final String LOGTAG = "VoicePlusSetup";

    private ISms smsTransport;
    private SharedPreferences settings;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ensure that this notification listener is enabled.
    // the service watches for google voice notifications to know when to check for new
    // messages.
    final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"; //Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
    private void ensureEnabled() {
        ComponentName me = new ComponentName(this, VoiceListenerService.class);
        String meFlattened = me.flattenToString();

        String existingListeners = Settings.Secure.getString(getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);

        if (!TextUtils.isEmpty(existingListeners)) {
            if (existingListeners.contains(meFlattened)) {
                return;
            } else {
                existingListeners += ":" + meFlattened;
            }
        } else {
            existingListeners = meFlattened;
        }

        Settings.Secure.putString(getContentResolver(),
        ENABLED_NOTIFICATION_LISTENERS,
        existingListeners);
    }

    // hook into sms manager to be able to synthesize SMS events.
    // new messages from google voice get mocked out as real SMS events in Android.
    private void registerSmsMiddleware() {
        try {
            if (smsTransport != null)
                return;
            Class sm = Class.forName("android.os.ServiceManager");
            Method getService = sm.getMethod("getService", String.class);
            smsTransport = ISms.Stub.asInterface((IBinder)getService.invoke(null, "isms"));
        }
        catch (Exception e) {
            Log.e(LOGTAG, "register error", e);
        }
    }

    BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // refresh inbox if connectivity returns
            if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false))
                return;
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null)
                startRefresh();
        }
    };

   @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mConnectivityReceiver);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        settings = getSharedPreferences("settings", MODE_PRIVATE);

        registerSmsMiddleware();

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectivityReceiver, filter);

        startRefresh();
    }

    // parse out the intent extras from android.intent.action.NEW_OUTGOING_SMS
    // and send it off via google voice
    void handleOutgoingSms(Intent intent) {
        boolean multipart = intent.getBooleanExtra("multipart", false);
        String destAddr = intent.getStringExtra("destAddr");
        String scAddr = intent.getStringExtra("scAddr");
        ArrayList<String> parts = intent.getStringArrayListExtra("parts");
        ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
        ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

        onSendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, multipart);
    }

    boolean canDeliverToAddress(String address) {
        if (address == null)
            return false;
        if (address.startsWith("+") && !address.startsWith("+1"))
            return false;

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
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
    public int onStartCommand(final Intent intent, int flags, int startId) {
        int ret = super.onStartCommand(intent, flags, startId);

        if (null == settings.getString("account", null)) {
            stopSelf();
            return ret;
        }

        ensureEnabled();

        if (intent == null)
            return ret;

        // handle an outgoing sms on a background thread.
        if ("android.intent.action.NEW_OUTGOING_SMS".equals(intent.getAction())) {
            String destination = intent.getStringExtra("destAddr");
            if (!canDeliverToAddress(destination)) {
                if (destination == null)
                    destination = "(null)";
                Log.d(LOGTAG, "Sending <" + destination + "> via cellular instead of gvoice.");
                stopSelf();
                return ret;
            }

            new Thread() {
                @Override
                public void run() {
                    handleOutgoingSms(intent);
                }
            }.start();
        }
        else if (ACTION_INCOMING_VOICE.equals(intent.getAction())) {
            if (null == settings.getString("account", null))
                return ret;
            startRefresh();
        }
        else if (ACCOUNT_CHANGED.equals(intent.getAction())) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        fetchRnrSe(getAuthToken(settings.getString("account", null)));
                    }
                    catch (Exception e) {
                    }
                }
            }.start();
        }

        return ret;
    }

    public static final String ACCOUNT_CHANGED = VoicePlusService.class.getPackage().getName() + ".ACCOUNT_CHANGED";

    // mark all sent intents as failures
    public void fail(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si == null)
                continue;
            try {
                si.send();
            }
            catch (Exception e) {
            }
        }
    }

    // mark all sent intents as successfully sent
    public void success(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si == null)
                continue;
            try {
                si.send(Activity.RESULT_OK);
            }
            catch (Exception e) {
            }
        }
    }

    // fetch the weirdo opaque token google voice needs...
    void fetchRnrSe(String authToken) throws ExecutionException, InterruptedException {
        JsonObject userInfo = Ion.with(this)
        .load("https://www.google.com/voice/request/user")
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .asJsonObject()
        .get();

        String rnrse = userInfo.get("r").getAsString();

        try {
            TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry: phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString()))
                        continue;
                    if (!phone.get("smsEnabled").getAsBoolean())
                        break;
                    Log.i(LOGTAG, "Disabling SMS forwarding to phone.");
                    Ion.with(this)
                    .load("https://www.google.com/voice/settings/editForwardingSms/")
                    .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                    .setBodyParameter("phoneId", entry.getKey())
                    .setBodyParameter("enabled", "0")
                    .setBodyParameter("_rnr_se", rnrse)
                    .asJsonObject();
                    break;
                }
            }
        }
        catch (Exception e) {
            Log.e(LOGTAG, "Error verifying GV SMS forwarding", e);
        }

        settings.edit()
        .putString("_rnr_se", rnrse)
        .commit();
    }

    // mark an outgoing text as recently sent, so if it comes in via
    // round trip, we ignore it.
    PriorityQueue<String> recentSent = new PriorityQueue<String>();
    private void addRecent(String text) {
        while (recentSent.size() > 20)
            recentSent.remove();
        recentSent.add(text);
    }

    public String getAuthToken(String account) throws IOException, OperationCanceledException, AuthenticatorException {
        Bundle bundle = AccountManager.get(this).getAuthToken(new Account(account, "com.google"), "grandcentral", true, null, null).getResult();
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }

    // send an outgoing sms event via google voice
    public void onSendMultipartText(String destAddr, String scAddr, List<String> texts, final List<PendingIntent> sentIntents, final List<PendingIntent> deliveryIntents, boolean multipart) {
        // grab the account and wacko opaque routing token thing
        String rnrse = settings.getString("_rnr_se", null);
        String account = settings.getString("account", null);
        String authToken;

        try {
            // grab the auth token
            authToken = getAuthToken(account);

            if (rnrse == null) {
                fetchRnrSe(authToken);
                rnrse = settings.getString("_rnr_se", null);
            }
        }
        catch (Exception e) {
            Log.e(LOGTAG, "Error fetching tokens", e);
            fail(sentIntents);
            return;
        }

        // combine the multipart text into one string
        StringBuilder textBuilder = new StringBuilder();
        for (String text: texts) {
            textBuilder.append(text);
        }
        String text = textBuilder.toString();

        try {
            // send it off, and note that we recently sent this message
            // for round trip tracking
            sendRnrSe(authToken, rnrse, destAddr, text);
            addRecent(text);
            success(sentIntents);
            return;
        }
        catch (Exception e) {
            Log.d(LOGTAG, "send error", e);
        }

        try {
            // on failure, fetch info and try again
            fetchRnrSe(authToken);
            rnrse = settings.getString("_rnr_se", null);
            sendRnrSe(authToken, rnrse, destAddr, text);
            addRecent(text);
            success(sentIntents);
        }
        catch (Exception e) {
            Log.d(LOGTAG, "send failure", e);
            fail(sentIntents);
        }
    }

    // hit the google voice api to send a text
    void sendRnrSe(final String authToken, String rnrse, String number, String text) throws Exception {
        JsonObject json = Ion.with(this)
        .load("https://www.google.com/voice/sms/send/")
        .onHeaders(new HeadersCallback() {
            @Override
            public void onHeaders(RawHeaders headers) {
                if (headers.getResponseCode() == 401) {
                    AccountManager.get(VoicePlusService.this).invalidateAuthToken("com.google", authToken);
                    settings.edit().remove("_rnr_se").commit();
                }
            }
        })
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .setBodyParameter("phoneNumber", number)
        .setBodyParameter("sendErrorSms", "0")
        .setBodyParameter("text", text)
        .setBodyParameter("_rnr_se", rnrse)
        .asJsonObject()
        .get();

        if (!json.get("ok").getAsBoolean())
            throw new Exception(json.toString());
    }

    public static class Payload {
        @SerializedName("messageList")
        public ArrayList<Conversation> conversations = new ArrayList<Conversation>();
    }

    public static class Conversation {
        @SerializedName("children")
        public ArrayList<Message> messages = new ArrayList<Message>();
    }

    public static class Message {
        @SerializedName("startTime")
        public long date;

        @SerializedName("phoneNumber")
        public String phoneNumber;

        @SerializedName("message")
        public String message;

        // 10 is incoming
        // 11 is outgoing
        @SerializedName("type")
        int type;
    }

    private static final int VOICE_INCOMING_SMS = 10;
    private static final int VOICE_OUTGOING_SMS = 11;

    private static final int PROVIDER_INCOMING_SMS = 1;
    private static final int PROVIDER_OUTGOING_SMS = 2;

    private static final Uri URI_SENT = Uri.parse("content://sms/sent");
    private static final Uri URI_RECEIVED = Uri.parse("content://sms/inbox");

    // insert a message into the sms/mms provider.
    // we do this in the case of outgoing messages
    // that were not sent via this phone, and also on initial
    // message sync.
    synchronized void insertMessage(String number, String text, int type, long date) {
        Uri uri;
        if (type == PROVIDER_INCOMING_SMS)
            uri = URI_RECEIVED;
        else
            uri = URI_SENT;

        Cursor c = getContentResolver().query(uri, null, "date = ?",
                    new String[] { String.valueOf(date) }, null);
        try {
            if (c.moveToNext())
                return;
        }
        finally {
            c.close();
        }
        ContentValues values = new ContentValues();
        values.put("address", number);
        values.put("body", text);
        values.put("type", type);
        values.put("date", date);
        values.put("date_sent", date);
        values.put("read", 1);
        getContentResolver().insert(uri, values);
    }

    synchronized void synthesizeMessage(String number, String message, long date) {
        Cursor c = getContentResolver().query(URI_RECEIVED, null, "date = ?",
                new String[] { String.valueOf(date) }, null);
        try {
            if (c.moveToNext())
                return;
        }
        finally {
            c.close();
        }

        ArrayList<String> list = new ArrayList<String>();
        list.add(message);
        try {
            // synthesize a BROADCAST_SMS event
            smsTransport.synthesizeMessages(number, null, list, date);
        }
        catch (Exception e) {
            Log.e(LOGTAG, "Error synthesizing SMS messages", e);
        }
    }

    // refresh the messages that were on the server
    void refreshMessages() throws Exception {
        String account = settings.getString("account", null);
        if (account == null)
            return;

        Log.i(LOGTAG, "Refreshing messages");

        // tokens!
        final String authToken = getAuthToken(account);

        Payload payload = Ion.with(this)
        .load("https://www.google.com/voice/request/messages")
        .onHeaders(new HeadersCallback() {
            @Override
            public void onHeaders(RawHeaders headers) {
                if (headers.getResponseCode() == 401) {
                    Log.e(LOGTAG, "Refresh failed:\n" + headers.toHeaderString());
                    AccountManager.get(VoicePlusService.this).invalidateAuthToken("com.google", authToken);
                    settings.edit().remove("_rnr_se").commit();
                }
            }
        })
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .as(Payload.class)
        .get();

        ArrayList<Message> all = new ArrayList<Message>();
        for (Conversation conversation: payload.conversations) {
            for (Message message: conversation.messages)
                all.add(message);
        }

        // sort by date order so the events get added in the same order
        Collections.sort(all, new Comparator<Message>() {
            @Override
            public int compare(Message lhs, Message rhs) {
                if (lhs.date == rhs.date)
                    return 0;
                if (lhs.date > rhs.date)
                    return 1;
                return -1;
            }
        });

        registerSmsMiddleware();
        if (smsTransport == null)
            throw new Exception("SMS transport unavailable");

        long timestamp = settings.getLong("timestamp", 0);
        boolean first = timestamp == 0;
        long max = timestamp;
        for (Message message: all) {
            max = Math.max(max, message.date);
            if (message.phoneNumber == null)
                continue;
            if (message.date <= timestamp)
                continue;
            if (message.message == null)
                continue;

            // on first sync, just populate the mms provider...
            // don't send any broadcasts.
            if (first) {
                int type;
                if (message.type == VOICE_INCOMING_SMS)
                    type = PROVIDER_INCOMING_SMS;
                else if (message.type == VOICE_OUTGOING_SMS)
                    type = PROVIDER_OUTGOING_SMS;
                else
                    continue;
                // just populate the content provider and go
                insertMessage(message.phoneNumber, message.message, type, message.date);
                continue;
            }

            // sync up outgoing messages
            if (message.type == VOICE_OUTGOING_SMS) {
                boolean found = false;
                for (String recent: recentSent) {
                    if (TextUtils.equals(recent, message.message)) {
                        recentSent.remove(message.message);
                        found = true;
                        break;
                    }
                }
                if (!found)
                    insertMessage(message.phoneNumber, message.message, PROVIDER_OUTGOING_SMS, message.date);
                continue;
            }

            if (message.type != VOICE_INCOMING_SMS)
                continue;
            synthesizeMessage(message.phoneNumber, message.message, message.date);
        }
        settings.edit()
        .putLong("timestamp", max)
        .commit();
    }

    void startRefresh() {
        new Thread() {
            @Override
            public void run() {
                try {
                    refreshMessages();
                }
                catch (Exception e) {
                    Log.e(LOGTAG, "Error refreshing messages", e);
                }
            }
        }.start();
    }
}
