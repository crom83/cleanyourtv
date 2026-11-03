package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.widget.Toast;

/**
 * The broom button in the top bar (local patch, clean_button): unloads every app's background processes with
 * killBackgroundProcesses and shows how much memory came free. Android leaves the app on screen, system and
 * persistent processes alone, and starts some services again right away.
 */
final class BackgroundCleaner {
    private BackgroundCleaner() { }

    static void run(Activity activity) {
        ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        String self = activity.getPackageName();
        new Thread(() -> {
            long before = availMb(am);
            int count = 0;
            for (ApplicationInfo app : activity.getPackageManager().getInstalledApplications(0)) {
                if (app.packageName.equals(self)) continue;
                try {
                    am.killBackgroundProcesses(app.packageName);
                    count++;
                } catch (RuntimeException ignored) {
                    // The package went away or the system refused: go on.
                }
            }
            try {
                Thread.sleep(1500); // memory takes a moment to come back to the system
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, availMb(am) - before);
            long free = availMb(am);
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    activity.getString(R.string.clean_freed, freed, free), Toast.LENGTH_LONG).show());
        }, "cleaner").start();
    }

    private static long availMb(ActivityManager am) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem / (1024 * 1024);
    }
}
