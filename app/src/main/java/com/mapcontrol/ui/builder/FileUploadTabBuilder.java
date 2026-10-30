package com.mapcontrol.ui.builder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mapcontrol.R;
import com.mapcontrol.manager.WebServerManager;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;

public class FileUploadTabBuilder {
    private final Context context;
    private final WebServerManager webServerManager;

    private ScrollView scrollView;
    private LinearLayout fileUploadTabContent;
    private Button btnWebServerToggle;
    private TextView webServerStatusText;
    private ImageView qrCodeImageView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public FileUploadTabBuilder(Context context, WebServerManager webServerManager) {
        this.context = context;
        this.webServerManager = webServerManager;
        build();
    }

    public ScrollView build() {
        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setPadding(0, 0, 0, 0);
        scrollView.setFillViewport(true);

        fileUploadTabContent = new LinearLayout(context);
        fileUploadTabContent.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        fileUploadTabContent.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_md),
                margin, margin);

        CockpitGrid grid = new CockpitGrid(context);
        fileUploadTabContent.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        CockpitCard control = new CockpitCard(context, context.getString(R.string.web_mgmt_title)).span(4);
        TextView desc = new TextView(context);
        CockpitTokens.bodySm(desc);
        desc.setText(R.string.web_mgmt_desc);
        desc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        control.addContent(desc);

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText(R.string.web_server_start);
        btnWebServerToggle.setAllCaps(false);
        btnWebServerToggle.setTextSize(16);
        btnWebServerToggle.setTypeface(CockpitTokens.inter(context,
                CockpitTokens.WEIGHT_SEMIBOLD));
        btnWebServerToggle.setTextColor(CockpitTokens.color(context, R.color.ck_on_accent));
        UiStyles.styleOemButton(btnWebServerToggle,
                CockpitTokens.color(context, R.color.ck_accent));
        btnWebServerToggle.setPadding(16, 16, 16, 16);
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        control.addContent(btnWebServerToggle, toggleLp);
        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });

        webServerStatusText = new TextView(context);
        CockpitTokens.mono(webServerStatusText);
        webServerStatusText.setText(R.string.web_server_stopped);
        webServerStatusText.setGravity(Gravity.CENTER);
        webServerStatusText.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_sm), 0,
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        control.addContent(webServerStatusText);
        grid.add(control, 4);

        CockpitCard qrCard = new CockpitCard(context, context.getString(R.string.web_qr_title)).span(4);
        qrCodeImageView = new ImageView(context);
        qrCodeImageView.setVisibility(View.GONE);
        qrCodeImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int qrSize = CockpitTokens.dp(context, 240);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrCard.addContent(qrCodeImageView, qrParams);
        grid.add(qrCard, 4);

        CockpitCard hint = new CockpitCard(context, "Talimatlar").span(8);
        TextView hintBody = new TextView(context);
        CockpitTokens.bodySm(hintBody);
        hintBody.setText(R.string.ck_web_hint);
        hintBody.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        hint.addContent(hintBody);
        grid.add(hint, 8);

        scrollView.addView(fileUploadTabContent, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return scrollView;
    }

    public LinearLayout getFileUploadTabContent() {
        return fileUploadTabContent;
    }

    public ScrollView getScrollView() {
        if (scrollView == null) {
            return build();
        }
        return scrollView;
    }

    public Button getToggleButton() {
        return btnWebServerToggle;
    }

    public TextView getStatusText() {
        return webServerStatusText;
    }

    public ImageView getQrImageView() {
        return qrCodeImageView;
    }

    public void generateQRCode(String url) {
        new Thread(() -> {
            try {
                QRCodeWriter qrCodeWriter = new QRCodeWriter();
                java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
                hints.put(EncodeHintType.MARGIN, 1);

                int size = 512;
                BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, size, size, hints);

                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
                for (int x = 0; x < size; x++) {
                    for (int y = 0; y < size; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }

                handler.post(() -> {
                    if (qrCodeImageView != null) {
                        qrCodeImageView.setImageBitmap(bitmap);
                        qrCodeImageView.setVisibility(View.VISIBLE);
                    }
                });
            } catch (WriterException ignored) {
            }
        }).start();
    }
}
