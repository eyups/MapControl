package com.mapcontrol.ui.builder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.mapcontrol.R;
import com.mapcontrol.profile.DriverProfileApplier;
import com.mapcontrol.profile.DriverProfileManager;
import com.mapcontrol.profile.ProfileAvatars;
import com.mapcontrol.ui.cockpit.CockpitChip;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggleRow;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.StatusBadge;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.IdScanner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** "Sürücü profilleri" sekmesi: profil listesi, koltuk/ayna hafıza slotu ve ek hafıza maddeleri. */
public class DriverProfilesTabBuilder {

    private final Context context;
    private final Consumer<String> log;
    private final Runnable onProfileChanged;
    private final DriverProfileManager manager;
    private final ScrollView scrollView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DriverProfilesTabBuilder(Context context, Consumer<String> log, Runnable onProfileChanged) {
        this.context = context;
        this.log = log;
        this.onProfileChanged = onProfileChanged;
        this.manager = DriverProfileManager.get(context);
        this.scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        scrollView.setFillViewport(true);
        rebuild();
    }

    public ScrollView getScrollView() {
        return scrollView;
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }


    public void rebuild() {
        scrollView.removeAllViews();

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        int margin = CockpitTokens.dim(context, R.dimen.ck_margin);
        outer.setPadding(margin, CockpitTokens.dim(context, R.dimen.ck_space_sm),
                margin, margin);

        DriverProfileManager.Profile active = manager.getActive();
        if (context.getResources().getConfiguration().screenWidthDp >= 1000) {
            LinearLayout columns = new LinearLayout(context);
            columns.setOrientation(LinearLayout.HORIZONTAL);
            columns.setBaselineAligned(false);

            LinearLayout left = createCard();
            addProfileList(left, active);
            LinearLayout right = createCard();
            addProfileDetail(right, active);

            int gutter = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f);
            leftLp.setMarginEnd(gutter / 2);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.64f);
            rightLp.setMarginStart(gutter / 2);
            columns.addView(left, leftLp);
            columns.addView(right, rightLp);
            outer.addView(columns, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout listCard = createCard();
            addProfileList(listCard, active);
            outer.addView(listCard, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout detailCard = createCard();
            addProfileDetail(detailCard, active);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            detailLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_medium);
            outer.addView(detailCard, detailLp);
        }

        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int inner = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(inner, inner, inner, inner);
        CockpitSurfaces.level1(card);
        return card;
    }

    private void addProfileList(LinearLayout card, DriverProfileManager.Profile active) {
        addText(card, context.getString(R.string.profiles_title), 18, R.color.textPrimary, true, 0, 4);
        addText(card, context.getString(R.string.profiles_desc_short), 13, R.color.textHint, false, 0, 12);

        for (DriverProfileManager.Profile profile : manager.getProfiles()) {
            addProfileRow(card, profile, profile == active);
        }

        Button btnNew = button(R.string.profiles_new, R.color.buttonPrimary);
        btnNew.setOnClickListener(v -> showNewProfileDialog());
        btnNew.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        btnNew.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.addView(btnNew, lp);
    }

    private void addProfileDetail(LinearLayout card, DriverProfileManager.Profile active) {
        if (active == null) {
            addText(card, context.getString(R.string.profiles_title), 18, R.color.textPrimary, true, 0, 8);
            return;
        }

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        FrameLayout avatar = createAvatar(active.avatar, 56, false);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                Math.round(56 * density), Math.round(56 * density));
        avatarLp.setMarginEnd(Math.round(14 * density));
        header.addView(avatar, avatarLp);

        LinearLayout headerText = new LinearLayout(context);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(active.name);
        CockpitTokens.headlineSm(name);
        name.setTextColor(UiStyles.color(context, R.color.textPrimary));
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        headerText.addView(name);
        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.profiles_detail_subtitle);
        CockpitTokens.bodySm(subtitle);
        subtitle.setTextColor(UiStyles.color(context, R.color.textHint));
        subtitle.setPadding(0, Math.round(2 * density), 0, 0);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button rename = button(R.string.profiles_edit, R.color.buttonSecondaryMuted);
        rename.setOnClickListener(v -> showEditProfileDialog(active));
        Button delete = button(R.string.profiles_delete, R.color.buttonDestructiveBg);
        delete.setOnClickListener(v -> confirmDelete(active));
        rename.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        rename.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        delete.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        delete.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        renameLp.setMarginEnd(CockpitTokens.dim(context, R.dimen.ck_space_sm));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actions.addView(rename, renameLp);
        actions.addView(delete, deleteLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        card.addView(actions, actionsLp);

        addSeatSection(card, active);
        addAdvancedItems(card, active);
    }

    private void addProfileRow(LinearLayout parent, DriverProfileManager.Profile profile, boolean isActive) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        row.setBackground(CockpitSurfaces.outlined(context,
                CockpitTokens.color(context, isActive ? R.color.ck_surface_2 : R.color.ck_surface_1),
                CockpitTokens.color(context, isActive ? R.color.ck_accent : R.color.ck_stroke), 12f));

        FrameLayout avatar = createAvatar(profile.avatar, 40, false);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                Math.round(40 * density), Math.round(40 * density));
        avatarLp.setMarginEnd(Math.round(12 * density));
        row.addView(avatar, avatarLp);

        TextView name = new TextView(context);
        name.setText(profile.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        name.setTypeface(com.mapcontrol.ui.cockpit.CockpitTokens.inter(context,
                com.mapcontrol.ui.cockpit.CockpitTokens.WEIGHT_MEDIUM));
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (isActive) {
            row.addView(StatusBadge.nominal(context,
                    context.getString(R.string.profiles_active_badge)));
        }

        row.setOnClickListener(v -> {
            if (isActive) {
                return;
            }
            manager.activate(profile.id);
            log.accept("[Profil] Aktif profil: " + profile.name);
            handler.post(onProfileChanged);
        });
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, Math.round(4 * density), 0, Math.round(4 * density));
        parent.addView(row, rowLp);
    }

    private void addSeatSection(LinearLayout parent, DriverProfileManager.Profile profile) {
        addText(parent, context.getString(R.string.profiles_seat_title), 16, R.color.textPrimary, true, 16, 4);
        addText(parent, context.getString(R.string.profiles_seat_desc_short), 13, R.color.textHint, false, 0, 8);

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (int slot = 0; slot <= ComfortCommands.SEAT_MEMORY_SLOTS; slot++) {
            final int value = slot;
            String label = slot == 0 ? context.getString(R.string.profiles_slot_none)
                    : context.getString(R.string.profiles_slot_n, slot);
            CockpitChip chip = new CockpitChip(context, label);
            chip.setSelectedState(profile.seatSlot == slot);
            chip.setOnClickListener(v -> {
                manager.setSeatSlot(profile.id, value);
                rebuild();
            });
            chips.addView(chip, wrap(0, 0, 8, 0));
        }
        parent.addView(chips, wrap(0, 0, 0, 8));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button(R.string.profiles_seat_save, R.color.buttonSecondaryMuted);
        save.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        save.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        save.setOnClickListener(v -> confirmSaveSeat(profile));
        actions.addView(save, wrap(0, 0, 8, 0));
        Button applyNow = button(R.string.profiles_seat_apply_now, R.color.buttonPrimary);
        applyNow.setMinWidth(CockpitTokens.dim(context, R.dimen.ck_action_min_w));
        applyNow.setMaxWidth(CockpitTokens.dim(context, R.dimen.ck_action_max_w));
        applyNow.setOnClickListener(v -> executor.execute(() ->
                DriverProfileApplier.applyNow(context, log)));
        actions.addView(applyNow, wrap(0, 0, 8, 0));
        parent.addView(actions, wrap(0, 0, 0, 8));

        CockpitToggleRow applyOnStart = new CockpitToggleRow(context,
                R.drawable.ic_mdi_car, context.getString(R.string.profiles_apply_on_start_title),
                context.getString(profile.applyOnStart
                        ? R.string.profiles_apply_on_start_on
                        : R.string.profiles_apply_on_start_off), profile.applyOnStart);
        applyOnStart.getToggle().setOnCheckedChangeListener((toggle, on) -> {
            manager.setApplyOnStart(profile.id, on);
            applyOnStart.subtitle(context.getString(on
                    ? R.string.profiles_apply_on_start_on
                    : R.string.profiles_apply_on_start_off));
            log.accept("[Profil] " + profile.name + " kontakta uygula: " + (on ? "Açık" : "Kapalı"));
        });
        parent.addView(applyOnStart, wrap(0, 8, 0, 8));
    }

    private void addAdvancedItems(LinearLayout parent, DriverProfileManager.Profile profile) {
        LinearLayout itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setVisibility(android.view.View.GONE);

        LinearLayout toggle = new LinearLayout(context);
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_md),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        CockpitSurfaces.level2(toggle);

        TextView label = new TextView(context);
        label.setText(context.getString(R.string.profiles_advanced_items, profile.items.size()));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        toggle.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mark = new TextView(context);
        mark.setText("+");
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mark.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        mark.setGravity(Gravity.CENTER);
        toggle.addView(mark, new LinearLayout.LayoutParams(
                Math.round(28 * context.getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final boolean[] expanded = {false};
        toggle.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            if (expanded[0] && itemsContainer.getChildCount() == 0) {
                addItemsContent(itemsContainer, profile);
            }
            itemsContainer.setVisibility(expanded[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            mark.setText(expanded[0] ? "−" : "+");
        });

        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleLp.topMargin = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        toggleLp.bottomMargin = UiStyles.dimenPx(context, R.dimen.spacing_small);
        parent.addView(toggle, toggleLp);
        parent.addView(itemsContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addItemsContent(LinearLayout parent, DriverProfileManager.Profile profile) {
        if (profile.items.isEmpty()) {
            addText(parent, context.getString(R.string.profiles_items_empty), 13, R.color.textHint, false, 0, 8);
            return;
        }
        for (DriverProfileManager.MemoryItem item : profile.items) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            CheckBox enabled = new CheckBox(context);
            enabled.setText(item.label + " (" + IdScanner.moduleLabel(item.module) + " " + item.id
                    + ") = " + item.value);
            enabled.setTextColor(UiStyles.color(context, R.color.textPrimary));
            enabled.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            enabled.setChecked(item.enabled);
            enabled.setOnCheckedChangeListener((b, checked) -> {
                item.enabled = checked;
                manager.persist();
            });
            row.addView(enabled, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = button(R.string.profiles_item_remove, R.color.buttonDestructiveBg);
            remove.setOnClickListener(v -> {
                manager.removeItem(profile.id, item);
                rebuild();
            });
            row.addView(remove);
            parent.addView(row, wrap(0, 2, 0, 2));
        }
        Button refresh = button(R.string.profiles_items_refresh, R.color.buttonSecondaryMuted);
        refresh.setOnClickListener(v -> executor.execute(() -> {
            if (!ComfortCommands.isConnected()) {
                handler.post(() -> toast(R.string.discovery_not_connected));
                return;
            }
            for (DriverProfileManager.MemoryItem item : profile.items) {
                int current = ComfortCommands.readRaw(item.module, item.id);
                if (current != ComfortCommands.INVALID_TEMP) {
                    item.value = current;
                }
            }
            manager.persist();
            handler.post(this::rebuild);
        }));
        parent.addView(refresh, wrap(0, 8, 0, 8));
    }

    private void confirmSaveSeat(DriverProfileManager.Profile profile) {
        if (profile.seatSlot < 1) {
            toast(R.string.profiles_pick_slot_first);
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.profiles_seat_save)
                .setMessage(context.getString(R.string.profiles_seat_save_confirm, profile.seatSlot))
                .setPositiveButton(R.string.profiles_ok, (d, w) -> executor.execute(() -> saveSeat(profile)))
                .setNegativeButton(R.string.profiles_cancel, null)
                .show();
    }

    private void saveSeat(DriverProfileManager.Profile profile) {
        if (!ComfortCommands.isConnected()) {
            handler.post(() -> toast(R.string.discovery_not_connected));
            return;
        }
        if (ComfortCommands.readSpeedKmh() != 0 || !ComfortCommands.isSeatMemoryAvailable()) {
            handler.post(() -> toast(R.string.profiles_seat_unavailable));
            return;
        }
        ComfortCommands.saveSeatMemory(profile.seatSlot);
        log.accept("[Profil] " + profile.name + ": koltuk/ayna konumu slot " + profile.seatSlot + "'e kaydedildi");
        handler.post(() -> toast(R.string.profiles_saved_toast));
    }

    private void showNewProfileDialog() {
        EditText input = nameInput("");
        int[] selectedAvatar = {0};
        LinearLayout content = buildProfileDialogContent(input, selectedAvatar);
        new AlertDialog.Builder(context)
                .setTitle(R.string.profiles_new)
                .setView(content)
                .setPositiveButton(R.string.profiles_copy_current,
                        (d, w) -> createProfile(input, true, selectedAvatar[0]))
                .setNeutralButton(R.string.profiles_start_empty,
                        (d, w) -> createProfile(input, false, selectedAvatar[0]))
                .setNegativeButton(R.string.profiles_cancel, null)
                .show();
    }

    private void createProfile(EditText input, boolean copy, int avatar) {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            toast(R.string.profiles_name_required);
            return;
        }
        manager.create(name, copy, avatar);
        log.accept("[Profil] Oluşturuldu: " + name);
        rebuild();
    }

    private void showEditProfileDialog(DriverProfileManager.Profile profile) {
        EditText input = nameInput(profile.name);
        int[] selectedAvatar = {profile.avatar};
        LinearLayout content = buildProfileDialogContent(input, selectedAvatar);
        new AlertDialog.Builder(context)
                .setTitle(R.string.profiles_edit)
                .setView(content)
                .setPositiveButton(R.string.profiles_ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast(R.string.profiles_name_required);
                        return;
                    }
                    manager.update(profile.id, name, selectedAvatar[0]);
                    rebuild();
                })
                .setNegativeButton(R.string.profiles_cancel, null)
                .show();
    }

    private LinearLayout buildProfileDialogContent(EditText input, int[] selectedAvatar) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = UiStyles.dimenPx(context, R.dimen.spacing_medium);
        content.setPadding(pad, pad / 2, pad, 0);
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText(R.string.profiles_avatar_title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(CockpitTokens.color(context, R.color.ck_text_high));
        label.setPadding(0, pad, 0, UiStyles.dimenPx(context, R.dimen.spacing_small));
        content.addView(label);
        content.addView(createAvatarPicker(selectedAvatar));
        return content;
    }

    private LinearLayout createAvatarPicker(int[] selectedAvatar) {
        LinearLayout picker = new LinearLayout(context);
        picker.setOrientation(LinearLayout.VERTICAL);
        int size = 52;
        int gap = 8;
        int gapPx = UiStyles.dimenPx(context, R.dimen.spacing_small);
        int sizePx = Math.round(size * context.getResources().getDisplayMetrics().density);
        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                FrameLayout avatar = createAvatar(index, size, index == selectedAvatar[0]);
                avatar.setOnClickListener(v -> {
                    selectedAvatar[0] = index;
                    refreshAvatarPicker(picker, index);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
                lp.setMargins(0, 0, col == 4 ? 0 : gapPx, gapPx);
                rowLayout.addView(avatar, lp);
            }
            picker.addView(rowLayout);
        }
        return picker;
    }

    private void refreshAvatarPicker(LinearLayout picker, int selected) {
        for (int i = 0; i < picker.getChildCount(); i++) {
            View row = picker.getChildAt(i);
            if (!(row instanceof LinearLayout)) {
                continue;
            }
            LinearLayout rowLayout = (LinearLayout) row;
            for (int j = 0; j < rowLayout.getChildCount(); j++) {
                View child = rowLayout.getChildAt(j);
                if (child instanceof FrameLayout) {
                    Object tag = child.getTag();
                    if (tag instanceof Integer) {
                        int index = (Integer) tag;
                        applyAvatarBackground((FrameLayout) child, index, index == selected);
                    }
                }
            }
        }
    }

    private FrameLayout createAvatar(int avatar, int sizeDp, boolean selected) {
        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = Math.round(sizeDp * density);
        FrameLayout frame = new FrameLayout(context);
        frame.setTag(avatar);
        applyAvatarBackground(frame, avatar, selected);

        ImageView icon = new ImageView(context);
        icon.setImageResource(ProfileAvatars.iconRes(avatar));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(UiStyles.color(context, R.color.textOnGold),
                android.graphics.PorterDuff.Mode.SRC_IN);
        int iconPx = Math.round(sizePx * 0.55f);
        frame.addView(icon, new FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER));

        ImageView check = new ImageView(context);
        check.setImageResource(R.drawable.ic_mdi_check);
        check.setScaleType(ImageView.ScaleType.FIT_CENTER);
        check.setColorFilter(CockpitTokens.color(context, R.color.ck_accent),
                android.graphics.PorterDuff.Mode.SRC_IN);
        int checkPx = Math.round(sizePx * 0.36f);
        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.OVAL);
        checkBg.setColor(UiStyles.color(context, R.color.textOnGold));
        check.setBackground(checkBg);
        int checkPad = Math.max(1, Math.round(2 * density));
        check.setPadding(checkPad, checkPad, checkPad, checkPad);
        FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(
                checkPx, checkPx, Gravity.END | Gravity.BOTTOM);
        check.setVisibility(selected ? View.VISIBLE : View.GONE);
        frame.addView(check, checkLp);
        return frame;
    }

    private void applyAvatarBackground(FrameLayout frame, int avatar, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(UiStyles.color(context, ProfileAvatars.colorRes(avatar)));
        if (selected) {
            float density = context.getResources().getDisplayMetrics().density;
            background.setStroke(Math.max(2, Math.round(3 * density)),
                    UiStyles.color(context, R.color.textPrimary));
        }
        frame.setBackground(background);
        if (frame.getChildCount() > 1) {
            frame.getChildAt(1).setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    private void confirmDelete(DriverProfileManager.Profile profile) {
        if (manager.getProfiles().size() <= 1) {
            toast(R.string.profiles_delete_last);
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.profiles_delete)
                .setMessage(context.getString(R.string.profiles_delete_confirm, profile.name))
                .setPositiveButton(R.string.profiles_delete, (d, w) -> {
                    boolean wasActive = profile.id.equals(manager.getActiveId());
                    manager.delete(profile.id);
                    log.accept("[Profil] Silindi: " + profile.name);
                    if (wasActive) {
                        handler.post(onProfileChanged);
                    } else {
                        rebuild();
                    }
                })
                .setNegativeButton(R.string.profiles_cancel, null)
                .show();
    }

    private EditText nameInput(String initial) {
        EditText input = new EditText(context);
        input.setHint(R.string.profiles_name_hint);
        input.setText(initial);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        return input;
    }

    private void toast(int resId) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show();
    }

    private Button button(int textRes, int colorRes) {
        Button b = new Button(context);
        b.setText(textRes);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTextColor(UiStyles.color(context, R.color.textPrimary));
        UiStyles.styleOemButton(b, UiStyles.color(context, colorRes));
        b.setPadding(16, 10, 16, 10);
        return b;
    }

    private static LinearLayout.LayoutParams wrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void addText(LinearLayout parent, String text, int sp, int colorRes, boolean bold,
            int topPad, int bottomPad) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(UiStyles.color(context, colorRes));
        if (bold) {
            view.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        view.setPadding(16, topPad, 16, bottomPad);
        parent.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
