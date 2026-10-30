package com.mapcontrol.ui.builder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.service.DmsFaceRecognitionManager;
import com.mapcontrol.ui.activity.DmsFaceProfileActivity;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.CarSeatMemoryManager;
import com.mapcontrol.vehicle.FaceProfileStore;

import java.util.List;

public final class DmsFaceRecognitionTabBuilder {
    public interface Logger {
        void log(String message);
    }

    public static final int CAMERA_PERMISSION_REQUEST = 4217;
    public static final String PREF_SEAT_TYPE_CHOSEN = "seat_memory_type_chosen_v1";

    private final Activity activity;
    private final SharedPreferences prefs;
    private final Logger logger;

    private View selectionScreenView;
    private LinearLayout normalContentContainer;
    private TextView activeModeLabel;

    private CockpitToggle enabledSwitch;
    private TextView description;
    private TextView toggleLabel;
    private TextView status;
    private LinearLayout savedProfilesContainer;
    private boolean pendingEnable;
    private boolean suppressSwitchCallback;

    public DmsFaceRecognitionTabBuilder(Activity activity, SharedPreferences prefs, Logger logger) {
        this.activity = activity;
        this.prefs = prefs;
        this.logger = logger;
    }

    public View build() {
        FrameLayout root = new FrameLayout(activity);

        selectionScreenView = buildSelectionScreen();
        normalContentContainer = buildNormalContent();

        root.addView(selectionScreenView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(normalContentContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        boolean chosen = prefs.getBoolean(PREF_SEAT_TYPE_CHOSEN, false);
        selectionScreenView.setVisibility(chosen ? View.GONE : View.VISIBLE);
        normalContentContainer.setVisibility(chosen ? View.VISIBLE : View.GONE);

        refresh();
        return root;
    }

    /**
     * Completely clean standalone screen with only the mode selection cards in the dead center.
     */
    private View buildSelectionScreen() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(activity, R.dimen.ck_margin);
        content.setPadding(margin, CockpitTokens.dim(activity, R.dimen.ck_space_lg),
                margin, CockpitTokens.dim(activity, R.dimen.ck_space_lg));
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(activity);
        title.setText("Koltuk Hafıza Tipi Seçimi");
        CockpitTokens.headlineMd(title);
        content.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Lütfen aracınızın koltuk hafıza donanım tipini seçin:");
        CockpitTokens.bodySm(subtitle);
        subtitle.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = CockpitTokens.dim(activity, R.dimen.ck_space_sm);
        subLp.bottomMargin = CockpitTokens.dim(activity, R.dimen.ck_space_md);
        content.addView(subtitle, subLp);

        CockpitGrid grid = new CockpitGrid(activity);
        content.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CockpitCard screenCard = new CockpitCard(activity,
                "Multimedya Ekranı Üzerinde");
        TextView screenDesc = new TextView(activity);
        screenDesc.setText("Koltuk hafızasını multimedya ekranından yönetin.");
        CockpitTokens.bodySm(screenDesc);
        screenDesc.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
        screenCard.addContent(screenDesc);
        screenCard.setClickable(true);
        screenCard.setFocusable(true);
        screenCard.setOnClickListener(v -> selectModeAndProceed(CarSeatMemoryManager.MODE_SCREEN));
        grid.add(screenCard, 4);

        CockpitCard doorCard = new CockpitCard(activity,
                "Araç Kapısı Üzerinde");
        TextView doorDesc = new TextView(activity);
        doorDesc.setText("Koltuk hafızasını kapıdaki kumandalardan yönetin.");
        CockpitTokens.bodySm(doorDesc);
        doorDesc.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
        doorCard.addContent(doorDesc);
        doorCard.setClickable(true);
        doorCard.setFocusable(true);
        doorCard.setOnClickListener(v -> selectModeAndProceed(CarSeatMemoryManager.MODE_DOOR));
        grid.add(doorCard, 4);

        return scroll;
    }

    private void selectModeAndProceed(int mode) {
        CarSeatMemoryManager.getInstance(activity).setMemoryMode(mode);
        prefs.edit().putBoolean(PREF_SEAT_TYPE_CHOSEN, true).apply();
        if (selectionScreenView != null) {
            selectionScreenView.setVisibility(View.GONE);
        }
        if (normalContentContainer != null) {
            normalContentContainer.setVisibility(View.VISIBLE);
        }
        refresh();
        Toast.makeText(activity, mode == CarSeatMemoryManager.MODE_DOOR
                ? "Araç Kapısı Üzerinde modu seçildi."
                : "Multimedya Ekranı Üzerinde modu seçildi.", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout buildNormalContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = CockpitTokens.dim(activity, R.dimen.ck_margin);
        content.setPadding(padding, CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                padding, CockpitTokens.dim(activity, R.dimen.ck_space_md));

        // Top Status Bar: shows active memory mode and a button to switch back to the selection screen
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(CockpitTokens.dim(activity, R.dimen.ck_space_md),
                CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                CockpitTokens.dim(activity, R.dimen.ck_space_md),
                CockpitTokens.dim(activity, R.dimen.ck_space_sm));
        CockpitSurfaces.level2(topBar);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topBarLp.bottomMargin = dp(14);
        content.addView(topBar, topBarLp);

        activeModeLabel = new TextView(activity);
        CockpitTokens.bodySm(activeModeLabel);
        activeModeLabel.setTextColor(CockpitTokens.color(activity, R.color.ck_accent));
        topBar.addView(activeModeLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        CockpitButton changeModeBtn = CockpitButton.secondary(activity,
                "Hafıza Tipini Değiştir");
        changeModeBtn.setOnClickListener(v -> {
            normalContentContainer.setVisibility(View.GONE);
            selectionScreenView.setVisibility(View.VISIBLE);
        });
        topBar.addView(changeModeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Header Card
        CockpitCard mainCard = new CockpitCard(activity,
                activity.getString(R.string.dms_recognition_title));
        content.addView(mainCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        description = new TextView(activity);
        CockpitTokens.bodySm(description);
        description.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
        LinearLayout.LayoutParams descriptionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mainCard.addContent(description, descriptionLp);

        LinearLayout toggleRow = new LinearLayout(activity);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(CockpitTokens.dim(activity, R.dimen.ck_space_md),
                CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                CockpitTokens.dim(activity, R.dimen.ck_space_md),
                CockpitTokens.dim(activity, R.dimen.ck_space_sm));
        CockpitSurfaces.level2(toggleRow);
        LinearLayout.LayoutParams toggleRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleRowLp.topMargin = CockpitTokens.dim(activity, R.dimen.ck_space_md);
        mainCard.addContent(toggleRow, toggleRowLp);

        toggleLabel = new TextView(activity);
        CockpitTokens.title(toggleLabel);
        toggleRow.addView(toggleLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        applySeatMemoryCopy();

        enabledSwitch = new CockpitToggle(activity);
        enabledSwitch.setChecked(DmsFaceRecognitionManager.isEnabled(activity), false);
        toggleRow.addView(enabledSwitch);
        enabledSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (suppressSwitchCallback) {
                return;
            }
            if (enabled) {
                enableRecognition();
            } else {
                pendingEnable = false;
                prefs.edit()
                        .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, false)
                        .putString(DmsFaceRecognitionManager.PREF_STATUS,
                                activity.getString(R.string.dms_recognition_stopped))
                        .apply();
                DmsFaceRecognitionManager.stop(activity);
                logger.log(activity.getString(R.string.dms_recognition_stopped));
                refresh();
            }
        });

        status = new TextView(activity);
        CockpitTokens.bodySm(status);
        status.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = CockpitTokens.dim(activity, R.dimen.ck_space_md);
        mainCard.addContent(status, statusLp);

        CockpitButton profilesButton = CockpitButton.primary(activity,
                activity.getString(R.string.dms_recognition_manage_profiles));
        profilesButton.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, DmsFaceProfileActivity.class)));
        LinearLayout.LayoutParams profilesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        profilesLp.topMargin = CockpitTokens.dim(activity, R.dimen.ck_space_md);
        mainCard.addContent(profilesButton, profilesLp);

        // Saved Profiles Summary Card
        CockpitCard profilesCard = new CockpitCard(activity,
                activity.getString(R.string.log_dms_face_profiles));
        LinearLayout.LayoutParams profilesCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        profilesCardLp.topMargin = CockpitTokens.dim(activity, R.dimen.ck_space_md);
        content.addView(profilesCard, profilesCardLp);

        savedProfilesContainer = new LinearLayout(activity);
        savedProfilesContainer.setOrientation(LinearLayout.VERTICAL);
        profilesCard.addContent(savedProfilesContainer);

        return content;
    }

