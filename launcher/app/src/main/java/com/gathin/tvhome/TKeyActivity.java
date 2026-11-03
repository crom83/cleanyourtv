package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The "T" key on a TCL remote (local patch). The firmware starts the action com.tcl.partnercustomizer.tkey on a
 * press; TCL Channel used to answer it, now this invisible screen opens the app named in config.xml t_key_app.
 */
public final class TKeyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getString(R.string.t_key_app);
        Intent launch = pkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null && !pkg.isEmpty()) launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
            } catch (RuntimeException ignored) {
                // The app is removed or disabled: the key simply does nothing.
            }
        }
        finish();
    }
}
