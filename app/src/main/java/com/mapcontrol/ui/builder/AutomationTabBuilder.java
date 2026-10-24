package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.mapcontrol.R;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.function.Consumer;

/** Sol menüdeki "Otomasyon" sekmesi: sıcaklığa bağlı koltuk/direksiyon otomasyonu kartları. */
public class AutomationTabBuilder {

    private final Context context;
    private final SharedPreferences prefs;
    private final Consumer<String> log;
    private final ScrollView scrollView;

    public AutomationTabBuilder(Context context, SharedPreferences prefs, Consumer<String> log) {
        this.context = context;
        this.prefs = prefs;
        this.log = log;
        this.scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);
        rebuild();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    /** Dil veya tema değişiminde içeriği yeniden üretir; ScrollView örneği aynı kalır. */
    public void rebuild() {
        scrollView.removeAllViews();

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TemperatureAutomationUi.addTo(context, outer, prefs, log);
    }
}
