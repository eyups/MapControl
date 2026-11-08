package com.eyups.roaddeck.lyricsbridge;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Bölünmüş ekranda "RoadDeck Şarkı Sözleri" olarak seçilebilmesi için küçük köprü uygulaması.
 * Bölünmüş ekran başlatıcısı paket bazlı çalıştığı için RoadDeck'in kendi paketi seçilemiyor;
 * bu paket açıldığında RoadDeck'in tam ekran söz aktivitesini aynı görevde başlatır ve kapanır.
 * Böylece pencere, bölünmüş ekranın verdiği freeform sınırlarını korur.
 */
public final class BridgeActivity extends Activity {

    private static final String TARGET_PACKAGE = "com.eyups.roaddeck";
    private static final String TARGET_ACTIVITY =
            "com.eyups.roaddeck.lyrics.LyricsFullscreenActivity";
    /** Bölünmüş ekranda açıldığını RoadDeck'e bildirir (sanatçı fotoğrafı gizlenir). */
    private static final String EXTRA_SPLIT_MODE = "com.eyups.roaddeck.extra.SPLIT_MODE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY));
            intent.putExtra(EXTRA_SPLIT_MODE, true);
            // NEW_TASK yok: mevcut (freeform) görevde açılsın. Pencere sınırlarını da
            // köprünün kendi freeform dikdörtgeniyle eşitle ki alt/üst inset kaymasın.
            Rect bounds = currentBounds();
            if (bounds != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ActivityOptions options = ActivityOptions.makeBasic().setLaunchBounds(bounds);
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "RoadDeck söz ekranı açılamadı: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private Rect currentBounds() {
        try {
            WindowManager wm = getWindowManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return wm.getCurrentWindowMetrics().getBounds();
            }
            Rect rect = new Rect();
            wm.getDefaultDisplay().getRectSize(rect);
            return rect;
        } catch (Exception e) {
            return null;
        }
    }
}
