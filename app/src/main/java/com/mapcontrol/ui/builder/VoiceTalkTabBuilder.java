package com.mapcontrol.ui.builder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mapcontrol.R;
import com.mapcontrol.tts.MapControlSpeech;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitSegmented;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Media > Sesli Konuşma: TTS motoru, ses profili ve konuşma ince ayarları. */
public class VoiceTalkTabBuilder {

    private static final String[] VOICE_NAMES = {
            "Seyfullah", "Hasan Yasin", "Adam", "Doga", "İrem", "Lisa"
    };
    private static final String[] VOICE_IDS = {
            "mF7tIc9VLrznhGooGjaT", "tILIOdKERNNbBHVEXk7I", "J17lijyP1BHYcM7ld0Rg",
            "IuRRIAcbQK5AQk1XevPj", "uvU9jrgGLWNPeNA4NgNT", "LYfSi2g3Frvxg50fRl91"
    };
    private static final String[] VOICE_META = {
            "Erkek · Doğal", "Erkek · Tok", "Erkek · Global",
            "Kadın · Yumuşak", "Kadın · Dinamik", "Kadın · Net"
    };

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<VoiceCard> voiceCards = new ArrayList<>();

    private ScrollView scrollView;
    private EditText keyField;
    private TextView keyStatus;
    private TextView balanceText;
    private TextView activeVoice;
    private EditText customVoice;
    private CockpitSegmented engineSegmented;
    private SeekBar speedBar;
    private SeekBar stabilityBar;
    private SeekBar clarityBar;
    private TextView speedValue;
    private TextView stabilityValue;
    private TextView clarityValue;
    private int selectedVoiceIndex;

    private static final class VoiceCard {
        final int index;
        final LinearLayout view;
        final TextView radio;

        VoiceCard(int index, LinearLayout view, TextView radio) {
            this.index = index;
            this.view = view;
            this.radio = radio;
        }
    }

