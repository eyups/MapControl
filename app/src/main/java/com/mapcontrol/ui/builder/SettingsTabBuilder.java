package com.mapcontrol.ui.builder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.InputType;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.driving.DrivingCoach;
import com.mapcontrol.driving.DrivingSessionRecorder;
import com.mapcontrol.driving.DrivingTestData;
import com.mapcontrol.driving.DrivingTripStore;
import com.mapcontrol.tts.MapControlSpeech;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;
import com.mapcontrol.ui.cockpit.CockpitListRow;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.ThemeManager;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.widget.SystemStatsOverlay;
import com.mapcontrol.manager.FloatingBackButtonManager;
import com.mapcontrol.service.BootReceiver;
import com.mapcontrol.util.AdbSettings;
import com.mapcontrol.util.AppLocaleManager;
import com.mapcontrol.util.LauncherModeManager;
import com.mapcontrol.util.PermissionProbeDialog;
import com.mapcontrol.vehicle.material.MaterialVehiclePreferences;
import com.mapcontrol.vehicle.material.MaterialVehicleResources;
import com.mapcontrol.vehicle.material.VehicleMaterialPickerDialog;

public class SettingsTabBuilder {
    public interface SettingsCallback {
        void log(String message);
        String getCarToken();
        void onLauncherModeChanged(boolean enabled);

        void onLocaleChanged();

        void onThemeChanged();

        void onSystemStatsChanged(boolean enabled);
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final SettingsCallback callback;
    private final Handler handler;
    private ScrollView scrollView;
    private LinearLayout settingsTabContent;
    private FloatingBackButtonManager floatingBackButtonManager;
    private UiStyles.BinarySegmentHandle launcherModeSegmentHandle;
    private UiStyles.BinarySegmentHandle languageSegmentHandle;
    private TextView launcherHomeSettingsLink;

    public SettingsTabBuilder(Context context, SharedPreferences prefs, SettingsCallback callback) {
        this.context = context;
        this.prefs = prefs;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        outer.setPadding(margin, margin, margin, margin);

        settingsTabContent = new LinearLayout(context);
        settingsTabContent.setOrientation(LinearLayout.VERTICAL);
        outer.addView(settingsTabContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        buildSettingsSections(settingsTabContent);
        return scrollView;
    }

    private void buildSettingsSections(LinearLayout parent) {
        if (context.getResources().getConfiguration().screenWidthDp >= 1000) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);

            LinearLayout left = createSettingsCard();
            LinearLayout right = createSettingsCard();
            int gutter = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
            leftLp.setMarginEnd(gutter / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 6f);
            rightLp.setMarginStart(gutter / 2);
            columns.addView(left, leftLp);
            columns.addView(right, rightLp);

            LinearLayout general = sectionGroup();
            createThemeSection(general);
            createLanguageSection(general);
            createTtsSection(general);
            createDrivingCoachSection(general);
            createDrivingTestDataSection(general);

            LinearLayout system = sectionGroup();
            createAdbSection(system);
            createSystemStatsSection(system);
            createPermissionsSection(system);
            createFloatingBackButtonSection(system);

            CockpitLegacyStyler.applySectionHeadings(general);
            CockpitLegacyStyler.applySectionHeadings(system);

            LinearLayout vehicle = sectionGroup();
            createLauncherModeSection(vehicle);
            createVehicleModelSection(vehicle);

            LinearLayout app = sectionGroup();
            createAppInfoSection(app);
            createBootAutostartSection(app);

            CockpitLegacyStyler.applySectionHeadings(vehicle);
            CockpitLegacyStyler.applySectionHeadings(app);
            final LinearLayout[] groups = {general, system, vehicle, app};
            final CockpitListRow[] selectedRow = {null};
            int[] icons = {R.drawable.ic_mdi_cog, R.drawable.ic_mdi_cellphone,
                    R.drawable.ic_mdi_car, R.drawable.ic_mdi_package_variant};
            int[] labels = {R.string.ck_settings_cat_general, R.string.ck_settings_cat_system,
                    R.string.ck_settings_cat_vehicle, R.string.ck_settings_cat_app};
            for (int i = 0; i < groups.length; i++) {
                final int index = i;
                CockpitListRow row = new CockpitListRow(context, icons[i],
                        context.getString(labels[i]), null);
                TextView chevron = new TextView(context);
                CockpitTokens.headlineSm(chevron);
                chevron.setText("›");
                chevron.setTextColor(CockpitTokens.color(context, R.color.ck_text_muted));
                row.trailing(chevron);
                row.clickRow(v -> {
                    if (selectedRow[0] != null) {
                        selectedRow[0].setBackground(CockpitSurfaces.outlined(context,
                                CockpitTokens.color(context, R.color.ck_surface_1),
                                CockpitTokens.color(context, R.color.ck_stroke), 16f));
                    }
                    selectedRow[0] = row;
                    row.setBackground(CockpitSurfaces.outlined(context,
                            CockpitTokens.color(context, R.color.ck_surface_2),
                            CockpitTokens.color(context, R.color.ck_accent), 16f));
                    right.removeAllViews();
                    right.addView(groups[index], new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                });
                left.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        CockpitTokens.dim(context, R.dimen.ck_space_sm));
                left.addView(new View(context), gap);
            }

            // Varsayılan kategori: Genel
            if (left.getChildCount() > 0 && left.getChildAt(0) instanceof CockpitListRow) {
                selectedRow[0] = (CockpitListRow) left.getChildAt(0);
                selectedRow[0].setBackground(CockpitSurfaces.outlined(context,
                        CockpitTokens.color(context, R.color.ck_surface_2),
                        CockpitTokens.color(context, R.color.ck_accent), 16f));
            }
            right.addView(general, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            parent.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }

        LinearLayout card = createSettingsCard();
        createThemeSection(card);
        createLanguageSection(card);
        createTtsSection(card);
        createDrivingCoachSection(card);
        createDrivingTestDataSection(card);
        createAppInfoSection(card);
        createBootAutostartSection(card);
        createAdbSection(card);
        createSystemStatsSection(card);
        createPermissionsSection(card);
        createFloatingBackButtonSection(card);
        createLauncherModeSection(card);
        createVehicleModelSection(card);
        parent.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout sectionGroup() {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_sm), 0,
                CockpitTokens.dim(context, R.dimen.ck_space_sm), 0);
        return group;
    }

