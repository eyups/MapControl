package com.mapcontrol.cloud;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggleRow;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;

import java.util.Locale;
import java.util.function.Consumer;

/** Ayarlar sekmesindeki "Bulut" kartı. Ağ yalnızca kullanıcı eylemiyle başlar. */
public final class CloudTabBuilder {

    private final Context context;
    private final CloudConfig config;
    private final CloudManager manager;
    private final Consumer<String> logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinearLayout root;
    private final TextView status;
    private final LinearLayout unpairedRow;
    private final EditText codeInput;
    private final Button pairButton;
    private final LinearLayout pairedRow;
    private final TextView actionsPlaceholder;
    private final Button startStopButton;
    private final CockpitToggleRow shareLocationRow;
    private final Button logUploadButton;
    private final Button vdbusButton;
    private final Button updateButton;
    private final Button backupButton;
    private final Button restoreButton;

    private final CloudManager.Listener managerListener = this::refresh;

    public CloudTabBuilder(Context context, Consumer<String> logger) {
        this.context = context;
        this.logger = logger;
        this.config = new CloudConfig(context);
        this.manager = CloudManager.get(context);
        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(R.string.ck_roadmate_title);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(24), dp(16), dp(8));
        root.addView(title, matchWidth());

        TextView description = new TextView(context);
        description.setText(R.string.settings_cloud_desc);
        description.setTextSize(13);
        description.setTextColor(UiStyles.color(context, R.color.textHint));
        description.setPadding(dp(16), 0, dp(16), dp(12));
        root.addView(description, matchWidth());

        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setBaselineAligned(false);
        LinearLayout pairCard = new LinearLayout(context);
        pairCard.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actionsCard = new LinearLayout(context);
        actionsCard.setOrientation(LinearLayout.VERTICAL);
        int cardPad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        pairCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        actionsCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        CockpitSurfaces.level1(pairCard);
        CockpitSurfaces.level1(actionsCard);

