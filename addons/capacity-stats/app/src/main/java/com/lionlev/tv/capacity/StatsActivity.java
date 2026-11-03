package com.lionlev.tv.capacity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.StatFs;
import android.widget.TextView;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Memory and storage as two ring gauges, read every {@link #SAMPLE_MS} while the screen is open (nothing runs in the
 * background), and a button that unloads other apps' background processes.
 */
public final class StatsActivity extends Activity {
    static final long SAMPLE_MS = 10_000;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private volatile Handler background;
    private Gauge memoryGauge, storageGauge;
    private TextView memoryFree, memoryUnit, memoryDetail, storageFree, storageUnit, storageDetail, cleanResult;
    private boolean cleaning;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_stats);
        memoryGauge = findViewById(R.id.memoryGauge);
        storageGauge = findViewById(R.id.storageGauge);
        memoryFree = findViewById(R.id.memoryFree);
        memoryUnit = findViewById(R.id.memoryUnit);
        memoryDetail = findViewById(R.id.memoryDetail);
        storageFree = findViewById(R.id.storageFree);
        storageUnit = findViewById(R.id.storageUnit);
        storageDetail = findViewById(R.id.storageDetail);
        cleanResult = findViewById(R.id.cleanResult);
        TextView clean = findViewById(R.id.clean);
        clean.setOnClickListener(v -> clean());
        clean.requestFocus();
    }

    @Override
    public void onResume() {
        super.onResume();
        worker = new HandlerThread("addon-stats");
        worker.start();
        Handler current = new Handler(worker.getLooper());
        background = current;
        current.post(new Runnable() {
            @Override
            public void run() {
                if (background != current) return;
                Reading r = read(StatsActivity.this);
                ui.post(() -> {
                    if (background == current) show(r);
                });
                if (background == current) current.postDelayed(this, SAMPLE_MS);
            }
        });
    }

    @Override
    public void onPause() {
        background = null;
        if (worker != null) {
            worker.quitSafely();
            worker = null;
        }
        ui.removeCallbacksAndMessages(null);
        super.onPause();
    }

    /** One look at /proc/meminfo and the data filesystem; -1 where a number can't be read. */
    static final class Reading {
        long memFreeMb = -1, memTotalMb = -1, diskFree = -1, diskTotal = -1;
    }

    static Reading read(Context c) {
        Reading r = new Reading();
        try (FileReader reader = new FileReader("/proc/meminfo")) {
            long[] m = MemoryCapacity.readMb(reader);
            r.memFreeMb = m[0];
            r.memTotalMb = m[1];
        } catch (IOException ignored) {
            // Some boxes hide /proc/meminfo: the memory card shows a dash.
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            r.diskFree = fs.getAvailableBytes();
            r.diskTotal = fs.getTotalBytes();
        } catch (IllegalArgumentException ignored) {
            // The data filesystem can't be read: the storage card shows a dash.
        }
        return r;
    }

    private void show(Reading r) {
        Locale locale = Locale.getDefault();
        float mem = Levels.share(r.memFreeMb, r.memTotalMb);
        if (mem >= 0) {
            memoryGauge.setShare(mem);
            memoryFree.setText(getString(R.string.percent, Levels.percent(mem)));
            memoryDetail.setText(getString(R.string.memory_detail, r.memFreeMb, r.memTotalMb));
        } else {
            memoryFree.setText(R.string.unavailable);
        }
        memoryUnit.setText(R.string.free);
        float disk = Levels.share(r.diskFree, r.diskTotal);
        if (disk >= 0) {
            storageGauge.setShare(disk);
            storageFree.setText(getString(R.string.percent, Levels.percent(disk)));
            storageDetail.setText(getString(R.string.storage_detail, Levels.gb(r.diskFree, locale),
                    Levels.gb(r.diskTotal, locale)));
        } else {
            storageFree.setText(R.string.unavailable);
        }
        storageUnit.setText(R.string.free);
    }

    /**
     * Unloads every other app's background processes (killBackgroundProcesses: Android itself keeps the app on
     * screen, system and persistent processes alive), leaving the home screen alone so it isn't slow to come back.
     */
    private void clean() {
        if (cleaning) return;
        cleaning = true;
        cleanResult.setText(R.string.cleaning);
        Context app = getApplicationContext();
        new Thread(() -> {
            ActivityManager am = (ActivityManager) app.getSystemService(ACTIVITY_SERVICE);
            long before = read(app).memFreeMb;
            for (String pkg : others(app)) {
                try {
                    am.killBackgroundProcesses(pkg);
                } catch (RuntimeException ignored) {
                    // The package went away or the system refused: go on.
                }
            }
            try {
                Thread.sleep(1500);   // memory takes a moment to come back to the system
            } catch (InterruptedException ignored) {
            }
            long after = read(app).memFreeMb;
            ui.post(() -> {
                cleaning = false;
                if (isFinishing()) return;
                long freed = Math.max(0, after - before);   // a few MB is noise, not something freed
                cleanResult.setText(freed >= 5 ? getString(R.string.cleaned, freed, after)
                        : getString(R.string.cleaned_none, after));
                show(read(this));   // the new numbers at once, not at the next reading
            });
        }, "clean").start();
    }

    /** Apps with a launcher entry (what this app may see), without itself and the home screen. */
    private static Set<String> others(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> out = new HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (ri.activityInfo != null) out.add(ri.activityInfo.packageName);
            }
        }
        out.remove(c.getPackageName());
        ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        if (home != null && home.activityInfo != null) out.remove(home.activityInfo.packageName);
        return out;
    }
}