    public VoiceTalkTabBuilder(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(MapControlSpeech.PREFS, Context.MODE_PRIVATE);
        build();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void rebuild() {
        build();
    }

    private void build() {
        voiceCards.clear();
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm), margin, margin);
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 1000;
        LinearLayout engineCard = card();
        LinearLayout voiceCard = card();
        if (wide) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);
            int gap = CockpitTokens.dim(context, R.dimen.ck_space_md);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            leftLp.setMarginEnd(gap / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMarginStart(gap / 2);
            columns.addView(engineCard, leftLp);
            columns.addView(voiceCard, rightLp);
            outer.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            outer.addView(engineCard, matchWrap());
            LinearLayout.LayoutParams gap = matchWrap();
            gap.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
            outer.addView(voiceCard, gap);
        }

        buildEngineCard(engineCard, voiceCard);
    }

    private void buildEngineCard(LinearLayout card, LinearLayout rightCard) {
        addTitle(card, R.string.settings_tts_section_title);
        addBody(card, R.string.settings_tts_section_desc, true);

        addLabel(card, R.string.settings_tts_engine_title);
        String engine = MapControlSpeech.engine(context);
        int initialEngine = MapControlSpeech.ENGINE_IFLYTEK.equals(engine)
                ? 2 : (MapControlSpeech.ENGINE_ELEVENLABS.equals(engine) ? 1 : 0);
        List<String> engines = new ArrayList<>();
        engines.add(context.getString(R.string.settings_tts_sherpa));
        engines.add(context.getString(R.string.settings_tts_elevenlabs));
        engines.add(context.getString(R.string.settings_tts_iflytek));
        engineSegmented = new CockpitSegmented(context, engines, initialEngine, index -> {
            String selected = index == 2 ? MapControlSpeech.ENGINE_IFLYTEK
                    : (index == 1 ? MapControlSpeech.ENGINE_ELEVENLABS : MapControlSpeech.ENGINE_SHERPA);
            prefs.edit().putString(MapControlSpeech.KEY_ENGINE, selected)
                    .putBoolean(MapControlSpeech.KEY_ENGINE_CONFIGURED, true).apply();
            addEngineHelp(card, index);
            rebuildRightPanel(rightCard, index);
        });
        card.addView(engineSegmented, matchWrap());
        addEngineHelp(card, initialEngine);
        rebuildRightPanel(rightCard, initialEngine);
    }

    private void addEngineHelp(LinearLayout card, int index) {
        View old = card.findViewWithTag("engine_help");
        if (old != null) card.removeView(old);
        TextView help = new TextView(context);
        help.setTag("engine_help");
        help.setText(index == 2 ? R.string.settings_tts_iflytek_help
                : (index == 1 ? R.string.settings_tts_elevenlabs_help
                : R.string.settings_tts_sherpa_help));
        CockpitTokens.bodySm(help);
        help.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        help.setPadding(dp(4), dp(8), dp(4), dp(8));
        int position = card.indexOfChild(engineSegmented) + 1;
        card.addView(help, position, matchWrap());
    }

    private void rebuildRightPanel(LinearLayout card, int engineIndex) {
        if (card == null) return;
        card.removeAllViews();
        voiceCards.clear();
        keyField = null;
        keyStatus = null;
        balanceText = null;
        activeVoice = null;
        customVoice = null;
        if (engineIndex == 1) {
            buildElevenLabsPanel(card);
        } else {
            buildSimpleEnginePanel(card, engineIndex);
        }
    }

    private void buildSimpleEnginePanel(LinearLayout card, int engineIndex) {
        addTitle(card, engineIndex == 0 ? R.string.settings_tts_sherpa
                : R.string.settings_tts_iflytek);
        addBody(card, engineIndex == 0 ? R.string.settings_tts_sherpa_help
                : R.string.settings_tts_iflytek_help, true);
        if (engineIndex == 0) {
            addLabel(card, R.string.voice_talk_sherpa_model);
            TextView model = new TextView(context);
            model.setText(R.string.voice_talk_sherpa_model_value);
            CockpitTokens.bodyMd(model);
            model.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
            model.setPadding(dp(4), dp(4), dp(4), dp(4));
            card.addView(model, matchWrap());
            TextView note = new TextView(context);
            note.setText(R.string.voice_talk_sherpa_single_speaker);
            CockpitTokens.bodySm(note);
            note.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            note.setPadding(dp(4), 0, dp(4), dp(12));
            card.addView(note, matchWrap());
            TextView speedValue = new TextView(context);
            addSlider(card, R.string.voice_talk_speed, speedValue, 70, 130,
                    MapControlSpeech.KEY_SHERPA_SPEED, 100);
            speedValue.setText(formatSliderValue(R.string.voice_talk_speed,
                    prefs.getInt(MapControlSpeech.KEY_SHERPA_SPEED, 100)));
        } else {
            TextView note = new TextView(context);
            note.setText(R.string.voice_talk_no_extra_settings);
            CockpitTokens.bodySm(note);
            note.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            note.setPadding(dp(4), dp(12), dp(4), dp(12));
            card.addView(note, matchWrap());
        }
        CockpitButton listen = CockpitButton.primary(context,
                context.getString(R.string.voice_talk_listen));
        listen.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        listen.setOnClickListener(v -> {
            MapControlSpeech.stop();
            String sample = context.getString(R.string.voice_talk_sample);
            MapControlSpeech.speak(context, sample, sample);
        });
        LinearLayout.LayoutParams listenLp = matchWrap();
        listenLp.topMargin = dp(12);
        card.addView(listen, listenLp);
    }

    private void buildElevenLabsPanel(LinearLayout card) {
        addLabel(card, R.string.settings_tts_api_key);
        LinearLayout keyRow = new LinearLayout(context);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        keyField = new EditText(context);
        keyField.setHint(R.string.settings_tts_api_hint);
        keyField.setTextSize(15);
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        keyField.setHintTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        keyField.setPadding(dp(14), dp(10), dp(14), dp(10));
        keyField.setBackground(CockpitSurfaces.outlined(context,
                CockpitTokens.color(context, R.color.ck_bg_recessed),
                CockpitTokens.color(context, R.color.ck_stroke), 12f));
        keyRow.addView(keyField, new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f));
        TextView show = new TextView(context);
        show.setText(R.string.voice_talk_show_key);
        CockpitTokens.bodySm(show);
        show.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        show.setGravity(Gravity.CENTER);
        show.setPadding(dp(12), 0, dp(8), 0);
        show.setClickable(true);
        show.setFocusable(true);
        final boolean[] visible = {false};
        show.setOnClickListener(v -> {
            visible[0] = !visible[0];
            keyField.setInputType(visible[0]
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            show.setText(visible[0] ? R.string.voice_talk_hide_key : R.string.voice_talk_show_key);
        });
        keyRow.addView(show, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min)));
        card.addView(keyRow, matchWrap());

        keyStatus = new TextView(context);
        CockpitTokens.bodySm(keyStatus);
        keyStatus.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        keyStatus.setPadding(dp(4), dp(6), 0, dp(8));
        updateKeyStatus();
        card.addView(keyStatus);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setBaselineAligned(false);
        CockpitButton save = CockpitButton.primary(context,
                context.getString(R.string.settings_tts_save));
        save.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        save.setOnClickListener(v -> saveSettings());
        actions.addView(save, new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f));
        CockpitButton test = CockpitButton.secondary(context,
                context.getString(R.string.voice_talk_test));
        test.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        test.setOnClickListener(v -> testConnection());
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f);
        testLp.leftMargin = dp(8);
        actions.addView(test, testLp);
        LinearLayout.LayoutParams actionsLp = matchWrap();
        actionsLp.topMargin = dp(8);
        card.addView(actions, actionsLp);

        balanceText = new TextView(context);
        CockpitTokens.bodySm(balanceText);
        balanceText.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        balanceText.setText(R.string.voice_talk_balance_unknown);
        balanceText.setPadding(dp(4), dp(8), 0, dp(16));
        card.addView(balanceText);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(R.string.voice_talk_profile_title);
        CockpitTokens.title(title);
        head.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CockpitButton listen = CockpitButton.secondary(context,
                context.getString(R.string.voice_talk_listen));
        listen.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        listen.setOnClickListener(v -> {
            MapControlSpeech.stop();
            String sample = context.getString(R.string.voice_talk_sample);
            MapControlSpeech.speak(context, sample, sample);
        });
        head.addView(listen);
        card.addView(head, matchWrap());
        addBody(card, R.string.voice_talk_profile_desc, true);

        activeVoice = new TextView(context);
        CockpitTokens.bodySm(activeVoice);
        activeVoice.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        activeVoice.setPadding(dp(4), dp(4), 0, dp(10));
        card.addView(activeVoice, matchWrap());

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < VOICE_NAMES.length; row += 2) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setBaselineAligned(false);
            for (int i = row; i < Math.min(row + 2, VOICE_NAMES.length); i++) {
                LinearLayout voice = voiceCard(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i == row) lp.rightMargin = dp(8);
                line.addView(voice, lp);
            }
            if ((VOICE_NAMES.length - row) == 1) {
                line.addView(new View(context), new LinearLayout.LayoutParams(
                        0, 1, 1f));
            }
            LinearLayout.LayoutParams lineLp = matchWrap();
            lineLp.topMargin = dp(8);
            grid.addView(line, lineLp);
        }
        card.addView(grid, matchWrap());

        addLabel(card, R.string.voice_talk_manual_label);
        LinearLayout manual = new LinearLayout(context);
        manual.setOrientation(LinearLayout.HORIZONTAL);
        manual.setGravity(Gravity.CENTER_VERTICAL);
        customVoice = new EditText(context);
        customVoice.setHint(R.string.settings_tts_voice_hint);
        customVoice.setTextSize(15);
        customVoice.setSingleLine(true);
        customVoice.setText(MapControlSpeech.voiceId(context));
        customVoice.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        customVoice.setHintTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        customVoice.setPadding(dp(14), dp(10), dp(14), dp(10));
        customVoice.setBackground(CockpitSurfaces.outlined(context,
                CockpitTokens.color(context, R.color.ck_bg_recessed),
                CockpitTokens.color(context, R.color.ck_stroke), 12f));
        manual.addView(customVoice, new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f));
        CockpitButton use = CockpitButton.secondary(context,
                context.getString(R.string.voice_talk_use));
        use.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        use.setOnClickListener(v -> {
            String id = customVoice.getText().toString().trim();
            if (id.isEmpty()) {
                Toast.makeText(context, R.string.settings_tts_voice_required, Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(MapControlSpeech.KEY_VOICE_ID, id).apply();
            selectedVoiceIndex = -1;
            refreshVoiceCards();
            updateActiveVoice();
        });
        LinearLayout.LayoutParams useLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        useLp.leftMargin = dp(8);
        manual.addView(use, useLp);
        card.addView(manual, matchWrap());

        addLabel(card, R.string.voice_talk_fine_title);
        speedValue = new TextView(context);
        speedBar = addSlider(card, R.string.voice_talk_speed, speedValue, 70, 120,
                MapControlSpeech.KEY_TTS_SPEED, 100);
        stabilityValue = new TextView(context);
        stabilityBar = addSlider(card, R.string.voice_talk_stability, stabilityValue, 0, 100,
                MapControlSpeech.KEY_TTS_STABILITY, 45);
        clarityValue = new TextView(context);
        clarityBar = addSlider(card, R.string.voice_talk_clarity, clarityValue, 0, 100,
                MapControlSpeech.KEY_TTS_CLARITY, 80);

        selectedVoiceIndex = presetIndex(MapControlSpeech.voiceId(context));
        refreshVoiceCards();
        updateActiveVoice();
        updateSliderLabels();
    }

    private LinearLayout voiceCard(int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView radio = new TextView(context);
        radio.setTextSize(20);
        radio.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        row.addView(radio, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView name = new TextView(context);
        name.setText(VOICE_NAMES[index]);
        CockpitTokens.bodyMd(name);
        name.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        name.setPadding(dp(8), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        TextView meta = new TextView(context);
        meta.setText(VOICE_META[index]);
        CockpitTokens.bodySm(meta);
        meta.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        meta.setPadding(dp(32), dp(2), 0, 0);
        card.addView(meta);
        card.setOnClickListener(v -> {
            selectedVoiceIndex = index;
            prefs.edit().putString(MapControlSpeech.KEY_VOICE_ID, VOICE_IDS[index]).apply();
            customVoice.setText(VOICE_IDS[index]);
            refreshVoiceCards();
            updateActiveVoice();
        });
        voiceCards.add(new VoiceCard(index, card, radio));
        return card;
    }

    private void refreshVoiceCards() {
        for (VoiceCard item : voiceCards) {
            boolean selected = item.index == selectedVoiceIndex;
            item.view.setBackground(CockpitSurfaces.outlined(context,
                    CockpitTokens.color(context, selected ? R.color.ck_surface_2 : R.color.ck_surface_1),
                    CockpitTokens.color(context, selected ? R.color.ck_accent : R.color.ck_stroke), 12f));
            item.radio.setText(selected ? "●" : "○");
            item.radio.setTextColor(CockpitTokens.color(context,
                    selected ? R.color.ck_accent : R.color.ck_text_medium));
        }
    }

    private void updateActiveVoice() {
        if (activeVoice == null) return;
        String id = MapControlSpeech.voiceId(context);
        int index = presetIndex(id);
        String name = index >= 0 ? VOICE_NAMES[index] : context.getString(R.string.voice_talk_manual_voice);
        activeVoice.setText(context.getString(R.string.voice_talk_active_voice, name));
    }

    private SeekBar addSlider(LinearLayout card, int labelRes, TextView valueView,
            int min, int max, String key, int fallback) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context);
        label.setText(labelRes);
        CockpitTokens.bodySm(label);
        label.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        row.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        CockpitTokens.bodySm(valueView);
        valueView.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(valueView);
        LinearLayout.LayoutParams rowLp = matchWrap();
        rowLp.topMargin = dp(10);
        card.addView(row, rowLp);

        SeekBar bar = new SeekBar(context);
        bar.setMax(max - min);
        bar.setProgress(prefs.getInt(key, fallback) - min);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(
                CockpitTokens.color(context, R.color.ck_accent)));
        bar.setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                valueView.setText(formatSliderValue(labelRes, value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt(key, min + seekBar.getProgress()).apply();
            }
        });
        card.addView(bar, matchWrap());
        return bar;
    }

    private void updateSliderLabels() {
        speedValue.setText(formatSliderValue(R.string.voice_talk_speed,
                prefs.getInt(MapControlSpeech.KEY_TTS_SPEED, 100)));
        stabilityValue.setText(formatSliderValue(R.string.voice_talk_stability,
                prefs.getInt(MapControlSpeech.KEY_TTS_STABILITY, 45)));
        clarityValue.setText(formatSliderValue(R.string.voice_talk_clarity,
                prefs.getInt(MapControlSpeech.KEY_TTS_CLARITY, 80)));
    }

    private String formatSliderValue(int labelRes, int value) {
        if (labelRes == R.string.voice_talk_speed) {
            return String.format(java.util.Locale.getDefault(), "%.1fx", value / 100f);
        }
        return value + "%";
    }

    private void saveSettings() {
        String key = keyField.getText().toString().trim();
        if (!key.isEmpty() && !MapControlSpeech.isValidApiKey(key)) {
            Toast.makeText(context, R.string.settings_tts_api_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        String voice = customVoice.getText().toString().trim();
        if (voice.isEmpty()) {
            Toast.makeText(context, R.string.settings_tts_voice_required, Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        if (!key.isEmpty()) editor.putString(MapControlSpeech.KEY_API_KEY, key);
        editor.putString(MapControlSpeech.KEY_VOICE_ID, voice);
        editor.apply();
        keyField.setText("");
        updateKeyStatus();
        Toast.makeText(context, R.string.settings_tts_saved, Toast.LENGTH_SHORT).show();
    }

    private void updateKeyStatus() {
        if (keyStatus == null) return;
        keyStatus.setText(MapControlSpeech.hasApiKey(context)
                ? R.string.settings_tts_key_saved : R.string.settings_tts_key_missing);
    }

    private void testConnection() {
        String key = keyField.getText().toString().trim();
        if (key.isEmpty()) key = MapControlSpeech.apiKey(context);
        if (!MapControlSpeech.isValidApiKey(key)) {
            keyStatus.setText(R.string.settings_tts_api_invalid);
            return;
        }
        final String apiKey = key;
        keyStatus.setText(R.string.voice_talk_testing);
        new Thread(() -> {
            String result = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://api.elevenlabs.io/v1/user/subscription").openConnection();
                c.setRequestProperty("xi-api-key", apiKey);
                c.setConnectTimeout(6000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    StringBuilder b = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(c.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) b.append(line);
                    }
                    JSONObject json = new JSONObject(b.toString());
                    int used = json.optInt("character_count", -1);
                    int limit = json.optInt("character_limit", -1);
                    if (used >= 0 && limit > 0) {
                        result = String.format(java.util.Locale.getDefault(),
                                "%,d / %,d karakter", used, limit);
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
            }
            final String balance = result;
            main.post(() -> {
                if (balance == null) {
                    keyStatus.setText(R.string.voice_talk_test_failed);
                } else {
                    keyStatus.setText(R.string.voice_talk_test_ok);
                    balanceText.setText(context.getString(R.string.voice_talk_balance, balance));
                }
            });
        }, "voice-talk-test").start();
    }

    private int presetIndex(String id) {
        for (int i = 0; i < VOICE_IDS.length; i++) if (VOICE_IDS[i].equals(id)) return i;
        return -1;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(pad, pad, pad, pad);
        CockpitSurfaces.level1(card);
        return card;
    }

    private void addTitle(LinearLayout card, int res) {
        TextView title = new TextView(context);
        title.setText(res);
        CockpitTokens.title(title);
        card.addView(title, matchWrap());
    }

    private void addBody(LinearLayout card, int res, boolean topMargin) {
        TextView body = new TextView(context);
        body.setText(res);
        CockpitTokens.bodySm(body);
        body.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams lp = matchWrap();
        if (topMargin) lp.topMargin = dp(6);
        card.addView(body, lp);
    }

    private void addLabel(LinearLayout card, int res) {
        TextView label = new TextView(context);
        label.setText(res);
        CockpitTokens.labelCaps(label);
        label.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(14);
        card.addView(label, lp);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return CockpitTokens.dp(context, value);
    }
}
