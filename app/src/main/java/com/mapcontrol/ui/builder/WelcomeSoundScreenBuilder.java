package com.mapcontrol.ui.builder;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mapcontrol.R;
import com.mapcontrol.ui.cockpit.CockpitGrid;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.ui.welcome.WelcomeDesign;
import com.mapcontrol.ui.welcome.WelcomeDesignCatalog;

import java.util.List;
import java.util.function.Consumer;

/**
 * Açılış sesi formu — ana sekme içeriği için kaydırılabilir görünüm (geri çubuğu yok).
 */
public final class WelcomeSoundScreenBuilder {

    private static final int FORM_MAX_WIDTH_DP = 1120;

    private WelcomeSoundScreenBuilder() {
    }

    public static final class Screen {
        public final ScrollView scrollView;
        public final TextView usbStatus;
        public final LinearLayout usbList;
        public final TextView tvFilePath;
        public final LinearLayout rowSelect;
        public final LinearLayout playbackRow;
        public final TextView playbackLabel;
        public final Button btnPreview;
        public final EditText ttsInput;
        public final LinearLayout ttsGenerateRow;
        public final TextView ttsStatus;

        Screen(ScrollView scrollView, TextView usbStatus, LinearLayout usbList, TextView tvFilePath,
                LinearLayout rowSelect, LinearLayout playbackRow, TextView playbackLabel,
                Button btnPreview, EditText ttsInput, LinearLayout ttsGenerateRow, TextView ttsStatus) {
            this.scrollView = scrollView;
            this.usbStatus = usbStatus;
            this.usbList = usbList;
            this.tvFilePath = tvFilePath;
            this.rowSelect = rowSelect;
            this.playbackRow = playbackRow;
            this.playbackLabel = playbackLabel;
            this.btnPreview = btnPreview;
            this.ttsInput = ttsInput;
            this.ttsGenerateRow = ttsGenerateRow;
            this.ttsStatus = ttsStatus;
        }
    }

    /** Tasarım kartlarıyla aynı yüzey: yuvarlak köşe, ince çerçeve, tek satır yazı. */
    public static LinearLayout actionRow(Context context, CharSequence label) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(16, 14, 16, 14);
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(primary);
        row.addView(title);
        paintCard(context, row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        row.setLayoutParams(params);
        return row;
    }

