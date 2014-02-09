package com.runnirr.xvoiceplus.gv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.koushikdutta.ion.Ion;
import com.runnirr.xvoiceplus.XVoicePlusService;

public class GoogleVoiceManager {
    
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
        public int type;

        @SerializedName("id")
        public String id;

        @SerializedName("conversationId")
        public String conversationId;

        @SerializedName("isRead")
        public int read;
    }
    
    private static final String TAG = "XVoice:GoogleVoiceManager";

    private final Context mContext;
    private String mRnrse;
    
    public GoogleVoiceManager(Context context) {
        mContext = context;
    }

    private SharedPreferences getSettings() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    private String getAccount() {
        SharedPreferences sp = mContext.getSharedPreferences("settings", Context.MODE_PRIVATE);
        return sp.getString("account", null);
    }
    
    public boolean refreshAuth() {
        return getRnrse(true) != null;
    }
    
    private void saveRnrse(String rnrse) {
        getSettings().edit().putString("_rnr_se", rnrse).apply();
    }
    
    private String getRnrse() {
        return getRnrse(false);
    }

    private String getRnrse(boolean force) {
        if (force || mRnrse == null) {
            try {
                mRnrse = fetchRnrSe();
            } catch (Exception e) {
                mRnrse = null;
            }
        }
        return mRnrse;
    }
    
    // fetch the weirdo opaque token google voice needs...
    private String fetchRnrSe() throws Exception {
        final String authToken = getAuthToken();
        JsonObject userInfo = Ion.with(mContext, "https://www.google.com/voice/request/user")
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .asJsonObject()
                .get();

        String rnrse = userInfo.get("r").getAsString();

        try {
            TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            String number = tm.getLine1Number();
            if (number != null) {
                JsonObject phones = userInfo.getAsJsonObject("phones");
                for (Map.Entry<String, JsonElement> entry: phones.entrySet()) {
                    JsonObject phone = entry.getValue().getAsJsonObject();
                    if (!PhoneNumberUtils.compare(number, phone.get("phoneNumber").getAsString()))
                        continue;
                    if (!phone.get("smsEnabled").getAsBoolean())
                        break;
                    Log.i(TAG, "Disabling SMS forwarding to phone.");
                    Ion.with(mContext, "https://www.google.com/voice/settings/editForwardingSms/")
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
            Log.e(TAG, "Error verifying GV SMS forwarding", e);
        }

       saveRnrse(rnrse);
       return rnrse;
    }
    
    private String getAuthToken() throws Exception {
        Bundle bundle = AccountManager.get(mContext)
                .getAuthToken(new Account(getAccount(), "com.google"),
                        "grandcentral", null, true, null, null)
                .getResult();
        return bundle.getString(AccountManager.KEY_AUTHTOKEN);
    }
    
    // hit the google voice api to send a text
    public void sendGvMessage(String number, String text) throws Exception {
        final String authToken = getAuthToken();
        JsonObject json = Ion.with(mContext, "https://www.google.com/voice/sms/send/")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .setBodyParameter("phoneNumber", number)
                .setBodyParameter("sendErrorSms", "0")
                .setBodyParameter("text", text)
                .setBodyParameter("_rnr_se", getRnrse())
                .asJsonObject()
                .get();

        if (!json.get("ok").getAsBoolean())
            throw new Exception(json.toString());
    }

    /**
     * Update the read state on GV
     * @param id - GV message id
     * @param read - 0 = unread, 1 = read
     * @throws Exception
     */
    public void markGvMessageRead(String id, int read) throws Exception {
        final String authToken = getAuthToken();
        Log.d(TAG, "Marking messsage " + id + " as read");
        Ion.with(mContext, "https://www.google.com/voice/inbox/mark/")
        .onHeaders(new GvHeadersCallback(mContext, authToken))
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .setBodyParameter("messages", id)
        .setBodyParameter("read", String.valueOf(read))
        .setBodyParameter("_rnr_se", getRnrse());
    }
    
    public void deleteGvMessage(String id) throws Exception {
        final String authToken = getAuthToken();
        Ion.with(mContext, "https://www.google.com/voice/inbox/deleteMessages/")
        .onHeaders(new GvHeadersCallback(mContext, authToken))
        .setHeader("Authorization", "GoogleLogin auth=" + authToken)
        .setBodyParameter("messages", id)
        .setBodyParameter("trash", "1")
        .setBodyParameter("_rnr_se", getRnrse());
    }
    
    // refresh the messages that were on the server
    public List<Conversation> retrieveMessages() throws Exception {
        String account = getAccount();
        if (account == null)
            return new ArrayList<Conversation>();

        Log.i(TAG, "Refreshing messages");

        // tokens!
        final String authToken = getAuthToken();

        Payload payload = Ion.with(mContext, "https://www.google.com/voice/request/messages")
                .onHeaders(new GvHeadersCallback(mContext, authToken))
                .setHeader("Authorization", "GoogleLogin auth=" + authToken)
                .as(Payload.class)
                .get();

        return payload.conversations;
    }
    
    public static void invalidateToken(final Context context, final String account) {
        if (account == null)
            return;

        new Thread() {
            @Override
            public void run() {
                try {
                    // grab the auth token
                    Bundle bundle = AccountManager.get(context).getAuthToken(new Account(account, "com.google"), "grandcentral", null, true, null, null).getResult();
                    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    AccountManager.get(context).invalidateAuthToken("com.google", authToken);
                    Log.i(TAG, "Token invalidated.");
                }
                catch (Exception e) {
                    Log.e(TAG, "error invalidating token", e);
                }
            }
        }.start();
    }

    public static void getToken(final Context context, final Account account) {
        AccountManager am = AccountManager.get(context);
        if (am == null)
            return;

        am.getAuthToken(account, "grandcentral", null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Intent intent = new Intent(context, XVoicePlusService.class);
                    intent.setAction(XVoicePlusService.ACCOUNT_CHANGED);
                    context.startService(intent);

                    Log.i(TAG, "Token retrieved.");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, new Handler());
    }
    
    
}
