package dk.mrsilly.kiosk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * Self-update: reads version.json from the public kiosk-apk repo, downloads kiosk.apk when it is newer and hands it to
 * Android's installer (one tap on "Opdatér"; no tap on newer Android when allowed). Run on a background thread.
 */
final class Updater {
    static final String BASE = "https://raw.githubusercontent.com/mrsillybot/kiosk-apk/main/";
    private static final long EVERY_MS = 6 * 3600 * 1000L;
    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private Updater() { }

    /** True when the 6-hour pause since the last check is over. */
    static boolean due(Context c) {
        return System.currentTimeMillis() - c.getSharedPreferences("kiosk", Context.MODE_PRIVATE).getLong("updChecked", 0) >= EVERY_MS;
    }

    /** Checks at most every 6 hours. `force` skips that limit. */
    static void check(Context c, boolean force) {
        SharedPreferences p = c.getSharedPreferences("kiosk", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong("updChecked", 0) < EVERY_MS) return;
        p.edit().putLong("updChecked", now).apply();
        try {
            if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
                if (!p.getBoolean("updAsked", false)) {       // once: let Alex switch on "install unknown apps" for this app
                    p.edit().putBoolean("updAsked", true).apply();
                    c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
                return;
            }
            JSONObject v = new JSONObject(new String(download(BASE + "version.json"), "UTF-8"));
            @SuppressWarnings("deprecation")
            int have = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            if (v.getInt("versionCode") <= have) return;
            File apk = new File(c.getCacheDir(), "update.apk");
            try (OutputStream out = new FileOutputStream(apk)) { out.write(download(BASE + v.optString("apk", "kiosk.apk"))); }
            install(c, apk);
        } catch (Exception ignored) { }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(30000);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_BYTES) throw new java.io.IOException("too big");
            }
            return buf.toByteArray();
        } finally { h.disconnect(); }
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("kiosk", 0, apk.length())) {
                byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) out.write(chunk, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(c, id, new Intent(c, UpdateReceiver.class), flags);
            s.commit(pend.getIntentSender());
        }
    }
}
