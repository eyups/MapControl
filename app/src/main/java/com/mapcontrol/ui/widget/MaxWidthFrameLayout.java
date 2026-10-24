package com.mapcontrol.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Geniş yatay ekranlarda sekme içeriğinin tüm genişliğe yayılmasını engeller.
 * İçerik en fazla {@link #DEFAULT_MAX_WIDTH_DP} dp olacak şekilde ölçülür; üst FrameLayout
 * içinde yatay olarak ortalanır.
 */
public class MaxWidthFrameLayout extends FrameLayout {

    public static final int DEFAULT_MAX_WIDTH_DP = 1120;

    private final int maxWidthPx;

    public MaxWidthFrameLayout(Context context) {
        this(context, null);
    }

    public MaxWidthFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        maxWidthPx = Math.round(DEFAULT_MAX_WIDTH_DP * density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int targetWidth = Math.min(widthSize, maxWidthPx);
        int targetSpec = MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY);
        super.onMeasure(targetSpec, heightMeasureSpec);
    }
}
