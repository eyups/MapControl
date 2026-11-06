package com.eyups.roaddeck;

import com.eyups.roaddeck.BuildConfig;

import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.eyups.roaddeck.ui.theme.UiStyles;
import com.eyups.roaddeck.profile.DriverProfileManager;
import com.eyups.roaddeck.util.AppLocaleManager;
import com.eyups.roaddeck.util.DisplayHelper;
import com.eyups.roaddeck.util.LauncherLegacyPrefs;
import com.eyups.roaddeck.util.LegacyPrefsMigration;

public final class RoadDeckApplication extends Application {

    private int lastNightModeUiBits = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        // Prefs'lere ilk erişimden önce: eski "MapControl*" dosyalarını taşı (ilk açılışta bir kez).
        LegacyPrefsMigration.run(this);
        GlitchTip.start(this);
        if (BuildConfig.DEBUG) {
            try {
                Class.forName("com.eyups.roaddeck.debug.DebugVdbusHooks")
                        .getMethod("install", android.content.Context.class)
                        .invoke(null, this);
            } catch (Throwable ignored) {
                // Debug helper yoksa release/normal debug akışı değişmez.
            }
        }
        AppLocaleManager.applyStoredLocale(this);
        LauncherLegacyPrefs.applyMigrations(this);
        DriverProfileManager.get(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        lastNightModeUiBits = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        UiStyles.setUiModeOverride(getResources().getConfiguration());
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                int night = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (night == lastNightModeUiBits) {
                    return;
                }
                lastNightModeUiBits = night;
                UiStyles.setUiModeOverride(newConfig);
                DisplayHelper.refreshBootSplashAfterConfigurationChange(RoadDeckApplication.this);
            }

            @Override
            public void onLowMemory() {
            }
        });
    }
}
