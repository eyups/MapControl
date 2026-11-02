package com.mapcontrol.nav;

import android.content.Context;

/** Cluster harita overlay'lerini z-sirasi icin tek noktadan one alir. */
public final class ClusterNavZOrder {
    private ClusterNavZOrder() {
    }

    public static void raiseIfShowing(Context context) {
        GoogleMapsClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
        YandexClusterNavOverlay.getInstance(context).raiseToTopIfShowing();
    }
}
