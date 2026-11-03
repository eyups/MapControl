package com.mapcontrol.assistant.wake;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Uyandırma modeli indirme/çıkarma. Model APK'ya gömülmez; ilk etkinleştirmede
 * kullanıcı onayıyla indirilir, SHA-256 doğrulanır ve
 * {@code getFilesDir()/assistant/models/} altına çıkarılır. Boot'ta indirme yok.
 */
public final class WakeWordModelStore {

    private static final String TAG = "VaWakeModel";

    public static final String URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip";
    public static final long EXPECTED_BYTES = 36_855_784L;
    /** vosk-model-small-tr-0.3.zip */
    public static final String EXPECTED_SHA256 =
            "8c8d07cec1bce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5";
    public static final String ARCHIVE_ROOT = "vosk-model-small-tr-0.3";
    public static final String MODEL_DIR_NAME = "vosk-model-small-tr";
    public static final long APPROX_SIZE_MB = 35L;

    public interface Progress {
        void onProgress(long downloaded, long total);

        void onError(String message);

        void onComplete();
    }

    private WakeWordModelStore() {
    }

    public static File modelsRoot(Context context) {
        return new File(context.getApplicationContext().getFilesDir(),
                "assistant/models");
    }

    public static File modelDir(Context context) {
        return new File(modelsRoot(context), MODEL_DIR_NAME);
    }

    public static boolean isInstalled(Context context) {
        return looksComplete(modelDir(context));
    }

    /**
     * Modelin zorunlu parçaları var mı. Ace/am/conf klasörleri küçük Türkçe
     * modelinin çekirdeğidir; biri eksikse indirme yarım kalmıştır.
     */
    public static boolean looksComplete(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        // vosk-model-small-tr-0.3 düz yapıdadır: final.mdl + HCLr.fst + mfcc.conf
        return new File(dir, "final.mdl").isFile()
                && new File(dir, "HCLr.fst").isFile()
                && new File(dir, "mfcc.conf").isFile();
    }

    public static long installedBytes(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                total += installedBytes(child);
            }
        }
        return total;
    }

    public static void delete(Context context) {
        deleteRecursive(modelDir(context));
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    /**
     * Bloklayan indirme; arka plan thread'inde çağır. Başarıda {@link Progress#onComplete()}.
     */
    public static void download(Context context, Progress progress) {
        File root = modelsRoot(context);
        File zipFile = new File(context.getCacheDir(), "vosk-tr-model.zip");
        File tempDir = new File(root, "extract-tmp");
        try {
            if (!root.isDirectory() && !root.mkdirs()) {
                fail(progress, "Model klasörü oluşturulamadı.");
                return;
            }
            deleteRecursive(tempDir);
            if (!downloadFile(zipFile, progress)) {
                return;
            }
            String hash = sha256(zipFile);
            if (EXPECTED_SHA256 != null && !EXPECTED_SHA256.isEmpty()
                    && !EXPECTED_SHA256.equalsIgnoreCase(hash)) {
                fail(progress, "Model bütünlük kontrolü başarısız.");
                //noinspection ResultOfMethodCallIgnored
                zipFile.delete();
                return;
            }
            if (!extract(zipFile, tempDir)) {
                fail(progress, "Model arşivi açılamadı.");
                return;
            }
            File source = new File(tempDir, ARCHIVE_ROOT);
            if (!source.isDirectory()) {
                // Bazı arşivlerde tek üst klasör adı farklı olabilir.
                File[] children = tempDir.listFiles();
                if (children != null && children.length == 1 && children[0].isDirectory()) {
                    source = children[0];
                }
            }
            if (!looksComplete(source)) {
                fail(progress, "Model dosyaları eksik.");
                return;
            }
            File target = modelDir(context);
            deleteRecursive(target);
            if (!source.renameTo(target)) {
                if (!copyTree(source, target)) {
                    fail(progress, "Model taşınamadı.");
                    return;
                }
            }
            deleteRecursive(tempDir);
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
            Log.i(TAG, "Model kuruldu: " + installedBytes(target) + " bayt");
            if (progress != null) {
                progress.onComplete();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Model indirme hatası", t);
            fail(progress, "İndirme hatası: " + t.getClass().getSimpleName());
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private static boolean downloadFile(File target, Progress progress) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            connection = (HttpURLConnection) new URL(URL).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                fail(progress, "Model indirilemedi (" + code + ").");
                return false;
            }
            long total = connection.getContentLengthLong();
            input = connection.getInputStream();
            output = new FileOutputStream(target);
            byte[] buffer = new byte[16384];
            long downloaded = 0L;
            long lastReport = 0L;
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
                downloaded += read;
                long now = System.currentTimeMillis();
                if (progress != null && now - lastReport > 400L) {
                    lastReport = now;
                    progress.onProgress(downloaded, total);
                }
            }
            output.flush();
            if (progress != null) {
                progress.onProgress(downloaded, total);
            }
            return downloaded > 1_000_000L;
        } catch (Throwable t) {
            fail(progress, "İndirme hatası: " + t.getClass().getSimpleName());
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean extract(File zipFile, File targetDir) {
        if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
            return false;
        }
        ZipInputStream zip = null;
        try {
            zip = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[16384];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..")) {
                    continue;
                }
                File outFile = new File(targetDir, name);
                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    outFile.mkdirs();
                    continue;
                }
                File parent = outFile.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return false;
                }
                FileOutputStream out = new FileOutputStream(outFile);
                try {
                    int read;
                    while ((read = zip.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                } finally {
                    out.close();
                }
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Arşiv açma hatası", t);
            return false;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean copyTree(File source, File target) {
        try {
            if (source.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) {
                    return false;
                }
                File[] children = source.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (!copyTree(child, new File(target, child.getName()))) {
                            return false;
                        }
                    }
                }
                return true;
            }
            InputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            } finally {
                in.close();
                out.close();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        byte[] data = digest.digest();
        StringBuilder b = new StringBuilder(data.length * 2);
        for (byte value : data) {
            b.append(String.format(Locale.US, "%02x", value & 0xFF));
        }
        return b.toString();
    }

    private static void fail(Progress progress, String message) {
        Log.w(TAG, message);
        if (progress != null) {
            progress.onError(message);
        }
    }
}
