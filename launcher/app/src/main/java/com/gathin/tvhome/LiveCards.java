package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Live module cards: a card the owner chose to show may also publish a short line for its tile, such as "12°" with a
 * cloud or "5" with a bulb. The app names its own provider in a {@value #META} meta-data; the launcher reads
 * content://authority/card (columns "text" and "icon", a small PNG) only while Home is on screen, watches it for
 * changes (and asks again every minute while Home stays up), and draws the result over the tile. The launcher stays offline: fetching is the add-on's business.
 */
final class LiveCards {
    static final String META = "com.gathin.tvhome.live";
    static final int MAX_TEXT = 12;
    static final int MAX_ICON_BYTES = 32 * 1024;
    static final int MAX_ICON_PX = 128;
    private static final String[] COLUMNS = {"text", "icon"};
    /** While Home stays on screen, ask again this often; the add-on decides whether that needs the network. */
    static final long REREAD_MS = 60_000;

    /** What a tile shows: text and an optional icon. */
    static final class Live {
        final String text;
        final Bitmap icon;

        Live(String text, Bitmap icon) {
            this.text = text;
            this.icon = icon;
        }
    }

    private final Context ctx;
    private final Handler main;
    private final Consumer<String> onChange;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Package to its card uri, for the shown cards that publish one. */
    private Map<String, Uri> sources = Collections.emptyMap();
    private final Map<String, ContentObserver> observers = new HashMap<>();
    private final Map<String, Live> live = new HashMap<>();
    private boolean started;
    private final Runnable reread = new Runnable() {
        @Override
        public void run() {
            for (Map.Entry<String, Uri> s : sources.entrySet()) read(s.getKey(), s.getValue());
            if (started) main.postDelayed(this, REREAD_MS);
        }
    };

    LiveCards(Context ctx, Handler main, Consumer<String> onChange) {
        this.ctx = ctx;
        this.main = main;
        this.onChange = onChange;
    }

    /** The shown cards changed (install, update, the owner's decision): find which of them publish a line. */
    void setCards(List<Cards.Card> shown) {
        Map<String, Uri> found = new HashMap<>();
        PackageManager pm = ctx.getPackageManager();
        for (Cards.Card c : shown) {
            Uri uri = uriFor(pm, c.pkg);
            if (uri != null) found.put(c.pkg, uri);
        }
        boolean was = started;
        if (was) stop();
        sources = found;
        live.keySet().retainAll(found.keySet());
        if (was) start();
    }

    /** Null unless the package names a provider of its own (not another app's that claims the authority). */
    private static Uri uriFor(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.equals(provider.packageName)) return null;
            return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim())
                    .appendPath("card").build();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }

    /** Home is on screen: watch every source and read it once. */
    void start() {
        started = true;
        for (Map.Entry<String, Uri> s : sources.entrySet()) {
            String pkg = s.getKey();
            Uri uri = s.getValue();
            ContentObserver o = new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange) {
                    read(pkg, uri);
                }
            };
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, o);
                observers.put(pkg, o);
            } catch (SecurityException gone) {
                // The provider went away; the tile keeps what it had.
            }
            read(pkg, uri);
        }
        main.removeCallbacks(reread);
        if (!sources.isEmpty()) main.postDelayed(reread, REREAD_MS);
    }

    /** Home is off screen: nothing is read until it is back. */
    void stop() {
        started = false;
        main.removeCallbacks(reread);
        for (ContentObserver o : observers.values()) ctx.getContentResolver().unregisterContentObserver(o);
        observers.clear();
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    Live get(String pkg) {
        return live.get(pkg);
    }

    /** One query off the main thread (the provider lives in the add-on's process). */
    private void read(String pkg, Uri uri) {
        try {
            io.execute(() -> {
                String text = null;
                byte[] png = null;
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c == null || !c.moveToFirst()) return;
                    text = c.isNull(0) ? null : c.getString(0);
                    if (c.getColumnCount() > 1 && !c.isNull(1)) png = c.getBlob(1);
                } catch (RuntimeException gone) {
                    return;   // uninstalled or crashed mid-query: keep what is on screen
                }
                Live result = text == null || text.isEmpty() ? null
                        : checkText(text) ? new Live(text, decodeIcon(png)) : null;
                main.post(() -> {
                    if (!sources.containsKey(pkg)) return;
                    if (result == null) live.remove(pkg);
                    else live.put(pkg, result);
                    onChange.accept(pkg);
                });
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    /** A small PNG within the size limits, or null. */
    static Bitmap decodeIcon(byte[] png) {
        if (png == null || png.length == 0 || png.length > MAX_ICON_BYTES) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > MAX_ICON_PX || bounds.outHeight > MAX_ICON_PX) return null;
        return BitmapFactory.decodeByteArray(png, 0, png.length);
    }

    /**
     * Short words and numbers only: letters, digits, space and ° % · . , : + - /. Anything else (control or bidi
     * characters, emoji, a long sentence) drops the line rather than drawing something the owner didn't agree to.
     */
    static boolean checkText(String s) {
        if (s == null || s.isEmpty() || s.length() > MAX_TEXT) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && " °%·.,:+-/".indexOf(c) < 0) return false;
        }
        return s.trim().length() > 0;
    }
}
