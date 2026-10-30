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

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.cockpit.StatusBadge;

public class AssistTabBuilder {
    public interface AssistCallback {
        void onSettingChanged(String settingKey, int newValue);
        /** Dialog onayından sonra UI + araç gönderimi için çalıştırılır */
        void onSafetyWarningRequired(String settingKey, int newValue, Runnable onUserConfirmed);
        void log(String message);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final AssistCallback callback;
    private final CardHolder[] holders = new CardHolder[8];
    private final boolean[] active = new boolean[8];
    private final int[] savedValues = new int[8];

    private static final Object[][] ASSIST_CARDS = {
            {R.string.assist_card_iss, R.drawable.ic_mdi_restart, 0, -1, "issSetting", "ISS"},
            {R.string.assist_card_spd_limit, R.drawable.ic_mdi_speedometer, 2, -1, "spdLimitSetting", "Hız Limitleyici"},
            {R.string.assist_card_ldw, R.drawable.ic_mdi_road_variant, 2, -1, "ldwSetting", "LDW"},
            {R.string.assist_card_ldp, R.drawable.ic_mdi_shield_car, 2, -1, "ldpSetting", "LDP"},
            {R.string.assist_card_fcw, R.drawable.ic_mdi_car_brake_alert, 2, -1, "fcwSetting", "FCW"},
            {R.string.assist_card_aeb, R.drawable.ic_mdi_car_brake_abs, 2, -1, "aebSetting", "AEB"},
            {R.string.assist_card_cpd, R.drawable.ic_mdi_car_seat, 2, -1, "cpdSetting", "CPD"},
            {R.string.assist_card_child_lock, R.drawable.ic_mdi_shield_car, 1, 0,
                    "childLockSetting", "Arka Cam Çocuk Kilidi"}
    };

    public AssistTabBuilder(Context context, SharedPreferences prefs, AssistCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
    }

    public ScrollView build() {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int m = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(m, CockpitTokens.dim(context, R.dimen.ck_space_md), m, m);

        TextView title = new TextView(context);
        CockpitTokens.headlineMd(title);
        title.setText(R.string.assist_title);
        outer.addView(title);

        TextView subtitle = new TextView(context);
        CockpitTokens.bodySm(subtitle);
        subtitle.setText(R.string.assist_subtitle);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        outer.addView(subtitle, subLp);

        CockpitGrid grid = new CockpitGrid(context);
        outer.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < ASSIST_CARDS.length; i++) {
            savedValues[i] = prefs.getInt((String) ASSIST_CARDS[i][4], -1);
            active[i] = savedValues[i] == (Integer) ASSIST_CARDS[i][2];
            grid.add(buildCard(i), 4);
        }

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private CockpitCard buildCard(int index) {
        final int cardIndex = index;
        final int titleRes = (Integer) ASSIST_CARDS[index][0];
        final int iconRes = (Integer) ASSIST_CARDS[index][1];
        final int activeValue = (Integer) ASSIST_CARDS[index][2];
        final int passiveValue = (Integer) ASSIST_CARDS[index][3];
        final String settingKey = (String) ASSIST_CARDS[index][4];
        final String logPrefix = (String) ASSIST_CARDS[index][5];

        CockpitCard card = new CockpitCard(context, context.getString(titleRes)).span(2);
        StatusBadge badge = active[index]
                ? StatusBadge.nominal(context, getStatusText(settingKey))
                : StatusBadge.neutral(context, context.getString(R.string.assist_status_not_set));
        card.status(badge);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(CockpitTokens.color(context, active[index]
                        ? R.color.ck_accent : R.color.ck_text_medium),
                PorterDuff.Mode.SRC_IN);
        body.addView(icon, new LinearLayout.LayoutParams(
                CockpitTokens.dp(context, 40), CockpitTokens.dp(context, 40)));

        View spacer = new View(context);
        body.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        CockpitToggle toggle = new CockpitToggle(context);
        toggle.setChecked(active[index], false);
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                CockpitTokens.dim(context, R.dimen.ck_toggle_w),
                CockpitTokens.dim(context, R.dimen.ck_toggle_h));
        toggleLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        body.addView(toggle, toggleLp);
        card.addContent(body);

        if ("cpdSetting".equals(settingKey)) {
            TextView note = new TextView(context);
            CockpitTokens.bodySm(note);
            note.setText(R.string.assist_cpd_disclaimer);
            note.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            card.addContent(note);
        } else if ("childLockSetting".equals(settingKey)) {
            TextView note = new TextView(context);
            CockpitTokens.bodySm(note);
            note.setText(R.string.assist_child_lock_desc);
            note.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            card.addContent(note);
        }

        holders[index] = new CardHolder(card, badge, toggle);

