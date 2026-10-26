package com.mapcontrol.cloud;

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
import android.widget.Switch;
import android.widget.TextView;

import com.mapcontrol.R;
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
    private final Button startStopButton;
    private final Switch shareLocationSwitch;

    private final CloudManager.Listener managerListener = this::refresh;

    public CloudTabBuilder(Context context, Consumer<String> logger) {
        this.context = context;
        this.logger = logger;
        this.config = new CloudConfig(context);
        this.manager = CloudManager.get(context);
        this.root = new LinearLayout(context);
        this.root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(R.string.settings_cloud_title);
        title.setTextSize(18);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(24), dp(16), dp(8));
        root.addView(title, matchWidth());

        TextView description = new TextView(context);
        description.setText(R.string.settings_cloud_desc);
        description.setTextSize(13);
        description.setTextColor(UiStyles.color(context, R.color.textHint));
        description.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(description, matchWidth());

        status = new TextView(context);
        status.setTextSize(13);
        status.setPadding(dp(16), 0, dp(16), dp(10));
        root.addView(status, matchWidth());

        unpairedRow = new LinearLayout(context);
        unpairedRow.setOrientation(LinearLayout.HORIZONTAL);
        unpairedRow.setGravity(Gravity.CENTER_VERTICAL);
        unpairedRow.setPadding(dp(16), 0, dp(16), dp(10));
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
        root.addView(unpairedRow, matchWidth());

        pairedRow = new LinearLayout(context);
        pairedRow.setOrientation(LinearLayout.VERTICAL);
        pairedRow.setPadding(dp(16), 0, dp(16), dp(8));

        startStopButton = new Button(context);
        startStopButton.setAllCaps(false);
        startStopButton.setTextColor(UiStyles.color(context, R.color.surfaceCard));
        UiStyles.applySolidRoundedBackground(
                startStopButton, UiStyles.color(context, R.color.buttonPrimary));
        pairedRow.addView(startStopButton, matchWidth());

        shareLocationSwitch = new Switch(context);
        shareLocationSwitch.setText(R.string.settings_cloud_share_location);
        shareLocationSwitch.setTextSize(14);
        shareLocationSwitch.setTextColor(UiStyles.color(context, R.color.textPrimary));
        shareLocationSwitch.setPadding(0, dp(8), 0, 0);
        pairedRow.addView(shareLocationSwitch, matchWidth());
        root.addView(pairedRow, matchWidth());

        pairButton.setOnClickListener(view -> pair());
        startStopButton.setOnClickListener(view -> toggleRunning());
        shareLocationSwitch.setChecked(config.shareLocation());
        shareLocationSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
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
        shareLocationSwitch.setEnabled(paired);
        if (shareLocationSwitch.isChecked() != config.shareLocation()) {
            shareLocationSwitch.setChecked(config.shareLocation());
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