        status = new TextView(context);
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(10));
        pairCard.addView(status, matchWidth());

        unpairedRow = new LinearLayout(context);
        unpairedRow.setOrientation(LinearLayout.HORIZONTAL);
        unpairedRow.setGravity(Gravity.CENTER_VERTICAL);
        unpairedRow.setPadding(0, 0, 0, dp(10));
        codeInput = new EditText(context);
        codeInput.setHint(R.string.settings_cloud_pair_hint);
        codeInput.setSingleLine(true);
        codeInput.setTextSize(16);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        codeInput.setTextColor(UiStyles.color(context, R.color.textPrimary));
        codeInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        unpairedRow.addView(codeInput, codeLp);

        pairButton = new Button(context);
        pairButton.setText(R.string.settings_cloud_pair_action);
        pairButton.setAllCaps(false);
        pairButton.setTextColor(UiStyles.color(context, R.color.surfaceCard));
        UiStyles.applySolidRoundedBackground(
                pairButton, UiStyles.color(context, R.color.buttonPrimary));
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pairLp.setMarginStart(dp(8));
        unpairedRow.addView(pairButton, pairLp);
        pairCard.addView(unpairedRow, matchWidth());

        actionsPlaceholder = new TextView(context);
        actionsPlaceholder.setText(R.string.ck_roadmate_unpaired_hint);
        actionsPlaceholder.setTextSize(14);
        actionsPlaceholder.setTextColor(UiStyles.color(context, R.color.textSecondaryCool));
        actionsPlaceholder.setPadding(0, dp(8), 0, dp(8));
        actionsCard.addView(actionsPlaceholder, matchWidth());

        pairedRow = new LinearLayout(context);
        pairedRow.setOrientation(LinearLayout.VERTICAL);
        pairedRow.setPadding(0, 0, 0, 0);

        startStopButton = new Button(context);
        startStopButton.setAllCaps(false);
        startStopButton.setTextSize(16);
        UiStyles.styleOemButton(startStopButton, CockpitTokens.color(context, R.color.ck_accent));
        startStopButton.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        startStopButton.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        startStopButton.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout startRow = new LinearLayout(context);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.addView(startStopButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams startRowLp = matchWidth();
        startRowLp.bottomMargin = dp(8);
        pairedRow.addView(startRow, startRowLp);

        TextView actionsTitle = new TextView(context);
        actionsTitle.setText(R.string.ck_roadmate_actions);
        CockpitTokens.labelCaps(actionsTitle);
        actionsTitle.setPadding(0, dp(10), 0, dp(6));
        pairedRow.addView(actionsTitle, matchWidth());

        CockpitGrid actionsGrid = new CockpitGrid(context);

        shareLocationRow = new CockpitToggleRow(context, R.drawable.ic_mdi_map,
                context.getString(R.string.settings_cloud_share_location), null,
                config.shareLocation());
        LinearLayout.LayoutParams shareLp = matchWidth();
        shareLp.topMargin = dp(8);
        pairedRow.addView(shareLocationRow, shareLp);

        logUploadButton = new Button(context);
        logUploadButton.setText(R.string.settings_cloud_send_log);
        logUploadButton.setAllCaps(false);
        logUploadButton.setTextSize(15);
        logUploadButton.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(logUploadButton, CockpitTokens.color(context, R.color.ck_surface_2));
        actionsGrid.add(logUploadButton, 4);

        vdbusButton = new Button(context);
        vdbusButton.setText(R.string.settings_cloud_vdbus_snapshot);
        vdbusButton.setAllCaps(false);
        vdbusButton.setTextSize(15);
        vdbusButton.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(vdbusButton, CockpitTokens.color(context, R.color.ck_surface_2));
        actionsGrid.add(vdbusButton, 4);

        updateButton = new Button(context);
        updateButton.setText(R.string.settings_cloud_check_update);
        updateButton.setAllCaps(false);
        updateButton.setTextSize(15);
        updateButton.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(updateButton, CockpitTokens.color(context, R.color.ck_surface_2));
        actionsGrid.add(updateButton, 4);

        backupButton = new Button(context);
        backupButton.setText(R.string.settings_cloud_backup);
        backupButton.setAllCaps(false);
        backupButton.setTextSize(15);
        backupButton.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(backupButton, CockpitTokens.color(context, R.color.ck_surface_2));
        actionsGrid.add(backupButton, 4);

        restoreButton = new Button(context);
        restoreButton.setText(R.string.settings_cloud_restore);
        restoreButton.setAllCaps(false);
        restoreButton.setTextSize(15);
        restoreButton.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        UiStyles.styleOemButton(restoreButton, CockpitTokens.color(context, R.color.ck_surface_2));
        actionsGrid.add(restoreButton, 4);
        actionsGrid.add(new View(context), 4); // tek kalan buton yarim kalsin
        pairedRow.addView(actionsGrid, matchWidth());
        actionsCard.addView(pairedRow, matchWidth());

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        leftLp.setMarginEnd(8);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rightLp.setMarginStart(8);
        columns.addView(pairCard, leftLp);
        columns.addView(actionsCard, rightLp);
        root.addView(columns, matchWidth());

        pairButton.setOnClickListener(view -> pair());
        startStopButton.setOnClickListener(view -> toggleRunning());
        logUploadButton.setOnClickListener(view -> uploadLog());
        vdbusButton.setOnClickListener(view -> uploadVdbus());
        updateButton.setOnClickListener(view -> checkUpdate());
        backupButton.setOnClickListener(view -> backupProfiles());
        restoreButton.setOnClickListener(view -> confirmRestoreProfiles());
        shareLocationRow.getToggle().setOnCheckedChangeListener((toggle, checked) -> {
            config.setShareLocation(checked);
            logger.accept("Konum paylaşımı: " + (checked ? "açık" : "kapalı"));
        });

        manager.addListener(managerListener);
        refresh();
    }

    public View getView() {
        return root;
    }

    public void release() {
        manager.removeListener(managerListener);
    }

    private void pair() {
        String code = codeInput.getText().toString().trim().toUpperCase(Locale.US);
        if (code.length() != 8) {
            status.setText(R.string.settings_cloud_invalid_code);
            return;
        }
        pairButton.setEnabled(false);
        status.setText(R.string.settings_cloud_pairing);
        manager.pair(code, new CloudManager.PairCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    codeInput.setText("");
                    pairButton.setEnabled(true);
                    logger.accept("Bulut cihazı eşleştirildi");
                    refresh();
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    pairButton.setEnabled(true);
                    status.setText(R.string.settings_cloud_pair_failed);
                    logger.accept("Bulut eşleştirme başarısız: " + message);
                    refresh();
                });
            }
        });
    }

    private void uploadLog() {
        logUploadButton.setEnabled(false);
        new CloudLogUploader().upload(config, new CloudHttp(),
                (ok, message) -> mainHandler.post(() -> {
                    logUploadButton.setEnabled(true);
                    Toast.makeText(context,
                            ok ? R.string.settings_cloud_log_sent
                                    : R.string.settings_cloud_log_failed,
                            Toast.LENGTH_SHORT).show();
                    logger.accept("Log yükleme: " + message);
                }));
    }

    private void backupProfiles() {
        backupButton.setEnabled(false);
        restoreButton.setEnabled(false);
        ProfileSyncService service = new ProfileSyncService(context, config, new CloudHttp());
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = service.backup();
            } catch (Exception ignored) {
            }
            boolean result = ok;
            mainHandler.post(() -> {
                backupButton.setEnabled(true);
                restoreButton.setEnabled(true);
                Toast.makeText(context,
                        result ? R.string.settings_cloud_backup_done
                                : R.string.settings_cloud_backup_failed,
                        Toast.LENGTH_SHORT).show();
                logger.accept("Profil yedeği: " + (result ? "ok" : "failed"));
            });
        }, "MapControlProfileBackup").start();
    }

    private void confirmRestoreProfiles() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_cloud_restore)
                .setMessage(R.string.settings_cloud_restore_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_cloud_restore_action,
                        (dialog, which) -> restoreProfiles())
                .show();
    }

    private void restoreProfiles() {
        backupButton.setEnabled(false);
        restoreButton.setEnabled(false);
        ProfileSyncService service = new ProfileSyncService(context, config, new CloudHttp());
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = service.restore();
            } catch (Exception ignored) {
            }
            boolean result = ok;
            mainHandler.post(() -> {
                backupButton.setEnabled(true);
                restoreButton.setEnabled(true);
                Toast.makeText(context,
                        result ? R.string.settings_cloud_restore_done
                                : R.string.settings_cloud_restore_failed,
                        Toast.LENGTH_LONG).show();
                logger.accept("Profil geri yükleme: " + (result ? "ok" : "failed"));
            });
        }, "MapControlProfileRestore").start();
    }

    private void checkUpdate() {
        updateButton.setEnabled(false);
        ApkUpdater updater = new ApkUpdater(context, config, new CloudHttp());
        new Thread(() -> {
            try {
                ApkUpdater.Info info = updater.check();
                mainHandler.post(() -> {
                    updateButton.setEnabled(true);
                    if (info == null) {
                        Toast.makeText(context, R.string.settings_cloud_up_to_date,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.settings_cloud_update_title)
                            .setMessage(context.getString(
                                    R.string.settings_cloud_update_message,
                                    info.versionName,
                                    info.size / (1024 * 1024),
                                    info.releaseNotes))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.settings_cloud_update_install,
                                    (dialog, which) -> downloadUpdate(updater, info))
                            .show();
                });
            } catch (Exception error) {
                mainHandler.post(() -> {
                    updateButton.setEnabled(true);
                    Toast.makeText(context, R.string.settings_cloud_update_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }, "MapControlOtaCheck").start();
    }

    private void downloadUpdate(ApkUpdater updater, ApkUpdater.Info info) {
        updateButton.setEnabled(false);
        Toast.makeText(context, R.string.settings_cloud_update_downloading,
                Toast.LENGTH_SHORT).show();
        updater.downloadAndInstall(info, (ok, message) -> {
            updateButton.setEnabled(true);
            logger.accept("OTA: " + message);
            Toast.makeText(context,
                    ok ? R.string.settings_cloud_update_ready
                            : R.string.settings_cloud_update_failed,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void uploadVdbus() {
        vdbusButton.setEnabled(false);
        new VdbusLabUploader(context, config, new CloudHttp()).upload((ok, message) -> {
            vdbusButton.setEnabled(true);
            Toast.makeText(context,
                    ok ? R.string.settings_cloud_vdbus_sent
                            : R.string.settings_cloud_vdbus_failed,
                    Toast.LENGTH_SHORT).show();
            logger.accept("VDBus snapshot: " + message);
        });
    }

    private void toggleRunning() {
        if (manager.isRunning()) {
            manager.stop();
            logger.accept("Bulut durduruldu");
        } else {
            manager.start();
            logger.accept("Bulut başlatıldı");
        }
        refresh();
    }

    private void refresh() {
        boolean paired = manager.isPaired();
        boolean running = manager.isRunning();
        unpairedRow.setVisibility(paired ? View.GONE : View.VISIBLE);
        pairedRow.setVisibility(paired ? View.VISIBLE : View.GONE);
        actionsPlaceholder.setVisibility(paired ? View.GONE : View.VISIBLE);

        if (manager.getState() == CloudManager.State.PAIRING) {
            status.setText(R.string.settings_cloud_pairing);
        } else if (manager.getState() == CloudManager.State.ERROR) {
            String detail = manager.getDetail();
            status.setText(detail == null || detail.isEmpty()
                    ? context.getString(R.string.settings_cloud_error)
                    : detail);
        } else if (running) {
            status.setText(R.string.settings_cloud_running);
        } else if (paired) {
            status.setText(R.string.settings_cloud_ready);
        } else {
            status.setText(R.string.settings_cloud_status_unpaired);
        }

        if (paired && running) {
            startStopButton.setText(R.string.settings_cloud_stop);
            UiStyles.applySolidRoundedBackground(
                    startStopButton, UiStyles.color(context, R.color.buttonDanger));
        } else {
            startStopButton.setText(R.string.settings_cloud_start);
            UiStyles.applySolidRoundedBackground(
                    startStopButton, UiStyles.color(context, R.color.buttonPrimary));
        }
        startStopButton.setEnabled(paired);
        shareLocationRow.getToggle().setEnabled(paired);
        if (shareLocationRow.getToggle().isChecked() != config.shareLocation()) {
            shareLocationRow.setCheckedSilently(config.shareLocation());
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