    private LinearLayout createSettingsCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(inner, inner, inner, inner);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(card);
        return card;
    }

    private void createThemeSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_theme_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(com.mapcontrol.ui.cockpit.CockpitTokens.inter(
                context, com.mapcontrol.ui.cockpit.CockpitTokens.WEIGHT_SEMIBOLD));
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.ck_theme_locked_desc);
        sectionDesc.setTextSize(15);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textSecondary));
        sectionDesc.setTypeface(com.mapcontrol.ui.cockpit.CockpitTokens.inter(
                context, com.mapcontrol.ui.cockpit.CockpitTokens.WEIGHT_REGULAR));
        sectionDesc.setPadding(16, 0, 16, 16);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void createLanguageSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_language_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_language_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean english = AppLocaleManager.isEnglish(context);
        languageSegmentHandle = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.settings_language_tr),
                context.getString(R.string.settings_language_en),
                context.getString(R.string.settings_language_tr),
                context.getString(R.string.settings_language_en),
                !english,
                turkishSelected -> {
                    String tag = turkishSelected
                            ? AppLocaleManager.LOCALE_TR
                            : AppLocaleManager.LOCALE_EN;
                    if (tag.equals(AppLocaleManager.getStoredLocaleTag(context))) {
                        return;
                    }
                    AppLocaleManager.setLocale(context, tag);
                    callback.log("Locale: " + tag);
                    callback.onLocaleChanged();
                });
    }

    private void createDrivingCoachSection(LinearLayout parentContainer) {
        TextView title = new TextView(context);
        title.setText(R.string.settings_driving_coach_title);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parentContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText(R.string.settings_driving_coach_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 12);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        UiStyles.addBinarySegmentedControl(context, parentContainer, null,
                context.getString(R.string.settings_driving_coach_off),
                context.getString(R.string.settings_driving_coach_on),
                context.getString(R.string.settings_driving_coach_off_help),
                context.getString(R.string.settings_driving_coach_on_help),
                !DrivingCoach.isEnabled(context),
                enabledSelected -> {
                    boolean enabled = !enabledSelected;
                    DrivingCoach.setEnabled(context, enabled);
                    callback.log("Sürüş koçu: " + (enabled ? "açık" : "kapalı"));
                });
    }

    private void createTtsSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_tts_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_tts_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        String engine = MapControlSpeech.engine(context);
        int engineMode = MapControlSpeech.ENGINE_IFLYTEK.equals(engine)
                ? MapControlSpeech.ENGINE_MODE_IFLYTEK
                : (MapControlSpeech.ENGINE_ELEVENLABS.equals(engine)
                        ? MapControlSpeech.ENGINE_MODE_ELEVENLABS
                        : MapControlSpeech.ENGINE_MODE_SHERPA);
        UiStyles.addTernarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_tts_engine_title),
                new String[]{
                        context.getString(R.string.settings_tts_sherpa),
                        context.getString(R.string.settings_tts_elevenlabs),
                        context.getString(R.string.settings_tts_iflytek)
                },
                new String[]{
                        context.getString(R.string.settings_tts_sherpa_help),
                        context.getString(R.string.settings_tts_elevenlabs_help),
                        context.getString(R.string.settings_tts_iflytek_help)
                },
                new int[]{
                        MapControlSpeech.ENGINE_MODE_SHERPA,
                        MapControlSpeech.ENGINE_MODE_ELEVENLABS,
                        MapControlSpeech.ENGINE_MODE_IFLYTEK
                },
                engineMode,
                selectedMode -> {
                    String selectedEngine;
                    String display;
                    switch (selectedMode) {
                        case MapControlSpeech.ENGINE_MODE_IFLYTEK:
                            selectedEngine = MapControlSpeech.ENGINE_IFLYTEK;
                            display = "iFlytek (Araç)";
                            break;
                        case MapControlSpeech.ENGINE_MODE_ELEVENLABS:
                            selectedEngine = MapControlSpeech.ENGINE_ELEVENLABS;
                            display = "ElevenLabs";
                            break;
                        default:
                            selectedEngine = MapControlSpeech.ENGINE_SHERPA;
                            display = "Sherpa-ONNX";
                            break;
                    }
                    prefs.edit().putString(MapControlSpeech.KEY_ENGINE, selectedEngine)
                            .putBoolean(MapControlSpeech.KEY_ENGINE_CONFIGURED, true)
                            .apply();
                    callback.log("TTS motoru: " + display);
                });

        TextView keyLabel = new TextView(context);
        keyLabel.setText(R.string.settings_tts_api_key);
        keyLabel.setTextSize(13);
        keyLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        keyLabel.setPadding(16, 14, 16, 4);
        parentContainer.addView(keyLabel);

        EditText keyField = createTtsField(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setHint(R.string.settings_tts_api_hint);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        keyLp.setMargins(16, 0, 16, 8);
        parentContainer.addView(keyField, keyLp);

        TextView voiceLabel = new TextView(context);
        voiceLabel.setText(R.string.settings_tts_voice_id);
        voiceLabel.setTextSize(13);
        voiceLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        voiceLabel.setPadding(16, 8, 16, 4);
        parentContainer.addView(voiceLabel);

        EditText voiceField = createTtsField(InputType.TYPE_CLASS_TEXT);
        voiceField.setHint(R.string.settings_tts_voice_hint);
        voiceField.setText(prefs.getString(MapControlSpeech.KEY_VOICE_ID,
                MapControlSpeech.DEFAULT_VOICE_ID));
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        voiceLp.setMargins(16, 0, 16, 8);
        parentContainer.addView(voiceField, voiceLp);

        TextView status = new TextView(context);
        status.setTextSize(12);
        status.setTextColor(UiStyles.color(context, R.color.textHint));
        status.setPadding(16, 4, 16, 6);
        status.setText(MapControlSpeech.hasApiKey(context)
                ? R.string.settings_tts_key_saved
                : R.string.settings_tts_key_missing);
        parentContainer.addView(status);

        Button save = new Button(context);
        save.setText(R.string.settings_tts_save);
        UiStyles.styleOemButton(save, UiStyles.color(context, R.color.accentHighlight));
        if (ThemeManager.isCyber(context)) {
            save.setTextColor(android.graphics.Color.BLACK);
        }
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        saveLp.setMargins(16, 0, 16, 10);
        save.setOnClickListener(v -> {
            String key = keyField.getText().toString().trim();
            String voice = voiceField.getText().toString().trim();
            if (!key.isEmpty() && !MapControlSpeech.isValidApiKey(key)) {
                Toast.makeText(context, R.string.settings_tts_api_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            if (!key.isEmpty()) {
                editor.putString(MapControlSpeech.KEY_API_KEY, key);
            }
            if (!voice.isEmpty()) {
                editor.putString(MapControlSpeech.KEY_VOICE_ID, voice);
            }
            editor.apply();
            keyField.setText("");
            status.setText(MapControlSpeech.hasApiKey(context)
                    ? R.string.settings_tts_key_saved
                    : R.string.settings_tts_key_missing);
            Toast.makeText(context, R.string.settings_tts_saved, Toast.LENGTH_SHORT).show();
            callback.log("TTS ayarları kaydedildi");
        });
        parentContainer.addView(save, saveLp);
    }

    private EditText createTtsField(int inputType) {
        EditText field = new EditText(context);
        field.setTextSize(14);
        field.setTextColor(UiStyles.color(context, R.color.textPrimary));
        field.setHintTextColor(UiStyles.color(context, R.color.textMuted));
        field.setInputType(inputType);
        field.setSingleLine(true);
        field.setPadding(14, 12, 14, 12);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.logFieldBg));
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(1, UiStyles.color(context, R.color.outlineStrong));
        field.setBackground(bg);
        return field;
    }

    private void createDrivingTestDataSection(LinearLayout parentContainer) {
        TextView title = new TextView(context);
        title.setText(R.string.settings_driving_test_title);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parentContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText(R.string.settings_driving_test_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 10);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(context);
        status.setTextSize(12);
        status.setTextColor(UiStyles.color(context, R.color.textHint));
        status.setPadding(16, 0, 16, 8);
        updateDrivingTestStatus(status);
        parentContainer.addView(status);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(16, 0, 16, 16);

        Button generate = new Button(context);
        generate.setText(R.string.settings_driving_test_generate);
        generate.setAllCaps(false);
        UiStyles.styleOemButton(generate, UiStyles.color(context, R.color.accentHighlight));
        generate.setOnClickListener(v -> {
            int count = DrivingTestData.generate(context, DrivingTestData.DEFAULT_COUNT);
            DrivingSessionRecorder.get(context).reloadLastTrip();
            updateDrivingTestStatus(status);
            Toast.makeText(context, R.string.settings_driving_test_generated,
                    Toast.LENGTH_SHORT).show();
            callback.log("Sürüş Karnesi: " + count + " test sürüşü oluşturuldu");
        });
        LinearLayout.LayoutParams genLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        genLp.setMarginEnd(8);
        buttons.addView(generate, genLp);

        Button delete = new Button(context);
        delete.setText(R.string.settings_driving_test_delete);
        delete.setAllCaps(false);
        UiStyles.styleOemButton(delete, UiStyles.color(context, R.color.statusError));
        delete.setOnClickListener(v -> {
            int removed = DrivingTripStore.deleteTestTrips(context);
            DrivingSessionRecorder.get(context).reloadLastTrip();
            updateDrivingTestStatus(status);
            Toast.makeText(context, removed > 0
                            ? context.getString(R.string.settings_driving_test_deleted, removed)
                            : context.getString(R.string.settings_driving_test_none),
                    Toast.LENGTH_SHORT).show();
            callback.log("Sürüş Karnesi: " + removed + " test sürüşü silindi");
        });
        buttons.addView(delete, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parentContainer.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void updateDrivingTestStatus(TextView status) {
        int count = DrivingTripStore.countTestTrips(context);
        status.setText(context.getString(R.string.settings_driving_test_count, count));
    }

    private void createBootAutostartSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_boot_section_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_boot_section_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean serviceOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_START, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_boot_service_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_boot_service_help_on),
                context.getString(R.string.settings_boot_service_help_off),
                serviceOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_START, on).apply();
                    callback.log("Açılışta servis: " + (on ? "Açık" : "Kapalı"));
                });

        boolean uiOn = prefs.getBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, true);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_boot_ui_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_boot_ui_help_on),
                context.getString(R.string.settings_boot_ui_help_off),
                uiOn,
                on -> {
                    prefs.edit().putBoolean(BootReceiver.KEY_BOOT_AUTO_LAUNCH_UI, on).apply();
                    callback.log("Açılışta ekran: " + (on ? "Açık" : "Kapalı"));
                });
    }

    private void createAdbSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_adb_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        boolean canWrite = AdbSettings.canWrite(context);
        sectionDesc.setText(canWrite ? R.string.settings_adb_desc : R.string.settings_adb_no_permission);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        UiStyles.BinarySegmentHandle[] usbHandle = new UiStyles.BinarySegmentHandle[1];
        usbHandle[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_adb_usb_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_adb_usb_help_on),
                context.getString(R.string.settings_adb_usb_help_off),
                AdbSettings.isUsbEnabled(context),
                on -> applyAdbSetting(false, on, usbHandle[0]));

        UiStyles.BinarySegmentHandle[] wifiHandle = new UiStyles.BinarySegmentHandle[1];
        wifiHandle[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_adb_wifi_title),
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_adb_wifi_help_on),
                context.getString(R.string.settings_adb_wifi_help_off),
                AdbSettings.isWifiEnabled(context),
                on -> applyAdbSetting(true, on, wifiHandle[0]));

        if (!canWrite) {
            usbHandle[0].setInteractionEnabled(false);
            wifiHandle[0].setInteractionEnabled(false);
        }
    }

    private void applyAdbSetting(boolean wifi, boolean on, UiStyles.BinarySegmentHandle handle) {
        String name = wifi ? "Wi-Fi ADB" : "USB ADB";
        boolean ok;
        AdbSettings.UsbResult usbResult = null;
        if (wifi) {
            ok = AdbSettings.setWifiEnabled(context, on);
        } else {
            usbResult = AdbSettings.setUsbEnabled(context, on);
            ok = usbResult.settingOk;
        }
        if (!ok) {
            callback.log(name + ": değer yazılamadı (izin yok veya sistem reddetti)");
            if (handle != null) {
                handle.syncVisualWithoutCommit(!on);
            }
            return;
        }
        if (usbResult != null) {
            callback.log(name + ": Android " + (on ? "açık" : "kapalı")
                    + ", OEM USB=" + usbResult.oemState
                    + (usbResult.oemSent ? " (AdbAdjustAction istendi)" : " (OEM kanalı yok)"));
        } else {
            callback.log(name + ": " + (on ? "Açık" : "Kapalı"));
        }
        // OEM servisleri değeri kısa süre sonra geri alabilir.
        handler.postDelayed(() -> {
            boolean now = wifi ? AdbSettings.isWifiEnabled(context) : AdbSettings.isUsbEnabled(context);
            if (now != on) {
                callback.log(name + ": sistem değeri geri aldı (" + (now ? "Açık" : "Kapalı") + ")");
                if (handle != null) {
                    handle.syncVisualWithoutCommit(now);
                }
            }
        }, 2000);
    }

    private void createPermissionsSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_permissions_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_permissions_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final Button toggle = new Button(context);
        toggle.setText(R.string.settings_permissions_show);
        UiStyles.styleOemButton(toggle, UiStyles.color(context, R.color.accentHighlight));
        if (ThemeManager.isCyber(context)) {
            toggle.setTextColor(android.graphics.Color.BLACK);
        }
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleLp.setMargins(16, 0, 16, 8);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermissionProbeDialog.show(context);
            }
        });
        parentContainer.addView(toggle, toggleLp);
    }

    private void createSystemStatsSection(LinearLayout parentContainer) {
        TextView title = new TextView(context);
        title.setText(R.string.settings_system_stats_title);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(16, 24, 16, 8);
        parentContainer.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView desc = new TextView(context);
        desc.setText(R.string.settings_system_stats_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(16, 0, 16, 12);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean enabled = prefs.getBoolean(SystemStatsOverlay.PREF_ENABLED, false);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_system_stats_help_on),
                context.getString(R.string.settings_system_stats_help_off),
                enabled,
                isEnabled -> {
                    prefs.edit().putBoolean(SystemStatsOverlay.PREF_ENABLED, isEnabled).apply();
                    callback.onSystemStatsChanged(isEnabled);
                    callback.log("Sistem izleme: " + (isEnabled ? "Açık" : "Kapalı"));
                });
    }

    private void createFloatingBackButtonSection(LinearLayout parentContainer) {
        TextView floatingBackButtonTitle = new TextView(context);
        floatingBackButtonTitle.setText(R.string.settings_floating_back_title);
        floatingBackButtonTitle.setTextSize(18);
        floatingBackButtonTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        floatingBackButtonTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        floatingBackButtonTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(floatingBackButtonTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView floatingBackButtonDesc = new TextView(context);
        floatingBackButtonDesc.setText(R.string.settings_floating_back_desc);
        floatingBackButtonDesc.setTextSize(13);
        floatingBackButtonDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        floatingBackButtonDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(floatingBackButtonDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView iconLegendTitle = new TextView(context);
        iconLegendTitle.setText(R.string.floating_hub_icon_legend_title);
        iconLegendTitle.setTextSize(15);
        iconLegendTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        iconLegendTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        iconLegendTitle.setPadding(16, 8, 16, 4);
        parentContainer.addView(iconLegendTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView iconLegendBody = new TextView(context);
        iconLegendBody.setText(R.string.floating_hub_icon_legend_body);
        iconLegendBody.setTextSize(13);
        iconLegendBody.setTextColor(UiStyles.color(context, R.color.textHint));
        iconLegendBody.setPadding(16, 0, 16, 12);
        iconLegendBody.setLineSpacing(3, 1.05f);
        parentContainer.addView(iconLegendBody, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        floatingBackButtonManager = FloatingBackButtonManager.getInstance(context);
        floatingBackButtonManager.setLogCallback(callback::log);

        final boolean savedEnabled = FloatingBackButtonManager.loadEnabledState(context);

        final UiStyles.BinarySegmentHandle[] floatingHandleRef = new UiStyles.BinarySegmentHandle[1];
        floatingHandleRef[0] = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_floating_back_help_on),
                context.getString(R.string.settings_floating_back_help_off),
                savedEnabled,
                isEnabled -> {
                    FloatingBackButtonManager.saveEnabledState(context, isEnabled);

                    if (isEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!android.provider.Settings.canDrawOverlays(context)) {
                                try {
                                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                    Toast.makeText(context, R.string.overlay_permission_toast, Toast.LENGTH_LONG).show();
                                    floatingHandleRef[0].setLeftSelected(false);
                                    return;
                                } catch (Exception e) {
                                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                                    floatingHandleRef[0].setLeftSelected(false);
                                    return;
                                }
                            }
                        }
                        floatingBackButtonManager.show();
                        callback.log("Floating Back Button açıldı");
                    } else {
                        floatingBackButtonManager.hide();
                        callback.log("Floating Back Button kapatıldı");
                    }
                });

        // RadioGroup.post(check) ile aynı: ilk açılışta kayıtlı duruma göre manager senkronu
        handler.post(() -> {
            boolean enabled = FloatingBackButtonManager.loadEnabledState(context);
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(context)) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context, R.string.overlay_permission_toast, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    callback.log("İzin ayarlarına gidilemedi: " + e.getMessage());
                }
                floatingHandleRef[0].setLeftSelected(false);
                return;
            }
            if (enabled) {
                floatingBackButtonManager.show();
            } else {
                floatingBackButtonManager.hide();
            }
        });
    }

    private void createAppInfoSection(LinearLayout parentContainer) {
        TextView appInfoTitle = new TextView(context);
        appInfoTitle.setText(R.string.settings_app_about_title);
        appInfoTitle.setTextSize(18);
        appInfoTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        appInfoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appInfoTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(appInfoTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionTitle = new TextView(context);
        versionTitle.setText(R.string.settings_version_title);
        versionTitle.setTextSize(16);
        versionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        versionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        versionTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(versionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView versionText = new TextView(context);
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            versionText.setText(context.getString(R.string.settings_version_current, versionName));
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText(R.string.settings_version_current_unknown);
        }
        versionText.setTextSize(14);
        versionText.setTextColor(UiStyles.color(context, R.color.textHint));
        versionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(versionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView latestVersionText = new TextView(context);
        latestVersionText.setId(View.generateViewId());
        latestVersionText.setText(R.string.settings_version_latest_loading);
        latestVersionText.setTextSize(14);
        latestVersionText.setTextColor(UiStyles.color(context, R.color.textHint));
        latestVersionText.setPadding(16, 0, 16, 16);
        parentContainer.addView(latestVersionText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallTitle = new TextView(context);
        freeInstallTitle.setText(R.string.settings_install_title);
        freeInstallTitle.setTextSize(16);
        freeInstallTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        freeInstallTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        freeInstallTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(freeInstallTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView freeInstallText = new TextView(context);
        freeInstallText.setText(R.string.settings_install_body);
        freeInstallText.setTextSize(14);
        freeInstallText.setTextColor(UiStyles.color(context, R.color.textHint));
        freeInstallText.setPadding(16, 0, 16, 16);
        parentContainer.addView(freeInstallText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogTitle = new TextView(context);
        changelogTitle.setText(R.string.settings_changelog_title);
        changelogTitle.setTextSize(16);
        changelogTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        changelogTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        changelogTitle.setPadding(16, 16, 16, 8);
        parentContainer.addView(changelogTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView changelogText = new TextView(context);
        changelogText.setId(View.generateViewId());
        changelogText.setText(R.string.common_loading);
        changelogText.setTextSize(14);
        changelogText.setTextColor(UiStyles.color(context, R.color.textHint));
        changelogText.setPadding(16, 0, 16, 16);
        changelogText.setLineSpacing(4, 1.0f);
        parentContainer.addView(changelogText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        fetchAnnouncement(latestVersionText, changelogText);
    }

    private void fetchAnnouncement(TextView latestVersionView, TextView changelogView) {
        new Thread(() -> {
            try {
                String currentVersion = "1.0.0";
                try {
                    currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException ignored) {
                }

                String token = context.getSharedPreferences(
                        "MapControlPrefs", Context.MODE_PRIVATE)
                        .getString("cloudDeviceToken", "");
                URL url = new URL("https://ctrl.eyups.com/api/v1/car/announcements");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Device " + token);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseStr = response.toString().trim();
                    if (responseStr.startsWith("[")) {
                        JSONArray announcements = new JSONArray(responseStr);
                        StringBuilder changelogBuilder = new StringBuilder();
                        String latestVersionFromApi = currentVersion;

                        for (int i = 0; i < announcements.length(); i++) {
                            JSONObject announcement = announcements.getJSONObject(i);
                            String version = announcement.optString("version", "");
                            String title = announcement.optString("title", "");
                            String message = announcement.optString("message", "");

                            if (!version.isEmpty() && compareVersions(version, latestVersionFromApi) > 0) {
                                latestVersionFromApi = version;
                            }

                            if (!title.isEmpty() || !message.isEmpty()) {
                                if (changelogBuilder.length() > 0) {
                                    changelogBuilder.append("\n\n");
                                }
                                if (!version.isEmpty()) {
                                    changelogBuilder.append(context.getString(R.string.settings_changelog_version_prefix)).append(version);
                                    if (!title.isEmpty()) {
                                        changelogBuilder.append(" - ");
                                    } else {
                                        changelogBuilder.append("\n");
                                    }
                                }
                                if (!title.isEmpty()) {
                                    changelogBuilder.append(title).append("\n");
                                }
                                if (!message.isEmpty()) {
                                    changelogBuilder.append(message);
                                }
                            }
                        }

                        final String finalLatestVersion = latestVersionFromApi;
                        final String finalChangelog = changelogBuilder.length() > 0
                                ? changelogBuilder.toString() : context.getString(R.string.settings_changelog_not_found);

                        handler.post(() -> {
                            latestVersionView.setText(context.getString(R.string.settings_version_latest, finalLatestVersion));
                            changelogView.setText(finalChangelog);
                        });
                    } else {
                        JSONObject json = new JSONObject(responseStr);
                        String latestVersion = json.optString("version", currentVersion);
                        String changelog = json.optString("changelog", json.optString("message",
                                context.getString(R.string.settings_changelog_not_found)));

                        handler.post(() -> {
                            latestVersionView.setText(context.getString(R.string.settings_version_latest, latestVersion));
                            CharSequence parsed = parseMarkdown(changelog);
                            changelogView.setText(parsed != null ? parsed : changelog);
                        });
                    }
                } else {
                    handler.post(() -> {
                        latestVersionView.setText(R.string.settings_version_latest_failed);
                        changelogView.setText(context.getString(R.string.settings_changelog_load_failed, responseCode));
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                handler.post(() -> {
                    latestVersionView.setText(R.string.settings_version_latest_error);
                    changelogView.setText(context.getString(R.string.settings_changelog_load_error, e.getMessage()));
                });
                callback.log("fetchAnnouncement hatası: " + e.getMessage());
            }
        }).start();
    }

    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int maxLength = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLength; i++) {
                int num1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
                int num2 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;
                if (num1 > num2) return 1;
                if (num1 < num2) return -1;
            }
            return 0;
        } catch (Exception e) {
            return v1.compareTo(v2);
        }
    }

    private android.text.SpannableString parseMarkdown(String markdownText) {
        return com.mapcontrol.util.MarkdownUtil.parse(markdownText);
    }

    /**
     * Ara\u00e7 Launcher Modu: tam ekran ara\u00e7 paneli deneyimi ({@link LauncherModeManager}).
     * A\u00e7\u0131kken HOME activity-alias da etkinle\u015fir; bu iste\u011fe ba\u011fl\u0131 ikincil bir ad\u0131md\u0131r.
     */
    private void createLauncherModeSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_launcher_mode_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_launcher_mode_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setLineSpacing(3, 1.05f);
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean launcherModeOn = LauncherModeManager.isEnabled(context);
        launcherModeSegmentHandle = UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                context.getString(R.string.settings_launcher_mode_help_on),
                context.getString(R.string.settings_launcher_mode_help_off),
                launcherModeOn,
                enabled -> {
                    callback.log("Ara\u00e7 Launcher Modu: " + (enabled ? "Evet" : "Hay\u0131r"));
                    callback.onLauncherModeChanged(enabled);
                    updateLauncherHomeSettingsLinkVisibility(enabled);
                });

        launcherHomeSettingsLink = new TextView(context);
        launcherHomeSettingsLink.setText(R.string.settings_launcher_home_link);
        launcherHomeSettingsLink.setTextSize(13);
        launcherHomeSettingsLink.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        launcherHomeSettingsLink.setPadding(16, 4, 16, 16);
        launcherHomeSettingsLink.setOnClickListener(v -> {
            LauncherModeManager.logHomeResolutionState(context, callback::log);
            LauncherModeManager.openHomeChooser(context);
        });
        parentContainer.addView(launcherHomeSettingsLink, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateLauncherHomeSettingsLinkVisibility(launcherModeOn);
    }

    private void createVehicleModelSection(LinearLayout parentContainer) {
        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.settings_vehicle_image_title);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(16, 24, 16, 8);
        parentContainer.addView(sectionTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.settings_vehicle_image_desc);
        sectionDesc.setTextSize(13);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setLineSpacing(3, 1.05f);
        sectionDesc.setPadding(16, 0, 16, 12);
        parentContainer.addView(sectionDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView currentLabel = new TextView(context);
        currentLabel.setTextSize(14);
        currentLabel.setTextColor(UiStyles.color(context, R.color.textSecondary));
        currentLabel.setPadding(16, 0, 16, 8);
        refreshVehicleModelStatusLabel(currentLabel);
        parentContainer.addView(currentLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button pickButton = new Button(context);
        pickButton.setText(R.string.settings_vehicle_image_pick);
        UiStyles.styleOemButton(pickButton, UiStyles.color(context, R.color.accentHighlight));
        if (ThemeManager.isCyber(context)) {
            pickButton.setTextColor(android.graphics.Color.BLACK);
        }
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pickLp.setMargins(16, 0, 16, 16);
        pickButton.setOnClickListener(v -> {
            if (context instanceof android.app.Activity) {
                VehicleMaterialPickerDialog.show((android.app.Activity) context,
                        new VehicleMaterialPickerDialog.OnSelectedListener() {
                            @Override
                            public void onManualSelected(
                                    com.mapcontrol.vehicle.material.MaterialVehicleCatalog.Entry entry) {
                                MaterialVehicleResources.getInstance().init(context);
                                refreshVehicleModelStatusLabel(currentLabel);
                                callback.log("Araç görseli seçildi: " + entry.label
                                        + " (" + entry.packageName + ")");
                            }

                            @Override
                            public void onAutoDetectionSelected() {
                                refreshVehicleModelStatusLabel(currentLabel);
                                callback.log("Araç görseli: otomatik algılama etkin");
                            }
                        });
            }
        });
        parentContainer.addView(pickButton, pickLp);
    }

    private void refreshVehicleModelStatusLabel(TextView label) {
        MaterialVehicleResources resources = MaterialVehicleResources.getInstance();
        resources.init(context);
        MaterialVehiclePreferences.Selection manual = MaterialVehiclePreferences.getSelection(context);
        if (manual != null) {
            label.setText(context.getString(R.string.settings_vehicle_selected, manual.label, manual.packageName));
            return;
        }
        if (MaterialVehiclePreferences.isAutoDetectionEnabled(context)) {
            String pkg = resources.getPackageName();
            String source = resources.getSourceLabel();
            if (pkg != null) {
                label.setText(context.getString(R.string.settings_vehicle_auto_detect, source, pkg));
            } else {
                label.setText(R.string.settings_vehicle_auto_no_pkg);
            }
            return;
        }
        label.setText(R.string.settings_vehicle_no_selection);
    }

    private void updateLauncherHomeSettingsLinkVisibility(boolean enabled) {
        if (launcherHomeSettingsLink != null) {
            launcherHomeSettingsLink.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    /** Tercih de\u011fi\u015fince (Ayarlar d\u0131\u015f\u0131ndan da) segment g\u00f6r\u00fcn\u00fcm\u00fcn\u00fc senkronlar. */
    public void syncLauncherModeFromPrefs() {
        boolean enabled = LauncherModeManager.isEnabled(context);
        if (launcherModeSegmentHandle != null) {
            launcherModeSegmentHandle.syncVisualWithoutCommit(enabled);
        }
        updateLauncherHomeSettingsLinkVisibility(enabled);
    }

    public LinearLayout getSettingsTabContent() {
        return settingsTabContent;
    }

    public ScrollView getScrollView() {
        return scrollView != null ? scrollView : build();
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
    }

}
