package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.mapcontrol.R;

/** 48dp chip strip between status bar and viewport; contextual actions at the right edge. */
public class CockpitSubTabBar extends LinearLayout {

    public interface Listener {
        void onTabSelected(int tabId, String title);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final LinearLayout chipRow;
    private final LinearLayout actionHost;
    private final HorizontalScrollView scroller;
    private int selectedTab;

    public CockpitSubTabBar(Context context, SharedPreferences prefs, Listener listener) {
        super(context);
        this.context = context;
        this.prefs = prefs;
        this.listener = listener;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(CockpitTokens.color(context, R.color.ck_bg_base));
        int h = CockpitTokens.dim(context, R.dimen.ck_margin);
        setPadding(h, 0, h, 0);
        setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_subtab_h));

        scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        addView(scroller, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        chipRow = new LinearLayout(context);
        chipRow.setOrientation(HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(chipRow, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        actionHost = new LinearLayout(context);
        actionHost.setOrientation(HORIZONTAL);
        actionHost.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams actionLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT);
        actionLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        addView(actionHost, actionLp);
    }

    public LinearLayout getActionHost() {
        return actionHost;
    }

    public void setDomain(CockpitNavModel.Domain domain, int tabId) {
        selectedTab = tabId;
        chipRow.removeAllViews();
        for (CockpitNavModel.Item item : domain.items) {
            if (!CockpitNavModel.isAvailable(prefs, item.tabId)) {
                continue;
            }
            String title = context.getString(item.titleRes);
            CockpitChip chip = new CockpitChip(context, title);
            chip.setSelectedState(item.tabId == selectedTab);
            chip.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabSelected(item.tabId, title);
                }
            });
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    CockpitTokens.dim(context, R.dimen.ck_chip_h));
            lp.rightMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
            chipRow.addView(chip, lp);
        }
        chipRow.setVisibility(VISIBLE);
    }

    public void setSelectedTab(int tabId) {
        if (tabId == selectedTab) {
            return;
        }
        selectedTab = tabId;
        setDomain(CockpitNavModel.domainForTab(tabId), tabId);
    }

    public int getSelectedTab() {
        return selectedTab;
    }
}
