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
import com.mapcontrol.tts.ElevenLabsTts;
import com.mapcontrol.tts.MapControlSpeech;
import com.mapcontrol.vehicle.FaceProfileStore;
import android.content.Intent;
import com.mapcontrol.ui.activity.DmsFaceProfileActivity;
import com.mapcontrol.ui.cockpit.CockpitCard;
import com.mapcontrol.ui.cockpit.CockpitButton;
import com.mapcontrol.ui.cockpit.CockpitChip;
import com.mapcontrol.ui.cockpit.CockpitLegacyStyler;
import com.mapcontrol.ui.cockpit.CockpitSurfaces;
import com.mapcontrol.ui.cockpit.CockpitToggleRow;
import com.mapcontrol.ui.cockpit.CockpitTokens;
import com.mapcontrol.ui.cockpit.StatusBadge;
import com.mapcontrol.ui.theme.UiStyles;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.IdScanner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.ConcurrentHashMap;
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
    private final java.util.Set<String> welcomePending = ConcurrentHashMap.newKeySet();
    private final java.util.Map<String, String> welcomeErrors = new ConcurrentHashMap<>();
    private boolean tabVisible;

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

    public void onTabVisible() {
        tabVisible = true;
        rebuild();
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
        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 1000;
        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        cards.setBaselineAligned(false);

        java.util.List<DriverProfileManager.Profile> profiles = manager.getProfiles();
        int shown = Math.min(profiles.size(), 3);
        for (int i = 0; i < shown; i++) {
            DriverProfileManager.Profile profile = profiles.get(i);
            CockpitCard card = buildProfileCard(profile, profile == active, i);
            if (wide) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (i > 0) lp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_gutter);
                cards.addView(card, lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_gutter);
                cards.addView(card, lp);
            }
        }

        if (profiles.size() < 3) {
        CockpitCard newCard = buildNewProfileCard();
        if (wide) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_gutter);
            cards.addView(newCard, lp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_gutter);
            cards.addView(newCard, lp);
        }
        }
        outer.addView(cards, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // Yeniden oluşturulan TextView'lar ilk açılıştaki Cockpit tipografisini korusun.
        CockpitLegacyStyler.apply(scrollView);
        if (tabVisible) {
            ensureWelcomeMessages(profiles);
        }
    }

    private CockpitCard buildProfileCard(DriverProfileManager.Profile profile,
            boolean active, int index) {
        CockpitCard card = new CockpitCard(context);
        if (active) {
            card.setBackground(CockpitSurfaces.outlined(context,
                    CockpitTokens.color(context, R.color.ck_surface_2),
                    CockpitTokens.color(context, R.color.ck_accent), 16f));
        }
        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        frame(card, profile, header, active, index);
        card.addContent(header);

        addInfoRow(card, R.drawable.ic_mdi_account,
                context.getString(R.string.profiles_face_label), faceStatus(profile));
        addInfoRow(card, R.drawable.ic_mdi_car_seat,
                context.getString(R.string.profiles_seat_label),
                profile.seatSlot > 0
                        ? context.getString(R.string.profiles_slot_n, profile.seatSlot)
                        : context.getString(R.string.profiles_slot_none));
        addInfoRow(card, R.drawable.ic_mdi_car,
                context.getString(R.string.profiles_apply_on_start_title),
                context.getString(profile.applyOnStart
                        ? R.string.profiles_apply_on_start_short_on
                        : R.string.profiles_apply_on_start_short_off));
        addWelcomeMessageRow(card, profile);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (active) {
            CockpitButton apply = CockpitButton.primary(context,
                    context.getString(R.string.profiles_sync_apply_short));
            apply.setOnClickListener(v -> executor.execute(() -> {
                DriverProfileApplier.applyNow(context, log);
                handler.post(onProfileChanged);
            }));
            actions.addView(apply, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            CockpitButton activate = CockpitButton.secondary(context,
                    context.getString(R.string.profiles_activate_short));
            activate.setOnClickListener(v -> {
                manager.activate(profile.id);
                log.accept("[Profil] Aktif profil: " + profile.name);
                handler.post(onProfileChanged);
            });
            actions.addView(activate, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        CockpitButton edit = CockpitButton.secondary(context,
                context.getString(R.string.profiles_edit));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        edit.setOnClickListener(v -> showEditProfileDialog(profile));
        actions.addView(edit, editLp);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        card.addContent(actions, actionsLp);

        CockpitButton face = CockpitButton.secondary(context,
                context.getString(profileHasFace(profile)
                        ? R.string.profiles_face_update
                        : R.string.profiles_face_add));
        face.setOnClickListener(v -> openFaceEnrollment(profile));
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        faceLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(face, faceLp);

        TextView advanced = new TextView(context);
        advanced.setText(R.string.profiles_memory_settings_link);
        CockpitTokens.bodySm(advanced);
        advanced.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        advanced.setGravity(Gravity.CENTER);
        advanced.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_sm), 0, 0);
        advanced.setOnClickListener(v -> showProfileSettings(profile));
        card.addContent(advanced);
        return card;
    }

    private void frame(CockpitCard card, DriverProfileManager.Profile profile,
            LinearLayout header, boolean active, int index) {
        float density = context.getResources().getDisplayMetrics().density;
        FrameLayout avatar = createAvatar(profile.avatar, 64, false);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(
                64 * Math.round(density), 64 * Math.round(density));
        avatarLp.setMarginEnd(Math.round(14 * density));
        header.addView(avatar, avatarLp);

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(profile.name);
        CockpitTokens.headlineSm(name);
        text.addView(name);
        TextView role = new TextView(context);
        role.setText(index == 0
                ? context.getString(R.string.profiles_role_primary)
                : context.getString(R.string.profiles_role_secondary));
        CockpitTokens.bodySm(role);
        role.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        text.addView(role);
        header.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View badge = active
                ? StatusBadge.nominal(context, context.getString(R.string.profiles_active_badge))
                : StatusBadge.neutral(context, context.getString(R.string.profiles_ready_badge));
        header.addView(badge);
    }

    private void addInfoRow(CockpitCard card, int iconRes, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        CockpitSurfaces.level1(row);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = new TextView(context);
        text.setText(label);
        CockpitTokens.bodySm(text);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        row.addView(text, textLp);
        TextView valueView = new TextView(context);
        valueView.setText(value);
        CockpitTokens.bodySm(valueView);
        valueView.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(valueView);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(row, rowLp);
    }

    private void addWelcomeMessageRow(CockpitCard card, DriverProfileManager.Profile profile) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        CockpitSurfaces.level1(row);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_mdi_account);
        icon.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView label = new TextView(context);
        label.setText(R.string.profiles_welcome_tts_label);
        CockpitTokens.bodySm(label);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        row.addView(label, labelLp);

        TextView status = new TextView(context);
        status.setText(welcomeStatus(profile));
        CockpitTokens.bodySm(status);
        status.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(status);

        ImageView refresh = new ImageView(context);
        refresh.setImageResource(R.drawable.ic_mdi_refresh);
        refresh.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        refresh.setContentDescription(context.getString(R.string.profiles_welcome_tts_refresh));
        refresh.setPadding(dp(4), dp(4), dp(4), dp(4));
        refresh.setClickable(true);
        refresh.setFocusable(true);
        refresh.setOnClickListener(v -> generateWelcome(profile, true));
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        refreshLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        row.addView(refresh, refreshLp);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(row, rowLp);
    }

    private void ensureWelcomeMessages(java.util.List<DriverProfileManager.Profile> profiles) {
        for (DriverProfileManager.Profile profile : profiles) {
            if (welcomePending.contains(profile.id)
                    || welcomeErrors.containsKey(profile.id)
                    || isWelcomeCached(profile)) {
                continue;
            }
            if (MapControlSpeech.apiKey(context).trim().isEmpty()
                    || !MapControlSpeech.isOnline(context)) {
                continue;
            }
            generateWelcome(profile, false);
        }
    }

    private boolean isWelcomeCached(DriverProfileManager.Profile profile) {
        if (profile.welcomeAudioPath == null || profile.welcomeAudioPath.trim().isEmpty()) {
            return false;
        }
        if (!expectedWelcome(profile).equals(profile.welcomeMessage)) {
            return false;
        }
        File file = new File(profile.welcomeAudioPath);
        return file.isFile() && file.length() > 0;
    }

    private String expectedWelcome(DriverProfileManager.Profile profile) {
        return context.getString(R.string.dms_face_welcome_tts, profile.name);
    }

    private String welcomeStatus(DriverProfileManager.Profile profile) {
        if (welcomePending.contains(profile.id)) {
            return context.getString(R.string.profiles_welcome_tts_generating);
        }
        if (welcomeErrors.containsKey(profile.id)) {
            return context.getString(R.string.profiles_welcome_tts_error);
        }
        if (isWelcomeCached(profile)) {
            return context.getString(R.string.profiles_welcome_tts_ready);
        }
        if (MapControlSpeech.apiKey(context).trim().isEmpty()) {
            return context.getString(R.string.profiles_welcome_tts_no_key);
        }
        if (!MapControlSpeech.isOnline(context)) {
            return context.getString(R.string.profiles_welcome_tts_no_network);
        }
        return context.getString(R.string.profiles_welcome_tts_missing);
    }

    private void generateWelcome(DriverProfileManager.Profile profile, boolean force) {
        if (profile == null || welcomePending.contains(profile.id)) {
            return;
        }
        if (!force && isWelcomeCached(profile)) {
            return;
        }
        String key = MapControlSpeech.apiKey(context).trim();
        if (key.isEmpty()) {
            return;
        }
        if (!MapControlSpeech.isOnline(context)) {
            return;
        }
        welcomeErrors.remove(profile.id);
        welcomePending.add(profile.id);
        handler.post(this::rebuild);

        String text = expectedWelcome(profile);
        String voice = MapControlSpeech.voiceId(context);
        File dir = new File(context.getFilesDir(), "profile_welcome");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            welcomePending.remove(profile.id);
            welcomeErrors.put(profile.id, "mkdir");
            handler.post(this::rebuild);
            return;
        }
        File temp = new File(dir, "profile-" + profile.id + ".part.mp3");
        File finalFile = new File(dir, "profile-" + profile.id + ".mp3");
        temp.delete();
        ElevenLabsTts.synthesizeToFile(context, text, key, voice, temp,
                new ElevenLabsTts.FileCallback() {
                    @Override
                    public void onSuccess(File file) {
                        boolean saved = replaceFile(file, finalFile);
                        welcomePending.remove(profile.id);
                        if (saved) {
                            manager.setWelcomeAudio(profile.id, text, finalFile.getAbsolutePath());
                            welcomeErrors.remove(profile.id);
                        } else {
                            file.delete();
                            welcomeErrors.put(profile.id, "write");
                        }
                        handler.post(DriverProfilesTabBuilder.this::rebuild);
                    }

                    @Override
                    public void onFailure(String reason) {
                        temp.delete();
                        welcomePending.remove(profile.id);
                        welcomeErrors.put(profile.id, reason == null ? "error" : reason);
                        handler.post(DriverProfilesTabBuilder.this::rebuild);
                    }
                });
    }

    private boolean replaceFile(File source, File target) {
        if (source == null || !source.isFile() || source.length() <= 0) {
            return false;
        }
        if (target.exists() && !target.delete()) {
            return false;
        }
        if (source.renameTo(target)) {
            return true;
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
            source.delete();
            return target.isFile() && target.length() > 0;
        } catch (Exception ignored) {
            target.delete();
            return false;
        }
    }

    private CockpitCard buildNewProfileCard() {
        CockpitCard card = new CockpitCard(context);
        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout icon = new FrameLayout(context);
        icon.setBackground(CockpitSurfaces.outlined(context,
                CockpitTokens.color(context, R.color.ck_surface_2),
                CockpitTokens.color(context, R.color.ck_stroke), 16f));
        ImageView plus = new ImageView(context);
        plus.setImageResource(R.drawable.ic_mdi_account);
        plus.setColorFilter(CockpitTokens.color(context, R.color.ck_accent));
        icon.addView(plus, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
        header.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView label = new TextView(context);
        label.setText(context.getString(R.string.profiles_new_card_title));
        CockpitTokens.title(label);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        header.addView(label, labelLp);
        header.addView(StatusBadge.neutral(context,
                context.getString(R.string.profiles_new_badge)));
        card.addContent(header);

        TextView desc = new TextView(context);
        desc.setText(context.getString(R.string.profiles_new_card_desc));
        CockpitTokens.bodySm(desc);
        desc.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.addContent(desc, descLp);

        CockpitButton create = CockpitButton.primary(context,
                context.getString(R.string.profiles_new));
        create.setOnClickListener(v -> showNewProfileDialog());
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        createLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        card.addContent(create, createLp);
        CockpitButton guest = CockpitButton.secondary(context,
                context.getString(R.string.profiles_guest_start));
        guest.setOnClickListener(v -> startGuestProfile());
        LinearLayout.LayoutParams guestLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guestLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
        card.addContent(guest, guestLp);
        return card;
    }

    private String faceStatus(DriverProfileManager.Profile profile) {
        return profileHasFace(profile)
                ? context.getString(R.string.profiles_face_registered)
                : context.getString(R.string.profiles_face_none);
    }

    private boolean profileHasFace(DriverProfileManager.Profile profile) {
        try {
            for (FaceProfileStore.Profile face : FaceProfileStore.load(context)) {
                if (face.name != null && face.name.equalsIgnoreCase(profile.name)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void openFaceEnrollment(DriverProfileManager.Profile profile) {
        Intent intent = new Intent(context, DmsFaceProfileActivity.class);
        intent.putExtra(DmsFaceProfileActivity.EXTRA_PREFILL_NAME, profile.name);
        intent.putExtra(DmsFaceProfileActivity.EXTRA_PREFILL_SEAT, profile.seatSlot);
        intent.putExtra(DmsFaceProfileActivity.EXTRA_LOCK_IDENTITY, true);
        context.startActivity(intent);
    }

    private void showProfileSettings(DriverProfileManager.Profile profile) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(8));
        addSeatSection(content, profile);
        addProfileSettingsSummary(content, profile);
        addAdvancedItems(content, profile);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        new AlertDialog.Builder(context)
                .setTitle(profile.name)
                .setView(scroll)
                .setPositiveButton(R.string.profiles_ok, null)
                .show();
    }

    private void addProfileSettingsSummary(LinearLayout parent,
            DriverProfileManager.Profile profile) {
        TextView title = new TextView(context);
        title.setText(R.string.profiles_settings_summary_title);
        CockpitTokens.labelCaps(title);
        title.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_md), 0,
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        parent.addView(title);

        java.util.Map<String, Object> settings = manager.scopedSettings(profile.id);
        addSummaryRow(parent, context.getString(R.string.profiles_seat_label),
                profile.seatSlot > 0
                        ? context.getString(R.string.profiles_slot_n, profile.seatSlot)
                        : context.getString(R.string.profiles_slot_none));
        addSummaryRow(parent, context.getString(R.string.profiles_apply_on_start_title),
                context.getString(profile.applyOnStart
                        ? R.string.common_on : R.string.common_off));

        Object drive = settings.get("driveModeSetting");
        if (drive instanceof Number) {
            addSummaryRow(parent, context.getString(R.string.profiles_summary_drive_mode),
                    driveLabel(((Number) drive).intValue()));
        }
        int assist = 0;
        assist += isActiveAssist(settings, "issSetting", 0);
        assist += isActiveAssist(settings, "spdLimitSetting", 2);
        assist += isActiveAssist(settings, "ldwSetting", 2);
        assist += isActiveAssist(settings, "ldpSetting", 2);
        assist += isActiveAssist(settings, "fcwSetting", 2);
        assist += isActiveAssist(settings, "aebSetting", 2);
        assist += isActiveAssist(settings, "cpdSetting", 2);
        addSummaryRow(parent, context.getString(R.string.ck_sub_assist),
                context.getString(R.string.profiles_assist_count, assist));

        boolean climate = Boolean.TRUE.equals(settings.get("tempAutoEnabled"))
                || Boolean.TRUE.equals(settings.get("recircAutoEnabled"));
        addSummaryRow(parent, context.getString(R.string.ck_sub_climate),
                context.getString(climate ? R.string.common_on : R.string.common_off));
        if (settings.containsKey("comfortSunroofSetting")
                || settings.containsKey("comfortSunshadeSetting")) {
            addSummaryRow(parent, context.getString(R.string.side_rail_sunroof_automation),
                    context.getString(R.string.profiles_configured));
        }
        if (hasText(settings.get("welcomeDesignId"))
                || hasText(settings.get("welcomeCustomText"))
                || hasText(settings.get("welcomeAudioFilePath"))) {
            addSummaryRow(parent, context.getString(R.string.side_rail_welcome_sound),
                    context.getString(R.string.profiles_customized));
        }
        addSummaryRow(parent, context.getString(R.string.profiles_advanced_items_short),
                String.valueOf(profile.items.size()));
        if (settings.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.profiles_summary_empty);
            CockpitTokens.bodySm(empty);
            empty.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
            empty.setPadding(0, CockpitTokens.dim(context, R.dimen.ck_space_sm), 0,
                    CockpitTokens.dim(context, R.dimen.ck_space_sm));
            parent.addView(empty);
        }
    }

    private static boolean hasText(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty();
    }

    private static int isActiveAssist(java.util.Map<String, Object> settings,
            String key, int activeValue) {
        Object value = settings.get(key);
        return value instanceof Number && ((Number) value).intValue() == activeValue ? 1 : 0;
    }

    private String driveLabel(int mode) {
        switch (mode) {
            case 0: return context.getString(R.string.ck_drive_eco);
            case 1: return context.getString(R.string.ck_drive_normal);
            case 2: return context.getString(R.string.ck_drive_sport);
            case 3: return context.getString(R.string.ck_drive_snow);
            case 4: return context.getString(R.string.ck_drive_mud);
            case 5: return context.getString(R.string.ck_drive_offroad);
            case 7: return context.getString(R.string.ck_drive_sand);
            default: return context.getString(R.string.ck_drive_none);
        }
    }

    private void addSummaryRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm),
                CockpitTokens.dim(context, R.dimen.ck_space_sm));
        CockpitSurfaces.level1(row);
        TextView labelView = new TextView(context);
        labelView.setText(label);
        CockpitTokens.bodySm(labelView);
        row.addView(labelView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = new TextView(context);
        valueView.setText(value);
        CockpitTokens.bodySm(valueView);
        valueView.setTextColor(CockpitTokens.color(context, R.color.ck_accent));
        row.addView(valueView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_xs);
        parent.addView(row, lp);
    }

    private void startGuestProfile() {
        String guestName = context.getString(R.string.profiles_guest_name);
        DriverProfileManager.Profile guest = null;
        for (DriverProfileManager.Profile p : manager.getProfiles()) {
            if (guestName.equals(p.name)) {
                guest = p;
                break;
            }
        }
        if (guest == null) {
            if (manager.getProfiles().size() >= 3) {
                toast(R.string.profiles_max_reached);
                return;
            }
            guest = manager.create(guestName, false);
        }
        manager.activate(guest.id);
        log.accept("[Profil] Misafir profili etkin");
        handler.post(onProfileChanged);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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
                for (int i = 0; i < chips.getChildCount(); i++) {
                    View child = chips.getChildAt(i);
                    if (child instanceof CockpitChip) {
                        ((CockpitChip) child).setSelectedState(i == value);
                    }
                }
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
        if (manager.getProfiles().size() >= 3) {
            toast(R.string.profiles_max_reached);
            return;
        }
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
