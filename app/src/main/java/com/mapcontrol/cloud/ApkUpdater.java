package com.mapcontrol.cloud;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import com.mapcontrol.BuildConfig;
import com.mapcontrol.vehicle.ComfortCommands;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** F4: imzalı APK indirir, hash + sertifika + paket doğrular ve kurulum ekranını açar. */
final class ApkUpdater {

    interface Callback {
        void onResult(boolean ok, String message);
    }

    static final class Info {
        String id;
        int versionCode;
        String versionName;
        String sha256;
        String signatureSha256;
        long size;
        String releaseNotes;
        String downloadPath;
    }

    private static final long MAX_APK_BYTES = 300L * 1024 * 1024;
    private final Context context;
    private final CloudConfig config;
    private final CloudHttp http;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    ApkUpdater(Context context, CloudConfig config, CloudHttp http) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.http = http;
    }

    Info check() throws Exception {
        CloudHttp.Response response = http.getJson(
                "/api/v1/car/apk/latest", config.token(), 15_000);
        if (response.code == 204) {
            return null;
        }
        if (response.code != 200) {
            throw new Exception("http_" + response.code);
        }
        JSONObject body = new JSONObject(response.body);
        Info info = new Info();
        info.id = body.optString("id", "");
        info.versionCode = body.optInt("version_code", 0);
        info.versionName = body.optString("version_name", "");
        info.sha256 = body.optString("sha256", "").toLowerCase(Locale.US);
        info.signatureSha256 = body.optString("signature_sha256", "").toLowerCase(Locale.US);
        info.size = body.optLong("size", 0);
        info.releaseNotes = body.optString("release_notes", "");
        info.downloadPath = body.optString("download_path", "");
        if (info.id.isEmpty() || info.versionCode <= BuildConfig.VERSION_CODE
                || info.size <= 0 || info.size > MAX_APK_BYTES
                || !info.downloadPath.startsWith("/api/v1/car/apk/")) {
            return null;
        }
        return info;
    }

    void downloadAndInstall(Info info, Callback callback) {
        new Thread(() -> {
            try {
                if (!isParked()) {
                    result(callback, false, "park_required");
                    return;
                }
                File dir = new File(context.getCacheDir(), "downloads");
                if (!dir.exists() && !dir.mkdirs()) {
                    result(callback, false, "storage_unavailable");
                    return;
                }
                File apk = new File(dir, "roadmate-update.apk");
                apk.delete();
                CloudHttp.Response response = http.downloadToFile(
                        info.downloadPath, config.token(), apk, MAX_APK_BYTES);
                if (response.code != 200) {
                    apk.delete();
                    result(callback, false, "download_http_" + response.code);
                    return;
                }
                if (!info.sha256.equals(sha256(apk))) {
                    apk.delete();
                    result(callback, false, "hash_mismatch");
                    return;
                }
                if (!verifyPackage(apk, info)) {
                    apk.delete();
                    result(callback, false, "signature_mismatch");
                    return;
                }
                sendResult(info, "verified", "");
                mainHandler.post(() -> openInstaller(apk, callback));
            } catch (Exception error) {
                result(callback, false, "error");
            }
        }, "MapControlApkUpdater").start();
    }

    private boolean isParked() {
        return ComfortCommands.isConnected()
                && ComfortCommands.readSpeedKmh() == 0
                && ComfortCommands.readGear() == 1;
    }

    private boolean verifyPackage(File apk, Info info) {
        PackageManager manager = context.getPackageManager();
        PackageInfo archive = manager.getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null
                || !BuildConfig.APPLICATION_ID.equals(archive.packageName)
                || archive.getLongVersionCode() != info.versionCode) {
            return false;
        }
        SigningInfo signing = archive.signingInfo;
        if (signing == null) {
            return false;
        }
        Signature[] signatures = signing.hasMultipleSigners()
                ? signing.getApkContentsSigners()
                : signing.getSigningCertificateHistory();
        String expected = BuildConfig.OTA_SIGNER_SHA256.toLowerCase(Locale.US);
        if (expected.isEmpty() || !expected.equals(info.signatureSha256)) {
            return false;
        }
        for (Signature signature : signatures) {
            if (expected.equals(sha256(signature.toByteArray()))) {
                return true;
            }
        }
        return false;
    }

    private void openInstaller(File apk, Callback callback) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    context, BuildConfig.APPLICATION_ID + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            result(callback, true, "installer_opened");
        } catch (Exception error) {
            result(callback, false, "installer_failed");
        }
    }

    private void sendResult(Info info, String status, String reason) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", status);
            body.put("release_id", info.id);
            body.put("reason", reason);
            http.postJson("/api/v1/car/ota/result", config.token(), body, 2048);
        } catch (Exception ignored) {
            // OTA sonucu bir sonraki raporda tekrar denenebilir.
        }
    }

    private void result(Callback callback, boolean ok, String message) {
        mainHandler.post(() -> callback.onResult(ok, message));
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                digest.update(chunk, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            return "";
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            output.append(String.format(Locale.US, "%02x", item));
        }
        return output.toString();
    }
}
