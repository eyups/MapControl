package com.eyups.roaddeck.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.cockpit.CockpitCard;
import com.eyups.roaddeck.ui.cockpit.CockpitGrid;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;
import com.eyups.roaddeck.ui.cockpit.StatusBadge;

public class DriveModeTabBuilder {
    public interface DriveModeCallback {
        void onModeSelected(int modeValue);
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final DriveModeCallback callback;

    private ScrollView scrollView;
    private LinearLayout tabContent;
    private final LinearLayout[] modeCards = new LinearLayout[8];
    private final ImageView[] modeIcons = new ImageView[8];
    private final TextView[] modeTitles = new TextView[8];
    private final View[] modeIndicators = new View[8];
    private StatusBadge currentBadge;
    private int selectedIndex;
    private HandlerThread vdbusThread;
    private Handler vdbusHandler;
    private Handler mainHandler;
    private int requestSeq;

    private final String[] modeNames = new String[8];
    private static final int[] MODE_VALUES = {-1, 0, 1, 2, 3, 4, 5, 7};
    private static final int[] MODE_ICONS = {R.drawable.ic_mdi_radiobox_blank,
            R.drawable.ic_mdi_leaf, R.drawable.ic_mdi_car, R.drawable.ic_mdi_car_sports,
            R.drawable.ic_mdi_snowflake, R.drawable.ic_mdi_waves, R.drawable.ic_mdi_terrain,
            R.drawable.ic_mdi_beach};

    public DriveModeTabBuilder(Context context, SharedPreferences prefs, DriveModeCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        modeNames[0] = context.getString(R.string.ck_drive_none);
        modeNames[1] = context.getString(R.string.ck_drive_eco);
        modeNames[2] = context.getString(R.string.ck_drive_normal);
        modeNames[3] = context.getString(R.string.ck_drive_sport);
        modeNames[4] = context.getString(R.string.ck_drive_snow);
        modeNames[5] = context.getString(R.string.ck_drive_mud);
        modeNames[6] = context.getString(R.string.ck_drive_offroad);
        modeNames[7] = context.getString(R.string.ck_drive_sand);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        tabContent = outer;
        int m = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(m, CockpitTokens.dim(context, R.dimen.ck_space_md), m, m);

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        CockpitTokens.headlineMd(title);
        title.setText(R.string.drive_modes_title);
        heading.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        currentBadge = StatusBadge.neutral(context, "—");
        heading.addView(currentBadge);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        outer.addView(heading, headLp);

        TextView subtitle = new TextView(context);
        CockpitTokens.bodySm(subtitle);
        subtitle.setText(context.getString(R.string.ck_drive_subtitle));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        outer.addView(subtitle, subLp);

        CockpitGrid grid = new CockpitGrid(context);
        outer.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int savedMode = prefs.getInt("driveModeSetting", -1);
        selectedIndex = indexForMode(savedMode);
        for (int i = 0; i < modeNames.length; i++) {
            grid.add(buildModeCard(i), 2);
        }
        refreshSelection();
        if (currentBadge != null) {
            currentBadge.setLevel(isCarInfoConnected() ? StatusBadge.NOMINAL : StatusBadge.NEUTRAL);
            currentBadge.setText(modeNames[selectedIndex]);
        }

        CockpitCard hint = new CockpitCard(context, context.getString(R.string.ck_drive_memory)).span(8);
        TextView body = new TextView(context);
        CockpitTokens.bodySm(body);
        body.setText(context.getString(R.string.ck_drive_memory_body));
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        hint.addContent(body);
        grid.add(hint, 8);

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private View buildModeCard(int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setMinimumHeight(CockpitTokens.dp(context, 160));
        card.setClickable(true);
        card.setFocusable(true);
        card.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_md));
        card.setContentDescription(modeNames[index]);