    public static Screen buildTabScrollView(Context context, boolean autoPlayInitial,
            Consumer<Boolean> onAutoPlayCommitted, String selectedDesignId,
            Consumer<String> onDesignCommitted, String customLine,
            Consumer<String> onLineCommitted, String ttsText) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        int secondary = UiStyles.color(context, R.color.textSecondary);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        LinearLayout mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        int margin = UiStyles.dimenPx(context, R.dimen.oem_card_margin);
        mainContainer.setPadding(margin, margin, margin, margin);
        mainContainer.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));

        TextView titleText = new TextView(context);
        titleText.setText(R.string.welcome_sound_title);
        CockpitTokens.headlineMd(titleText);
        mainContainer.addView(titleText);

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.welcome_sound_subtitle);
        CockpitTokens.bodySm(subtitle);
        subtitle.setTextColor(UiStyles.color(context, R.color.textSecondary));
        subtitle.setPadding(0, 4, 0, 16);
        mainContainer.addView(subtitle);

        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 1000;
        CockpitGrid grid = null;
        if (wide) {
            grid = new CockpitGrid(context);
            mainContainer.addView(grid, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        // 1) TTS ile ses üretimi
        LinearLayout ttsCard = sectionCard(context);
        addSectionHeader(context, ttsCard,
                R.string.welcome_tts_title, R.string.welcome_tts_desc);
        EditText ttsInput = new EditText(context);
        ttsInput.setText(ttsText == null || ttsText.trim().isEmpty()
                ? context.getString(R.string.welcome_tts_default) : ttsText);
        ttsInput.setHint(R.string.welcome_tts_hint);
        ttsInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ttsInput.setTextColor(primary);
        ttsInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        ttsInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ttsInput.setMinLines(2);
        ttsInput.setMaxLines(4);
        ttsInput.setPadding(14, 12, 14, 12);
        ttsInput.setBackground(fieldBackground(context));
        LinearLayout.LayoutParams ttsInputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsInputLp.setMargins(0, 0, 0, 10);
        ttsCard.addView(ttsInput, ttsInputLp);

        TextView tagsLabel = new TextView(context);
        tagsLabel.setText(R.string.welcome_tts_tags_label);
        tagsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tagsLabel.setTextColor(secondary);
        tagsLabel.setPadding(0, 0, 0, 6);
        ttsCard.addView(tagsLabel);

        HorizontalScrollView tagScroll = new HorizontalScrollView(context);
        tagScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tagRow = new LinearLayout(context);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] tags = {
                "[happy]", "[excited]", "[laughs]", "[shouts]", "[whispers]",
                "[sighs]", "[curious]", "[sarcastic]", "[mischievously]",
                "[giggles]", "[clears throat]", "[singing]", "[crying]",
                "[angry]", "[sad]", "[surprised]"
        };
        int chipPadX = Math.round(10 * context.getResources().getDisplayMetrics().density);
        int chipPadY = Math.round(6 * context.getResources().getDisplayMetrics().density);
        for (String tag : tags) {
            TextView chip = new TextView(context);
            chip.setText(tag);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            chip.setTextColor(primary);
            chip.setPadding(chipPadX, chipPadY, chipPadX, chipPadY);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            chipBg.setCornerRadius(8 * context.getResources().getDisplayMetrics().density);
            chipBg.setStroke(1, UiStyles.color(context, R.color.outlineSubtle));
            chip.setBackground(chipBg);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> insertTag(ttsInput, tag));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.setMarginEnd(Math.round(6 * context.getResources().getDisplayMetrics().density));
            tagRow.addView(chip, chipLp);
        }
        tagScroll.addView(tagRow);
        ttsCard.addView(tagScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tagsHelp = new TextView(context);
        tagsHelp.setText(R.string.welcome_tts_tags_help);
        tagsHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tagsHelp.setTextColor(UiStyles.color(context, R.color.textHint));
        tagsHelp.setPadding(0, 6, 0, 10);
        ttsCard.addView(tagsHelp);

        LinearLayout ttsGenerateRow = actionButtonRow(context,
                context.getString(R.string.welcome_tts_generate), true);
        ttsGenerateRow.setMinimumWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        ttsGenerateRow.setMinimumHeight(CockpitTokens.dim(context, R.dimen.ck_touch_min));
        LinearLayout.LayoutParams ttsGenLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ttsCard.addView(ttsGenerateRow, ttsGenLp);
        TextView ttsStatus = new TextView(context);
        ttsStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ttsStatus.setTextColor(UiStyles.color(context, R.color.textHint));
        ttsStatus.setPadding(0, 8, 0, 0);
        ttsCard.addView(ttsStatus);
        addCard(context, mainContainer, grid, ttsCard);

        // 2) Ses kaynağı
        LinearLayout sourceCard = sectionCard(context);
        addSectionHeader(context, sourceCard,
                R.string.welcome_sound_source_title, R.string.welcome_sound_source_desc);

        TextView filePathLabel = new TextView(context);
        filePathLabel.setText(R.string.welcome_sound_selected_label);
        filePathLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        filePathLabel.setTextColor(secondary);
        filePathLabel.setPadding(0, 0, 0, 6);
        sourceCard.addView(filePathLabel);

        TextView tvFilePath = new TextView(context);
        tvFilePath.setText(R.string.welcome_sound_no_file_yet);
        tvFilePath.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvFilePath.setTextColor(primary);
        tvFilePath.setPadding(14, 12, 14, 12);
        tvFilePath.setBackground(fieldBackground(context));
        LinearLayout.LayoutParams filePathParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        filePathParams.setMargins(0, 0, 0, 10);
        sourceCard.addView(tvFilePath, filePathParams);

        LinearLayout fileActions = new LinearLayout(context);
        fileActions.setOrientation(LinearLayout.HORIZONTAL);
        fileActions.setBaselineAligned(false);
        LinearLayout rowSelect = actionButtonRow(context,
                context.getString(R.string.welcome_sound_choose_file), false);
        LinearLayout playbackRow = actionButtonRow(context,
                context.getString(R.string.welcome_sound_play), false);
        TextView playbackLabel = (TextView) playbackRow.getChildAt(0);
        playbackRow.setAlpha(0.45f);
        rowSelect.setMinimumWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        playbackRow.setMinimumWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        LinearLayout.LayoutParams selectLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        selectLp.setMarginEnd(8);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        playLp.setMarginStart(0);
        fileActions.addView(rowSelect, selectLp);
        fileActions.addView(playbackRow, playLp);
        sourceCard.addView(fileActions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView usbTitle = new TextView(context);
        usbTitle.setText(R.string.welcome_sound_usb_title);
        usbTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        usbTitle.setTextColor(primary);
        usbTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        usbTitle.setPadding(0, 16, 0, 4);
        sourceCard.addView(usbTitle);

        TextView usbStatus = new TextView(context);
        usbStatus.setText(R.string.welcome_sound_usb_absent);
        usbStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        usbStatus.setTextColor(UiStyles.color(context, R.color.textHint));
        usbStatus.setPadding(0, 0, 0, 6);
        sourceCard.addView(usbStatus);

        LinearLayout usbList = new LinearLayout(context);
        usbList.setOrientation(LinearLayout.VERTICAL);
        usbList.setVisibility(android.view.View.GONE);
        sourceCard.addView(usbList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView steps = new TextView(context);
        steps.setText(R.string.welcome_sound_steps);
        steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        steps.setTextColor(UiStyles.color(context, R.color.textHint));
        steps.setLineSpacing(0f, 1.2f);
        steps.setPadding(0, 8, 0, 0);
        sourceCard.addView(steps);
        addCard(context, mainContainer, grid, sourceCard);

        // 3) Açılış davranışı
        LinearLayout behaviorCard = sectionCard(context);
        addSectionHeader(context, behaviorCard,
                R.string.welcome_sound_autoplay_title, R.string.welcome_sound_autoplay_desc);
        LinearLayout autoPlayBlock = new LinearLayout(context);
        autoPlayBlock.setOrientation(LinearLayout.VERTICAL);
        UiStyles.addBinarySegmentedControl(context, autoPlayBlock,
                null,
                context.getString(R.string.welcome_sound_autoplay_on),
                context.getString(R.string.welcome_sound_autoplay_off),
                context.getString(R.string.welcome_sound_autoplay_on_help),
                context.getString(R.string.welcome_sound_autoplay_off_help),
                autoPlayInitial,
                onAutoPlayCommitted);
        behaviorCard.addView(autoPlayBlock);
        addCard(context, mainContainer, grid, behaviorCard);

        // 4) Giriş tasarımı
        LinearLayout designCard = sectionCard(context);
        Button btnPreview = addDesignPicker(context, designCard, selectedDesignId,
                onDesignCommitted, customLine, onLineCommitted);
        addCard(context, mainContainer, grid, designCard);

        FrameLayout formHost = new FrameLayout(context);
        formHost.setBackgroundColor(UiStyles.color(context, R.color.backgroundPage));
        FrameLayout.LayoutParams formLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        mainContainer.setLayoutParams(formLp);
        formHost.addView(mainContainer);
        scrollView.addView(formHost, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        return new Screen(scrollView, usbStatus, usbList, tvFilePath, rowSelect, playbackRow,
                playbackLabel, btnPreview, ttsInput, ttsGenerateRow, ttsStatus);
    }

    private static void insertTag(EditText input, String tag) {
        int start = Math.max(0, input.getSelectionStart());
        Editable editable = input.getText();
        String before = editable.toString();
        String prefix = start > 0 && !Character.isWhitespace(before.charAt(start - 1)) ? " " : "";
        String suffix = start < before.length() && !Character.isWhitespace(before.charAt(start)) ? " " : "";
        editable.insert(start, prefix + tag + suffix);
        input.setSelection(start + prefix.length() + tag.length() + suffix.length());
        input.requestFocus();
    }

    private static void addCard(Context context, LinearLayout main,
            CockpitGrid grid, View card) {
        if (grid != null) {
            grid.add(card, 4);
        } else {
            main.addView(card, cardParams(context));
        }
    }

    private static LinearLayout sectionCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(18 * context.getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);
        UiStyles.setGlassCardBackground(card);
        return card;
    }

    private static LinearLayout.LayoutParams cardParams(Context context) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(12 * context.getResources().getDisplayMetrics().density);
        return lp;
    }

    private static void addSectionHeader(Context context, LinearLayout card,
            int titleRes, int descRes) {
        TextView title = new TextView(context);
        title.setText(titleRes);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(UiStyles.color(context, R.color.textPrimary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView desc = new TextView(context);
        desc.setText(descRes);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        desc.setTextColor(UiStyles.color(context, R.color.textHint));
        desc.setPadding(0, 4, 0, 12);
        card.addView(desc);
    }

    private static LinearLayout actionButtonRow(Context context, String label, boolean primary) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(Math.round(48 * context.getResources().getDisplayMetrics().density));
        row.setPadding(16, 12, 16, 12);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context,
                primary ? R.color.buttonPrimary : R.color.surfaceCardInner));
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        if (!primary) {
            bg.setStroke(1, UiStyles.color(context, R.color.outline));
        }
        row.setBackground(bg);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(UiStyles.color(context, R.color.textPrimary));
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setGravity(Gravity.CENTER);
        row.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static GradientDrawable fieldBackground(Context context) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        bg.setStroke(1, UiStyles.color(context, R.color.outline));
        return bg;
    }

    private static void paintCard(Context context, LinearLayout row) {
        float radius = context.getResources().getDimension(R.dimen.oem_button_radius);
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
        bg.setCornerRadius(radius);
        bg.setStroke(Math.max(1, Math.round(density)),
                UiStyles.color(context, R.color.outlineSubtle));
        row.setBackground(bg);
    }

    private static Button addDesignPicker(Context context, LinearLayout parent,
            String selectedDesignId, Consumer<String> onDesignCommitted,
            String customLine, Consumer<String> onLineCommitted) {
        int primary = UiStyles.color(context, R.color.textPrimary);
        int secondary = UiStyles.color(context, R.color.textSecondary);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(R.string.welcome_design_section_title);
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sectionTitle.setTextColor(UiStyles.color(context, R.color.textPrimary87));
        sectionTitle.setPadding(0, 0, 0, 8);
        parent.addView(sectionTitle);

        TextView sectionDesc = new TextView(context);
        sectionDesc.setText(R.string.welcome_design_section_desc);
        sectionDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sectionDesc.setTextColor(UiStyles.color(context, R.color.textHint));
        sectionDesc.setPadding(0, 0, 0, 12);
        parent.addView(sectionDesc);

        List<WelcomeDesign> designs = WelcomeDesignCatalog.designs();
        LinearLayout[] rows = new LinearLayout[designs.size()];
        final String[] selected = {selectedDesignId};

        boolean twoColumns = context.getResources().getConfiguration().screenWidthDp >= 600;
        int cardGap = Math.round(10 * context.getResources().getDisplayMetrics().density);
        LinearLayout cardRow = null;
        for (int i = 0; i < designs.size(); i++) {
            WelcomeDesign design = designs.get(i);
            if (i % 2 == 0 || !twoColumns) {
                cardRow = new LinearLayout(context);
                cardRow.setOrientation(LinearLayout.HORIZONTAL);
                cardRow.setBaselineAligned(false);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = cardGap;
                parent.addView(cardRow, rowLp);
            }
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClickable(true);
            row.setFocusable(true);
            row.setPadding(16, 14, 16, 14);
            row.setMinimumHeight(Math.round(84 * context.getResources().getDisplayMetrics().density));

            TextView title = new TextView(context);
            title.setText(design.titleRes());
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTextColor(primary);
            row.addView(title);

            TextView help = new TextView(context);
            help.setText(design.helpRes());
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            help.setTextColor(secondary);
            help.setPadding(0, 4, 0, 0);
            row.addView(help);

            LinearLayout.LayoutParams cardLp;
            if (twoColumns) {
                cardLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i % 2 == 0) {
                    cardLp.setMarginEnd(cardGap / 2);
                } else {
                    cardLp.setMarginStart(cardGap / 2);
                }
            } else {
                cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
            }
            cardRow.addView(row, cardLp);
            rows[i] = row;

            row.setOnClickListener(v -> {
                if (design.id().equals(selected[0])) {
                    return;
                }
                selected[0] = design.id();
                applyDesignSelection(context, designs, rows, selected[0]);
                onDesignCommitted.accept(design.id());
            });
        }
        if (twoColumns && cardRow != null && designs.size() % 2 != 0) {
            cardRow.addView(emptySpacer(context, cardGap));
        }
        applyDesignSelection(context, designs, rows, selected[0]);

        TextView lineLabel = new TextView(context);
        lineLabel.setText(R.string.welcome_custom_text_label);
        lineLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        lineLabel.setTextColor(secondary);
        lineLabel.setPadding(0, 16, 0, 8);
        parent.addView(lineLabel);

        EditText lineInput = new EditText(context);
        lineInput.setHint(R.string.welcome_custom_text_hint);
        lineInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        lineInput.setSingleLine(true);
        lineInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        lineInput.setPadding(16, 16, 16, 16);
        lineInput.setTextColor(primary);
        lineInput.setHintTextColor(UiStyles.color(context, R.color.textHint));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(UiStyles.color(context, R.color.surfaceColor));
        inputBg.setCornerRadius(context.getResources().getDimension(R.dimen.oem_button_radius));
        inputBg.setStroke(1, UiStyles.color(context, R.color.outlineMuted));
        lineInput.setBackground(inputBg);
        if (customLine != null && !customLine.isEmpty()) {
            lineInput.setText(customLine);
            lineInput.setSelection(customLine.length());
        }
        lineInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onLineCommitted.accept(s == null ? "" : s.toString());
            }
        });
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lineParams.setMargins(0, 0, 0, 8);
        parent.addView(lineInput, lineParams);

        addTurkishCharacterRow(context, parent, lineInput);

        TextView lineHelp = new TextView(context);
        lineHelp.setText(R.string.welcome_custom_text_help);
        lineHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        lineHelp.setTextColor(UiStyles.color(context, R.color.textHint));
        lineHelp.setPadding(0, 0, 0, 8);
        parent.addView(lineHelp);

        Button btnPreview = new Button(context);
        btnPreview.setText(R.string.welcome_design_preview);
        btnPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnPreview.setTextColor(primary);
        UiStyles.styleOemButton(btnPreview, UiStyles.color(context, R.color.buttonPrimary));
        btnPreview.setPadding(24, 16, 24, 16);
        btnPreview.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnPreview.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        previewParams.setMargins(0, 8, 0, 0);
        parent.addView(btnPreview, previewParams);
        return btnPreview;
    }

    private static void addTurkishCharacterRow(Context context, LinearLayout parent, EditText input) {
        TextView label = new TextView(context);
        label.setText(R.string.welcome_turkish_chars_label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(UiStyles.color(context, R.color.textHint));
        label.setPadding(0, 2, 0, 6);
        parent.addView(label);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String chars = "çğıİöşüÇĞÖŞÜ";
        int size = Math.round(42 * context.getResources().getDisplayMetrics().density);
        for (int i = 0; i < chars.length(); i++) {
            final String ch = String.valueOf(chars.charAt(i));
            TextView chip = new TextView(context);
            chip.setText(ch);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            chip.setTextColor(UiStyles.color(context, R.color.textPrimary));
            chip.setGravity(Gravity.CENTER);
            chip.setClickable(true);
            chip.setFocusable(true);
            UiStyles.applySolidRoundedBackgroundDp(chip,
                    UiStyles.color(context, R.color.surfaceCardInner), 8f);
            chip.setOnClickListener(v -> {
                int start = Math.max(0, input.getSelectionStart());
                input.getText().insert(start, ch);
                input.setSelection(start + ch.length());
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Math.max(size, (int) (34 * context.getResources().getDisplayMetrics().density)),
                    size);
            lp.setMarginEnd(6);
            row.addView(chip, lp);
        }
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollLp.bottomMargin = 8;
        parent.addView(scroll, scrollLp);
    }

    private static View emptySpacer(Context context, int gap) {
        View spacer = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(gap / 2);
        spacer.setLayoutParams(lp);
        return spacer;
    }

    private static void applyDesignSelection(Context context, List<WelcomeDesign> designs,
            LinearLayout[] rows, String selectedId) {
        float radius = context.getResources().getDimension(R.dimen.oem_button_radius);
        float density = context.getResources().getDisplayMetrics().density;
        for (int i = 0; i < designs.size(); i++) {
            boolean selected = designs.get(i).id().equals(selectedId);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UiStyles.color(context, R.color.surfaceCardInner));
            bg.setCornerRadius(radius);
            bg.setStroke(Math.max(1, Math.round((selected ? 2f : 1f) * density)),
                    UiStyles.color(context, selected ? R.color.oemAccent : R.color.outline));
            rows[i].setBackground(bg);
            TextView title = (TextView) rows[i].getChildAt(0);
            title.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
            title.setTextColor(UiStyles.color(context,
                    selected ? R.color.textPrimary : R.color.textSecondary));
        }
    }
}
