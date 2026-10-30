package com.mapcontrol.ui.cockpit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** Level 3 modal dialog: scrim, recessed card, primary/secondary actions. */
public class CockpitModal extends Dialog {

    public interface OnAction {
        void onAction(CockpitModal dialog);
    }

    private final LinearLayout bodyColumn;

    private CockpitModal(Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(CockpitTokens.color(context, R.color.ck_scrim));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        CockpitSurfaces.level3(card);
        int pad = CockpitTokens.dim(context, R.dimen.ck_space_lg);
        card.setPadding(pad, pad, pad, pad);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                Math.min(CockpitTokens.dp(context, 720),
                        context.getResources().getDisplayMetrics().widthPixels
                                - CockpitTokens.dp(context, 2 * 48)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        root.addView(card, cardParams);

        bodyColumn = new LinearLayout(context);
        bodyColumn.setOrientation(LinearLayout.VERTICAL);
        card.addView(bodyColumn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    public static Builder builder(Context context) {
        return new Builder(context);
    }

    /** Convenience two-button confirm. */
    public static void show(Context context, CharSequence title, CharSequence message,
            CharSequence positive, CharSequence negative, OnAction onPositive) {
        Builder b = new Builder(context).title(title).message(message)
                .positive(positive, onPositive);
        if (negative != null) {
            b.negative(negative, null);
        }
        b.build().show();
    }

    public LinearLayout getBodyColumn() {
        return bodyColumn;
    }

    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View content;
        private CharSequence positiveText;
        private OnAction positiveAction;
        private CharSequence negativeText;
        private OnAction negativeAction;
        private boolean destructivePrimary;

        Builder(Context context) {
            this.context = context;
        }

        public Builder title(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder message(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder content(View content) {
            this.content = content;
            return this;
        }

        public Builder positive(CharSequence text, OnAction action) {
            this.positiveText = text;
            this.positiveAction = action;
            return this;
        }

        public Builder negative(CharSequence text, OnAction action) {
            this.negativeText = text;
            this.negativeAction = action;
            return this;
        }

        public Builder destructivePrimary(boolean destructive) {
            this.destructivePrimary = destructive;
            return this;
        }

        public CockpitModal build() {
            final CockpitModal dialog = new CockpitModal(context);
            LinearLayout col = dialog.bodyColumn;

            if (title != null) {
                TextView t = new TextView(context);
                CockpitTokens.headlineSm(t);
                t.setText(title);
                col.addView(t);
            }
            if (message != null) {
                TextView m = new TextView(context);
                CockpitTokens.bodyMd(m);
                m.setTextColor(CockpitTokens.color(context, R.color.ck_text_medium));
                m.setText(message);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
                col.addView(m, lp);
            }
            if (content != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
                col.addView(content, lp);
            }

            LinearLayout actions = new LinearLayout(context);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionsLp.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_lg);
            col.addView(actions, actionsLp);

            if (negativeText != null) {
                CockpitButton neg = CockpitButton.secondary(context, negativeText);
                neg.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (negativeAction != null) {
                        negativeAction.onAction(dialog);
                    }
                });
                actions.addView(neg);
            }
            if (positiveText != null) {
                CockpitButton pos = destructivePrimary
                        ? CockpitButton.hazard(context, positiveText)
                        : CockpitButton.primary(context, positiveText);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = CockpitTokens.dim(context, R.dimen.ck_space_sm);
                pos.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (positiveAction != null) {
                        positiveAction.onAction(dialog);
                    }
                });
                actions.addView(pos, lp);
            }
            return dialog;
        }
    }
}
