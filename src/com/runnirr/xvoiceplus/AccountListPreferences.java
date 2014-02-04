package com.runnirr.xvoiceplus;

import org.cyanogenmod.voiceplus.VoicePlusService;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.util.Log;

public class AccountListPreferences extends ListPreference {

    private static final String TAG = "XVoiceAccountList";

    public AccountListPreferences(Context context) {
        this(context, null);
    }

    public AccountListPreferences(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Account[] accounts = AccountManager.get(context).getAccountsByType("com.google");
        String[] entries = new String[accounts.length];
        for (int i = 0; i < accounts.length; i++) {
            entries[i] = accounts[i].name;
        }
        setEntries(entries);
        setEntryValues(entries);

        setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String newAccountString = (String) newValue;
                for (int i = 0; i < accounts.length; i++) {
                    if (accounts[i].name.equals(newAccountString)) {
                        final Account newAccount = accounts[i];
                        final String previousAccount = getSharedPreferences().getString("account", null);

                        invalidateToken(previousAccount);
                        getToken(newAccount);

                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void invalidateToken(final String account) {
        if (account == null)
            return;

        new Thread() {
            @Override
            public void run() {
                try {
                    // grab the auth token
                    Bundle bundle = AccountManager.get(getContext()).getAuthToken(new Account(account, "com.google"), "grandcentral", null, true, null, null).getResult();
                    String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    AccountManager.get(getContext()).invalidateAuthToken("com.google", authToken);
                    Log.i(TAG, "Token invalidated.");
                }
                catch (Exception e) {
                    Log.e(TAG, "error invalidating token", e);
                }
            }
        }.start();    
    }

    private void getToken(final Account account) {
        AccountManager am = AccountManager.get(getContext());
        if (am == null)
            return;

        am.getAuthToken(account, "grandcentral", null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Intent intent = new Intent(getContext(), VoicePlusService.class);
                    intent.setAction(VoicePlusService.ACCOUNT_CHANGED);
                    getContext().startService(intent);

                    Log.i(TAG, "Token retrieved.");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, new Handler());
    }
}
