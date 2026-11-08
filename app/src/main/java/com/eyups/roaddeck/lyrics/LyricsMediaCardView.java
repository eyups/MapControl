package com.eyups.roaddeck.lyrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.eyups.roaddeck.R;
import com.eyups.roaddeck.ui.cockpit.CockpitSurfaces;
import com.eyups.roaddeck.ui.cockpit.CockpitTokens;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Kokpit medya kartı gövdesi — Stitch tasarımıyla birebir:
 * solda albüm kapağı + künye + ilerleme + 5 kontrollük satır,
 * sağda çerçeveli canlı söz paneli (önceki / güncel / sonraki satırlar).
 */
public final class LyricsMediaCardView extends LinearLayout {

    public interface Listener {
        void onOpenFullscreen();
    }


    private final LyricsEngine engine;
    private final ImageView artworkView;
    private final TextView titleView;
    private final TextView artistView;
    private final TextView elapsedView;
    private final TextView totalView;
    private final ProgressBar progressBar;
    private final ImageView playButton;
    private final TextView prevLineView;
    private final TextView currentLineView;
    private final TextView nextLineView;
    private final TextView nextLine2View;
    private final TextView messageView;

    private int lastIndex = -2;
    private List<LyricLine> boundLines = Collections.emptyList();

    public LyricsMediaCardView(Context context, LyricsEngine engine, Listener listener) {
        super(context);
        this.engine = engine;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(VERTICAL);
        addView(left, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.45f));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        FrameLayout artFrame = new FrameLayout(context);
        GradientDrawable artBg = CockpitSurfaces.rounded(context, CockpitTokens.color(context, R.color.ck_bg_recessed), CockpitTokens.RADIUS_CARD_DP);
        artBg.setStroke(dp(1), CockpitTokens.color(context, R.color.ck_stroke));
        artFrame.setBackground(artBg);
        artFrame.setClipToOutline(true);
        head.addView(artFrame, new LinearLayout.LayoutParams(dp(96), dp(96)));

