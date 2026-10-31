package com.mapcontrol.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.mapcontrol.profile.DriverProfileManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** F5: karşılama ses dosyalarını tek ZIP olarak yedekler/geri yükler. */
final class ProfileAudioBackup {

    private static final String PREFS = "MapControlPrefs";
    private static final String KEY_GLOBAL = "welcomeAudioFilePath";
    private static final String GLOBAL_ENTRY = "global.mp3";
    private static final String PROFILE_PREFIX = "profile-";
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private ProfileAudioBackup() {
    }

    static boolean upload(Context context, CloudConfig config, CloudHttp http) throws Exception {
        File zip = createZip(context);
        if (zip == null) {
            return true;
        }
        byte[] data = new byte[(int) zip.length()];
        try (FileInputStream input = new FileInputStream(zip)) {
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read <= 0) {
                    break;
                }
                offset += read;
            }
        } finally {
            zip.delete();
        }
        CloudHttp.Response response = http.postFile(
                "/api/v1/car/sync/audio",
                config.token(),
                "welcome-audio.zip",
                "application/zip",
                data,
                (int) MAX_BYTES + 64 * 1024);
        return response.code == 204;
    }

    static boolean restore(Context context, CloudConfig config, CloudHttp http) throws Exception {
        File zip = new File(context.getCacheDir(), "welcome-audio-restore.zip");
        CloudHttp.Response response = http.downloadToFile(
                "/api/v1/car/sync/audio", config.token(), zip, MAX_BYTES);
        if (response.code == 204) {
            return true;
        }
        if (response.code != 200) {
            zip.delete();
            return false;
        }
        try {
            return extract(context, zip);
        } finally {
            zip.delete();
        }
    }

    private static File createZip(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> paths = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String global = prefs.getString(KEY_GLOBAL, "");
        if (!global.isEmpty() && readable(context, global)) {
            paths.add(global);
            names.add(GLOBAL_ENTRY);
        }
        DriverProfileManager manager = DriverProfileManager.get(context);
        for (DriverProfileManager.Profile profile : manager.getProfiles()) {
            if (profile.welcomeAudioPath != null
                    && !profile.welcomeAudioPath.isEmpty()
                    && readable(context, profile.welcomeAudioPath)) {
                paths.add(profile.welcomeAudioPath);
                names.add(PROFILE_PREFIX + safe(profile.id) + ".mp3");
            }
        }
        if (paths.isEmpty()) {
            return null;
        }
        File output = new File(context.getCacheDir(), "welcome-audio-backup.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            for (int i = 0; i < paths.size(); i++) {
                zip.putNextEntry(new ZipEntry(names.get(i)));
                try (InputStream input = open(context, paths.get(i))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            return output;
        } catch (Exception error) {
            output.delete();
            return null;
        }
    }

    private static boolean extract(Context context, File zipFile) {
        DriverProfileManager manager = DriverProfileManager.get(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        File profileDir = new File(context.getFilesDir(), "profile_welcome");
        File globalDir = new File(context.getFilesDir(), "welcome_audio");
        if (!profileDir.isDirectory() && !profileDir.mkdirs()) {
            return false;
        }
        if (!globalDir.isDirectory() && !globalDir.mkdirs()) {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("/") || name.contains("\\") || name.contains("..")) {
                    continue;
                }
                if (GLOBAL_ENTRY.equals(name)) {
                    File target = new File(globalDir, "welcome.mp3");
                    copy(zip, target);
                    prefs.edit().putString(KEY_GLOBAL, target.getAbsolutePath()).apply();
                } else if (name.startsWith(PROFILE_PREFIX) && name.endsWith(".mp3")) {
                    String id = name.substring(PROFILE_PREFIX.length(), name.length() - 4);
                    DriverProfileManager.Profile profile = manager.getProfiles().stream()
                            .filter(item -> item.id.equals(id))
                            .findFirst().orElse(null);
                    if (profile == null) {
                        continue;
                    }
                    File target = new File(profileDir, "profile-" + safe(id) + ".mp3");
                    copy(zip, target);
                    manager.setWelcomeAudio(id, profile.welcomeMessage, target.getAbsolutePath());
                }
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean readable(Context context, String path) {
        try (InputStream ignored = open(context, path)) {
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static InputStream open(Context context, String path) throws Exception {
        if (path.startsWith("content://")) {
            InputStream input = context.getContentResolver().openInputStream(Uri.parse(path));
            if (input == null) {
                throw new Exception("unreadable");
            }
            return input;
        }
        return new FileInputStream(new File(path));
    }

    private static void copy(InputStream input, File output) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                stream.write(buffer, 0, read);
            }
        }
        output.setReadable(true, false);
    }

    private static String safe(String value) {
        return value == null ? "profile" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