        toggle.setOnCheckedChangeListener((t, checked) -> {
            boolean newActive = checked;
            int newValue = newActive ? activeValue : passiveValue;

            if ((settingKey.equals("fcwSetting") || settingKey.equals("aebSetting")) && newActive) {
                callback.onSafetyWarningRequired(settingKey, newValue, () -> {
                    prefs.edit().putInt(settingKey, newValue).apply();
                    callback.onSettingChanged(settingKey, newValue);
                    savedValues[cardIndex] = newValue;
                    callback.log(logPrefix + ": Aktif");
                    trySendCarInfoValue(settingKey, true, logPrefix);
                    active[cardIndex] = true;
                    updateCardUI(cardIndex, true, settingKey);
                });
                return;
            }

            prefs.edit().putInt(settingKey, newValue).apply();
            callback.onSettingChanged(settingKey, newValue);
            savedValues[cardIndex] = newValue;

            if (newActive) {
                callback.log(logPrefix + ": Aktif");
                trySendCarInfoValue(settingKey, true, logPrefix);
            } else {
                callback.log(logPrefix + ": Pasif (Hiçbiri)");
                trySendCarInfoValue(settingKey, false, logPrefix);
            }
            active[cardIndex] = newActive;
            updateCardUI(cardIndex, newActive, settingKey);
        });
        return card;
    }

    private void updateCardUI(int index, boolean isActive, String settingKey) {
        CardHolder holder = holders[index];
        if (holder == null) {
            return;
        }
        holder.toggle.setChecked(isActive, true);
        if (isActive) {
            holder.badge.setLevel(StatusBadge.NOMINAL);
            holder.badge.setText(getStatusText(settingKey));
        } else {
            holder.badge.setLevel(StatusBadge.NEUTRAL);
            holder.badge.setText(context.getString(R.string.assist_status_not_set));
        }
        // İkon rengi: kart başlığının yanındaki ikon body içinde; yeniden boyamak için
        // toggle/rozet yeterli, ikon durumu bir sonraki kurulumda güncellenir.
    }

    private String getStatusText(String settingKey) {
        switch (settingKey) {
            case "issSetting": return context.getString(R.string.assist_status_iss);
            case "spdLimitSetting": return context.getString(R.string.assist_status_spd);
            case "ldwSetting": return context.getString(R.string.assist_status_ldw);
            case "ldpSetting": return context.getString(R.string.assist_status_ldp);
            case "fcwSetting": return context.getString(R.string.assist_status_fcw);
            case "aebSetting": return context.getString(R.string.assist_status_aeb);
            case "cpdSetting": return context.getString(R.string.assist_status_cpd);
            case "childLockSetting": return context.getString(R.string.assist_status_child_lock);
            default: return context.getString(R.string.assist_status_active);
        }
    }

    private void trySendCarInfoValue(String settingKey, boolean disable, String logPrefix) {
        try {
            if ("childLockSetting".equals(settingKey)) {
                if (CarInfoProxy.getInstance().isServiceConnnected()) {
                    // ID_CAR_WINDOW_PROHIBIT is a momentary hardware-button pulse.
                    CarInfoProxy.getInstance().sendItemValue(
                            VDEventCarInfo.MODULE_CAR_SETTING,
                            CarSettingID.ID_CAR_WINDOW_PROHIBIT,
                            1);
                    callback.log("✅ Arka Cam Çocuk Kilidi pulse gönderildi");
                } else {
                    callback.log("🔌 CarInfoProxy bağlı değil, çocuk kilidi gönderilemedi");
                }
                return;
            }
            int currentPowerMode = prefs.getInt("lastPowerMode", -1);
            int[] pwrItems = CarInfoProxy.getInstance().getItemValues(
                    VDEventCarInfo.MODULE_READONLY_INFO,
                    ReadOnlyID.ID_SYSTEM_POWER_MODE);
            if (pwrItems != null && pwrItems.length > 0) {
                currentPowerMode = pwrItems[0];
            }
            callback.log((disable ? "Kapatma" : "Açma") + " Modu PowerMode: " + currentPowerMode);
            if (currentPowerMode == 2 && CarInfoProxy.getInstance().isServiceConnnected()) {
                if (settingKey.equals("issSetting")) {
                    CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_ISS, disable ? 0 : 1);
                } else if (settingKey.equals("ldwSetting")) {
                    CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_LDW, disable ? 2 : 1);
                } else if (settingKey.equals("ldpSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_FCM_INHIBIT, vals);
                } else if (settingKey.equals("fcwSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1, 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 36, vals);
                } else if (settingKey.equals("aebSetting")) {
                    int[] vals = new int[]{disable ? 2 : 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, 37, vals);
                } else if (settingKey.equals("spdLimitSetting")) {
                    int[] vals = disable ? new int[]{2, 2} : new int[]{1, 1};
                    CarInfoProxy.getInstance().sendItemValues(VDEventCarInfo.MODULE_CAR_SETTING, CarSettingID.ID_CAR_SPD_LIMIT_WARN_SET, vals);
                } else if (settingKey.equals("cpdSetting")) {
                    CarInfoProxy.getInstance().sendItemValue(
                            VDEventCarInfo.MODULE_CAR_SETTING,
                            CarSettingID.ID_CAR_CIR_ENA_STS,
                            disable ? 2 : 1);
                }
                callback.log("✅ " + logPrefix + " değeri araca gönderildi");
            }
        } catch (Exception e) {
            callback.log("❌ CarInfo gönderim hatası: " + e.getMessage());
        }
    }

    private static final class CardHolder {
        final CockpitCard card;
        final StatusBadge badge;
        final CockpitToggle toggle;

        CardHolder(CockpitCard card, StatusBadge badge, CockpitToggle toggle) {
            this.card = card;
            this.badge = badge;
            this.toggle = toggle;
        }
    }
}
