package com.mapcontrol.ui.builder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitNavModel;
import com.mapcontrol.ui.cockpit.CockpitNavRail;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.valet.ValetModeManager;

/** Cockpit nav rail adapter; SideRailCallback is preserved. */
public class SideRailBuilder {
    public interface SideRailCallback {
        void onTabSelected(int tabIndex, String title);
        void onAppManagementRequested();
        void log(String message);
        default void onRailWidthChanged(boolean expanded) { }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SideRailCallback callback;
    private CockpitNavRail rail;
    private boolean launcherModeActive;
    private boolean contentWide;
    private int currentTab = CockpitNavModel.TAB_HOME;

    public SideRailBuilder(Context context, SharedPreferences prefs, SideRailCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public LinearLayout build() {
        rail = new CockpitNavRail(context, prefs, new CockpitNavRail.Listener() {
            @Override
            public void onDomainSelected(CockpitNavModel.Domain domain) {
                int tab = restoredTab(domain);
                prefs.edit().putInt(CockpitNavModel.PREF_LAST_TAB + "_" + domain.id, tab)
                        .putInt(CockpitNavModel.PREF_LAST_DOMAIN, domain.id).apply();
                callback.onTabSelected(tab, context.getString(domain.titleRes));
            }

            @Override
            public void onSettingsSelected() {
                callback.onTabSelected(CockpitNavModel.TAB_SETTINGS,
                        context.getString(R.string.ck_domain_settings));
            }

            @Override
            public void onRailWidthChanged(boolean expanded) {
                callback.onRailWidthChanged(expanded);
            }

            @Override
            public void onAppManagementNeeded() {
                callback.onAppManagementRequested();
            }
        }, false);
        rail.setSelectedTab(currentTab);
        applyRailVisibility();
        return rail;
    }

    /** Dikey ekran: üstte yatay domain şeridi. */
    public LinearLayout buildPortrait() {
        rail = new CockpitNavRail(context, prefs, new CockpitNavRail.Listener() {
            @Override
            public void onDomainSelected(CockpitNavModel.Domain domain) {
                int tab = restoredTab(domain);
                prefs.edit().putInt(CockpitNavModel.PREF_LAST_TAB + "_" + domain.id, tab)
                        .putInt(CockpitNavModel.PREF_LAST_DOMAIN, domain.id).apply();
                callback.onTabSelected(tab, context.getString(domain.titleRes));
            }

            @Override
            public void onSettingsSelected() {
                callback.onTabSelected(CockpitNavModel.TAB_SETTINGS,
                        context.getString(R.string.ck_domain_settings));
            }

            @Override
            public void onAppManagementNeeded() {
                callback.onAppManagementRequested();
            }
        }, true);
        rail.setSelectedTab(currentTab);
        applyRailVisibility();
        return rail;
    }

    private int restoredTab(CockpitNavModel.Domain domain) {
        // Son seçilen alt sekme kullanıcı seçiminde yazılır; yoksa domain'in ilk sekmesi.
        int stored = prefs.getInt(CockpitNavModel.PREF_LAST_TAB + "_" + domain.id, -1);
        if (stored >= 0) {
            for (CockpitNavModel.Item item : domain.items) {
                if (item.tabId == stored && CockpitNavModel.isAvailable(prefs, stored)) {
                    return stored;
                }
            }
        }
        for (CockpitNavModel.Item item : domain.items) {
            if (CockpitNavModel.isAvailable(prefs, item.tabId)) {
                return item.tabId;
            }
        }
        return domain.items.get(0).tabId;
    }

    public void setLauncherModeActive(boolean active) {
        launcherModeActive = active;
        applyRailVisibility();
    }

    public void setContentWide(boolean wide) {
        contentWide = wide;
        applyRailVisibility();
    }

    public boolean isRailHidden() {
        return launcherModeActive || contentWide;
    }

    public boolean isLauncherModeActive() {
        return launcherModeActive;
    }

    private void applyRailVisibility() {
        if (rail != null) {
            rail.setRailVisible(!isRailHidden());
        }
    }

    public void setSelectionForTabIndex(int tabIndex) {
        currentTab = tabIndex;
        if (rail != null) {
            rail.setSelectedTab(tabIndex);
        }
    }

    public void setExpanded(boolean expanded) {
        if (rail != null) {
            rail.setExpanded(expanded, true);
        }
    }

    public boolean isExpanded() {
        return rail != null && rail.isExpanded();
    }

    public void reapplyTheme() {
        if (rail != null) {
            rail.setSelectedTab(currentTab);
        }
        if (rail != null) {
            View root = rail;
            root.setBackgroundColor(UiStyles.color(context, R.color.ck_surface_1));
        }
    }

    /** Valet oturumunda kişisel domain'i (Uygulamalar) gizler. */
    public void setValetModeActive(boolean active) {
        if (rail == null) {
            return;
        }
        rail.setValetModeActive(active);
        if (active && CockpitNavModel.domainForTab(currentTab).id == 4) {
            callback.onTabSelected(CockpitNavModel.TAB_HOME,
                    context.getString(R.string.ck_domain_cockpit));
        }
    }

    public boolean isValetModeActive() {
        return ValetModeManager.isActive(context);
    }

    public void showExperimentalWarning(String seenKey, Runnable open) {
        if (prefs.getBoolean(seenKey, false)) {
            open.run();
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(28 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, Math.round(8 * context.getResources().getDisplayMetrics().density));

        TextView title = new TextView(context);
        title.setText(R.string.experimental_feature_warning_title);
        title.setTextSize(26);
        title.setTypeface(com.mapcontrol.ui.cockpit.CockpitTokens.inter(context,
                com.mapcontrol.ui.cockpit.CockpitTokens.WEIGHT_BOLD));
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView message = new TextView(context);
        message.setText(R.string.experimental_feature_warning);
        message.setTextSize(20);
        message.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, Math.round(18 * context.getResources().getDisplayMetrics().density), 0, 0);
        box.addView(message);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton(R.string.experimental_feature_warning_ok, (d, which) -> {
                    prefs.edit().putBoolean(seenKey, true).apply();
                    open.run();
                })
                .create();
        dialog.show();
    }
}
