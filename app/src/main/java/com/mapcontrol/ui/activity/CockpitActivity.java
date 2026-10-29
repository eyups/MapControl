package com.mapcontrol.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Stitch cockpit template. The four primary screens are rendered from the
 * exported Stitch HTML, while feature tabs that are not part of the template
 * are opened in the existing native screen.
 */
public final class CockpitActivity extends AppCompatActivity {

    public static final String EXTRA_PAGE = "com.mapcontrol.extra.COCKPIT_PAGE";
    private static final String ASSET_ROOT = "file:///android_asset/stitch/";
    private WebView webView;
    private String currentPage = "home";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0B0F14);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "Android");
        setContentView(webView);
        hideSystemBars();
        String initialPage = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_PAGE) : null;
        loadPage(initialPage == null ? "home" : initialPage);
    }

    private void loadPage(String page) {
        String normalized;
        switch (page == null ? "" : page) {
            case "comfort":
            case "driver":
            case "diagnostics":
                normalized = page;
                break;
            default:
                normalized = "home";
                break;
        }
        currentPage = normalized;
        webView.loadUrl(ASSET_ROOT + normalized + ".html");
    }

    private void openNative(String target) {
        int tab = -1;
        switch (target == null ? "" : target) {
            case "vehicle":
                tab = 9;
                break;
            case "camera":
                tab = 20;
                break;
            case "connectivity":
                tab = 0;
                break;
            case "cloud":
            case "settings":
                tab = 7;
                break;
            case "driving":
                tab = 14;
                break;
            case "driver":
                tab = 13;
                break;
            default:
                break;
        }
        if (tab < 0) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_OPEN_TAB, tab);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String page = intent != null ? intent.getStringExtra(EXTRA_PAGE) : null;
        if (page != null) {
            loadPage(page);
        }
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(currentPage)) {
            loadPage("home");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private final class Bridge {
        @JavascriptInterface
        public void navigate(String page) {
            runOnUiThread(() -> loadPage(page));
        }

        @JavascriptInterface
        public void openNative(String target) {
            runOnUiThread(() -> CockpitActivity.this.openNative(target));
        }
    }
}
