package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.mapcontrol.R;

/**
 * Cockpit design tokens. Renk/dimens okumaları ve Inter / JetBrains Mono typeface cache'i.
 * Fontlar ilk erişimde yüklenir; Application.onCreate'te ön yükleme yapılmaz.
 */
public final class CockpitTokens {

    public static final int WEIGHT_REGULAR = 400;
    public static final int WEIGHT_MEDIUM = 500;
    public static final int WEIGHT_SEMIBOLD = 600;
    public static final int WEIGHT_BOLD = 700;

    private static final SparseArray<Typeface> INTER = new SparseArray<>();
    private static Typeface mono;

    private CockpitTokens() {
    }

    public static int color(Context context, int colorRes) {
        return context.getResources().getColor(colorRes, context.getTheme());
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static int dp(Context context, int dimenRes) {
        return Math.round(context.getResources().getDimension(dimenRes));
    }

    public static Typeface inter(Context context, int weight) {
        int key = weight;
        Typeface cached = INTER.get(key);
        if (cached != null) {
            return cached;
        }
        int res;
        if (weight >= WEIGHT_BOLD) {
            res = R.font.inter_bold;
        } else if (weight >= WEIGHT_SEMIBOLD) {
            res = R.font.inter_semibold;
        } else if (weight >= WEIGHT_MEDIUM) {
            res = R.font.inter_medium;
        } else {
            res = R.font.inter_regular;
        }
        Typeface tf = ResourcesCompat.getFont(context.getApplicationContext(), res);
        if (tf == null) {
            tf = Typeface.DEFAULT;
        }
        INTER.put(key, tf);
        return tf;
    }

    public static Typeface mono(Context context) {
        if (mono == null) {
            Typeface tf = ResourcesCompat.getFont(context.getApplicationContext(),
                    R.font.jetbrains_mono_medium);
            mono = tf != null ? tf : Typeface.MONOSPACE;
        }
        return mono;
    }

    public static void apply(TextView view, int textAppearanceRes) {
        if (view == null) {
            return;
        }
        view.setTextAppearance(textAppearanceRes);
    }

    public static void display(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_Display);
        tabular(v);
    }

    public static void headlineLg(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_HeadlineLg);
    }

    public static void headlineMd(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_HeadlineMd);
    }

    public static void headlineSm(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_HeadlineSm);
    }

    public static void title(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_Title);
    }

    public static void bodyLg(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_BodyLg);
    }

    public static void bodyMd(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_BodyMd);
    }

    public static void bodySm(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_BodySm);
    }

    public static void labelCaps(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_LabelCaps);
        v.setAllCaps(true);
    }

    public static void mono(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_Mono);
        tabular(v);
    }

    public static void tabular(TextView v) {
        v.setFontFeatureSettings("tnum");
    }

    /** Metric: büyük değer + küçük birim. Layout zıplamasın diye min genişlik verilir. */
    public static void metricValue(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_Display);
        tabular(v);
    }

    public static void metricUnit(TextView v) {
        apply(v, R.style.TextAppearance_Cockpit_BodySm);
        v.setTextColor(color(v.getContext(), R.color.ck_text_medium));
    }
}
