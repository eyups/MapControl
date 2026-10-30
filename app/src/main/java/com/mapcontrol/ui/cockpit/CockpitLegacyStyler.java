package com.mapcontrol.ui.cockpit;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Legacy builder'ların ürettiği Roboto metinleri Cockpit tipografisine çeker.
 * Yalnız görsel katman; metin, boyut ve iş mantığı değişmez.
 */
public final class CockpitLegacyStyler {

    private CockpitLegacyStyler() {
    }

    public static void apply(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof TextView) {
            styleText((TextView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                apply(group.getChildAt(i));
            }
        }
    }

    /** Yalnız 17sp+ kalın başlıkları LabelCaps'e çeker; ayar ekranı bölüm başlıkları için. */
    public static void applySectionHeadings(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof TextView && !(root instanceof Button)) {
            TextView tv = (TextView) root;
            float sp = tv.getTextSize() / tv.getResources().getDisplayMetrics().scaledDensity;
            Typeface tf = tv.getTypeface();
            boolean bold = tf != null && (tf.getStyle() & Typeface.BOLD) != 0;
            if (sp >= 17f && bold && tv.getText().length() > 0) {
                CockpitTokens.labelCaps(tv);
                return;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applySectionHeadings(group.getChildAt(i));
            }
        }
    }

    private static void styleText(TextView view) {
        Typeface current = view.getTypeface();
        // Log/terminal ve açıkça monospace bırakılan alanları koru.
        if (current != null && current.equals(Typeface.MONOSPACE)) {
            view.setTypeface(CockpitTokens.mono(view.getContext()),
                    current.getStyle() == Typeface.BOLD ? Typeface.BOLD : Typeface.NORMAL);
            return;
        }
        if (view instanceof EditText) {
            view.setTypeface(CockpitTokens.inter(view.getContext(), CockpitTokens.WEIGHT_REGULAR));
            return;
        }
        int style = current != null ? current.getStyle() : Typeface.NORMAL;
        int weight = (style & Typeface.BOLD) != 0
                ? CockpitTokens.WEIGHT_SEMIBOLD : CockpitTokens.WEIGHT_REGULAR;
        view.setTypeface(CockpitTokens.inter(view.getContext(), weight));
        if (view instanceof Button) {
            view.setAllCaps(false);
        }
    }
}
