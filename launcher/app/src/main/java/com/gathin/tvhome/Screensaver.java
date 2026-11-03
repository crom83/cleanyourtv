package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV's screensaver from the launcher settings (local patch): which one, or none, and after how long. The
 * values are the system's own (Settings.Secure screensaver_components and screensaver_enabled, Settings.System
 * screen_off_timeout), so the TV's settings show the same. Writing them needs WRITE_SECURE_SETTINGS, granted
 * over ADB; without it the launcher opens the TV's screensaver settings instead.
 */
final class Screensaver {
    static final String DREAM_ACTION = "android.service.dreams.DreamService";
    /** Minutes the owner can pick from. */
    static final int[] MINUTES = {5, 10, 15, 30};

    /** One installed screensaver. */
    static final class Dream {
        final ComponentName component;
        final String label;

        Dream(ComponentName component, String label) {
            this.component = component;
            this.label = label;
        }
    }

    private Screensaver() {}

    static boolean canWrite(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Every installed screensaver, by name. */
    static List<Dream> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        List<Dream> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentServices(new Intent(DREAM_ACTION), PackageManager.GET_META_DATA)) {
            if (ri.serviceInfo == null) continue;
            out.add(new Dream(new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name),
                    Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), ri.serviceInfo.packageName)));
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /** The screensaver in use, or null when it is off or not set. */
    static ComponentName current(Context c) {
        if (Settings.Secure.getInt(c.getContentResolver(), "screensaver_enabled", 1) == 0) return null;
        return first(Settings.Secure.getString(c.getContentResolver(), "screensaver_components"));
    }

    /** The first component of a comma-separated list, or null. */
    static ComponentName first(String components) {
        if (components == null || components.trim().isEmpty()) return null;
        String head = components.split(",")[0].trim();
        return head.isEmpty() ? null : ComponentName.unflattenFromString(head);
    }

    /** Puts this screensaver in use, or turns the screensaver off for null. */
    static void set(Context c, ComponentName dream) {
        if (dream == null) {
            Settings.Secure.putInt(c.getContentResolver(), "screensaver_enabled", 0);
            return;
        }
        Settings.Secure.putString(c.getContentResolver(), "screensaver_components", dream.flattenToString());
        Settings.Secure.putInt(c.getContentResolver(), "screensaver_enabled", 1);
    }

    /** Minutes before the screensaver starts (the system's screen-off timeout), or -1 when unknown. */
    static int minutes(Context c) {
        int ms = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, -1);
        return ms <= 0 ? -1 : Math.round(ms / 60_000f);
    }

    static void setMinutes(Context c, int minutes) {
        Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000);
    }

    /** The TV's own screensaver screen, for a launcher without the permission. */
    static boolean openSystem(Context c) {
        try {
            c.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
