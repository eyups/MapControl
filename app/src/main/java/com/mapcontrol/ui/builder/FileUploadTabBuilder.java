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
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        fileUploadTabContent.setPadding(margin, margin, margin, margin);

        if (context.getResources().getConfiguration().screenWidthDp >= 1000) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);

            LinearLayout left = createCard();
            addControlPanel(left);
            LinearLayout right = createCard();
            addStatusPanel(right);

            int gutter = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMarginEnd(gutter / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(gutter / 2);
            columns.addView(left, leftLp);
            columns.addView(right, rightLp);
            fileUploadTabContent.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout card = createCard();
            addControlPanel(card);
            addStatusPanel(card);
            fileUploadTabContent.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        scrollView.addView(fileUploadTabContent, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        return scrollView;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = UiStyles.dimenPx(context, R.dimen.oem_card_inner_padding);
        card.setPadding(inner, inner, inner, inner);
        UiStyles.setGlassCardBackground(card);
        return card;
    }

    private void addControlPanel(LinearLayout card) {
        TextView fileUploadTitle = new TextView(context);
        fileUploadTitle.setText(R.string.web_mgmt_title);
        fileUploadTitle.setTextSize(18);
        fileUploadTitle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        fileUploadTitle.setTypeface(null, Typeface.BOLD);
        fileUploadTitle.setPadding(16, 8, 16, 8);
        card.addView(fileUploadTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView fileUploadDesc = new TextView(context);
        fileUploadDesc.setText(R.string.web_mgmt_desc);
        fileUploadDesc.setTextSize(13);
        fileUploadDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        fileUploadDesc.setPadding(16, 0, 16, 16);
        card.addView(fileUploadDesc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        btnWebServerToggle = new Button(context);
        btnWebServerToggle.setText(R.string.web_server_start);
        btnWebServerToggle.setTextColor(UiStyles.color(context, R.color.textPrimary));
        btnWebServerToggle.setTextSize(16);
        btnWebServerToggle.setTypeface(null, Typeface.BOLD);
        UiStyles.styleOemButton(btnWebServerToggle, UiStyles.color(context, R.color.buttonPrimary));
        btnWebServerToggle.setPadding(16, 20, 16, 20);
        LinearLayout.LayoutParams webServerToggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        webServerToggleParams.setMargins(0, 0, 0, UiStyles.dimenPx(context, R.dimen.spacing_medium));
        card.addView(btnWebServerToggle, webServerToggleParams);

        btnWebServerToggle.setOnClickListener(v -> {
            if (webServerManager.isRunning()) {
                webServerManager.stopServer();
            } else {
                webServerManager.startServer();
            }
        });
    }

    private void addStatusPanel(LinearLayout card) {
        LinearLayout urlQrContainer = new LinearLayout(context);
        urlQrContainer.setOrientation(LinearLayout.VERTICAL);
        urlQrContainer.setGravity(Gravity.CENTER);
        urlQrContainer.setPadding(16, 16, 16, 16);

        webServerStatusText = new TextView(context);
        webServerStatusText.setText(R.string.web_server_stopped);
        webServerStatusText.setTextSize(20);
        webServerStatusText.setTextColor(UiStyles.color(context, R.color.textHint));
        webServerStatusText.setGravity(Gravity.CENTER);
        webServerStatusText.setPadding(16, 16, 16, 16);
        webServerStatusText.setTypeface(null, Typeface.BOLD);
        urlQrContainer.addView(webServerStatusText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        qrCodeImageView = new ImageView(context);
        qrCodeImageView.setVisibility(View.GONE);
        qrCodeImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        qrCodeImageView.setPadding(16, 16, 16, 16);
        int qrSize = (int) (200 * context.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER;
        urlQrContainer.addView(qrCodeImageView, qrParams);

        card.addView(urlQrContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
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
