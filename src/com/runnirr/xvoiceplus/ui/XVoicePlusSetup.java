package com.runnirr.xvoiceplus.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

public class XVoicePlusSetup extends Activity implements OnSharedPreferenceChangeListener {

    private final XVoicePlusFragment mVPFragment = new XVoicePlusFragment();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, mVPFragment)
            .commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        mVPFragment.getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mVPFragment.getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mVPFragment.updateSummary(key);
    }
}
