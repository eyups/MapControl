package com.eyups.roaddeck.nav;

import android.content.Context;

/** Cluster harita overlay'lerini z-sirasi icin tek noktadan one alir. */
public final class ClusterNavZOrder {
    private ClusterNavZOrder() {
    }

    public static void raiseIfShowing(Context context) {
        GoogleMapsClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
        YandexClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
    }

    /** Araç OFF iken tüm cluster navigasyon kartlarını kapatır. */
    public static void setPowerOff(Context context, boolean off) {
        GoogleMapsClusterNavOverlay.setPowerOff(context, off);
        YandexClusterNavOverlay.setPowerOff(context, off);
    }
}
