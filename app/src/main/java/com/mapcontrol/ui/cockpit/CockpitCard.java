package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mapcontrol.R;

/** Level 1 telematics card: header + central content + footer. */
public class CockpitCard extends LinearLayout {

    private final TextView titleView;
    private final LinearLayout headerRow;
    private final LinearLayout body;
    private final LinearLayout footer;
    private View statusView;
    private int span = 4;

    public CockpitCard(Context context) {
        this(context, null);
    }

    public CockpitCard(Context context, String title) {
        super(context);
        setOrientation(VERTICAL);
        CockpitSurfaces.level1(this);
        int pad = CockpitTokens.dim(context, R.dimen.ck_card_padding);
        setPadding(pad, pad, pad, pad);
        setClipToPadding(false);

        headerRow = new LinearLayout(context);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        addView(headerRow, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        titleView = new TextView(context);
        CockpitTokens.title(titleView);
        titleView.setText(title == null ? "" : title);
        titleView.setVisibility(title == null ? GONE : VISIBLE);
        headerRow.addView(titleView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        addView(body, bodyParams);

        footer = new LinearLayout(context);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LayoutParams footerParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = CockpitTokens.dim(context, R.dimen.ck_space_md);
        addView(footer, footerParams);
        footer.setVisibility(GONE);
    }

    public CockpitCard title(String text) {
        titleView.setText(text);
        titleView.setVisibility(text == null || text.isEmpty() ? GONE : VISIBLE);
        return this;
    }

    public TextView getTitleView() {
        return titleView;
    }

    public LinearLayout getBody() {
        return body;
    }

    public LinearLayout getFooter() {
        footer.setVisibility(VISIBLE);
        return footer;
    }

    public LinearLayout getHeaderRow() {
        return headerRow;
    }

    public CockpitCard status(View view) {
        if (statusView != null) {
            headerRow.removeView(statusView);
        }
        statusView = view;
        if (view != null) {
            headerRow.addView(view, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
        }
        return this;
    }

    public CockpitCard addContent(View view) {
        body.addView(view, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        return this;
    }

    public CockpitCard addContent(View view, LayoutParams params) {
        body.addView(view, params);
        return this;
    }

    public CockpitCard addAction(View view) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.leftMargin = CockpitTokens.dim(getContext(), R.dimen.ck_space_sm);
        footer.addView(view, lp);
        footer.setVisibility(VISIBLE);
        return this;
    }

    public CockpitCard span(int span) {
        this.span = span;
        return this;
    }

    public int getSpan() {
        return span;
    }

    public void setCardOnClickListener(OnClickListener listener) {
        setOnClickListener(listener);
        setFocusable(true);
    }
}
