package com.andrerinas.openheadunit.ota;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.andrerinas.openheadunit.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * İndirilen APK'yı önce uygulama cache'ine kopyalar, doğrular, sonra kurar.
 * Doğrudan Downloads URI ile kurulum araçta sık "paket ayrıştırılamadı" verir.
 * Platform/system UID'de önce {@code pm install -r -f} denenir (MG4'te en güvenilir yol;
 * {@code -f} OEM "internal storage" volume redini bypass eder).
 */
final class OtaInstaller {

    private static final String TAG = "DiAuto_OTA";
    private static final String OTA_DIR = "ota";
    private static final String OTA_APK_NAME = "update.apk";

    private OtaInstaller() {
    }

    static void install(Context context, long downloadId, Uri apkUri) throws Exception {
        File staged = stageApkToCache(context, downloadId, apkUri);
        validateApk(context, staged);

        if (Build.VERSION.SDK_INT >= 26
                && !context.getPackageManager().canRequestPackageInstalls()
                && !isSystemUid()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            throw new IllegalStateException("unknown sources permission required");
        }

        // 1) System / platform build: pm install (avoids OEM package-installer parse bugs)
        if (isSystemUid()) {
            try {
                installWithPm(staged);
                Log.i(TAG, "pm install ok: " + staged.getAbsolutePath()
                        + " size=" + staged.length());
                OtaCleanup.deleteAllOtaApks(context);
                Toast.makeText(context, R.string.ota_install_success, Toast.LENGTH_LONG).show();
                return;
            } catch (Throwable t) {
                Log.w(TAG, "pm install failed, trying PackageInstaller: " + t.getMessage());
            }
        }

        // 2) PackageInstaller session
        try {
            installWithSession(context, staged);
            Log.i(TAG, "PackageInstaller session started: " + staged.getAbsolutePath()
                    + " size=" + staged.length());
            return;
        } catch (Throwable t) {
            Log.w(TAG, "session install failed, fallback Intent: " + t.getMessage());
            if (isSystemUid()) {
                // Intent/content:// path is what triggers "paket ayrıştırılamadı" on MG4.
                throw new IllegalStateException(
                        "System install failed (pm + session). " + t.getMessage(), t);
            }
        }

        // 3) Normal apps only: Intent installer UI
        Uri contentUri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", staged);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(contentUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        grantToInstallers(context, contentUri);
        if (install.resolveActivity(context.getPackageManager()) == null) {
            throw new IllegalStateException("No package installer activity");
        }
        context.startActivity(install);
        Log.i(TAG, "install Intent started uri=" + contentUri);
    }

    /** Kurulum sırasında korumak için kaynak Downloads dosyası. */
    static File resolveApkFilePublic(Context context, long downloadId, Uri apkUri) {
        return resolveApkFile(context, downloadId, apkUri);
    }

    private static boolean isSystemUid() {
        return Process.myUid() == Process.SYSTEM_UID;
    }

