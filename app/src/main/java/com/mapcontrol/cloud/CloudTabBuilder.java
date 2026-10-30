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
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggleRow;
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
    private final TextView statusChip;
    private final TextView identityLine;
    private final LinearLayout unpairedRow;
    private final EditText codeInput;
    private final Button pairButton;
    private final LinearLayout pairedRow;
    private final TextView actionsPlaceholder;
    private final Button startStopButton;
    private final CockpitToggleRow shareLocationRow;
    private final CockpitGrid actionsGrid;
    private final LinearLayout logUploadButton;
    private final LinearLayout vdbusButton;
    private final LinearLayout updateButton;
    private final LinearLayout backupButton;
    private final LinearLayout restoreButton;

    private final CloudManager.Listener managerListener = this::refresh;

    public CloudTabBuilder(Context context, Consumer<String> logger) {
        this.context = context;
        this.logger = logger;
        this.config = new CloudConfig(context);
        this.manager = CloudManager.get(context);
        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        root.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);

        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 900;
        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
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

        LinearLayout pairHeader = new LinearLayout(context);
        pairHeader.setOrientation(LinearLayout.HORIZONTAL);
        pairHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView pairTitle = new TextView(context);
        pairTitle.setText(R.string.ck_roadmate_paired_title);
        CockpitTokens.labelCaps(pairTitle);
        pairHeader.addView(pairTitle, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusChip = new TextView(context);
        statusChip.setPadding(dp(8), dp(4), dp(8), dp(4));
        pairHeader.addView(statusChip);
        pairCard.addView(pairHeader, matchWidth());

        TextView pairDesc = new TextView(context);
        pairDesc.setText(R.string.settings_cloud_desc);
        CockpitTokens.bodySm(pairDesc);
        pairDesc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams pairDescLp = matchWidth();
        pairDescLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        pairCard.addView(pairDesc, pairDescLp);

        status = new TextView(context);
        CockpitTokens.bodySm(status);
        status.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams statusLp = matchWidth();
        statusLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        pairCard.addView(status, statusLp);

        unpairedRow = new LinearLayout(context);
        unpairedRow.setOrientation(LinearLayout.HORIZONTAL);
        unpairedRow.setGravity(Gravity.CENTER_VERTICAL);
        unpairedRow.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0, 0);
        codeInput = new EditText(context);
        codeInput.setHint(R.string.settings_cloud_pair_hint);
        codeInput.setSingleLine(true);
        codeInput.setTextSize(16);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        codeInput.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        codeInput.setHintTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        unpairedRow.addView(codeInput, codeLp);

        pairButton = new Button(context);
        pairButton.setText(R.string.settings_cloud_pair_action);
        pairButton.setAllCaps(false);
        pairButton.setTextSize(15);
        pairButton.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        UiStyles.styleOemButton(pairButton, CockpitTokens.color(context, R.color.ck_accent));
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pairLp.setMarginStart(dp(8));
        unpairedRow.addView(pairButton, pairLp);
        pairCard.addView(unpairedRow, matchWidth());

        pairedRow = new LinearLayout(context);
        pairedRow.setOrientation(LinearLayout.VERTICAL);
        pairedRow.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0, 0);

        startStopButton = new Button(context);
        startStopButton.setAllCaps(false);
        startStopButton.setTextSize(18);
        startStopButton.setTypeface(CockpitTokens.inter(context,
                CockpitTokens.WEIGHT_SEMIBOLD));
        UiStyles.styleOemButton(startStopButton, CockpitTokens.color(context, R.color.ck_accent));
        startStopButton.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        pairedRow.addView(startStopButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, CockpitTokens.dim(context,
                R.dimen.ck_touch_primary)));

        shareLocationRow = new CockpitToggleRow(context, R.drawable.ic_mdi_map,
                context.getString(R.string.settings_cloud_share_location), null,
                config.shareLocation());
        LinearLayout.LayoutParams shareLp = matchWidth();
        shareLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        pairedRow.addView(shareLocationRow, shareLp);
        pairCard.addView(pairedRow, matchWidth());

        identityLine = new TextView(context);
        CockpitTokens.mono(identityLine);
        identityLine.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        identityLine.setSingleLine(true);
        identityLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams identityLp = matchWidth();
        identityLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        pairCard.addView(identityLine, identityLp);

        LinearLayout encryption = new LinearLayout(context);
        encryption.setOrientation(LinearLayout.HORIZONTAL);
        encryption.setGravity(Gravity.CENTER_VERTICAL);
        encryption.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0, 0);
        ImageView lock = new ImageView(context);
        lock.setImageResource(R.drawable.ic_mdi_shield_car);
        lock.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        encryption.addView(lock, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout encText = new LinearLayout(context);
        encText.setOrientation(LinearLayout.VERTICAL);
        encText.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_sm), 0, 0, 0);
        TextView encTitle = new TextView(context);
        encTitle.setText(R.string.ck_roadmate_encryption);
        CockpitTokens.bodySm(encTitle);
        encTitle.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        encText.addView(encTitle);
        TextView encSub = new TextView(context);
        encSub.setText(R.string.ck_roadmate_encryption_sub);
        CockpitTokens.bodySm(encSub);
        encSub.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        encText.addView(encSub);
        encryption.addView(encText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        pairCard.addView(encryption, matchWidth());

        TextView actionsTitle = new TextView(context);
        actionsTitle.setText(R.string.ck_roadmate_actions);
        CockpitTokens.labelCaps(actionsTitle);
        actionsCard.addView(actionsTitle, matchWidth());
        TextView actionsSub = new TextView(context);
        actionsSub.setText(R.string.ck_roadmate_actions_sub);
        CockpitTokens.bodySm(actionsSub);
        actionsSub.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams actionsSubLp = matchWidth();
        actionsSubLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        actionsSubLp.bottomMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        actionsCard.addView(actionsSub, actionsSubLp);

        actionsGrid = new CockpitGrid(context);
        actionsCard.addView(actionsGrid, matchWidth());

        logUploadButton = actionCard(R.drawable.ic_mdi_inbox_outline,
                R.string.settings_cloud_send_log, R.string.ck_roadmate_log_sub);
        actionsGrid.add(logUploadButton, 4);
        vdbusButton = actionCard(R.drawable.ic_mdi_car,
                R.string.settings_cloud_vdbus_snapshot, R.string.ck_roadmate_vdbus_sub);
        actionsGrid.add(vdbusButton, 4);
        updateButton = actionCard(R.drawable.ic_mdi_refresh,
                R.string.settings_cloud_check_update, R.string.ck_roadmate_update_sub);
        actionsGrid.add(updateButton, 4);
        backupButton = actionCard(R.drawable.ic_mdi_inbox_outline,
                R.string.settings_cloud_backup, R.string.ck_roadmate_backup_sub);
        actionsGrid.add(backupButton, 4);
        restoreButton = actionCard(R.drawable.ic_mdi_restart,
                R.string.settings_cloud_restore, R.string.ck_roadmate_restore_sub);
        actionsGrid.add(restoreButton, 8);

        actionsPlaceholder = new TextView(context);
        actionsPlaceholder.setText(R.string.ck_roadmate_unpaired_hint);
        CockpitTokens.bodySm(actionsPlaceholder);
        actionsPlaceholder.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        actionsPlaceholder.setVisibility(View.GONE);
        actionsCard.addView(actionsPlaceholder, matchWidth());

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0, 0);
        View dot = new View(context);
        dot.setBackground(CockpitSurfaces.rounded(context,
                CockpitTokens.color(context, R.color.ck_nominal), 6f));
        footer.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView footerText = new TextView(context);
        footerText.setText(R.string.ck_roadmate_vdbus_footer);
        CockpitTokens.mono(footerText);
        footerText.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams footerTextLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        footerTextLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        footer.addView(footerText, footerTextLp);
        TextView baud = new TextView(context);
        baud.setText(R.string.ck_roadmate_baud);
        CockpitTokens.mono(baud);
        baud.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        footer.addView(baud);
        actionsCard.addView(footer, matchWidth());

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f);
        if (wide) {
            leftLp.setMarginEnd(CockpitTokens.dim(context, R.dimen.ck_gutter) / 2);
            rightLp.setMarginStart(CockpitTokens.dim(context, R.dimen.ck_gutter) / 2);
        } else {
            rightLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        }
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

    private LinearLayout actionCard(int iconRes, int titleRes, int subtitleRes) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.setPadding(pad, pad, pad, pad);
        card.setMinimumHeight(CockpitTokens.dp(context, 88));
        CockpitSurfaces.level2(card);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        card.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textsLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addView(texts, textsLp);
        TextView title = new TextView(context);
        title.setText(titleRes);
        CockpitTokens.bodyLg(title);
        texts.addView(title);
        TextView sub = new TextView(context);
        sub.setText(subtitleRes);
        CockpitTokens.bodySm(sub);
        sub.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        sub.setMaxLines(2);
        texts.addView(sub);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_mdi_chevron_up);
        chevron.setRotation(90f);
        chevron.setColorFilter(CockpitTokens.color(context, R.color.ck_text_medium));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return card;
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
        actionsGrid.setVisibility(paired ? View.VISIBLE : View.GONE);

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
        if (statusChip != null) {
            int color = running
                    ? CockpitTokens.color(context, R.color.ck_accent)
                    : paired ? CockpitTokens.color(context, R.color.ck_nominal)
                    : CockpitTokens.color(context, R.color.ck_text_medium);
            statusChip.setText(running
                    ? R.string.ck_roadmate_status_active
                    : paired ? R.string.ck_roadmate_status_ready
                    : R.string.ck_roadmate_status_passive);
            statusChip.setTextColor(color);
            statusChip.setBackground(CockpitSurfaces.outlined(context,
                    CockpitTokens.color(context, R.color.ck_surface_2),
                    color, 12f));
        }
        if (identityLine != null) {
            String id = config.deviceId();
            identityLine.setText(context.getString(R.string.ck_roadmate_identity,
                    id == null || id.isEmpty() ? "—" : id));
        }
        UiStyles.setButtonStartIconTinted(startStopButton,
                running ? R.drawable.ic_mdi_pause : R.drawable.ic_mdi_play,
                CockpitTokens.color(context, R.color.ck_on_accent), dp(8));
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
