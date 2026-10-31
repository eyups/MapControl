package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.audio.AudioEqualizerController;
import com.mapcontrol.ui.cockpit.CockpitSegmented;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.theme.UiStyles;

/**
 * Ekolayzer sekmesi. Cam kart, parçalı anahtar ve kaydırıcılar diğer sekmelerle aynı dili kullanır.
 */
public class EqualizerTabBuilder {

    private static final String[] PRESET_IDS = {
            AudioEqualizerController.PRESET_FLAT,
            AudioEqualizerController.PRESET_BASS,
            AudioEqualizerController.PRESET_VOCAL,
            AudioEqualizerController.PRESET_TREBLE
    };

    private final Context context;
    private ScrollView scrollView;
    private boolean enabled;
    private int gainMb;
    private int minLevelMb;
    private int maxLevelMb;
    private int[] centerHz = new int[0];
    private int[] bandLevels = new int[0];
    private String preset = AudioEqualizerController.PRESET_FLAT;
    private boolean updatingSliders;
    private TextView gainValue;
    private SeekBar gainBar;
    private CockpitSegmented presetSegment;
    private TextView[] bandValues;
    private SeekBar[] bandBars;

    public EqualizerTabBuilder(Context context) {
        this.context = context;
        build();
    }

    public void rebuild() {
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public ScrollView build() {
        AudioEqualizerController.State state = AudioEqualizerController.read(context);
        enabled = state.enabled;
        gainMb = state.gainMb;
        minLevelMb = state.minLevelMb;
        maxLevelMb = state.maxLevelMb;
        centerHz = state.centerHz;
        bandLevels = state.bands;
        preset = state.preset;
        if (!AudioEqualizerController.PRESET_CUSTOM.equals(preset)
                && !bandsMatchPreset(preset)) {
            preset = AudioEqualizerController.PRESET_CUSTOM;
        }

        scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        if (!state.supported) {
            LinearLayout card = createCard();
            addSectionTitle(card, R.string.equalizer_title);
            addSectionDesc(card, R.string.equalizer_desc);
            addSectionDesc(card, R.string.equalizer_unsupported);
            outer.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            return scrollView;
        }

        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 1000;
        if (wide) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);
            LinearLayout left = createCard();
            LinearLayout right = createCard();
            int gutter = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMarginEnd(gutter / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(gutter / 2);
            columns.addView(left, leftLp);
            columns.addView(right, rightLp);

            addSettingsPanel(left);
            addCardTitle(right, R.string.equalizer_bands_title);
            addBandRows(right);

            outer.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout card = createCard();
            addSettingsPanel(card);
            addCardTitle(card, R.string.equalizer_bands_title);
            addBandRows(card);
            outer.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return scrollView;
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(inner, inner, inner, inner);
        com.mapcontrol.ui.cockpit.CockpitSurfaces.level1(card);
        return card;
    }

    private void addSettingsPanel(LinearLayout card) {
        addCardTitle(card, R.string.equalizer_settings_title);
        addSectionDesc(card, R.string.equalizer_desc);

        LinearLayout enableRow = new LinearLayout(context);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(16, 8, 16, 8);
        TextView enableLabel = new TextView(context);
        enableLabel.setText(R.string.equalizer_title);
        CockpitTokens.bodyMd(enableLabel);
        enableLabel.setTextColor(UiStyles.color(context, R.color.textPrimary));
        enableRow.addView(enableLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CockpitToggle toggle = new CockpitToggle(context);
        toggle.setChecked(enabled, false);
        toggle.setOnCheckedChangeListener((button, checked) -> onEnabledChanged(checked));
        enableRow.addView(toggle);
        card.addView(enableRow, matchWrap());

        TextView help = new TextView(context);
        CockpitTokens.bodySm(help);
        help.setText(context.getString(enabled ? R.string.equalizer_help_on : R.string.equalizer_help_off));
        help.setTextColor(UiStyles.color(context, R.color.textSecondary));
        help.setPadding(16, 0, 16, 8);
        card.addView(help, matchWrap());

        addSectionTitle(card, R.string.equalizer_gain_title);
        LinearLayout gainRow = new LinearLayout(context);
        gainRow.setOrientation(LinearLayout.HORIZONTAL);
        gainRow.setPadding(16, 0, 16, 0);
        gainRow.setGravity(Gravity.END);
        gainValue = new TextView(context);
        CockpitTokens.bodySm(gainValue);
        gainValue.setText(formatDb(gainMb, false));
        gainValue.setTextColor(UiStyles.color(context, R.color.ck_accent));
        gainRow.addView(gainValue);
        card.addView(gainRow, matchWrap());

        gainBar = new SeekBar(context);
        gainBar.setContentDescription(context.getString(R.string.equalizer_gain_title)
                + ", " + formatDb(gainMb, false));
        styleSeekBar(gainBar, AudioEqualizerController.MAX_GAIN_MB, gainMb,
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (!fromUser || updatingSliders) {
                            return;
                        }
                        gainMb = progress;
                        gainValue.setText(formatDb(gainMb, false));
                        AudioEqualizerController.preview(context, gainMb, bandLevels);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        commit(preset);
                    }
                });
        LinearLayout.LayoutParams gainBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiStyles.dimenPx(context, R.dimen.equalizer_slider_height));
        gainBarLp.setMargins(16, 0, 16, 4);
        card.addView(gainBar, gainBarLp);

