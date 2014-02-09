package com.runnirr.xvoiceplus.gv;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.ion.HeadersCallback;

public class GvHeadersCallback implements HeadersCallback {
    
    private static final String TAG = "XVoicePlus:HeadersCallback";
    
    private final String mAuthToken;
    private final Context mContext;
    
    public GvHeadersCallback(Context context, String authToken) {
        super();
        mAuthToken = authToken;
        mContext = context;
    }

    @Override
    public void onHeaders(RawHeaders headers) {
        if (headers.getResponseCode() == 401) {
            Log.e(TAG, "Refresh failed:\n" + headers.toHeaderString());
            AccountManager.get(mContext).invalidateAuthToken("com.google", mAuthToken);
            removeRnrse();
        }
    }

    private void removeRnrse() {
        getSettings().edit().remove("_rnr_se").apply();
    }

    private SharedPreferences getSettings() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }
}
