package com.mapcontrol.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;

/**
 * Persisted UI skin selection and the terminal-style Cyber palette.
 *
 * Modern is the default and keeps the existing OEM/glass look. Cyber is intentionally
 * dark-only: phosphor green on near-black, square panes, monospace type and scanlines.
 */
public final class ThemeManager {

    public static final String PREF_FILE = "MapControlPrefs";
    public static final String PREF_THEME = "uiTheme";
    public static final String THEME_MODERN = "modern";
    public static final String THEME_CYBER = "cyber";

    private static final int BG = 0xFF0A0A0A;
    private static final int BG_PANEL = 0xFF0D120D;
    private static final int BG_RAISED = 0xFF101A10;
    private static final int FG = 0xFF33FF00;
    private static final int FG_SECONDARY = 0xFF25C225;
    private static final int FG_DIM = 0xFF1F9A1F;
    private static final int FG_MUTED = 0xFF1F7A1F;
    private static final int AMBER = 0xFFFFB000;
    private static final int RED = 0xFFFF3333;
    private static final int BORDER = 0xFF1F521F;
    private static final int SELECTED = 0xFF123D12;

    private static final int[] AVATARS = {
            0xFF33FF00, 0xFFFFB000, 0xFF1F9A1F, 0xFF7CFF4D,
            0xFFC47A00, 0xFF4D9A4D, 0xFFB8FF80, 0xFF9A6A1F,
            0xFF6A9A6A, 0xFFFF6A6A
    };

    private static volatile Boolean cyberCache;

    private ThemeManager() {
    }

    public static boolean isCyber(Context context) {
        // K1: Cyber yolu kaldırıldı. Pref okunur ama yok sayılır; tek Cockpit teması.
        return false;
    }

