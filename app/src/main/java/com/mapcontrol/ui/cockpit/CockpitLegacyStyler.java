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

    private static boolean isAllCaps(CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (Character.isLowerCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private static void styleText(TextView view) {
        Typeface current = view.getTypeface();
        float sp = view.getTextSize() / view.getResources().getDisplayMetrics().scaledDensity;
        boolean caps = view.isAllCaps() || isAllCaps(view.getText());
        if (caps && sp <= 16f && !(view instanceof EditText) && !(view instanceof Button)) {
            CockpitTokens.labelCaps(view);
            return;
        }
        if (!(view instanceof EditText) && !(view instanceof StatusBadge) && sp < 15f) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
        }
        // Log/terminal ve açıkça monospace bırakılan alanları koru.
        if (current != null && current.equals(Typeface.MONOSPACE)) {
            view.setTypeface(CockpitTokens.mono(view.getContext()),
                    current.getStyle() == Typeface.BOLD ? Typeface.BOLD : Typeface.NORMAL);
            return;
        }
        if (view instanceof EditText) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
            view.setTypeface(CockpitTokens.inter(view.getContext(), CockpitTokens.WEIGHT_REGULAR));
            return;
        }
        if (view instanceof Button) {
            view.setTypeface(CockpitTokens.inter(view.getContext(),
                    CockpitTokens.WEIGHT_SEMIBOLD));
            view.setAllCaps(false);
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