        artworkView = new ImageView(context);
        artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkView.setPadding(dp(44), dp(44), dp(44), dp(44));
        artworkView.setImageResource(R.drawable.ic_mdi_music_note);
        artworkView.setColorFilter(color(R.color.ck_primary));
        artFrame.addView(artworkView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(12);
        head.addView(info, infoLp);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        titleView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        titleView.setTextColor(color(R.color.ck_text_high));
        titleView.setSingleLine(false);
        titleView.setMaxLines(3);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(titleView);

        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        artistView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_MEDIUM));
        artistView.setTextColor(color(R.color.ck_text_medium));
        artistView.setSingleLine(true);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams artistLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        artistLp.topMargin = dp(2);
        info.addView(artistView, artistLp);

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color(R.color.ck_accent)));
        progressBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color(R.color.ck_bg_recessed)));
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressLp.topMargin = dp(14);
        left.addView(progressBar, progressLp);

        LinearLayout times = new LinearLayout(context);
        times.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams timesLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timesLp.topMargin = dp(6);
        left.addView(times, timesLp);

        elapsedView = timeLabel(context, color(R.color.ck_primary), true);
        times.addView(elapsedView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        totalView = timeLabel(context, color(R.color.ck_text_medium), false);
        totalView.setGravity(Gravity.END);
        times.addView(totalView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dp(10);
        left.addView(controls, controlsLp);

        ImageView prev = controlIcon(context, R.drawable.ic_mdi_skip_previous, dp(24),
                R.string.ck_lyrics_prev);
        prev.setOnClickListener(v -> engine.skipPrevious());
        controls.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1f));

        ImageView back10 = controlIcon(context, R.drawable.ic_mdi_replay_10, dp(20),
                R.string.ck_lyrics_replay10);
        back10.setOnClickListener(v -> engine.seekBy(-10_000L));
        controls.addView(back10, new LinearLayout.LayoutParams(0, dp(48), 1f));

        playButton = new ImageView(context);
        playButton.setImageResource(R.drawable.ic_mdi_play);
        playButton.setColorFilter(Color.parseColor("#0B0E12"));
        playButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable playBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{CockpitTokens.color(context, R.color.ck_accent), CockpitTokens.color(context, R.color.ck_accent_active)});
        playBg.setShape(GradientDrawable.OVAL);
        playButton.setBackground(playBg);
        playButton.setElevation(dp(6));
        playButton.setContentDescription(context.getString(R.string.ck_lyrics_play));
        playButton.setOnClickListener(v -> engine.playPause());
        controls.addView(playButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageView forward10 = controlIcon(context, R.drawable.ic_mdi_forward_10, dp(20),
                R.string.ck_lyrics_forward10);
        forward10.setOnClickListener(v -> engine.seekBy(10_000L));
        controls.addView(forward10, new LinearLayout.LayoutParams(0, dp(48), 1f));

        ImageView next = controlIcon(context, R.drawable.ic_mdi_skip_next, dp(24),
                R.string.ck_lyrics_next);
        next.setOnClickListener(v -> engine.skipNext());
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1f));

        // Nested kart yerine tek yüzey: ince bir ayırıcı + düz metin kolonu.
        View focusDivider = new View(context);
        focusDivider.setBackgroundColor(color(R.color.ck_stroke));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                dp(1), LayoutParams.MATCH_PARENT);
        dividerLp.leftMargin = dp(12);
        dividerLp.rightMargin = dp(12);
        addView(focusDivider, dividerLp);

        LinearLayout focus = new LinearLayout(context);
        focus.setOrientation(VERTICAL);
        focus.setPadding(dp(8), dp(14), 0, dp(12));
        focus.setFocusable(true);
        focus.setContentDescription(context.getString(R.string.ck_lyrics_fullscreen));
        focus.setOnClickListener(v -> listener.onOpenFullscreen());
        LinearLayout.LayoutParams focusLp = new LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 0.55f);
        focusLp.leftMargin = dp(0);
        addView(focus, focusLp);

        View topSpacer = new View(context);
        focus.addView(topSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        prevLineView = focusLine(context, 15f, color(R.color.ck_text_medium), false, 1);
        focus.addView(prevLineView);

        currentLineView = focusLine(context, 22f, color(R.color.ck_text_high), true, 3);
        currentLineView.setTypeface(CockpitTokens.inter(context, CockpitTokens.WEIGHT_BOLD));
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        currentLp.topMargin = dp(8);
        focus.addView(currentLineView, currentLp);

        nextLineView = focusLine(context, 16f, color(R.color.ck_text_medium), true, 1);
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = dp(10);
        focus.addView(nextLineView, nextLp);

        nextLine2View = focusLine(context, 13f, color(R.color.ck_text_medium), true, 1);
        focus.addView(nextLine2View);

        View spacer = new View(context);
        focus.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        messageView = new TextView(context);
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        messageView.setTextColor(color(R.color.ck_text_medium));
        messageView.setVisibility(GONE);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = dp(8);
        focus.addView(messageView, messageLp);
    }

    public void render(LyricsState state) {
        titleView.setText(state.title == null || state.title.isEmpty()
                ? getContext().getString(R.string.ck_lyrics_no_track)
                : state.title);
        artistView.setText(state.artist == null ? "" : state.artist);
        applyArtwork(state.artwork);

        long duration = Math.max(0L, state.durationMs);
        long position = Math.max(0L, Math.min(state.positionMs,
                duration > 0 ? duration : state.positionMs));
        progressBar.setProgress(duration > 0 ? (int) (position * 1000 / duration) : 0);
        elapsedView.setText(formatTime(position));
        totalView.setText(duration > 0 ? formatTime(duration) : "--:--");
        playButton.setImageResource(state.playing
                ? R.drawable.ic_mdi_pause
                : R.drawable.ic_mdi_play);

        boolean hasLines = !state.lines.isEmpty();
        int visible = hasLines ? VISIBLE : GONE;
        prevLineView.setVisibility(visible);
        nextLineView.setVisibility(visible);
        nextLine2View.setVisibility(visible);
        currentLineView.setVisibility(visible);
        messageView.setVisibility(hasLines ? GONE : VISIBLE);

        if (!hasLines) {
            prevLineView.setText("");
            currentLineView.setText("");
            nextLineView.setText("");
            nextLine2View.setText("");
            messageView.setText(messageFor(state.status));
            return;
        }

        int index = state.currentLine >= 0 ? state.currentLine : 0;
        if (index == lastIndex && state.lines == boundLines) {
            return;
        }
        lastIndex = index;
        boundLines = state.lines;

        prevLineView.setText(index > 0 ? state.lines.get(index - 1).text : "");
        currentLineView.setText("“" + state.lines.get(index).text + "”");
        nextLineView.setText(index + 1 < state.lines.size()
                ? state.lines.get(index + 1).text : "");
        nextLine2View.setText(index + 2 < state.lines.size()
                ? state.lines.get(index + 2).text : "");
    }

    private int color(int res) {
        return CockpitTokens.color(getContext(), res);
    }

    private int tokenAlpha(int res, float alpha) {
        return CockpitSurfaces.alpha(color(res), alpha);
    }

    private void applyArtwork(Bitmap artwork) {
        if (artwork == null) {
            artworkView.setPadding(dp(44), dp(44), dp(44), dp(44));
            artworkView.setColorFilter(color(R.color.ck_primary));
            artworkView.setImageResource(R.drawable.ic_mdi_music_note);
            return;
        }
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(
                getResources(), artwork);
        drawable.setCornerRadius(CockpitTokens.dim(getContext(), R.dimen.ck_radius_lg));
        drawable.setAntiAlias(true);
        artworkView.setPadding(0, 0, 0, 0);
        artworkView.clearColorFilter();
        artworkView.setImageDrawable(drawable);
    }

    private int messageFor(LyricsState.Status status) {
        switch (status) {
            case NO_ACCESS:
                return R.string.ck_lyrics_no_access;
            case NO_SESSION:
                return R.string.ck_lyrics_empty_session;
            case NOT_FOUND:
                return R.string.ck_lyrics_not_found;
            case INSTRUMENTAL:
                return R.string.ck_lyrics_instrumental;
            case ERROR:
                return R.string.ck_lyrics_offline;
            default:
                return R.string.ck_lyrics_loading;
        }
    }

    private TextView timeLabel(Context context, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setTextColor(color);
        view.setTypeface(CockpitTokens.mono(context));
        view.setText("0:00");
        return view;
    }

    private TextView focusLine(Context context, float sizeSp, int color, boolean bold, int maxLines) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(CockpitTokens.inter(context, bold
                ? CockpitTokens.WEIGHT_BOLD
                : CockpitTokens.WEIGHT_SEMIBOLD));
        view.setMaxLines(maxLines);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private ImageView controlIcon(Context context, int drawableRes, int iconPx, int contentDescRes) {
        ImageView view = new ImageView(context);
        view.setImageResource(drawableRes);
        view.setColorFilter(color(R.color.ck_text_medium));
        int pad = Math.max(0, (dp(48) - iconPx) / 2);
        view.setPadding(pad, pad, pad, pad);
        view.setContentDescription(context.getString(contentDescRes));
        return view;
    }

    private static String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d",
                totalSeconds / 60L, totalSeconds % 60L);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
