package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

import java.util.ArrayList;
import java.util.List;

/** 96dp collapsed / 240dp expanded navigation rail with 6 domains + settings. */
public class CockpitNavRail extends LinearLayout {

    public interface Listener {
        void onDomainSelected(CockpitNavModel.Domain domain);
        void onSettingsSelected();
        default void onRailWidthChanged(boolean expanded) { }
        void onAppManagementNeeded();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final boolean portrait;
    private final List<View> domainViews = new ArrayList<>();
    private final List<CockpitNavModel.Domain> domainModels = new ArrayList<>();
    private View settingsView;
    private TextView expandLabel;
    private boolean expanded;
    private int selectedTab = CockpitNavModel.TAB_HOME;

    public CockpitNavRail(Context context, SharedPreferences prefs, Listener listener,
            boolean portrait) {
        super(context);
        this.context = context;
        this.prefs = prefs;
        this.listener = listener;
        this.portrait = portrait;
        this.expanded = prefs.getBoolean(CockpitNavModel.PREF_RAIL_EXPANDED, false);
        build();
    }

    private void build() {
        setOrientation(portrait ? HORIZONTAL : VERTICAL);
        setBackgroundColor(CockpitTokens.color(context, R.color.ck_surface_1));
        setGravity(portrait ? Gravity.CENTER_VERTICAL : Gravity.TOP);

        if (!portrait) {
            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            int pad = CockpitTokens.dim(context, R.dimen.ck_space_md);
            top.setPadding(pad, pad, pad, pad);
            addView(top, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));

            expandLabel = new TextView(context);
            CockpitTokens.bodySm(expandLabel);
            expandLabel.setText("RoadDeck");
            expandLabel.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
            expandLabel.setSingleLine(true);
            expandLabel.setVisibility(GONE);
            top.addView(expandLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            CockpitButton expandButton = CockpitButton.icon(context,
                    R.drawable.ic_mdi_chevron_double_left,
                    context.getString(R.string.ck_rail_expand));
            expandButton.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
            expandButton.setOnClickListener(v -> setExpanded(!expanded, true));
            top.addView(expandButton, new LayoutParams(
                    CockpitTokens.dim(context, R.dimen.ck_touch_min),
                    CockpitTokens.dim(context, R.dimen.ck_touch_min)));
        }

        LinearLayout items = new LinearLayout(context);
        items.setOrientation(portrait ? HORIZONTAL : VERTICAL);
        if (portrait) {
            addView(items, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
        } else {
            addView(items, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
        }

        for (CockpitNavModel.Domain domain : CockpitNavModel.domains()) {
            if (domain.id == 5) {
                continue;
            }
            View row = createDomainRow(domain);
            domainViews.add(row);
            domainModels.add(domain);
            items.addView(row, itemParams());
        }

        if (!portrait) {
            View spacer = new View(context);
            addView(spacer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
            settingsView = createSettingsRow();
            addView(settingsView, itemParams());
        } else {
            settingsView = createSettingsRow();
            items.addView(settingsView, itemParams());
        }
        if (expandLabel != null) {
            expandLabel.setVisibility(expanded ? VISIBLE : GONE);
        }
        refreshSelection();
    }

    private LinearLayout.LayoutParams itemParams() {
        if (portrait) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, CockpitTokens.dim(context, R.dimen.ck_touch_min));
            lp.rightMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
            return lp;
        }
        return new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                CockpitTokens.dim(context, R.dimen.ck_rail_item_h));
    }

    private View createDomainRow(CockpitNavModel.Domain domain) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(portrait ? VERTICAL : HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(context.getString(domain.titleRes));

        View bar = new View(context);
        bar.setBackgroundColor(CockpitTokens.color(context, R.color.ck_accent));
        if (portrait) {
            bar.setVisibility(GONE);
        } else {
            row.addView(bar, new LayoutParams(CockpitTokens.dp(context, 4),
                    LayoutParams.MATCH_PARENT));
        }

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setGravity(Gravity.CENTER);
        row.addView(content, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        ImageView icon = new ImageView(context);
        icon.setImageResource(domain.iconRes);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium),
                PorterDuff.Mode.SRC_IN);
        content.addView(icon, new LayoutParams(CockpitTokens.dp(context, 26),
                CockpitTokens.dp(context, 26)));