    public void onCameraPermissionResult(boolean granted) {
        if (!pendingEnable) {
            return;
        }
        pendingEnable = false;
        if (granted) {
            enabledSwitch.setChecked(true, false);
        } else {
            Toast.makeText(activity, R.string.dms_face_camera_permission, Toast.LENGTH_LONG).show();
        }
    }

    public void refresh() {
        if (status == null) {
            return;
        }
        boolean isDoor = CarSeatMemoryManager.getInstance(activity).isDoorMode();
        if (activeModeLabel != null) {
            activeModeLabel.setText(isDoor
                    ? "Hafıza Tipi: Araç Kapısı Üzerinde"
                    : "Hafıza Tipi: Multimedya Ekranı Üzerinde");
        }
        applySeatMemoryCopy();
        int profileCount = 0;
        try {
            List<FaceProfileStore.Profile> profiles = FaceProfileStore.load(activity);
            profileCount = profiles.size();
            String lastName = prefs.getString(DmsFaceRecognitionManager.PREF_LAST_NAME, "");
            int memory = prefs.getInt(DmsFaceRecognitionManager.PREF_LAST_MEMORY, 0);
            boolean seatMemoryAvailable = CarSeatMemoryManager.isSeatMemoryAvailable(activity);
            String last = lastName.isEmpty() ? activity.getString(R.string.dms_recognition_no_match_yet)
                    : seatMemoryAvailable
                    ? activity.getString(R.string.dms_recognition_last_match, lastName, memory)
                    : activity.getString(R.string.dms_recognition_last_match_name, lastName);
            String serviceStatus = prefs.getString(DmsFaceRecognitionManager.PREF_STATUS,
                    activity.getString(R.string.dms_recognition_idle));
            boolean enabled = DmsFaceRecognitionManager.isEnabled(activity);
            String enabledText = enabled ? activity.getString(R.string.climate_auto_master_on)
                    : activity.getString(R.string.climate_auto_master_off);
            status.setText(activity.getString(R.string.dms_recognition_status,
                    profiles.size(), FaceProfileStore.MAX_PROFILES,
                    enabledText, last, serviceStatus));
            populateSavedProfilesSummary(profiles);
        } catch (IllegalStateException e) {
            status.setText(activity.getString(R.string.dms_face_load_error, e.getMessage()));
        }
        if (enabledSwitch != null) {
            boolean isServiceEnabled = DmsFaceRecognitionManager.isEnabled(activity);
            enabledSwitch.setEnabled(profileCount > 0 || isServiceEnabled);
            if (enabledSwitch.isChecked() != isServiceEnabled && !pendingEnable) {
                suppressSwitchCallback = true;
                enabledSwitch.setChecked(isServiceEnabled, false);
                suppressSwitchCallback = false;
            }
        }
    }