    public static void setCyber(Context context, boolean cyber) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_THEME, cyber ? THEME_CYBER : THEME_MODERN)
                .apply();
        cyberCache = cyber;
    }

    public static int activityThemeRes(Context context) {
        return R.style.Theme_MapControl;
    }

    public static void applyActivityTheme(Activity activity) {
        if (activity != null) {
            activity.setTheme(activityThemeRes(activity));
        }
    }

    /** Maps an existing resource color to its Cyber equivalent; unknown names get a safe dim green. */
    @ColorInt
    public static int cyberColor(Context context, @ColorRes int colorRes) {
        final String name;
        try {
            name = context.getResources().getResourceEntryName(colorRes);
        } catch (RuntimeException e) {
            return FG;
        }

        if ("transparent".equals(name) || "wifiScreenBg".equals(name)
                || "tabContentScrim".equals(name)) {
            return Color.TRANSPARENT;
        }
        if (name.startsWith("avatar_")) {
            int index = 0;
            try {
                index = Integer.parseInt(name.substring("avatar_".length())) - 1;
            } catch (RuntimeException ignored) {
            }
            return AVATARS[Math.floorMod(index, AVATARS.length)];
        }
        if (name.startsWith("text")) {
            if (name.contains("destructive")) {
                return RED;
            }
            if (name.contains("loading")) {
                return AMBER;
            }
            if (name.contains("onGold")) {
                return BG;
            }
            if (name.contains("primary87")) {
                return 0xE633FF00;
            }
            if (name.contains("primary85")) {
                return 0xD933FF00;
            }
            if (name.contains("primary80")) {
                return 0xCC33FF00;
            }
            if (name.contains("primary70")) {
                return 0xB333FF00;
            }
            if (name.contains("primary") || name.contains("accent")) {
                return FG;
            }
            if (name.contains("secondary")) {
                return FG_SECONDARY;
            }
            if (name.contains("muted") || name.contains("tertiary") || name.contains("hint")
                    || name.contains("dialog")) {
                return FG_MUTED;
            }
            return FG_DIM;
        }
        if ("buttonPrimary".equals(name) || "buttonActive".equals(name)
                || "buttonWelcome".equals(name)) {
            return 0xFF0D2B0D;
        }
        if ("buttonDanger".equals(name) || "buttonDestructiveBg".equals(name)) {
            return 0xFF2A0808;
        }
        if ("buttonGold".equals(name)) {
            return AMBER;
        }
        if ("buttonSuccess".equals(name) || "buttonSuccessBright".equals(name)) {
            return SELECTED;
        }
        if (name.contains("toast") || name.contains("halo")) {
            if (name.contains("error") || name.contains("fail")) {
                return name.contains("halo") ? 0x221F0808 : 0xE61F0A0A;
            }
            if (name.contains("success") || name.contains("ok")) {
                return name.contains("halo") ? 0x22103A10 : 0xE60D1A0D;
            }
            if (name.contains("preparing")) {
                return name.contains("halo") ? 0x22221A05 : 0xE6141A0A;
            }
            if (name.contains("stroke")) {
                return BORDER;
            }
            return BG_PANEL;
        }
        if (name.contains("error") || name.contains("danger") || name.contains("destructive")
                || name.contains("red") || name.contains("fail")) {
            return RED;
        }
        if (name.contains("warning") || name.contains("gold") || name.contains("loading")
                || name.contains("yellow") || name.contains("amber") || name.contains("sport")) {
            return AMBER;
        }
        if (name.contains("success") || name.contains("eco") || name.contains("green")
                || "chipOn".equals(name)) {
            return FG;
        }
        if (name.contains("accent") || name.contains("highlight") || name.contains("selectionBar")
                || name.contains("statusInfo")) {
            return FG;
        }
        if (name.contains("nav_pill_selected")) {
            return SELECTED;
        }
        if (name.contains("buttonNeutral") || name.contains("buttonInactive")
                || name.contains("buttonSecondaryMuted")) {
            return BG_RAISED;
        }
        if (name.contains("selectionRow") || name.contains("driveModeSelected")
                || name.contains("assistCardActive")) {
            return SELECTED;
        }
        if (name.contains("driveModeUnselected") || name.contains("assistCardIdle")
                || name.contains("wifiItemPressed")) {
            return BG_RAISED;
        }
        if (name.contains("outline") || name.contains("divider") || name.contains("stroke")) {
            return BORDER;
        }
        if (name.contains("glass") || name.contains("surfaceCard") || name.contains("cardColor")
                || "segmentTrackFill".equals(name)) {
            return BG_PANEL;
        }
        if (name.contains("background") || name.contains("surface") || name.contains("root_")
                || name.contains("boot_splash") || name.contains("map_grid")
                || name.contains("preparing") || name.contains("modal")
                || name.contains("logBackground") || name.contains("logPanel")
                || name.contains("logField") || name.contains("logTerminal")
                || name.contains("wifiItem") || name.contains("filament")) {
            return BG;
        }
        if (name.contains("logText")) {
            return FG;
        }
        if (name.contains("cluster_nav_maneuver_text")) {
            return FG;
        }
        if (name.contains("cluster_nav_maneuver_subtext")) {
            return FG_DIM;
        }
        if (name.contains("cluster_nav")) {
            return BG_PANEL;
        }
        return FG_DIM;
    }

    /** Cyber replacements for the few XML surface drawables that would otherwise keep OEM colors. */
    @Nullable
    public static Drawable cyberDrawable(Context context, @DrawableRes int drawableRes) {
        if (!isCyber(context)) {
            return null;
        }
        final String name;
        try {
            name = context.getResources().getResourceEntryName(drawableRes);
        } catch (RuntimeException e) {
            return null;
        }
        switch (name) {
            case "bg_root_gradient":
                return solid(BG);
            case "bg_card_glass":
                return panel(BG_PANEL, BORDER);
            case "bg_rail_panel":
                return panel(BG_PANEL, BORDER);
            case "bg_nav_pill_selected":
                return panel(SELECTED, FG);
            case "bg_segment_track":
                return panel(BG, BORDER);
            case "bg_segment_thumb":
                return panel(SELECTED, FG);
            case "bg_topbar_divider":
                return solid(BORDER);
            case "bg_launcher_icon_slot":
                return panel(BG_PANEL, BORDER);
            case "bg_launcher_vehicle_glow": {
                GradientDrawable d = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{0x331F521F, 0x001F521F});
                d.setShape(GradientDrawable.OVAL);
                d.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                d.setGradientRadius(dp(context, 120));
                return d;
            }
            default:
                return null;
        }
    }

    public static Drawable cyberBadgeBackground(Context context) {
        return panel(BG, BORDER);
    }

    public static void applyCyberTypeface(View root) {
        if (root == null || !isCyber(root.getContext())) {
            return;
        }
        if (root instanceof TextView) {
            TextView text = (TextView) root;
            Typeface current = text.getTypeface();
            int style = current != null ? current.getStyle() : Typeface.NORMAL;
            text.setTypeface(Typeface.MONOSPACE, style);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCyberTypeface(group.getChildAt(i));
            }
        }
    }

    private static GradientDrawable solid(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(0f);
        return d;
    }

    private static GradientDrawable panel(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(0f);
        d.setStroke(Math.max(1, Math.round(1f)), stroke);
        return d;
    }

    private static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    @ColorInt
    public static int color(Context context, @ColorRes int colorRes) {
        if (isCyber(context)) {
            return cyberColor(context, colorRes);
        }
        return ContextCompat.getColor(context, colorRes);
    }
}
