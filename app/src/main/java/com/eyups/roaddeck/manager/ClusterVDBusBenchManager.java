package com.eyups.roaddeck.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.eyups.roaddeck.service.RoadDeckService;

/**
 * Araç yanında olmadan VDBus / cluster yollarını bench etmek için.
 * {@link com.eyups.roaddeck.ui.activity.ClusterVDBusTestActivity} üzerinden kullanılır.
 */
public final class ClusterVDBusBenchManager {

    public interface UiActions {
        void navKeyToggleLikeVDBus();

        void alertToneLikeVDBus();

        void clusterOpenDirectUi();

        void clusterCloseDirectUi();
    }

    public interface Logger {
        void log(String line);
    }

    private final Context appContext;
    private final UiActions uiActions;
    private final Logger logger;

    public ClusterVDBusBenchManager(Context context, UiActions uiActions, Logger logger) {
        this.appContext = context.getApplicationContext();
        this.uiActions = uiActions;
        this.logger = logger;
    }

    public void benchNavToggle() {
        logger.log("[Bench] Nav toggle (VDBus 26/4 ile aynı UI yolu)");
        uiActions.navKeyToggleLikeVDBus();
    }

    public void benchAlertTone() {
        logger.log("[Bench] Uyarı sesi (VDBus 26/1)");
        uiActions.alertToneLikeVDBus();
    }

    public void benchClusterOpenUi() {
        logger.log("[Bench] Cluster aç — ClusterDisplayManager (isNavigationOpen atlanır)");
        uiActions.clusterOpenDirectUi();
    }

    public void benchClusterCloseUi() {
        logger.log("[Bench] Cluster kapat — ClusterDisplayManager");
        uiActions.clusterCloseDirectUi();
    }

    public void benchClusterOpenService() {
        logger.log("[Bench] Cluster aç — RoadDeckService.openClusterDisplay yolu");
        startServiceBench(RoadDeckService.ACTION_BENCH_OPEN_CLUSTER);
    }

    public void benchClusterCloseService() {
        logger.log("[Bench] Cluster kapat — RoadDeckService.closeClusterDisplay yolu");
        startServiceBench(RoadDeckService.ACTION_BENCH_CLOSE_CLUSTER);
    }

    private void startServiceBench(String action) {
        Intent i = new Intent(appContext, RoadDeckService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(i);
        } else {
            appContext.startService(i);
        }
    }
}
