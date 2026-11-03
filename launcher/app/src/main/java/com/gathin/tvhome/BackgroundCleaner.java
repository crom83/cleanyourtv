package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.widget.Toast;

/**
 * Кнопка-щётка в верхней панели (локальная доработка, clean_button): выгружает фоновые процессы всех приложений
 * через killBackgroundProcesses и показывает, сколько памяти освободилось. Открытое на экране приложение,
 * системные и постоянные процессы Android не трогает; часть служб система сразу запускает снова.
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
                    // Пакет пропал или система отказала: просто дальше.
                }
            }
            try {
                Thread.sleep(1500); // память возвращается системе не мгновенно
            } catch (InterruptedException ignored) {
            }
            long freed = Math.max(0, availMb(am) - before);
            long free = availMb(am);
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    "Освобождено " + freed + " МБ · свободно " + free + " МБ", Toast.LENGTH_LONG).show());
        }, "cleaner").start();
    }

    private static long availMb(ActivityManager am) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem / (1024 * 1024);
    }
}