    private static void installWithPm(File apk) throws Exception {
        // -r replace, -f force internal flash (bypasses MG4 volume policy that rejects
        // non-system apps on internal storage), -d allow downgrade, -t allow test packages.
        java.lang.Process process = new ProcessBuilder(
                "pm", "install", "-r", "-f", "-d", "-t", apk.getAbsolutePath()
        ).redirectErrorStream(true).start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
        }
        int code = process.waitFor();
        String result = out.toString().trim();
        Log.i(TAG, "pm install exit=" + code + " output=" + result);
        if (code != 0 || !result.toLowerCase(java.util.Locale.US).contains("success")) {
            throw new IllegalStateException(
                    result.isEmpty() ? ("pm install exit " + code) : result);
        }
    }

    private static File stageApkToCache(Context context, long downloadId, Uri apkUri) throws Exception {
        File dir = new File(context.getCacheDir(), OTA_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Cannot create ota cache dir");
        }
        File dest = new File(dir, OTA_APK_NAME);
        if (dest.exists() && !dest.delete()) {
            Log.w(TAG, "could not delete old staged apk");
        }

        try (InputStream in = openApkStream(context, downloadId, apkUri);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
            }
            out.flush();
            if (total < 1024L) {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
                throw new IllegalStateException("APK too small (" + total + " bytes)");
            }
            Log.i(TAG, "staged apk " + total + " bytes → " + dest.getAbsolutePath());
        }

        // ZIP/APK magic: PK
        try (FileInputStream fis = new FileInputStream(dest)) {
            byte[] magic = new byte[2];
            if (fis.read(magic) != 2 || magic[0] != 'P' || magic[1] != 'K') {
                //noinspection ResultOfMethodCallIgnored
                dest.delete();
                throw new IllegalStateException("Not a valid APK/ZIP (bad magic)");
            }
        }
        return dest;
    }

    private static void validateApk(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null || info.packageName == null) {
            throw new IllegalStateException("PackageManager cannot parse APK");
        }
        if (!context.getPackageName().equals(info.packageName)) {
            throw new IllegalStateException("APK package mismatch: " + info.packageName);
        }
        Log.i(TAG, "APK ok package=" + info.packageName
                + " versionName=" + info.versionName
                + " versionCode=" + (Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode));
    }

    private static void installWithSession(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(apk.length());
        // PackageManager.INSTALL_LOCATION_INTERNAL_ONLY (=1) is not in the public SDK.
        params.setInstallLocation(1);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = null;
        try {
            session = installer.openSession(sessionId);
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }
            Intent callback = new Intent(context, OtaInstallResultReceiver.class);
            callback.setAction(OtaInstallResultReceiver.ACTION_INSTALL_RESULT);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(context, sessionId, callback, flags);
            session.commit(pi.getIntentSender());
            session = null; // commit owns it
        } finally {
            if (session != null) {
                try {
                    session.abandon();
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void grantToInstallers(Context context, Uri uri) {
        String[] pkgs = {
                "com.android.packageinstaller",
                "com.google.android.packageinstaller",
                "com.samsung.android.packageinstaller"
        };
        for (String pkg : pkgs) {
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
        }
    }

    private static InputStream openApkStream(Context context, long downloadId, Uri apkUri) throws Exception {
        // 1) DownloadManager PFD
        if (downloadId > 0L) {
            try {
                android.app.DownloadManager dm =
                        (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    ParcelFileDescriptor pfd = dm.openDownloadedFile(downloadId);
                    if (pfd != null) {
                        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "openDownloadedFile: " + t.getMessage());
            }
        }
        // 2) File path
        File file = resolveApkFile(context, downloadId, apkUri);
        if (file != null && file.exists()) {
            return new FileInputStream(file);
        }
        // 3) content/file URI
        if (apkUri != null) {
            InputStream in = context.getContentResolver().openInputStream(apkUri);
            if (in != null) return in;
        }
        throw new IllegalStateException("Cannot open downloaded APK stream");
    }

    private static File resolveApkFile(Context context, long downloadId, Uri apkUri) {
        try {
            android.app.DownloadManager dm =
                    (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null && downloadId > 0L) {
                try (Cursor c = dm.query(new android.app.DownloadManager.Query().setFilterById(downloadId))) {
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI);
                        if (idx >= 0) {
                            String local = c.getString(idx);
                            if (local != null && local.startsWith("file:")) {
                                File f = new File(Uri.parse(local).getPath());
                                if (f.exists()) return f;
                            }
                        }
                        int pathIdx = c.getColumnIndex("local_filename");
                        if (pathIdx >= 0) {
                            String path = c.getString(pathIdx);
                            if (path != null) {
                                File f = new File(path);
                                if (f.exists()) return f;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "resolve download file: " + t.getMessage());
        }
        if (apkUri != null && "file".equalsIgnoreCase(apkUri.getScheme())) {
            File f = new File(apkUri.getPath());
            if (f.exists()) return f;
        }
        // Downloads klasöründe beklenen isim
        try {
            File downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null && downloads.isDirectory()) {
                File[] files = downloads.listFiles((d, name) -> {
                    if (name == null) return false;
                    String lower = name.toLowerCase(java.util.Locale.US);
                    return (lower.startsWith("diauto-mg4_") || lower.startsWith("diauto_mg4_"))
                            && lower.endsWith(".apk");
                });
                if (files != null && files.length > 0) {
                    File best = files[0];
                    for (File f : files) {
                        if (f.lastModified() > best.lastModified()) best = f;
                    }
                    return best;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
