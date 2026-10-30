package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.StatusBadge;

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

    private static final String[] MODE_NAMES = {"Hiçbiri", "Eco", "Normal", "Sport", "Snow",
            "Mud", "Offroad", "Sand"};
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
        subtitle.setText("Hafıza modu aracın sürüş karakterini belirler. 'Hiçbiri' otomatik ayarlama yapmaz.");
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        outer.addView(subtitle, subLp);

        CockpitGrid grid = new CockpitGrid(context);
        outer.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int savedMode = prefs.getInt("driveModeSetting", -1);
        selectedIndex = indexForMode(savedMode);
        for (int i = 0; i < MODE_NAMES.length; i++) {
            grid.add(buildModeCard(i), 2);
        }
        refreshSelection();
        if (currentBadge != null) {
            currentBadge.setLevel(StatusBadge.NOMINAL);
            currentBadge.setText(MODE_NAMES[selectedIndex]);
        }

        CockpitCard hint = new CockpitCard(context, "Hafıza Modu").span(8);
        TextView body = new TextView(context);
        CockpitTokens.bodySm(body);
        body.setText("Seçim prefs'e yazılır ve servis aynı değeri yeniden uygular. "
                + "Hiçbiri seçildiğinde araç ayarlarına yazılmaz.");
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
        card.setContentDescription(MODE_NAMES[index]);

        ImageView icon = new ImageView(context);
        icon.setImageResource(MODE_ICONS[index]);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium),
                PorterDuff.Mode.SRC_IN);
        card.addView(icon, new LinearLayout.LayoutParams(
                CockpitTokens.dp(context, 40), CockpitTokens.dp(context, 40)));

        TextView label = new TextView(context);
        CockpitTokens.title(label);
        label.setText(MODE_NAMES[index]);
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
        selectedIndex = index;
        int mode = MODE_VALUES[index];
        prefs.edit().putInt("driveModeSetting", mode).apply();
        callback.onModeSelected(mode);
        refreshSelection();
        if (currentBadge != null) {
            currentBadge.setText(MODE_NAMES[index]);
        }
        if (mode == -1) {
            callback.log("Hafıza modu seçildi: Hiçbiri (Otomatik ayarlama yapılmayacak)");
        } else {
            callback.log("Hafıza modu seçildi: " + MODE_NAMES[index] + " (Mode: " + mode + ")");
            applyDriveMode(mode);
        }
    }

    private void refreshSelection() {
        for (int i = 0; i < modeCards.length; i++) {
            if (modeCards[i] == null) {
                continue;
            }
            boolean selected = i == selectedIndex;
            if (selected) {
                modeCards[i].setBackground(CockpitSurfaces.outlined(context,
                        CockpitTokens.color(context, R.color.ck_surface_2),
                        CockpitTokens.color(context, R.color.ck_accent), 16f));
            } else {
                modeCards[i].setBackground(CockpitSurfaces.outlined(context,
                        CockpitTokens.color(context, R.color.ck_surface_1),
                        CockpitTokens.color(context, R.color.ck_stroke), 16f));
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

    private void applyDriveMode(int mode) {
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();

            if (!carInfo.isServiceConnnected()) {
                carInfo.init(context);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        () -> applyDriveMode(mode), 500);
                return;
            }

            int valueToSend = mode;
            if (mode == 6 || mode == 7) {
                valueToSend = 7; // SAND
            }

            carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, valueToSend);
            callback.log("Hafıza modu gönderildi: " + valueToSend + " (Mode: " + mode + ")");
        } catch (Exception e) {
            callback.log("applyDriveMode hatası: " + e.getMessage());
        }
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public LinearLayout getTabContent() {
        return tabContent;
    }
}
