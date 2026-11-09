package com.eyups.roaddeck.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.eyups.roaddeck.util.LauncherModeManager;

/**
 * Uygulama güncellendikten hemen sonra ({@code MY_PACKAGE_REPLACED}) Launcher Modu'nu geri kurar
 * ve otomatik başlatma açıksa {@link RoadDeckService}'i yeniden başlatır.
 */
public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        LauncherModeManager.restoreIfEnabled(app);

        SharedPreferences prefs = app.getSharedPreferences(
                BootReceiver.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true)) {
            return;
        }
        Intent serviceIntent = new Intent(app, RoadDeckService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(serviceIntent);
            } else {
                app.startService(serviceIntent);
            }
        } catch (Exception ignored) {
        }
    }
}
