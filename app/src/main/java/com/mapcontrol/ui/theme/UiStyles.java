package com.mapcontrol.ui.theme;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitTokens;

import java.util.function.Consumer;

/**
 * OEM-style surfaces, glass cards, and rounded controls shared across tab builders.
 */
public final class UiStyles {

    /** Activity onConfigurationChanged'den gelen güncel uiMode (sticky AppCompat bypass). */
    @Nullable
    private static volatile Configuration uiModeOverride;

    private UiStyles() {
    }

    public static int dimenPx(Context context, int dimenResId) {
        return Math.round(context.getResources().getDimension(dimenResId));
    }

    /** MainActivity uiMode değişiminde çağrılır; renk/drawable çözümünü kilitlemez. */
    public static void setUiModeOverride(@Nullable Configuration config) {
        uiModeOverride = config != null ? new Configuration(config) : null;
    }

    /**
     * Activity {@code configChanges=uiMode} + AppCompat DayNight, Activity Resources'ı
     * bazen gece/gündüzde yapıştırır. Override veya Application uiMode kullan.
     */
    public static Context uiModeContext(Context context) {
        if (context == null) {
            return null;
        }
        Configuration override = uiModeOverride;
        if (override != null) {
            Configuration cfg = new Configuration(context.getResources().getConfiguration());
            int night = override.uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (night != 0) {
                cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
                return context.createConfigurationContext(cfg);
            }
        }
        Context app = context.getApplicationContext();
        return app != null ? app : context;
    }

    @ColorInt
    public static int color(Context context, @ColorRes int colorRes) {
        if (ThemeManager.isCyber(context)) {
            return ThemeManager.cyberColor(context, colorRes);
        }
        return ContextCompat.getColor(uiModeContext(context), colorRes);
    }

    @Nullable
    public static Drawable drawable(Context context, @DrawableRes int drawableRes) {
        Drawable cyber = ThemeManager.cyberDrawable(context, drawableRes);
        if (cyber != null) {
            return cyber;
        }
        Drawable d = ContextCompat.getDrawable(uiModeContext(context), drawableRes);
        return d != null ? d.mutate() : null;
    }

    public static void setBackgroundRes(View view, @DrawableRes int drawableRes) {
        view.setBackground(drawable(view.getContext(), drawableRes));
    }