        TextView label = new TextView(context);
        CockpitTokens.bodySm(label);
        label.setText(railLabelRes(domain.id));
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (!portrait) {
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            lp.topMargin = CockpitTokens.dp(context, 2);
            content.addView(label, lp);
        }

        if (portrait) {
            row.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                    CockpitTokens.dim(context, R.dimen.ck_space_xs),
                    CockpitTokens.dim(context, R.dimen.ck_space_md),
                    CockpitTokens.dim(context, R.dimen.ck_space_xs));
        }

        row.setOnClickListener(v -> {
            if (listener != null) {
                if (domain.id == 4
                        && !prefs.getBoolean("appManagementDisclaimerAccepted", false)) {
                    listener.onAppManagementNeeded();
                } else {
                    listener.onDomainSelected(domain);
                }
            }
        });
        return row;
    }

    private int railLabelRes(int domainId) {
        switch (domainId) {
            case 0: return R.string.ck_rail_cockpit;
            case 1: return R.string.ck_rail_vehicle;
            case 2: return R.string.ck_rail_safety;
            case 3: return R.string.ck_rail_media;
            case 4: return R.string.ck_rail_apps;
            default: return R.string.ck_rail_settings;
        }
    }

    private View createSettingsRow() {
        final CockpitNavModel.Domain domain = CockpitNavModel.domainById(5);
        View row = createDomainRow(domain);
        row.setOnClickListener(v -> listener.onSettingsSelected());
        return row;
    }

    public void setSelectedTab(int tabId) {
        selectedTab = tabId;
        refreshSelection();
    }

    private void refreshSelection() {
        CockpitNavModel.Domain current = CockpitNavModel.domainForTab(selectedTab);
        for (int i = 0; i < domainViews.size(); i++) {
            boolean selected = domainModels.get(i).id == current.id;
            styleSelected(domainViews.get(i), selected);
        }
        if (settingsView != null) {
            styleSelected(settingsView, current.id == 5);
            // settings row is a domain row created from a domain; tint its icon
        }
    }

    private void styleSelected(View row, boolean selected) {
        row.setBackgroundColor(selected
                ? CockpitTokens.color(context, R.color.ck_surface_2)
                : 0x00000000);
        if (row instanceof LinearLayout) {
            View bar = ((LinearLayout) row).getChildAt(0);
            if (bar != null && !(bar instanceof LinearLayout)) {
                bar.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            }
        }
        tintIcons(row, selected
                ? CockpitTokens.color(context, R.color.ck_accent)
                : CockpitTokens.color(context, R.color.ck_text_medium));
    }

    private void tintIcons(View row, int color) {
        if (row instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) row;
            for (int i = 0; i < g.getChildCount(); i++) {
                View child = g.getChildAt(i);
                if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(color, PorterDuff.Mode.SRC_IN);
                } else {
                    tintIcons(child, color);
                }
            }
        }
    }

    public void setExpanded(boolean value, boolean notify) {
        expanded = value;
        if (expandLabel != null) {
            expandLabel.setVisibility(value ? VISIBLE : GONE);
        }
        prefs.edit().putBoolean(CockpitNavModel.PREF_RAIL_EXPANDED, value).apply();
        if (portrait) {
            return;
        }
        if (notify) {
            listener.onRailWidthChanged(value);
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setRailVisible(boolean visible) {
        setVisibility(visible ? VISIBLE : GONE);
    }

    /** Vale oturumunda kişisel domain'i (Uygulamalar) gizler; seçim ana ekrana döner. */
    public void setValetModeActive(boolean active) {
        for (int i = 0; i < domainModels.size(); i++) {
            if (domainModels.get(i).id == 4) {
                domainViews.get(i).setVisibility(active ? GONE : VISIBLE);
            }
        }
    }
}