        ImageView icon = new ImageView(context);
        icon.setImageResource(MODE_ICONS[index]);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium),
                PorterDuff.Mode.SRC_IN);
        card.addView(icon, new LinearLayout.LayoutParams(
                CockpitTokens.dp(context, 40), CockpitTokens.dp(context, 40)));

        TextView label = new TextView(context);
        CockpitTokens.title(label);
        label.setText(modeNames[index]);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addView(label, labelLp);

        View indicator = new View(context);
        indicator.setBackgroundColor(CockpitTokens.color(context, R.color.ck_accent));
        LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams(
                CockpitTokens.dp(context, 48), CockpitTokens.dp(context, 3));
        indLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addView(indicator, indLp);

        modeCards[index] = card;
        modeIcons[index] = icon;
        modeTitles[index] = label;
        modeIndicators[index] = indicator;

        card.setOnClickListener(v -> selectMode(index));
        return card;
    }

    private void selectMode(int index) {
        if (index == selectedIndex) {
            return;
        }
        final int mode = MODE_VALUES[index];
        final String name = modeNames[index];
        final int seq = ++requestSeq;
        if (mode == -1) {
            commitSelection(index, mode, name);
            callback.log("Hafıza modu seçildi: Hiçbiri (Otomatik ayarlama yapılmayacak)");
            return;
        }
        applyDriveMode(seq, index, mode, name);
    }

    private void commitSelection(int index, int mode, String name) {
        selectedIndex = index;
        prefs.edit().putInt("driveModeSetting", mode).apply();
        callback.onModeSelected(mode);
        refreshSelection();
        if (currentBadge != null) {
            currentBadge.setLevel(StatusBadge.NOMINAL);
            currentBadge.setText(name);
        }
    }

    private void showModeFailure(String name) {
        if (currentBadge != null) {
            currentBadge.setLevel(StatusBadge.ADVISORY);
            currentBadge.setText(R.string.ck_drive_connection_missing);
        }
        callback.log("Hafıza modu uygulanamadı: araç bağlantısı yok (" + name + ")");
    }

    private void refreshSelection() {
        for (int i = 0; i < modeCards.length; i++) {
            if (modeCards[i] == null) {
                continue;
            }
            boolean selected = i == selectedIndex;
            modeCards[i].setSelected(selected);
            if (selected) {
                CockpitSurfaces.rippleOutlined(modeCards[i],
                        CockpitTokens.color(context, R.color.ck_surface_2),
                        CockpitTokens.color(context, R.color.ck_accent), 16f, 0x33FFFFFF);
            } else {
                CockpitSurfaces.rippleOutlined(modeCards[i],
                        CockpitTokens.color(context, R.color.ck_surface_1),
                        CockpitTokens.color(context, R.color.ck_stroke), 16f, 0x33FFFFFF);
            }
            int tint = selected
                    ? CockpitTokens.color(context, R.color.ck_accent)
                    : CockpitTokens.color(context, R.color.ck_text_medium);
            modeIcons[i].setColorFilter(tint, PorterDuff.Mode.SRC_IN);
            modeTitles[i].setTextColor(selected
                    ? CockpitTokens.color(context, R.color.ck_text_high)
                    : CockpitTokens.color(context, R.color.ck_text_medium));
            modeIndicators[i].setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private int indexForMode(int mode) {
        for (int i = 0; i < MODE_VALUES.length; i++) {
            if (MODE_VALUES[i] == mode) {
                return i;
            }
        }
        return 0;
    }

    private void applyDriveMode(int seq, int index, int mode, String name) {
        ensureVdbusHandler();
        vdbusHandler.post(() -> {
            boolean success = false;
            try {
                CarInfoProxy carInfo = CarInfoProxy.getInstance();
                // init/sleep ana thread'de yasak: bu HandlerThread'de, sinirli denemeyle.
                for (int attempt = 0; attempt < 3; attempt++) {
                    if (seq != requestSeq) {
                        return;
                    }
                    if (carInfo.isServiceConnnected()) {
                        success = true;
                        break;
                    }
                    carInfo.init(context);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (success) {
                    carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, mode);
                }
            } catch (Exception e) {
                callback.log("applyDriveMode hatası: " + e.getMessage());
                success = false;
            }
            final boolean ok = success;
            mainHandler.post(() -> {
                if (seq != requestSeq) {
                    return;
                }
                if (ok) {
                    commitSelection(index, mode, name);
                    callback.log("Hafıza modu gönderildi: " + mode + " (Mode: " + name + ")");
                } else {
                    showModeFailure(name);
                }
            });
        });
    }

    private boolean isCarInfoConnected() {
        try {
            return CarInfoProxy.getInstance().isServiceConnnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureVdbusHandler() {
        if (vdbusHandler != null) {
            return;
        }
        vdbusThread = new HandlerThread("drive-mode-vdbus");
        vdbusThread.start();
        vdbusHandler = new Handler(vdbusThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }
}
