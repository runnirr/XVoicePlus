package com.runnirr.xvoiceplus.ui;

import com.runnirr.xvoiceplus.gv.GoogleVoiceManager;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
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
                Log.d(TAG, "Account changed to " + newValue);
                for (int i = 0; i < accounts.length; i++) {
                    if (accounts[i].name.equals(newAccountString)) {
                        final String previousAccount = getSharedPreferences().getString("account", null);
                        GoogleVoiceManager.invalidateToken(getContext(), previousAccount);

                        final Account newAccount = accounts[i];
                        GoogleVoiceManager.getToken(getContext(), newAccount);

                        return true;
                    }
                }
                return false;
            }
        });
    }
}
