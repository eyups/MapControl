package com.eyups.roaddeck.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eyups.roaddeck.R
import com.eyups.roaddeck.audio.AudioEqualizerController
import com.eyups.roaddeck.service.DmsFaceRecognitionManager
import com.eyups.roaddeck.service.RoadDeckService
import com.eyups.roaddeck.ui.activity.MainActivity
import com.eyups.roaddeck.util.ClusterNavigationState
import com.eyups.roaddeck.util.SplitScreenLauncher
import com.eyups.roaddeck.util.SplitScreenMessages
import com.eyups.roaddeck.util.SplitScreenStore
import com.eyups.roaddeck.util.TargetPackageStore
import com.eyups.roaddeck.vehicle.ClimateAutomationEngine
import com.eyups.roaddeck.vehicle.ClimateAutomationStore
import com.eyups.roaddeck.vehicle.SunroofAutomationEngine

/**
 * RoadDeck'in yerel arka uçlarıyla launcher widget'larını besler. Cloud/FrostNav'a bağlı
 * web paneli kapalıdır; ekolayzer, otomasyon, bölünmüş ekran ve gösterge yansıtma yerel
 * yöneticileri kullanır.
 */
internal class RoadDeckLauncherFeatures(context: Context) : FrostNavFeatures {

    private val app = context.applicationContext

    override val available = true

    // Web paneli (cloud/Aktar) bu portta kapsam dışı.
    override fun readWebPanel() = WebPanelState(running = false, url = null, reachable = false)

    override fun setWebPanel(on: Boolean, done: (String?) -> Unit) {
        done(app.getString(R.string.launcher_fn_web_unavailable))
    }

    override fun watchWebPanel(onChange: () -> Unit): () -> Unit = {}

    override fun qr(text: String): QrMatrix? = null

    // --- Ekolayzer ---

    override fun readEqualizer(): EqState {
        val s = AudioEqualizerController.read(app)
        return EqState(s.supported, s.enabled, s.preset, PRESETS)
    }

    override fun setEqualizer(enabled: Boolean, preset: String) {
        val s = AudioEqualizerController.read(app)
        val bands = if (preset == AudioEqualizerController.PRESET_CUSTOM) s.bands
        else AudioEqualizerController.bandsForPreset(preset, s.centerHz, s.minLevelMb, s.maxLevelMb)
        AudioEqualizerController.update(app, enabled, s.gainMb, bands, preset)
    }

    // --- Otomasyon ---

    private fun mainPrefs() = app.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)

    private fun sunroofPrefs() = app.getSharedPreferences(SunroofAutomationEngine.PREFS_NAME, Context.MODE_PRIVATE)

    override fun readAutomations(): List<AutomationItem> = listOf(
        AutomationItem(AutomationId.CLIMATE, ClimateAutomationStore.isMasterEnabled(mainPrefs()), true),
        AutomationItem(AutomationId.SUNROOF, sunroofPrefs().getBoolean(SunroofAutomationEngine.KEY_ENABLED, false), true),
        AutomationItem(AutomationId.FACE, DmsFaceRecognitionManager.isEnabled(app), false),
    )

    override fun setAutomation(id: AutomationId, on: Boolean) {
        when (id) {
            AutomationId.CLIMATE -> {
                ClimateAutomationStore.setMasterEnabled(mainPrefs(), on)
                val engine = ClimateAutomationEngine.getInstance(app)
                if (on) engine.notifyRulesChanged() else engine.disableAllRules()
            }
            AutomationId.SUNROOF -> sunroofPrefs().edit()
                .putBoolean(SunroofAutomationEngine.KEY_ENABLED, on).apply()
            AutomationId.FACE -> Unit
        }
    }

    // --- Bölünmüş ekran ---

    override fun readSplitPair() = SplitPair(SplitScreenStore.getPkg1(app), SplitScreenStore.getPkg2(app))

    override fun startSplit(done: (String?) -> Unit) {
        val pair = readSplitPair()
        val first = pair.first
        val second = pair.second
        if (first.isNullOrEmpty() || second.isNullOrEmpty() || first == second) {
            done(app.getString(R.string.split_select_both))
            return
        }
        SplitScreenLauncher.start(app, first, second, SplitScreenStore.getRatio(app)) { result ->
            done(if (result == SplitScreenLauncher.Result.STARTED) null
            else SplitScreenMessages.resultText(app, result))
        }
    }

    override fun startSplitWithMiniDash(done: (String?) -> Unit) {
        done(app.getString(R.string.launcher_fn_minidash_unavailable))
    }

    override fun startDriveScreen(mapPkg: String?, route: Intent?, done: (String?) -> Unit) {
        done(app.getString(R.string.launcher_fn_drive_unavailable))
    }

    override fun mapFullScreen(pkg: String, done: (Boolean) -> Unit) = done(false)

    override fun endDriveScreen() = Unit

    override fun openFullScreen(pkg: String) = Unit

    override fun restoreStaleMap(pkg: String) = Unit

    override fun driveMapSlot(): android.graphics.Rect? = null

    // --- Göstergeye yansıt ---

    override fun readCluster() = ClusterState(
        ClusterNavigationState.getLastKnownOpen(),
        TargetPackageStore.read(app).ifEmpty { null },
    )

    override fun setCluster(open: Boolean) {
        val intent = Intent(app, RoadDeckService::class.java)
            .setAction(
                if (open) RoadDeckService.ACTION_BENCH_OPEN_CLUSTER
                else RoadDeckService.ACTION_BENCH_CLOSE_CLUSTER
            )
            .putExtra(RoadDeckService.EXTRA_CLUSTER_CLOSE_SEND_BACKGROUND, true)
        try {
            app.startService(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "cluster $open: ${e.message}")
        }
    }

    override fun openTab(activity: Activity, tab: FrostNavTab) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    private companion object {
        const val TAG = "RoadDeckLauncher"
        const val PREFS_MAIN = "RoadDeckPrefs"
        val PRESETS = listOf(
            AudioEqualizerController.PRESET_FLAT,
            AudioEqualizerController.PRESET_BASS,
            AudioEqualizerController.PRESET_VOCAL,
            AudioEqualizerController.PRESET_TREBLE,
        )
    }
}