    public static void setRootBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_root_gradient);
    }

    public static void setRailPanelBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_rail_panel);
    }

    public static void setGlassCardBackground(View view) {
        setBackgroundRes(view, R.drawable.bg_card_glass);
    }

    public static void setTabContentBackdrop(View view) {
        view.setBackgroundColor(color(view.getContext(), R.color.tabContentScrim));
    }

    public static void applySolidRoundedBackground(View view, int colorArgb) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(colorArgb);
        if (ThemeManager.isCyber(view.getContext())) {
            d.setCornerRadius(0f);
            if (android.graphics.Color.alpha(colorArgb) != 0) {
                d.setStroke(Math.max(1, Math.round(view.getResources().getDisplayMetrics().density)),
                        ThemeManager.cyberColor(view.getContext(), R.color.outline));
            }
        } else {
            d.setCornerRadius(view.getResources().getDimension(R.dimen.oem_button_radius));
        }
        view.setBackground(d);
    }

    public static void applySolidRoundedBackgroundDp(View view, int colorArgb, float cornerRadiusDp) {
        float density = view.getContext().getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(colorArgb);
        if (ThemeManager.isCyber(view.getContext())) {
            d.setCornerRadius(0f);
            if (android.graphics.Color.alpha(colorArgb) != 0) {
                d.setStroke(Math.max(1, Math.round(density)),
                        ThemeManager.cyberColor(view.getContext(), R.color.outline));
            }
        } else {
            d.setCornerRadius(cornerRadiusDp * density);
        }
        view.setBackground(d);
    }

    public static void styleOemButton(Button button, int backgroundColorArgb) {
        applySolidRoundedBackgroundDp(button, backgroundColorArgb, 16f);
        button.setMinHeight(dimenPx(button.getContext(), R.dimen.ck_touch_min));
        button.setAllCaps(false);
        button.setTypeface(CockpitTokens.inter(button.getContext(), CockpitTokens.WEIGHT_SEMIBOLD));
        if (ThemeManager.isCyber(button.getContext())) {
            boolean bright = Color.luminance(backgroundColorArgb) > 0.45f;
            button.setTextColor(bright
                    ? Color.BLACK
                    : color(button.getContext(), R.color.textPrimary));
        }
    }

    /**
     * Optional real blur on supported API levels; safe no-op where unsupported.
     * Prefer glass drawables for content-heavy regions — blur affects descendants.
     */
    public static void applyOptionalBackdropBlur(View view, float radiusPx) {
        // Cockpit tasarımı blur kullanmaz; geriye dönük API uyumluluğu için no-op.
    }

    public static void clearRenderEffect(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null);
        }
    }

    /**
     * OEM-style two-segment control (hap). {@link #setLeftSelected} updates visuals and runs
     * {@code onCommit} when the value changes.
     */
    public static final class BinarySegmentHandle {
        private final boolean[] state;
        private final Runnable refresh;
        private final Consumer<Boolean> onCommit;
        private final TextView leftSeg;
        private final TextView rightSeg;
        private final TextView helpSeg;

        private BinarySegmentHandle(boolean[] state, Runnable refresh, Consumer<Boolean> onCommit,
                TextView leftSeg, TextView rightSeg, TextView helpSeg) {
            this.state = state;
            this.refresh = refresh;
            this.onCommit = onCommit;
            this.leftSeg = leftSeg;
            this.rightSeg = rightSeg;
            this.helpSeg = helpSeg;
        }

        /**
         * Programmatic selection (e.g. overlay permission denied). Fires {@code onCommit} if value changes.
         */
        public void setLeftSelected(boolean left) {
            if (state[0] == left) {
                return;
            }
            state[0] = left;
            refresh.run();
            onCommit.accept(left);
        }

        /** Updates thumb/help only; does not call {@code onCommit} (e.g. prefs sync after layout). */
        public void syncVisualWithoutCommit(boolean left) {
            if (state[0] == left) {
                return;
            }
            state[0] = left;
            refresh.run();
        }

        public void setInteractionEnabled(boolean enabled) {
            float a = enabled ? 1f : 0.5f;
            leftSeg.setEnabled(enabled);
            rightSeg.setEnabled(enabled);
            leftSeg.setAlpha(a);
            rightSeg.setAlpha(a);
            if (helpSeg != null) {
                helpSeg.setAlpha(a);
            }
        }

        public boolean isLeftSelected() {
            return state[0];
        }

        /** Light/dark sonrası segment thumb/metin renklerini yeniler. */
        public void reapplyTheme() {
            refresh.run();
        }
    }

    /** Seçili seçeneğin kısa açıklaması; eski madde işaretli liste yerine tek satır. */
    private static CharSequence buildOptionsHelp(Context context, String[] labels, String[] helps, int selected) {
        int index = Math.max(0, Math.min(selected, Math.min(labels.length, helps.length) - 1));
        return helps[index];
    }

    /**
     * @param title optional section title above the track
     */
    public static BinarySegmentHandle addBinarySegmentedControl(Context context, LinearLayout parent,
            @Nullable String title,
            String leftLabel, String rightLabel,
            String leftHelp, String rightHelp,
            boolean initialLeftSelected,
            Consumer<Boolean> onCommit) {
        if (title != null && !title.isEmpty()) {
            TextView rowTitle = new TextView(context);
            rowTitle.setText(title);
            CockpitTokens.labelCaps(rowTitle);
            int hPad = dimenPx(context, R.dimen.spacing_medium);
            rowTitle.setPadding(hPad, dimenPx(context, R.dimen.spacing_medium), hPad,
                    dimenPx(context, R.dimen.spacing_small));
            parent.addView(rowTitle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        setBackgroundRes(track, R.drawable.bg_segment_track);
        track.setMinimumHeight(dimenPx(context, R.dimen.ck_touch_min));
        int tpad = dimenPx(context, R.dimen.ck_space_xs);
        track.setPadding(tpad, tpad, tpad, tpad);

        TextView left = new TextView(context);
        TextView right = new TextView(context);
        left.setText(leftLabel);
        right.setText(rightLabel);
        left.setTextSize(16);
        right.setTextSize(16);
        left.setGravity(Gravity.CENTER);
        right.setGravity(Gravity.CENTER);
        left.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        right.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        left.setMinHeight(dimenPx(context, R.dimen.button_height));
        right.setMinHeight(dimenPx(context, R.dimen.button_height));

        TextView help = new TextView(context);
        help.setTextSize(15);
        help.setTextColor(UiStyles.color(context, R.color.textSecondary));
        help.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        int hPad = dimenPx(context, R.dimen.spacing_medium);
        help.setPadding(hPad, dimenPx(context, R.dimen.spacing_small), hPad,
                dimenPx(context, R.dimen.spacing_medium));

        final boolean[] state = {initialLeftSelected};
        Runnable refresh = () -> {
            boolean on = state[0];
            if (on) {
                setBackgroundRes(left, R.drawable.bg_segment_thumb);
                left.setTextColor(color(context, R.color.textPrimary));
                left.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                right.setBackgroundColor(Color.TRANSPARENT);
                right.setTextColor(color(context, R.color.textSecondary));
                right.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                help.setText(buildOptionsHelp(context, new String[]{leftLabel, rightLabel},
                        new String[]{leftHelp, rightHelp}, 0));
            } else {
                setBackgroundRes(right, R.drawable.bg_segment_thumb);
                right.setTextColor(color(context, R.color.textPrimary));
                right.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                left.setBackgroundColor(Color.TRANSPARENT);
                left.setTextColor(color(context, R.color.textSecondary));
                left.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                help.setText(buildOptionsHelp(context, new String[]{leftLabel, rightLabel},
                        new String[]{leftHelp, rightHelp}, 1));
            }
        };

        left.setOnClickListener(v -> {
            if (!left.isEnabled()) {
                return;
            }
            if (!state[0]) {
                state[0] = true;
                refresh.run();
                onCommit.accept(true);
            }
        });
        right.setOnClickListener(v -> {
            if (!right.isEnabled()) {
                return;
            }
            if (state[0]) {
                state[0] = false;
                refresh.run();
                onCommit.accept(false);
            }
        });
        refresh.run();

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        track.addView(left, segLp);
        track.addView(right, segLp);

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(hPad, 0, hPad, 0);
        parent.addView(track, trackLp);
        parent.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return new BinarySegmentHandle(state, refresh, onCommit, left, right, help);
    }

    public static final class TernarySegmentHandle {
        private final int[] modeIds;
        private final int[] selectedIndex;
        private final Runnable refresh;
        private TextView[] segs;
        private TextView helpSeg;

        private TernarySegmentHandle(int[] modeIds, int[] selectedIndex, Runnable refresh) {
            this.modeIds = modeIds;
            this.selectedIndex = selectedIndex;
            this.refresh = refresh;
        }

        private void bindViews(TextView[] segs, TextView helpSeg) {
            this.segs = segs;
            this.helpSeg = helpSeg;
        }

        public void setInteractionEnabled(boolean enabled) {
            float a = enabled ? 1f : 0.5f;
            if (segs != null) {
                for (TextView tv : segs) {
                    tv.setEnabled(enabled);
                    tv.setAlpha(a);
                }
            }
            if (helpSeg != null) {
                helpSeg.setAlpha(a);
            }
        }

        /** Sync UI from persisted mode id without calling {@code onCommit}. */
        public void syncVisualFromModeId(int modeId) {
            int idx = -1;
            for (int i = 0; i < modeIds.length; i++) {
                if (modeIds[i] == modeId) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                idx = 0;
            }
            selectedIndex[0] = idx;
            refresh.run();
        }

        public int getSelectedModeId() {
            return modeIds[selectedIndex[0]];
        }
    }

    /**
     * Three-segment control; {@code modeIds} order must match {@code labels} / {@code helps} (e.g. 2, 1, 0).
     */
    public static TernarySegmentHandle addTernarySegmentedControl(Context context, LinearLayout parent,
            @Nullable String title,
            String[] labels, String[] helps,
            int[] modeIds,
            int initialModeId,
            Consumer<Integer> onCommit) {
        if (labels.length != 3 || helps.length != 3 || modeIds.length != 3) {
            throw new IllegalArgumentException("labels, helps, modeIds must have length 3");
        }

        if (title != null && !title.isEmpty()) {
            TextView rowTitle = new TextView(context);
            rowTitle.setText(title);
            CockpitTokens.labelCaps(rowTitle);
            int hPad = dimenPx(context, R.dimen.spacing_medium);
            rowTitle.setPadding(hPad, dimenPx(context, R.dimen.spacing_medium), hPad,
                    dimenPx(context, R.dimen.spacing_small));
            parent.addView(rowTitle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout track = new LinearLayout(context);
        track.setOrientation(LinearLayout.HORIZONTAL);
        setBackgroundRes(track, R.drawable.bg_segment_track);
        int tpad = dimenPx(context, R.dimen.spacing_tiny);
        track.setPadding(tpad, tpad, tpad, tpad);

        TextView[] segs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(context);
            tv.setText(labels[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(16);
            tv.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
            tv.setMinHeight(dimenPx(context, R.dimen.button_height));
            tv.setMaxLines(2);
            segs[i] = tv;
        }

        TextView help = new TextView(context);
        help.setTextSize(15);
        help.setTextColor(UiStyles.color(context, R.color.textSecondary));
        help.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        int hPad = dimenPx(context, R.dimen.spacing_medium);
        help.setPadding(hPad, dimenPx(context, R.dimen.spacing_small), hPad,
                dimenPx(context, R.dimen.spacing_medium));

        int initialIndex = 0;
        for (int i = 0; i < 3; i++) {
            if (modeIds[i] == initialModeId) {
                initialIndex = i;
                break;
            }
        }

        final int[] selectedIndex = {initialIndex};
        Runnable refresh = () -> {
            int idx = selectedIndex[0];
            for (int i = 0; i < 3; i++) {
                TextView tv = segs[i];
                if (i == idx) {
                    setBackgroundRes(tv, R.drawable.bg_segment_thumb);
                    tv.setTextColor(UiStyles.color(context, R.color.textPrimary));
                    tv.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                } else {
                    tv.setBackgroundColor(Color.TRANSPARENT);
                    tv.setTextColor(UiStyles.color(context, R.color.textSecondary));
                    tv.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
                }
            }
            help.setText(buildOptionsHelp(context, labels, helps, idx));
        };

        for (int i = 0; i < 3; i++) {
            final int fi = i;
            segs[i].setOnClickListener(v -> {
                if (selectedIndex[0] != fi) {
                    selectedIndex[0] = fi;
                    refresh.run();
                    onCommit.accept(modeIds[fi]);
                }
            });
        }
        refresh.run();

        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        for (TextView tv : segs) {
            track.addView(tv, segLp);
        }

        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        trackLp.setMargins(hPad, 0, hPad, 0);
        parent.addView(track, trackLp);
        parent.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TernarySegmentHandle handle = new TernarySegmentHandle(modeIds, selectedIndex, refresh);
        handle.bindViews(segs, help);
        return handle;
    }

    /** Tinted start drawable for {@link Button} labels (Material-style icons as vector drawables). */
    public static void setButtonStartIconTinted(Button button, @DrawableRes int iconRes, int colorArgb, int drawablePaddingPx) {
        Drawable d = ContextCompat.getDrawable(button.getContext(), iconRes);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, colorArgb);
        }
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        button.setCompoundDrawablePadding(drawablePaddingPx);
    }

    public static void setButtonIconOnlyTinted(Button button, @DrawableRes int iconRes, int colorArgb, CharSequence contentDescription) {
        button.setText("");
        button.setContentDescription(contentDescription);
        setButtonStartIconTinted(button, iconRes, colorArgb, 0);
    }

    public static void clearButtonCompoundDrawables(Button button) {
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        button.setCompoundDrawablePadding(0);
    }
}
