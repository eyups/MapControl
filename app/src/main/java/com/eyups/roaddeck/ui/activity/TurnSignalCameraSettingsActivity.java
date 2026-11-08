package com.eyups.roaddeck.ui.activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.builder.TurnSignalCameraSettingsSection;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;
import com.eyups.roaddeck.ui.theme.UiStyles;

/** Kamera 360 > Kadran > Sinyal Kamerası ayarları. */
public class TurnSignalCameraSettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyles.color(this, R.color.ck_bg_base));
        int pad = dp(24);
        root.setPadding(pad, dp(12), pad, pad);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(8), 0, dp(16), 0);
        back.setTextColor(UiStyles.color(this, R.color.textPrimary));
        back.setContentDescription(getString(R.string.settings_turn_signal_camera_title));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView title = new TextView(this);
        title.setText(R.string.settings_turn_signal_camera_title);
        title.setTextSize(20);
        title.setTypeface(CockpitTokens.inter(this, CockpitTokens.WEIGHT_SEMIBOLD));
        title.setTextColor(UiStyles.color(this, R.color.textPrimary));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = dp(16);
        card.setPadding(inner, inner, inner, inner);
        CockpitSurfaces.level1(card);
        SharedPreferences prefs = getSharedPreferences("RoadDeckPrefs", MODE_PRIVATE);
        TurnSignalCameraSettingsSection.attach(this, card, prefs,
                message -> Log.i("TurnSignalSettings", message));
        scroll.addView(card, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollP.topMargin = dp(8);
        root.addView(scroll, scrollP);

        setContentView(root);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
