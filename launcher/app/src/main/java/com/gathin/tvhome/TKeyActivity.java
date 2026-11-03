package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Кнопка «T» на пульте TCL (локальная доработка). Прошивка по нажатию запускает действие
 * com.tcl.partnercustomizer.tkey; раньше на него отвечал TCL Channel, теперь этот невидимый экран
 * открывает приложение из config.xml t_key_app.
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
                // Приложение удалено или выключено: кнопка просто ничего не делает.
            }
        }
        finish();
    }
}
