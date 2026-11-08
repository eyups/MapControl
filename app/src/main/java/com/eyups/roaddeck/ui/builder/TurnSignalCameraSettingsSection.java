package com.eyups.roaddeck.ui.builder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.manager.TurnSignalCameraController;
import com.eyups.roaddeck.ui.theme.UiStyles;

/** Sinyal kamerası ayarlarının tek kaynağı; Ayarlar'dan Kamera 360 > Sinyal Kamerası'na taşındı. */
public final class TurnSignalCameraSettingsSection {

    public interface LogSink {
        void log(String message);
    }

    private TurnSignalCameraSettingsSection() {
    }

    public static void attach(Context context, LinearLayout parentContainer,
            SharedPreferences prefs, LogSink callback) {
        TextView desc = new TextView(context);
        desc.setText(R.string.settings_turn_signal_camera_desc);
        desc.setTextSize(13);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(16, 8, 16, 12);
        parentContainer.addView(desc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean enabled = prefs.getBoolean(TurnSignalCameraController.PREF_ENABLED, false);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                null,
                context.getString(R.string.common_on), context.getString(R.string.common_off),
                context.getString(R.string.settings_turn_signal_camera_help_on),
                context.getString(R.string.settings_turn_signal_camera_help_off),
                enabled, on -> {
                    prefs.edit().putBoolean(TurnSignalCameraController.PREF_ENABLED, on).apply();
                    callback.log("Sinyal kamerası: " + (on ? "Açık" : "Kapalı"));
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && !Settings.canDrawOverlays(context)) {
                        try {
                            context.startActivity(new Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + context.getPackageName())));
                        } catch (Exception ignored) {
                        }
                    }
                });

        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_turn_signal_camera_design),
                context.getString(R.string.turn_signal_camera_style_iso),
                context.getString(R.string.turn_signal_camera_style_live),
                "", "",
                TurnSignalCameraController.STYLE_ISO.equals(
                        TurnSignalCameraController.getStyle(context)),
                leftSelected -> {
                    TurnSignalCameraController.setStyle(context,
                            leftSelected ? TurnSignalCameraController.STYLE_ISO
                                    : TurnSignalCameraController.STYLE_LIVE);
                    callback.log("Sinyal kamerası tasarımı: "
                            + (leftSelected ? "ISO Kadran" : "Canlı Kamera"));
                });

        boolean navFront = prefs.getBoolean(TurnSignalCameraController.PREF_NAV_FRONT, false);
        UiStyles.addBinarySegmentedControl(context, parentContainer,
                context.getString(R.string.settings_turn_signal_camera_nav_front),
                context.getString(R.string.common_yes), context.getString(R.string.common_no),
                context.getString(R.string.settings_turn_signal_camera_nav_front_help_on),
                context.getString(R.string.settings_turn_signal_camera_nav_front_help_off),
                navFront, on -> {
                    prefs.edit().putBoolean(TurnSignalCameraController.PREF_NAV_FRONT, on).apply();
                    callback.log("Harita kartları kameranın önünde: " + (on ? "Evet" : "Hayır"));
                });
        addSignalCameraSlider(context, parentContainer, prefs, callback,
                R.string.settings_turn_signal_camera_nav_delay,
                TurnSignalCameraController.PREF_NAV_DELAY_SEC,
                TurnSignalCameraController.NAV_DELAY_MIN,
                TurnSignalCameraController.NAV_DELAY_MAX,
                TurnSignalCameraController.NAV_DELAY_DEFAULT,
                "Harita öne geçme gecikmesi: ",
                R.string.settings_turn_signal_camera_nav_delay_value);

        addSignalCameraSlider(context, parentContainer, prefs, callback,
                R.string.settings_turn_signal_camera_opacity,
                TurnSignalCameraController.PREF_OPACITY,
                TurnSignalCameraController.OPACITY_MIN,
                TurnSignalCameraController.OPACITY_MAX,
                TurnSignalCameraController.OPACITY_MAX,
                "Sinyal kamerası opaklığı: %",
                R.string.projection_cluster_offset_scale_value);
        addSignalCameraSlider(context, parentContainer, prefs, callback,
                R.string.settings_turn_signal_camera_size,
                TurnSignalCameraController.PREF_SIZE,
                TurnSignalCameraController.SIZE_MIN,
                TurnSignalCameraController.SIZE_MAX,
                100,
                "Sinyal kamerası boyutu: %",
                R.string.projection_cluster_offset_scale_value);
    }

    private static void addSignalCameraSlider(Context context, LinearLayout parent,
            SharedPreferences prefs, LogSink callback, int titleRes, String prefKey,
            int min, int max, int def, String logPrefix, int valueFormatRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(16, 10, 16, 0);
        TextView label = new TextView(context);
        label.setText(titleRes);
        label.setTextSize(14);
        label.setTextColor(UiStyles.color(context, R.color.textPrimary));
        row.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(context);
        value.setTextSize(14);
        value.setGravity(Gravity.END);
        value.setTextColor(UiStyles.color(context, R.color.accentHighlight));
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SeekBar bar = new SeekBar(context);
        bar.setMax(max - min);
        int current = Math.max(min, Math.min(max, prefs.getInt(prefKey, def)));
        bar.setProgress(current - min);
        bar.setPadding(16, 0, 16, 0);
        value.setText(context.getString(valueFormatRes, current));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + progress;
                value.setText(context.getString(valueFormatRes, v));
                if (fromUser) {
                    prefs.edit().putInt(prefKey, v).apply();
                    callback.log(logPrefix + v);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
