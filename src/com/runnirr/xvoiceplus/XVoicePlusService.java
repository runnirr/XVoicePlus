package com.runnirr.xvoiceplus;

import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.runnirr.xvoiceplus.gv.GoogleVoiceManager;
import com.runnirr.xvoiceplus.gv.GvResponse.Conversation;
import com.runnirr.xvoiceplus.gv.GvResponse.Message;
import com.runnirr.xvoiceplus.receivers.BootCompletedReceiver;
import com.runnirr.xvoiceplus.receivers.MessageEventReceiver;
import com.runnirr.xvoiceplus.receivers.UserPollReceiver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by koush on 7/5/13.
 */
public class XVoicePlusService extends IntentService {
    private static final String TAG = XVoicePlusService.class.getName();
    
    private GoogleVoiceManager GVManager = new GoogleVoiceManager(this);
    
    public XVoicePlusService() {
        this("XVoicePlusService");
    }

    public XVoicePlusService(String name) {
        super(name);
    }
    
    private SharedPreferences getAppSettings() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }
    
    private SharedPreferences getRecentMessages() {
        return getSharedPreferences("recent_messages", MODE_PRIVATE);
    }

    // parse out the intent extras from android.intent.action.NEW_OUTGOING_SMS
    // and send it off via google voice
    private void handleOutgoingSms(Intent intent) {
        boolean multipart = intent.getBooleanExtra("multipart", false);
        String destAddr = intent.getStringExtra("destAddr");
        String scAddr = intent.getStringExtra("scAddr");
        ArrayList<String> parts = intent.getStringArrayListExtra("parts");
        ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
        ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

        onSendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, multipart);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // handle an outgoing sms
        if (MessageEventReceiver.OUTGOING_SMS.equals(intent.getAction())) {
            handleOutgoingSms(intent);
            MessageEventReceiver.completeWakefulIntent(intent);
        }
        else if (UserPollReceiver.USER_POLL.equals(intent.getAction())) {
            startRefresh();
            UserPollReceiver.completeWakefulIntent(intent);
        }
        else if (MessageEventReceiver.INCOMING_VOICE.equals(intent.getAction())) {
            Message m = new Message();
            Bundle extras = intent.getExtras();

            m.date = Long.valueOf(extras.getString("call_time"));
            m.phoneNumber = extras.getString("sender_address");
            m.type = Integer.valueOf(extras.getString("call_type"));
            m.message = extras.getString("call_content");
            m.id = extras.getString("call_id");

            if (m.type == VOICE_INCOMING_SMS) {
                Log.i(TAG, "Handling push message");
                Set<String> recentPushMessages = getRecentMessages().getStringSet("push_messages", new HashSet<String>());
                recentPushMessages.add(m.id);
                getRecentMessages().edit().putStringSet("push_messages", recentPushMessages).apply();
                synthesizeMessage(m);
            } else {
                startRefresh();
            }
            MessageEventReceiver.completeWakefulIntent(intent);
        }
        else if (BootCompletedReceiver.BOOT_COMPLETED.equals(intent.getAction())) {
            startRefresh();
            BootCompletedReceiver.completeWakefulIntent(intent);
        }
        else if (GoogleVoiceManager.ACCOUNT_CHANGED.equals(intent.getAction())) {
            GVManager = new GoogleVoiceManager(this);
        }
    }

    // mark all sent intents as failures
    public void fail(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si != null){
                try {
                    si.send();
                }
                catch (Exception e) {
                    Log.w(TAG, "Error marking failed intent", e);
                }
            }
        }
    }

    // mark all sent intents as successfully sent
    public void success(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si != null) {
                try {
                    si.send(Activity.RESULT_OK);
                }
                catch (Exception e) {
                    Log.w(TAG, "Error marking success intent", e);
                }
            }
        }
    }

    

    // mark an outgoing text as recently sent, so if it comes in via
    // round trip, we ignore it.
    private final Object recentLock = new Object();
    private void addRecent(String text) {
        synchronized(recentLock) {
            SharedPreferences savedRecent = getRecentMessages();
            Set<String> recentMessage = savedRecent.getStringSet("recent", new HashSet<String>());
            recentMessage.add(text);
            savedRecent.edit().putStringSet("recent", recentMessage).apply();
        }
    }

    private boolean removeRecent(String text) {
        synchronized(recentLock) {
            SharedPreferences savedRecent = getRecentMessages();
            Set<String> recentMessage = savedRecent.getStringSet("recent", new HashSet<String>());
            if (recentMessage.remove(text)) {
                savedRecent.edit().putStringSet("recent", recentMessage).apply();
                return true;
            }
            return false;
        }
    }

    // send an outgoing sms event via google voice
    private void onSendMultipartText(String destAddr, String scAddr, List<String> texts,
            final List<PendingIntent> sentIntents, final List<PendingIntent> deliveryIntents,
            boolean multipart) {
        // combine the multipart text into one string
        StringBuilder textBuilder = new StringBuilder();
        for (String text: texts) {
            textBuilder.append(text);
        }
        String text = textBuilder.toString();

        try {
            // send it off, and note that we recently sent this message
            // for round trip tracking
            GVManager.sendGvMessage(destAddr, text);
            addRecent(text);
            success(sentIntents);
            return;
        }
        catch (Exception e) {
            Log.d(TAG, "send error", e);
        }

        try {
            // on failure, fetch info and try again
            GVManager.refreshAuth();
            synchronized (recentLock) {
                GVManager.sendGvMessage(destAddr, text);
                addRecent(text);
            }
            success(sentIntents);
        }
        catch (Exception e) {
            Log.d(TAG, "send failure", e);
            fail(sentIntents);
        }
    }

    private static final int VOICE_INCOMING_SMS = 10;
    private static final int VOICE_OUTGOING_SMS = 11;

    private static final int PROVIDER_INCOMING_SMS = 1;
    private static final int PROVIDER_OUTGOING_SMS = 2;

    private static final Uri URI_SENT = Uri.parse("content://sms/sent");
    private static final Uri URI_RECEIVED = Uri.parse("content://sms/inbox");

    boolean messageExists(Message m) {
        Uri uri = m.type == VOICE_INCOMING_SMS ? URI_RECEIVED : URI_SENT;
        return messageExists(m, uri);
    }
    
    boolean messageExists(Message m, Uri uri) {
        Cursor c = getContentResolver().query(uri, null, "date = ? AND body = ?",
                new String[] { String.valueOf(m.date), m.message }, null);
        try {
            return c.moveToFirst();
        }
        finally {
            c.close();
        }
    }

    // insert a message into the sms/mms provider.
    // we do this in the case of outgoing messages
    // that were not sent via this phone, and also on initial
    // message sync.
    void insertMessage(Message m) {
        Uri uri;
        int type;
        if (m.type == VOICE_INCOMING_SMS) {
            uri = URI_RECEIVED;
            type = PROVIDER_INCOMING_SMS;
        } else if (m.type == VOICE_OUTGOING_SMS) {
            uri = URI_SENT;
            type = PROVIDER_OUTGOING_SMS;
        } else {
            return;
        }

        if (!messageExists(m, uri)) {
            ContentValues values = new ContentValues();
            values.put("address", m.phoneNumber);
            values.put("body", m.message);
            values.put("type", type);
            values.put("date", m.date);
            values.put("date_sent", m.date);
            values.put("read", m.read);
            getContentResolver().insert(uri, values);
        }
    }

    void synthesizeMessage(Message m) {
        if (!messageExists(m, URI_RECEIVED)){
            try{
                SmsUtils.createFakeSms(this, m.phoneNumber, m.message, m.date);
            } catch (IOException e){
                Log.e(TAG, "IOException when creating fake sms, ignoring");
            }
        }
    }
    
    private void markReadIfNeeded(Message message){
        if (message.read == 0){
            Uri uri;
            if (message.type == VOICE_INCOMING_SMS) {
                uri = URI_RECEIVED;
            } else if (message.type == VOICE_OUTGOING_SMS) {
                uri = URI_SENT;
            } else {
                return;
            }

            Cursor c = getContentResolver().query(uri, null, "date = ? AND body = ?",
                    new String[] { String.valueOf(message.date), message.message }, null);
            try {
                if(c.moveToFirst()){
                    GVManager.markGvMessageRead(message.id, 1);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error marking message as read. ID: " + message.id, e);
            } finally {
                c.close();
            }
        }
    }

    private void updateMessages() throws Exception {
        List<Conversation> conversations = GVManager.retrieveMessages();

        long timestamp = getAppSettings().getLong("timestamp", 0);
        LinkedList<Message> oldMessages = new LinkedList<Message>();
        LinkedList<Message> newMessages = new LinkedList<Message>();
        for (Conversation conversation: conversations) {
            for (Message m : conversation.messages) {
                if(m.phoneNumber != null && m.message != null) {
                    markReadIfNeeded(m);
                    if (m.date > timestamp) {
                        newMessages.add(m);
                    } else {
                        oldMessages.add(m);
                    }
                }
            }
        }

        // sort by date order so the events get added in the same order
        Collections.sort(newMessages, new Comparator<Message>() {
            @Override
            public int compare(Message lhs, Message rhs) {
                return Long.valueOf(lhs.date).compareTo(rhs.date);
            }
        });

        long max = timestamp;
        for (Message message : newMessages) {
            max = Math.max(max, message.date);

            // on first sync, just populate the mms provider...
            // don't send any broadcasts.
            if (timestamp == 0) {
                insertMessage(message);
                continue;
            }

            // sync up outgoing messages
            if (message.type == VOICE_OUTGOING_SMS) {
                if (!removeRecent(message.message)) {
                    insertMessage(message);
                }
            } else if (message.type == VOICE_INCOMING_SMS) {
                Set<String> recentPushMessages = getRecentMessages().getStringSet("push_messages", new HashSet<String>());
                if (recentPushMessages.remove(message.id)) {
                    // We already synthesized this message
                    getRecentMessages().edit().putStringSet("push_messages", recentPushMessages).apply();
                } else {
                    synthesizeMessage(message);
                }
            }
        }

        getAppSettings().edit().putLong("timestamp", max).apply();
    }

    void startRefresh() {
        try {
            updateMessages();
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing messages", e);
        }
    }
}