    private void applySeatMemoryCopy() {
        boolean seatMemory = CarSeatMemoryManager.isSeatMemoryAvailable(activity);
        if (description != null) {
            description.setText(seatMemory
                    ? R.string.dms_recognition_description
                    : R.string.dms_recognition_description_no_memory);
        }
        if (toggleLabel != null) {
            toggleLabel.setText(seatMemory
                    ? R.string.dms_recognition_enable
                    : R.string.dms_recognition_enable_no_memory);
        }
    }

    private void populateSavedProfilesSummary(List<FaceProfileStore.Profile> profiles) {
        if (savedProfilesContainer == null) {
            return;
        }
        savedProfilesContainer.removeAllViews();
        if (profiles.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText(CarSeatMemoryManager.isSeatMemoryAvailable(activity)
                    ? R.string.dms_face_no_profiles
                    : R.string.dms_face_no_profiles_no_memory);
            CockpitTokens.bodySm(empty);
            empty.setTextColor(CockpitTokens.color(activity, R.color.ck_text_medium));
            savedProfilesContainer.addView(empty);
            return;
        }
        for (int i = 0; i < profiles.size(); i++) {
            FaceProfileStore.Profile p = profiles.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                    CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                    CockpitTokens.dim(activity, R.dimen.ck_space_sm),
                    CockpitTokens.dim(activity, R.dimen.ck_space_sm));
            CockpitSurfaces.level2(row);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rowLp.topMargin = dp(8);
            }

            TextView nameTv = new TextView(activity);
            nameTv.setText((i + 1) + ". " + p.name);
            CockpitTokens.title(nameTv);
            row.addView(nameTv, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (CarSeatMemoryManager.isSeatMemoryAvailable(activity)) {
                TextView badge = new TextView(activity);
                if (CarSeatMemoryManager.getInstance(activity).isDoorMode()) {
                    String coords = CarSeatMemoryManager.getInstance(activity).getCachedSlotSummary(p.seatMemory);
                    badge.setText("Hafıza " + p.seatMemory + "\n" + coords);
                } else {
                    badge.setText("Koltuk Hafızası " + p.seatMemory);
                }
                CockpitTokens.labelCaps(badge);
                badge.setTextColor(CockpitTokens.color(activity, R.color.ck_accent));
                badge.setGravity(Gravity.END);
                row.addView(badge);
            }

            savedProfilesContainer.addView(row, rowLp);
        }
    }

    private void enableRecognition() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            pendingEnable = true;
            suppressSwitchCallback = true;
            enabledSwitch.setChecked(false);
            suppressSwitchCallback = false;
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
            return;
        }
        try {
            prefs.edit()
                    .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, true)
                    .putString(DmsFaceRecognitionManager.PREF_STATUS,
                            activity.getString(R.string.dms_recognition_starting))
                    .apply();
            DmsFaceRecognitionManager.start(activity);
            logger.log(activity.getString(R.string.dms_recognition_started));
            refresh();
        } catch (RuntimeException e) {
            prefs.edit()
                    .putBoolean(DmsFaceRecognitionManager.PREF_ENABLED, false)
                    .putString(DmsFaceRecognitionManager.PREF_STATUS,
                            activity.getString(R.string.dms_recognition_stopped))
                    .apply();
            suppressSwitchCallback = true;
            enabledSwitch.setChecked(false);
            suppressSwitchCallback = false;
            Toast.makeText(activity, activity.getString(
                    R.string.dms_recognition_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