        addSectionTitle(card, R.string.equalizer_presets_title);
        addPresetRow(card);
    }

    private void onEnabledChanged(boolean nowEnabled) {
        enabled = nowEnabled;
        commit(preset);
    }

    private void onPresetSelected(String presetId) {
        int[] curve = AudioEqualizerController.bandsForPreset(
                presetId, centerHz, minLevelMb, maxLevelMb);
        if (curve.length == bandLevels.length) {
            bandLevels = curve;
        }
        preset = presetId;
        pushSliders();
        stylePresetChips();
        commit(presetId);
    }

    private void commit(String presetId) {
        preset = presetId;
        AudioEqualizerController.update(context, enabled, gainMb, bandLevels, presetId);
        stylePresetChips();
    }

    private void addPresetRow(LinearLayout parent) {
        int[] labels = {
                R.string.equalizer_preset_flat,
                R.string.equalizer_preset_bass,
                R.string.equalizer_preset_vocal,
                R.string.equalizer_preset_treble
        };
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (int label : labels) {
            texts.add(context.getString(label));
        }
        int initial = 0;
        for (int i = 0; i < PRESET_IDS.length; i++) {
            if (PRESET_IDS[i].equals(preset)) {
                initial = i;
                break;
            }
        }
        presetSegment = new CockpitSegmented(context, texts, initial, index ->
                onPresetSelected(PRESET_IDS[index]));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        int h = CockpitTokens.dim(context, R.dimen.ck_space_md);
        lp.setMargins(h, 0, h, CockpitTokens.dim(context, R.dimen.ck_space_md));
        parent.addView(presetSegment, lp);
    }

    private void stylePresetChips() {
        if (presetSegment == null) {
            return;
        }
        if (AudioEqualizerController.PRESET_CUSTOM.equals(preset)) {
            presetSegment.clearSelection();
            return;
        }
        for (int i = 0; i < PRESET_IDS.length; i++) {
            if (PRESET_IDS[i].equals(preset)) {
                presetSegment.syncSelection(i);
                return;
            }
        }
    }

    private boolean bandsMatchPreset(String presetId) {
        int[] expected = AudioEqualizerController.bandsForPreset(
                presetId, centerHz, minLevelMb, maxLevelMb);
        if (expected.length != bandLevels.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != bandLevels[i]) {
                return false;
            }
        }
        return true;
    }

    private void addBandRows(LinearLayout parent) {
        bandValues = new TextView[bandLevels.length];
        bandBars = new SeekBar[bandLevels.length];
        int span = Math.max(1, maxLevelMb - minLevelMb);
        for (int i = 0; i < bandLevels.length; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
            row.setPadding(16, 4, 16, 4);

            TextView hz = new TextView(context);
            hz.setText(formatHz(i < centerHz.length ? centerHz[i] : 0));
            CockpitTokens.bodySm(hz);
            hz.setTextColor(UiStyles.color(context, R.color.textPrimary));
            hz.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_space_xl) * 2);
            row.addView(hz, new LinearLayout.LayoutParams(
                    CockpitTokens.dim(context, R.dimen.ck_space_xl) * 3,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView value = new TextView(context);
            CockpitTokens.bodySm(value);
            value.setText(formatDb(bandLevels[i], true));
            value.setTextColor(UiStyles.color(context, R.color.ck_accent));
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(
                    CockpitTokens.dim(context, R.dimen.ck_space_xl) * 2,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            bandValues[i] = value;

            int progress = bandLevels[i] - minLevelMb;
            SeekBar bar = new SeekBar(context);
            styleSeekBar(bar, span, progress, new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                    if (!fromUser || updatingSliders) {
                        return;
                    }
                    bandLevels[index] = minLevelMb + value;
                    bandValues[index].setText(formatDb(bandLevels[index], true));
                    if (!AudioEqualizerController.PRESET_CUSTOM.equals(preset)) {
                        preset = AudioEqualizerController.PRESET_CUSTOM;
                        stylePresetChips();
                    }
                    AudioEqualizerController.preview(context, gainMb, bandLevels);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    commit(preset);
                }
            });
            row.addView(bar, new LinearLayout.LayoutParams(
                    0, UiStyles.dimenPx(context, R.dimen.equalizer_slider_height), 1f));
            bar.setContentDescription(hz.getText() + ", " + value.getText());
            bandBars[i] = bar;
            parent.addView(row, matchWrap());
        }
    }

    private void pushSliders() {
        updatingSliders = true;
        if (gainBar != null) {
            gainBar.setProgress(gainMb);
        }
        if (gainValue != null) {
            gainValue.setText(formatDb(gainMb, false));
        }
        if (bandBars != null) {
            for (int i = 0; i < bandBars.length && i < bandLevels.length; i++) {
                bandBars[i].setProgress(bandLevels[i] - minLevelMb);
                if (bandValues != null && i < bandValues.length) {
                    bandValues[i].setText(formatDb(bandLevels[i], true));
                }
            }
        }
        updatingSliders = false;
    }

    private void styleSeekBar(SeekBar bar, int max, int progress,
            SeekBar.OnSeekBarChangeListener listener) {
        bar.setMax(Math.max(1, max));
        bar.setProgress(Math.max(0, Math.min(progress, bar.getMax())));
        int accent = CockpitTokens.color(context, R.color.ck_accent);
        int thumbPx = UiStyles.dimenPx(context, R.dimen.equalizer_thumb_size);
        int trackPx = UiStyles.dimenPx(context, R.dimen.equalizer_track_height);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(accent);
        thumb.setSize(thumbPx, thumbPx);
        bar.setThumb(thumb);
        bar.setThumbOffset(thumbPx / 2);
        bar.setSplitTrack(false);
        bar.setMinHeight(trackPx);
        bar.setMaxHeight(trackPx);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_stroke)));
        bar.setThumbTintList(ColorStateList.valueOf(accent));
        int side = thumbPx / 2;
        bar.setPadding(side, 0, side, 0);
        bar.setOnSeekBarChangeListener(listener);
    }

    private TextView addValueLabel(LinearLayout parent, String text) {
        TextView value = new TextView(context);
        value.setText(text);
        CockpitTokens.bodySm(value);
        value.setTextColor(UiStyles.color(context, R.color.textSecondary));
        int h = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        value.setPadding(h, 0, h, 0);
        parent.addView(value, matchWrap());
        return value;
    }

    private void addCardTitle(LinearLayout parent, int textRes) {
        TextView title = new TextView(context);
        title.setText(textRes);
        CockpitTokens.title(title);
        title.setPadding(16, 24, 16, 4);
        parent.addView(title, matchWrap());
    }

    private void addSectionTitle(LinearLayout parent, int textRes) {
        TextView title = new TextView(context);
        title.setText(textRes);
        CockpitTokens.labelCaps(title);
        title.setPadding(16, 24, 16, 8);
        parent.addView(title, matchWrap());
    }

    private void addSectionDesc(LinearLayout parent, int textRes) {
        TextView desc = new TextView(context);
        desc.setText(textRes);
        CockpitTokens.bodySm(desc);
        desc.setTextColor(UiStyles.color(context, R.color.textSecondary));
        desc.setPadding(16, 0, 16, 12);
        parent.addView(desc, matchWrap());
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private String formatHz(int hz) {
        if (hz >= 1000) {
            if (hz % 1000 == 0) {
                return context.getString(R.string.equalizer_band_khz_whole, hz / 1000);
            }
            return context.getString(R.string.equalizer_band_khz, hz / 1000f);
        }
        return context.getString(R.string.equalizer_band_hz, hz);
    }

    private String formatDb(int milliBel, boolean signed) {
        float db = milliBel / 100f;
        if (signed) {
            return context.getString(R.string.equalizer_level_db, db);
        }
        return context.getString(R.string.equalizer_gain_db, db);
    }
}
