package com.mapcontrol.assistant.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.mapcontrol.R;
import com.mapcontrol.assistant.AssistantPrefs;
import com.mapcontrol.assistant.core.AssistantState;
import com.mapcontrol.assistant.core.AssistantStateBus;
import com.mapcontrol.assistant.core.VoiceAssistantManager;
import com.mapcontrol.assistant.llm.GeminiClient;
import com.mapcontrol.assistant.llm.MiniJson;
import com.mapcontrol.assistant.store.ConversationStore;
import com.mapcontrol.assistant.tools.AssistantToolRegistry;
import com.mapcontrol.assistant.wake.WakeWordModelStore;
import com.mapcontrol.assistant.wake.WakeWordNormalizer;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitChip;
import com.mapcontrol.ui.cockpit.CockpitInput;
import com.mapcontrol.ui.cockpit.CockpitSegmented;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggle;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Medya &gt; Sesli Asistan: sol tarafta sohbet + canlı giriş, sağ tarafta kalıcı
 * ayar paneli (geniş ekran). Dar ekranda ayarlar katlanır.
 *
 * <p>Geçmiş sanallaştırılmıştır (son 60 mesaj çizilir), böylece 500 mesajda
 * kaydırma akıcı kalır. Ses tayfı yalnızca komut kaydı sırasında çizilir;
 * boştayken hiçbir animasyon döngüsü çalışmaz.</p>
 */
public final class AssistantTabBuilder {

    private static final int PAGE_SIZE = 60;

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final boolean wide;

    private final LinearLayout root;
    private final LinearLayout leftColumn;
    private final LinearLayout messages;
    private final ScrollView chatScroll;
    private final TextView emptyHint;
    private final CockpitChip statusChip;
    private final CockpitButton micButton;
    private final EditText input;
    private final AssistantWaveformView waveform;
    private final TextView liveStatus;
    private final TextView liveMeta;
    private final TextView liveModel;
    private final TextView modelStatus;
    private final TextView modelBadge;
    private final TextView testStatus;
    private final TextView pingStatus;
    private final ScrollView settingsScroll;
    private final LinearLayout quickRow;

    private int visibleLimit = PAGE_SIZE;
    private boolean listening;
    private boolean attached;
    private boolean storeListenerAttached;

    private final Runnable renderRunnable = this::renderMessages;

    private final ConversationStore.Listener storeListener = () -> main.post(renderRunnable);
    private final AssistantStateBus.Listener stateListener = (state, detail) -> main.post(() -> {
        updateStatus(state, detail);
        renderMessages();
    });

    public AssistantTabBuilder(Context context) {
        this.context = context;
        this.prefs = AssistantPrefs.prefs(context);
        this.wide = context.getResources().getConfiguration().screenWidthDp >= 1000;

        root = new LinearLayout(context);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        root.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);

        leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);

        // ---------- başlık
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(R.string.assistant_page_title);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        titles.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.assistant_page_subtitle);
        CockpitTokens.bodySm(subtitle);
        subtitle.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusChip = new CockpitChip(context, context.getString(R.string.assistant_state_disabled));
        header.addView(statusChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_chip_h)));
        if (!wide) {
            CockpitButton settings = CockpitButton.secondary(context,
                    context.getString(R.string.assistant_settings));
            settings.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
            settings.setOnClickListener(v -> toggleSettings());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    CockpitTokens.dim(context, R.dimen.ck_touch_min));
            lp.leftMargin = dp(8);
            header.addView(settings, lp);
        }
        leftColumn.addView(header, matchWrap(0));

        // ---------- hızlı durum kartları
        quickRow = new LinearLayout(context);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView quickScroll = new HorizontalScrollView(context);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickScroll.setFillViewport(false);
        quickScroll.addView(quickRow);
        LinearLayout.LayoutParams quickLp = matchWrap(10);
        leftColumn.addView(quickScroll, quickLp);
        rebuildQuickActions();

        // ---------- canlı giriş kartı
        LinearLayout liveCard = card();
        LinearLayout liveHead = new LinearLayout(context);
        liveHead.setOrientation(LinearLayout.HORIZONTAL);
        liveHead.setGravity(Gravity.CENTER_VERTICAL);
        liveStatus = new TextView(context);
        liveStatus.setText(R.string.assistant_live_title);
        CockpitTokens.labelCaps(liveStatus);
        liveStatus.setTextColor(UiStyles.color(context, R.color.ck_accent));
        liveHead.addView(liveStatus, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        liveModel = new TextView(context);
        CockpitTokens.bodySm(liveModel);
        liveModel.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        liveHead.addView(liveModel);
        liveCard.addView(liveHead, matchWrap(0));

        waveform = new AssistantWaveformView(context);
        LinearLayout meter = new LinearLayout(context);
        meter.setOrientation(LinearLayout.VERTICAL);
        meter.setPadding(dp(12), dp(6), dp(12), dp(6));
        meter.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_bg_recessed),
                UiStyles.color(context, R.color.ck_stroke), 12f));
        meter.addView(waveform, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        liveCard.addView(meter, matchWrap(8));

        liveMeta = new TextView(context);
        CockpitTokens.bodySm(liveMeta);
        liveMeta.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        liveCard.addView(liveMeta, matchWrap(6));
        leftColumn.addView(liveCard, matchWrap(10));

        // ---------- sohbet
        chatScroll = new ScrollView(context);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        messages = new LinearLayout(context);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(6), 0, dp(6));
        chatScroll.addView(messages, matchWrap(0));
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        chatLp.topMargin = dp(6);
        leftColumn.addView(chatScroll, chatLp);

        emptyHint = new TextView(context);
        emptyHint.setText(R.string.assistant_empty_hint);
        CockpitTokens.bodyMd(emptyHint);
        emptyHint.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setPadding(0, dp(16), 0, dp(16));
        leftColumn.addView(emptyHint, matchWrap(0));

        // ---------- öneri çipleri
        HorizontalScrollView suggestions = new HorizontalScrollView(context);
        suggestions.setHorizontalScrollBarEnabled(false);
        LinearLayout suggestionRow = new LinearLayout(context);
        suggestionRow.setOrientation(LinearLayout.HORIZONTAL);
        addSuggestion(suggestionRow, R.string.assistant_suggest_1);
        addSuggestion(suggestionRow, R.string.assistant_suggest_2);
        addSuggestion(suggestionRow, R.string.assistant_suggest_3);
        suggestions.addView(suggestionRow);
        LinearLayout.LayoutParams suggestLp = matchWrap(8);
        leftColumn.addView(suggestions, suggestLp);

        // ---------- alt çubuk
        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        micButton = CockpitButton.primary(context,
                context.getString(R.string.assistant_push_to_talk));
        micButton.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        micButton.setOnClickListener(v -> onMicClicked());
        bottom.addView(micButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min)));

        input = new CockpitInput(context);
        input.setHint(R.string.assistant_input_hint);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f);
        inputLp.leftMargin = dp(8);
        bottom.addView(input, inputLp);

        CockpitButton send = CockpitButton.secondary(context,
                context.getString(R.string.assistant_send));
        send.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        send.setOnClickListener(v -> sendTyped());
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        sendLp.leftMargin = dp(8);
        bottom.addView(send, sendLp);
        leftColumn.addView(bottom, matchWrap(8));

        // ---------- ayarlar
        settingsScroll = new ScrollView(context);
        settingsScroll.setFillViewport(false);
        LinearLayout settingsBody = new LinearLayout(context);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        settingsBody.setPadding(pad, pad, pad, pad);
        CockpitSurfaces.level1(settingsBody);
        settingsScroll.addView(settingsBody, matchWrap(0));

        modelStatus = new TextView(context);
        modelBadge = new TextView(context);
        testStatus = new TextView(context);
        pingStatus = new TextView(context);
        buildSettings(settingsBody);
        settingsScroll.setVisibility(wide ? View.VISIBLE : View.GONE);

        if (wide) {
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f);
            root.addView(leftColumn, leftLp);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            rightLp.leftMargin = dp(16);
            root.addView(settingsScroll, rightLp);
        } else {
            root.addView(leftColumn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            settingsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.min(dp(420), (int) (context.getResources()
                            .getDisplayMetrics().heightPixels * 0.5f))));
            root.addView(settingsScroll);
        }

        updateStatus(AssistantStateBus.get().state(), AssistantStateBus.get().detail());
        updateLiveMeta();
    }

    public View getView() {
        return root;
    }

    public void onTabVisible() {
        attached = true;
        attachStoreIfNeeded();
        AssistantStateBus.get().addListener(stateListener);
        rebuildQuickActions();
        updateModelStatus();
        updateLiveMeta();
        renderMessages();
    }

    public void onTabHidden() {
        attached = false;
        ConversationStore store = store();
        if (store != null && storeListenerAttached) {
            store.removeListener(storeListener);
        }
        storeListenerAttached = false;
        AssistantStateBus.get().removeListener(stateListener);
        waveform.setActive(false);
    }

    public void release() {
        onTabHidden();
    }

    private void attachStoreIfNeeded() {
        if (storeListenerAttached) {
            return;
        }
        ConversationStore store = store();
        if (store == null) {
            return;
        }
        store.load();
        store.addListener(storeListener);
        storeListenerAttached = true;
    }

    private void sendTyped() {
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) {
            return;
        }
        VoiceAssistantManager manager = manager();
        if (manager == null) {
            toast(R.string.assistant_enable_first);
            return;
        }
        input.setText("");
        manager.submitText(value);
    }

    private void onMicClicked() {
        VoiceAssistantManager manager = manager();
        if (manager == null) {
            toast(R.string.assistant_enable_first);
            return;
        }
        listening = !listening;
        if (listening) {
            manager.beginPushToTalk();
        } else {
            manager.endPushToTalk();
        }
        micButton.setText(listening
                ? R.string.assistant_push_to_talk_stop : R.string.assistant_push_to_talk);
    }

    // --------------------------------------------------------------- status

    private void updateStatus(AssistantState state, String detail) {
        int text;
        boolean active = false;
        switch (state) {
            case LISTENING_WAKE:
                text = "ptt".equals(detail)
                        ? R.string.assistant_state_ready_button
                        : R.string.assistant_state_listening;
                active = true;
                break;
            case RECORDING:
                text = R.string.assistant_state_recording;
                active = true;
                break;
            case THINKING:
                text = R.string.assistant_state_thinking;
                active = true;
                break;
            case CONFIRMING:
                text = R.string.assistant_state_confirming;
                active = true;
                break;
            case SPEAKING:
                text = R.string.assistant_state_speaking;
                active = true;
                break;
            case PAUSED:
                text = R.string.assistant_state_paused;
                break;
            case ERROR:
                text = R.string.assistant_state_error;
                break;
            default:
                text = R.string.assistant_state_disabled;
        }
        statusChip.setText(text);
        statusChip.setSelectedState(active);
        if (detail != null && !detail.isEmpty()) {
            statusChip.setContentDescription(detail);
        }
        waveform.setActive(state == AssistantState.RECORDING
                || state == AssistantState.CONFIRMING);
        if (micButton != null) {
            boolean recording = state == AssistantState.RECORDING
                    || state == AssistantState.CONFIRMING;
            listening = recording;
            micButton.setText(recording
                    ? R.string.assistant_push_to_talk_stop : R.string.assistant_push_to_talk);
        }
        if (liveMeta != null) {
            updateLiveMeta();
        }
    }

    private void updateLiveMeta() {
        if (liveMeta == null) {
            return;
        }
        AssistantState state = AssistantStateBus.get().state();
        liveStatus.setText(R.string.assistant_live_title);
        boolean connected = ComfortCommands.isConnected();
        String meta = context.getString(R.string.assistant_live_mic)
                + "   ·   " + context.getString(connected
                ? R.string.assistant_live_vdbus_ok : R.string.assistant_live_vdbus_off);
        if (state == AssistantState.DISABLED) {
            meta = context.getString(R.string.assistant_live_off) + "   ·   " + meta;
        }
        liveMeta.setText(meta);
        if (WakeWordModelStore.isInstalled(context)) {
            liveModel.setText(context.getString(R.string.assistant_live_model,
                    WakeWordModelStore.MODEL_DIR_NAME));
        } else {
            liveModel.setText(R.string.assistant_live_no_model);
        }
    }

    // ------------------------------------------------------------- quick row

    private void rebuildQuickActions() {
        if (quickRow == null) {
            return;
        }
        quickRow.removeAllViews();
        quickCard(quickRow, R.string.assistant_quick_ai,
                AssistantPrefs.model(context), this::toggleSettings);
        quickCard(quickRow, R.string.assistant_quick_tts,
                AssistantPrefs.TTS_IFLYTEK.equals(AssistantPrefs.ttsEngine(context))
                        ? context.getString(R.string.assistant_tts_iflytek)
                        : context.getString(R.string.assistant_tts_sherpa),
                this::toggleSettings);
        quickCard(quickRow, R.string.assistant_quick_wake,
                AssistantPrefs.wakeWord(context), this::toggleSettings);
        quickCard(quickRow, R.string.assistant_quick_reset,
                context.getString(R.string.assistant_quick_reset), () -> {
                    VoiceAssistantManager manager = manager();
                    if (manager != null) {
                        manager.resetSession();
                    }
                    toast(R.string.assistant_reset_done);
                });
        quickCard(quickRow, R.string.assistant_quick_clear,
                context.getString(R.string.assistant_quick_clear), this::confirmClearHistory);
    }

    private void quickCard(LinearLayout parent, int labelRes, String value, Runnable onClick) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(6), dp(12), dp(6));
        card.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_surface_1),
                UiStyles.color(context, R.color.ck_stroke), 12f));
        card.setMinimumHeight(dp(56));
        card.setClickable(true);
        card.setFocusable(true);
        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        label.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        label.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        card.addView(label, matchWrap(0));
        TextView text = new TextView(context);
        text.setText(value);
        CockpitTokens.bodySm(text);
        text.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        text.setSingleLine(true);
        card.addView(text, matchWrap(2));
        card.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        parent.addView(card, lp);
    }

    private void addSuggestion(LinearLayout parent, int textRes) {
        CockpitChip chip = new CockpitChip(context, context.getString(textRes));
        chip.setOnClickListener(v -> {
            VoiceAssistantManager manager = manager();
            if (manager == null) {
                toast(R.string.assistant_enable_first);
                return;
            }
            manager.submitText(context.getString(textRes));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_chip_h));
        lp.rightMargin = dp(8);
        parent.addView(chip, lp);
    }

    private void toggleSettings() {
        if (wide) {
            return;
        }
        boolean show = settingsScroll.getVisibility() != View.VISIBLE;
        settingsScroll.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------- messages

    private void renderMessages() {
        if (!attached || messages == null) {
            return;
        }
        attachStoreIfNeeded();
        ConversationStore store = store();
        List<ConversationStore.Entry> all = store == null
                ? java.util.Collections.<ConversationStore.Entry>emptyList()
                : store.snapshot();
        messages.removeAllViews();
        emptyHint.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        if (all.isEmpty()) {
            return;
        }
        int start = Math.max(0, all.size() - visibleLimit);
        if (start > 0) {
            CockpitButton older = CockpitButton.secondary(context,
                    context.getString(R.string.assistant_load_older));
            older.setOnClickListener(v -> {
                visibleLimit += PAGE_SIZE;
                renderMessages();
            });
            messages.addView(older, matchWrap(0));
        }
        long lastDay = -1L;
        for (int i = start; i < all.size(); i++) {
            ConversationStore.Entry entry = all.get(i);
            long day = dayOf(entry.timeMs);
            if (day != lastDay) {
                lastDay = day;
                messages.addView(dayDivider(entry.timeMs), matchWrap(8));
            }
            messages.addView(messageView(entry), matchWrap(6));
        }
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View messageView(ConversationStore.Entry entry) {
        if (ConversationStore.ROLE_TOOL.equals(entry.role)) {
            return toolCard(entry);
        }
        boolean user = ConversationStore.ROLE_USER.equals(entry.role);
        boolean error = ConversationStore.ROLE_ERROR.equals(entry.role);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(user ? Gravity.END : Gravity.START);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(10), dp(14), dp(10));
        int fill = user
                ? blend(UiStyles.color(context, R.color.ck_accent), 0.16f)
                : UiStyles.color(context, R.color.ck_surface_1);
        int stroke = error ? UiStyles.color(context, R.color.ck_critical)
                : UiStyles.color(context, user ? R.color.ck_accent : R.color.ck_stroke);
        column.setBackground(CockpitSurfaces.outlined(context, fill, stroke, 16f));

        String text;
        if (error) {
            text = entry.text == null ? "" : entry.text;
        } else if (user) {
            text = entry.text == null || entry.text.isEmpty()
                    ? context.getString(R.string.assistant_voice_message)
                    : entry.text;
        } else {
            text = entry.text == null ? "" : entry.text;
        }

        TextView body = new TextView(context);
        body.setText(text);
        body.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        body.setTypeface(CockpitTokens.inter(context,
                user ? CockpitTokens.WEIGHT_MEDIUM : CockpitTokens.WEIGHT_REGULAR));
        body.setTextColor(UiStyles.color(context,
                error ? R.color.ck_critical : R.color.ck_text_high));
        body.setLineSpacing(dp(2), 1f);
        column.addView(body, matchWrap(0));

        TextView meta = new TextView(context);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(entry.timeMs));
        meta.setText(user || entry.text == null || entry.text.isEmpty()
                ? time : ("RoadDeck Asistan · " + time));
        CockpitTokens.bodySm(meta);
        meta.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        meta.setPadding(0, dp(4), 0, 0);
        column.addView(meta, matchWrap(0));

        // Geniş düzende sohbet sütunu ekranın ~%55'i; balon bunu taşmamalı.
        float ratio = wide ? 0.34f : 0.62f;
        int maxWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * ratio);
        body.setMaxWidth(maxWidth);
        row.addView(column);
        return row;
    }

    private View toolCard(ConversationStore.Entry entry) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        boolean ok = entry.result != null && !entry.result.contains("olmadı")
                && !entry.result.contains("başarısız") && !entry.result.contains("yok");
        card.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_bg_recessed),
                UiStyles.color(context, ok ? R.color.ck_accent_stroke_30 : R.color.ck_critical),
                14f));

        TextView header = new TextView(context);
        header.setText(R.string.assistant_action_title);
        header.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        header.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        header.setTextColor(UiStyles.color(context, R.color.ck_accent));
        card.addView(header, matchWrap(0));

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView action = new TextView(context);
        action.setText(toolLabel(entry.tool));
        CockpitTokens.bodyMd(action);
        action.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        actionRow.addView(action, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = new TextView(context);
        badge.setText(ok ? R.string.assistant_action_done : R.string.assistant_action_failed);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        badge.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        badge.setTextColor(UiStyles.color(context,
                ok ? R.color.ck_nominal : R.color.ck_critical));
        actionRow.addView(badge);
        card.addView(actionRow, matchWrap(4));

        if (entry.args != null && !entry.args.isEmpty() && !"{}".equals(entry.args)) {
            TextView args = new TextView(context);
            args.setText(entry.args);
            CockpitTokens.mono(args);
            args.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            args.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
            card.addView(args, matchWrap(4));
        }
        if (entry.result != null && !entry.result.isEmpty()) {
            TextView result = new TextView(context);
            result.setText(entry.result);
            CockpitTokens.bodySm(result);
            result.setTextColor(UiStyles.color(context, R.color.ck_text_high));
            card.addView(result, matchWrap(4));
        }
        TextView meta = new TextView(context);
        meta.setText(context.getString(R.string.assistant_action_meta, entry.tool,
                new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(new Date(entry.timeMs))));
        meta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        meta.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        card.addView(meta, matchWrap(4));
        return card;
    }

    private String toolLabel(String tool) {
        if (tool == null) {
            return "";
        }
        switch (tool) {
            case "sunroof_set":
                return "Cam tavan";
            case "sunshade_set":
                return "Güneşlik";
            case "sunroof_status":
                return "Cam tavan durumu";
            case "recirculation_set":
                return "İç sirkülasyon";
            case "seat_heat_set":
                return "Koltuk ısıtma";
            case "seat_vent_set":
                return "Koltuk havalandırma";
            case "seat_massage_set":
                return "Koltuk masajı";
            case "steering_heat_set":
                return "Direksiyon ısıtma";
            case "seat_memory_restore":
                return "Koltuk hafızası";
            case "drive_mode_set":
                return "Sürüş modu";
            case "door_lock_set":
                return "Merkezi kilit";
            case "media_control":
                return "Medya";
            case "volume_set":
                return "Ses seviyesi";
            case "navigate_to":
                return "Navigasyon";
            case "cluster_nav_toggle":
                return "Gösterge navigasyonu";
            case "open_app":
                return "Uygulama aç";
            case "open_screen":
                return "Ekran aç";
            case "equalizer_preset":
                return "Ekolayzer";
            case "weather_now":
                return "Hava durumu";
            case "driving_report_summary":
                return "Sürüş özeti";
            case "time_date":
                return "Saat ve tarih";
            case "read_last_nav_instruction":
                return "Navigasyon yönergesi";
            case "assistant_control":
                return "Asistan kontrolü";
            case "vehicle_status":
                return "Araç durumu";
            default:
                return tool;
        }
    }

    private View dayDivider(long timeMs) {
        TextView divider = new TextView(context);
        divider.setText(new SimpleDateFormat("d MMMM yyyy, EEEE", new Locale("tr", "TR"))
                .format(new Date(timeMs)));
        CockpitTokens.bodySm(divider);
        divider.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        divider.setGravity(Gravity.CENTER);
        return divider;
    }

    private static long dayOf(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
    }

    // --------------------------------------------------------------- settings

    private void buildSettings(LinearLayout body) {
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(R.string.assistant_config_title);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        head.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tag = new TextView(context);
        tag.setText(R.string.assistant_config_tag);
        tag.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        tag.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_SEMIBOLD));
        tag.setTextColor(UiStyles.color(context, R.color.ck_accent));
        tag.setPadding(dp(8), dp(2), dp(8), dp(2));
        tag.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_bg_recessed),
                UiStyles.color(context, R.color.ck_accent_stroke_30), 10f));
        head.addView(tag);
        body.addView(head, matchWrap(0));

        // Etkinleştir
        LinearLayout enableRow = settingRow(body, R.string.assistant_enable_title,
                R.string.assistant_enable_desc);
        CockpitToggle enable = new CockpitToggle(context);
        enable.setChecked(AssistantPrefs.isEnabled(context), false);
        enable.setOnCheckedChangeListener((toggle, checked) -> {
            if (checked && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toggle.setChecked(false, true);
                toast(R.string.assistant_mic_permission);
                return;
            }
            AssistantPrefs.setEnabled(context, checked);
            VoiceAssistantManager manager = checked
                    ? VoiceAssistantManager.get(context) : VoiceAssistantManager.peek();
            if (manager != null) {
                manager.refreshSettings();
            }
            AssistantOverlayButton.sync(context);
            AssistantListeningPanel.sync(context);
            if (checked && !WakeWordModelStore.isInstalled(context)) {
                promptModelDownload(modelStatus);
            }
            updateModelStatus();
            rebuildQuickActions();
            updateLiveMeta();
        });
        enableRow.addView(enable, new LinearLayout.LayoutParams(dp(72), dp(40)));

        // Uyandırma kelimesi
        addLabel(body, R.string.assistant_wake_word);
        LinearLayout wakeRow = new LinearLayout(context);
        wakeRow.setOrientation(LinearLayout.HORIZONTAL);
        wakeRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText wakeField = new EditText(context);
        styleInput(wakeField);
        wakeField.setSingleLine(true);
        wakeField.setText(AssistantPrefs.wakeWord(context));
        wakeRow.addView(wakeField, new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f));

        modelBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        modelBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = dp(8);
        wakeRow.addView(modelBadge, badgeLp);
        body.addView(wakeRow, matchWrap(4));

        LinearLayout wakeActions = new LinearLayout(context);
        wakeActions.setOrientation(LinearLayout.HORIZONTAL);
        CockpitButton save = CockpitButton.secondary(context,
                context.getString(R.string.assistant_save));
        save.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        save.setOnClickListener(v -> {
            String value = wakeField.getText().toString().trim();
            int code = WakeWordNormalizer.validate(value);
            if (code != WakeWordNormalizer.CODE_OK) {
                toast(WakeWordNormalizer.describeCode(code));
            }
            if (value.isEmpty()) {
                return;
            }
            prefs.edit().putString(AssistantPrefs.KEY_WAKE_WORD,
                    WakeWordNormalizer.normalize(value)).apply();
            VoiceAssistantManager manager = manager();
            if (manager != null) {
                manager.refreshSettings();
            }
            rebuildQuickActions();
            toast(R.string.assistant_saved);
        });
        wakeActions.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min)));

        CockpitButton testWake = CockpitButton.secondary(context,
                context.getString(R.string.assistant_test_wake_5s));
        testWake.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        testWake.setOnClickListener(v -> {
            VoiceAssistantManager manager = manager();
            if (manager == null) {
                toast(R.string.assistant_enable_first);
                return;
            }
            testStatus.setText(R.string.assistant_test_wake_running);
            manager.testWakeWord(wakeField.getText().toString().trim(), detected ->
                    main.post(() -> testStatus.setText(detected
                            ? R.string.assistant_test_wake_ok
                            : R.string.assistant_test_wake_fail)));
        });
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        testLp.leftMargin = dp(8);
        wakeActions.addView(testWake, testLp);
        body.addView(wakeActions, matchWrap(6));

        // Hassasiyet
        addLabel(body, R.string.assistant_sensitivity);
        List<String> sensitivity = new ArrayList<>();
        sensitivity.add(context.getString(R.string.assistant_sensitivity_low));
        sensitivity.add(context.getString(R.string.assistant_sensitivity_medium));
        sensitivity.add(context.getString(R.string.assistant_sensitivity_high));
        body.addView(new CockpitSegmented(context, sensitivity,
                AssistantPrefs.sensitivity(context), index -> {
                    prefs.edit().putInt(AssistantPrefs.KEY_SENSITIVITY, index).apply();
                    VoiceAssistantManager manager = manager();
                    if (manager != null) {
                        manager.refreshSettings();
                    }
                }), matchWrap(4));

        // Gemini
        addSectionTitle(body, R.string.assistant_gemini_engine);
        pingStatus.setText(R.string.assistant_test_idle);
        pingStatus.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        pingStatus.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        body.addView(pingStatus, matchWrap(0));

        addLabel(body, R.string.assistant_api_key);
        LinearLayout keyRow = new LinearLayout(context);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText keyField = new EditText(context);
        styleInput(keyField);
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setHint(AssistantPrefs.hasApiKey(context)
                ? AssistantPrefs.maskedApiKey(context)
                : context.getString(R.string.assistant_api_key_hint));
        keyRow.addView(keyField, new LinearLayout.LayoutParams(0,
                CockpitTokens.dim(context, R.dimen.ck_touch_min), 1f));
        CockpitButton testApi = CockpitButton.secondary(context,
                context.getString(R.string.assistant_test_api));
        testApi.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        testApi.setOnClickListener(v -> testApi(keyField));
        LinearLayout.LayoutParams apiLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        apiLp.leftMargin = dp(8);
        keyRow.addView(testApi, apiLp);
        body.addView(keyRow, matchWrap(4));

        CockpitButton saveKey = CockpitButton.secondary(context,
                context.getString(R.string.assistant_save));
        saveKey.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        saveKey.setOnClickListener(v -> {
            String value = keyField.getText().toString().trim();
            if (!value.isEmpty() && !AssistantPrefs.isPlausibleKey(value)) {
                toast(R.string.assistant_api_key_invalid);
                return;
            }
            if (!value.isEmpty()) {
                AssistantPrefs.setApiKey(context, value);
            }
            keyField.setText("");
            keyField.setHint(AssistantPrefs.maskedApiKey(context));
            toast(R.string.assistant_saved);
        });
        body.addView(saveKey, matchWrap(4));

        addLabel(body, R.string.assistant_model_version);
        EditText modelField = new EditText(context);
        styleInput(modelField);
        modelField.setSingleLine(true);
        modelField.setText(AssistantPrefs.model(context));
        body.addView(modelField, matchWrap(4));
        CockpitButton saveModel = CockpitButton.secondary(context,
                context.getString(R.string.assistant_save));
        saveModel.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        saveModel.setOnClickListener(v -> {
            String value = modelField.getText().toString().trim();
            if (value.isEmpty()) {
                return;
            }
            prefs.edit().putString(AssistantPrefs.KEY_MODEL,
                    GeminiClient.sanitizeModel(value)).apply();
            rebuildQuickActions();
            toast(R.string.assistant_saved);
        });
        body.addView(saveModel, matchWrap(4));

        testStatus.setText(R.string.assistant_test_idle);
        CockpitTokens.bodySm(testStatus);
        testStatus.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        body.addView(testStatus, matchWrap(4));

        // Ses
        addSectionTitle(body, R.string.assistant_settings_voice);
        addLabel(body, R.string.assistant_tts_engine);
        List<String> engines = new ArrayList<>();
        engines.add(context.getString(R.string.assistant_tts_iflytek));
        engines.add(context.getString(R.string.assistant_tts_sherpa));
        body.addView(new CockpitSegmented(context, engines,
                AssistantPrefs.TTS_SHERPA.equals(AssistantPrefs.ttsEngine(context)) ? 1 : 0,
                index -> {
                    prefs.edit().putString(AssistantPrefs.KEY_TTS_ENGINE,
                            index == 1 ? AssistantPrefs.TTS_SHERPA
                                    : AssistantPrefs.TTS_IFLYTEK).apply();
                    rebuildQuickActions();
                }), matchWrap(4));

        LinearLayout voiceRow = settingRow(body, R.string.assistant_voice_reply,
                R.string.assistant_voice_reply_desc);
        CockpitToggle voiceToggle = new CockpitToggle(context);
        voiceToggle.setChecked(AssistantPrefs.voiceReply(context), false);
        voiceToggle.setOnCheckedChangeListener((toggle, checked) ->
                prefs.edit().putBoolean(AssistantPrefs.KEY_VOICE_REPLY, checked).apply());
        voiceRow.addView(voiceToggle, new LinearLayout.LayoutParams(dp(72), dp(40)));

        addLabel(body, R.string.assistant_wake_sound);
        List<String> sounds = new ArrayList<>();
        sounds.add(context.getString(R.string.assistant_wake_sound_none));
        sounds.add(context.getString(R.string.assistant_wake_sound_tone));
        sounds.add(context.getString(R.string.assistant_wake_sound_speak));
        String currentSound = AssistantPrefs.wakeSound(context);
        int soundIndex = AssistantPrefs.WAKE_SOUND_NONE.equals(currentSound) ? 0
                : (AssistantPrefs.WAKE_SOUND_SPEAK.equals(currentSound) ? 2 : 1);
        body.addView(new CockpitSegmented(context, sounds, soundIndex,
                index -> prefs.edit().putString(AssistantPrefs.KEY_WAKE_SOUND,
                        index == 0 ? AssistantPrefs.WAKE_SOUND_NONE
                                : (index == 2 ? AssistantPrefs.WAKE_SOUND_SPEAK
                                : AssistantPrefs.WAKE_SOUND_TONE)).apply()), matchWrap(4));

        // Overlay
        addSectionTitle(body, R.string.assistant_settings_overlay);
        LinearLayout overlayRow = settingRow(body, R.string.assistant_overlay_title,
                R.string.assistant_overlay_desc);
        CockpitToggle overlayToggle = new CockpitToggle(context);
        overlayToggle.setChecked(prefs.getBoolean(AssistantPrefs.KEY_OVERLAY, false), false);
        overlayToggle.setOnCheckedChangeListener((toggle, checked) -> {
            if (checked && !android.provider.Settings.canDrawOverlays(context)) {
                toggle.setChecked(false, true);
                toast(R.string.assistant_overlay_permission);
                return;
            }
            prefs.edit().putBoolean(AssistantPrefs.KEY_OVERLAY, checked).apply();
            AssistantOverlayButton.sync(context);
            AssistantListeningPanel.sync(context);
        });
        overlayRow.addView(overlayToggle, new LinearLayout.LayoutParams(dp(72), dp(40)));

        // Onay
        LinearLayout confirmRow = settingRow(body, R.string.assistant_confirm_toggle,
                R.string.assistant_confirm_toggle_desc);
        CockpitToggle confirmToggle = new CockpitToggle(context);
        confirmToggle.setChecked("strict".equals(prefs.getString(
                AssistantPrefs.KEY_CONFIRM_POLICY, "layered")), false);
        confirmToggle.setOnCheckedChangeListener((toggle, checked) ->
                prefs.edit().putString(AssistantPrefs.KEY_CONFIRM_POLICY,
                        checked ? "strict" : "layered").apply());
        confirmRow.addView(confirmToggle, new LinearLayout.LayoutParams(dp(72), dp(40)));

        // Model durumu
        addSectionTitle(body, R.string.assistant_settings_model);
        CockpitTokens.bodySm(modelStatus);
        modelStatus.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        body.addView(modelStatus, matchWrap(4));
        LinearLayout modelRow = new LinearLayout(context);
        modelRow.setOrientation(LinearLayout.HORIZONTAL);
        CockpitButton download = CockpitButton.secondary(context,
                context.getString(R.string.assistant_model_download));
        download.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        download.setOnClickListener(v -> promptModelDownload(modelStatus));
        modelRow.addView(download, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min)));
        CockpitButton delete = CockpitButton.secondary(context,
                context.getString(R.string.assistant_model_delete));
        delete.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        delete.setOnClickListener(v -> {
            WakeWordModelStore.delete(context);
            updateModelStatus();
            VoiceAssistantManager manager = manager();
            if (manager != null) {
                manager.refreshSettings();
            }
        });
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                CockpitTokens.dim(context, R.dimen.ck_touch_min));
        deleteLp.leftMargin = dp(8);
        modelRow.addView(delete, deleteLp);
        body.addView(modelRow, matchWrap(4));

        // Gizlilik
        TextView privacy = new TextView(context);
        privacy.setText(R.string.assistant_privacy_note);
        CockpitTokens.bodySm(privacy);
        privacy.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        privacy.setPadding(dp(10), dp(8), dp(10), dp(8));
        privacy.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_bg_recessed),
                UiStyles.color(context, R.color.ck_accent_stroke_30), 12f));
        body.addView(privacy, matchWrap(14));

        // Feature flag
        LinearLayout unverifiedRow = settingRow(body,
                R.string.assistant_unverified_title, R.string.assistant_unverified_desc);
        CockpitToggle unverified = new CockpitToggle(context);
        unverified.setChecked(prefs.getBoolean(AssistantToolRegistry.KEY_UNVERIFIED, false), false);
        unverified.setOnCheckedChangeListener((toggle, checked) -> {
            prefs.edit().putBoolean(AssistantToolRegistry.KEY_UNVERIFIED, checked).apply();
            VoiceAssistantManager manager = manager();
            if (manager != null) {
                manager.refreshSettings();
            }
        });
        unverifiedRow.addView(unverified, new LinearLayout.LayoutParams(dp(72), dp(40)));

        updateModelStatus();
    }

    private void testApi(EditText keyField) {
        String key = !keyField.getText().toString().trim().isEmpty()
                ? keyField.getText().toString().trim() : AssistantPrefs.apiKey(context);
        testStatus.setText(R.string.assistant_test_api_running);
        pingStatus.setText(R.string.assistant_test_api_running);
        new Thread(() -> {
            long start = System.currentTimeMillis();
            GeminiClient.Reply reply = new GeminiClient().listModels(key);
            long elapsed = System.currentTimeMillis() - start;
            String message;
            String ping;
            if (reply.isOk()) {
                message = context.getString(R.string.assistant_test_api_ok,
                        modelSummary(reply.body));
                ping = context.getString(R.string.assistant_api_ping, elapsed);
            } else {
                message = GeminiClient.describeError(reply.kind, reply.httpCode, reply.error);
                ping = context.getString(R.string.assistant_api_ping_fail);
            }
            main.post(() -> {
                testStatus.setText(message);
                pingStatus.setText(ping);
            });
        }, "assistant-api-test").start();
    }

    private void promptModelDownload(TextView statusView) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.assistant_model_title)
                .setMessage(context.getString(R.string.assistant_model_prompt,
                        WakeWordModelStore.APPROX_SIZE_MB))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    statusView.setText(R.string.assistant_model_downloading);
                    new Thread(() -> WakeWordModelStore.download(context,
                            new WakeWordModelStore.Progress() {
                                @Override
                                public void onProgress(long downloaded, long total) {
                                    int percent = total > 0
                                            ? (int) (downloaded * 100L / total) : -1;
                                    main.post(() -> statusView.setText(percent >= 0
                                            ? context.getString(
                                            R.string.assistant_model_progress, percent)
                                            : context.getString(
                                            R.string.assistant_model_downloading)));
                                }

                                @Override
                                public void onError(String message) {
                                    main.post(() -> {
                                        statusView.setText(message);
                                        updateModelStatus();
                                    });
                                }

                                @Override
                                public void onComplete() {
                                    main.post(() -> {
                                        updateModelStatus();
                                        VoiceAssistantManager manager = manager();
                                        if (manager != null) {
                                            manager.refreshSettings();
                                        }
                                        updateLiveMeta();
                                    });
                                }
                            }), "assistant-model-download").start();
                })
                .show();
    }

    private void updateModelStatus() {
        if (modelStatus == null) {
            return;
        }
        boolean installed = WakeWordModelStore.isInstalled(context);
        if (installed) {
            long mb = WakeWordModelStore.installedBytes(
                    WakeWordModelStore.modelDir(context)) / (1024L * 1024L);
            modelStatus.setText(context.getString(R.string.assistant_model_installed, mb));
            modelBadge.setText(context.getString(R.string.assistant_model_badge_ready, mb));
            modelBadge.setTextColor(UiStyles.color(context, R.color.ck_nominal));
        } else {
            modelStatus.setText(R.string.assistant_model_missing);
            modelBadge.setText(R.string.assistant_model_badge_missing);
            modelBadge.setTextColor(UiStyles.color(context, R.color.ck_caution));
        }
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(context)
                .setMessage(R.string.assistant_clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ConversationStore store = store();
                    if (store != null) {
                        store.clear();
                    }
                })
                .show();
    }

    private static String modelSummary(String body) {
        Map<String, Object> root = MiniJson.parseObject(body);
        if (root == null) {
            return "OK";
        }
        Object models = root.get("models");
        if (!(models instanceof List)) {
            return "OK";
        }
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (Object item : (List<?>) models) {
            if (!(item instanceof Map)) {
                continue;
            }
            Object name = ((Map<?, ?>) item).get("name");
            if (name instanceof String) {
                if (count > 0) {
                    b.append(", ");
                }
                String value = (String) name;
                b.append(value.startsWith("models/") ? value.substring(7) : value);
                if (++count >= 3) {
                    break;
                }
            }
        }
        return b.length() == 0 ? "OK" : b.toString();
    }

    // ---------------------------------------------------------------- helpers

    private VoiceAssistantManager manager() {
        if (!AssistantPrefs.isEnabled(context)) {
            return null;
        }
        return VoiceAssistantManager.get(context);
    }

    private ConversationStore store() {
        VoiceAssistantManager manager = VoiceAssistantManager.peek();
        return manager == null ? null : manager.store();
    }

    private LinearLayout settingRow(LinearLayout parent, int titleRes, int descRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(titleRes);
        CockpitTokens.bodyMd(title);
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        texts.addView(title, matchWrap(0));
        TextView desc = new TextView(context);
        desc.setText(descRes);
        CockpitTokens.bodySm(desc);
        desc.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        texts.addView(desc, matchWrap(2));
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, matchWrap(12));
        return row;
    }

    private void addSectionTitle(LinearLayout parent, int textRes) {
        TextView title = new TextView(context);
        title.setText(textRes);
        title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        parent.addView(title, matchWrap(18));
    }

    private void addLabel(LinearLayout parent, int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        CockpitTokens.labelCaps(label);
        label.setTextColor(UiStyles.color(context, R.color.ck_text_medium));
        parent.addView(label, matchWrap(12));
    }

    private void styleInput(EditText field) {
        field.setTextSize(15);
        field.setTextColor(UiStyles.color(context, R.color.ck_text_high));
        field.setHintTextColor(UiStyles.color(context, R.color.ck_text_medium));
        field.setPadding(dp(14), dp(8), dp(14), dp(8));
        field.setBackground(CockpitSurfaces.outlined(context,
                UiStyles.color(context, R.color.ck_bg_recessed),
                UiStyles.color(context, R.color.ck_stroke), 12f));
        field.setMinHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.setPadding(padding, padding, padding, padding);
        CockpitSurfaces.level1(card);
        return card;
    }

    private LinearLayout.LayoutParams matchWrap(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (topMarginDp > 0) {
            lp.topMargin = dp(topMarginDp);
        }
        return lp;
    }

    private int dp(int value) {
        return CockpitTokens.dp(context, value);
    }

    private void toast(int res) {
        Toast.makeText(context, res, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private static int blend(int color, float amount) {
        int alpha = Math.round(255 * amount);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
