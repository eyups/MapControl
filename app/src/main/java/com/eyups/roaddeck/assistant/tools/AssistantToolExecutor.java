package com.eyups.roaddeck.assistant.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.eyups.roaddeck.audio.AudioEqualizerController;
import com.eyups.roaddeck.assistant.AssistantPrefs;
import com.eyups.roaddeck.assistant.wake.WakeWordNormalizer;
import com.eyups.roaddeck.driving.DrivingTrip;
import com.eyups.roaddeck.driving.DrivingTripStore;
import com.eyups.roaddeck.manager.ClusterDisplayManager;
import com.eyups.roaddeck.media.LauncherMediaNotificationListener;
import com.eyups.roaddeck.nav.GoogleMapsNavNotificationCoordinator;
import com.eyups.roaddeck.nav.GoogleMapsNavSnapshot;
import com.eyups.roaddeck.vehicle.ComfortCommands;
import com.eyups.roaddeck.vehicle.VehicleMetricsRepository;
import com.eyups.roaddeck.vehicle.VehicleMetricsSnapshot;
import com.eyups.roaddeck.vehicle.VehicleQuickControls;
import com.eyups.roaddeck.vehicle.VehicleSunroofController;
import com.eyups.roaddeck.weather.OpenMeteoWeatherClient;
import com.eyups.roaddeck.weather.OpenMeteoWeatherSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Araç ve uygulama araçlarının yürütücüsü. Tüm çağrılar {@code va-core}
 * thread'inde çalışır; VDBus okumaları ana thread'i bloklamaz.
 */
public final class AssistantToolExecutor {

    public interface UiActions {
        void openScreen(String screen);

        void clearHistory();

        void disableAssistant();
    }

    private static final long DEBOUNCE_MS = 3000L;
    private static final long WRITE_WINDOW_MS = 60000L;
    private static final int MAX_WRITES_PER_MINUTE = 10;

    private final Context appContext;
    private final UiActions ui;

    private String lastWriteKey = "";
    private long lastWriteAt;
    private final ArrayDeque<Long> writeTimes = new ArrayDeque<>();

    public AssistantToolExecutor(Context context, UiActions ui) {
        this.appContext = context.getApplicationContext();
        this.ui = ui;
    }

    public ToolResult execute(String name, Map<String, String> args) {
        if (name == null) {
            return ToolResult.fail("Bilinmeyen işlem.");
        }
        if (AssistantSafetyPolicy.isWriteTool(name)) {
            ToolResult throttle = checkWriteThrottle(name, args);
            if (throttle != null) {
                return throttle;
            }
        }
        try {
            return dispatch(name, args == null ? new java.util.LinkedHashMap<>() : args);
        } catch (Throwable t) {
            return ToolResult.fail("İşlem başarısız: " + t.getClass().getSimpleName());
        }
    }

    private ToolResult checkWriteThrottle(String name, Map<String, String> args) {
        long now = System.currentTimeMillis();
        while (!writeTimes.isEmpty() && now - writeTimes.peekFirst() > WRITE_WINDOW_MS) {
            writeTimes.pollFirst();
        }
        if (writeTimes.size() >= MAX_WRITES_PER_MINUTE) {
            return ToolResult.fail("Çok fazla komut; biraz sonra tekrar dene.");
        }
        StringBuilder key = new StringBuilder(name);
        if (args != null) {
            for (Map.Entry<String, String> entry : args.entrySet()) {
                key.append('|').append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        String value = key.toString();
        if (value.equals(lastWriteKey) && now - lastWriteAt < DEBOUNCE_MS) {
            return ToolResult.fail("Aynı komut zaten gönderildi.");
        }
        lastWriteKey = value;
        lastWriteAt = now;
        writeTimes.addLast(now);
        return null;
    }

    private ToolResult dispatch(String name, Map<String, String> a) {
        switch (name) {
            case "report_transcript":
                return ToolResult.ok("alındı");
            case "vehicle_status":
                return vehicleStatus();
            case "sunroof_set":
                return sunroofSet(a.get("action"));
            case "sunshade_set":
                return sunshadeSet(a.get("action"));
            case "sunroof_status":
                return sunroofStatus();
            case "recirculation_set":
                return recirculationSet("true".equals(a.get("on")));
            case "seat_heat_set":
                return seatLevel(a.get("seat"), a.get("level"), true, false);
            case "seat_vent_set":
                return seatLevel(a.get("seat"), a.get("level"), false, false);
            case "seat_massage_set":
                return seatLevel(a.get("seat"), a.get("level"), false, true);
            case "steering_heat_set":
                return steeringHeat("true".equals(a.get("on")));
            case "seat_memory_restore":
                return seatMemory(a.get("slot"));
            case "drive_mode_set":
                return driveMode(a.get("mode"));
            case "door_lock_set":
                return doorLock(a.get("action"));
            case "window_set":
                return ToolResult.fail("Cam kontrolü araçta doğrulanmadı; kullanılamıyor.");
            case "climate_set":
                return ToolResult.fail("Klima yazma değerleri araçta doğrulanmadı.");
            case "media_control":
                return mediaControl(a.get("action"));
            case "volume_set":
                return volume(a.get("action"), a.get("level"));
            case "navigate_to":
                return navigateTo(a.get("destination"));
            case "cluster_nav_toggle":
                return clusterNav("true".equals(a.get("open")));
            case "open_app":
                return openApp(a.get("name"));
            case "open_screen":
                if (ui != null) {
                    ui.openScreen(a.get("screen"));
                    return ToolResult.ok("ekran açıldı");
                }
                return ToolResult.fail("Ekran açılamadı.");
            case "equalizer_preset":
                return equalizer(a.get("preset"));
            case "weather_now":
                return weather();
            case "driving_report_summary":
                return drivingSummary();
            case "time_date":
                return timeDate();
            case "read_last_nav_instruction":
                return lastNavInstruction();
            case "assistant_control":
                return assistantControl(a.get("action"));
            default:
                return ToolResult.fail("Bu işlemi yapamıyorum.");
        }
    }

    // ---------------------------------------------------------------- araç

    private ToolResult vehicleStatus() {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        int speed = Math.max(0, ComfortCommands.readSpeedKmh());
        int gear = ComfortCommands.readGear();
        int power = ComfortCommands.readRaw(
                VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_SYSTEM_POWER_MODE);
        int posi = ComfortCommands.readSunroofPosition();
        int shade = ComfortCommands.readRaw(VDEventCarInfo.MODULE_CAR_SETTING, 10);
        float outside = ComfortCommands.readOutsideTempC();
        int range = ComfortCommands.readRaw(
                VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_ENDURANCE_KM);
        int pmIn = ComfortCommands.readRaw(
                VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_PM25_INCAR);
        int pmOut = ComfortCommands.readRaw(
                VDEventCarInfo.MODULE_HVAC, HvacID.ID_AC_PM25_OUTCAR);
        StringBuilder state = new StringBuilder(128);
        state.append("{\"speed\":").append(speed)
                .append(",\"gear\":\"").append(gearLabel(gear)).append('"')
                .append(",\"powerMode\":").append(
                        power == ComfortCommands.INVALID_TEMP ? -1 : power)
                .append(",\"sunroofOpen\":").append(posi > 1)
                .append(",\"sunshadeOpen\":").append(shade == 2);
        if (range != ComfortCommands.INVALID_TEMP && range >= 0) {
            state.append(",\"rangeKm\":").append(range);
        }
        if (pmIn != ComfortCommands.INVALID_TEMP && pmIn > 0 && pmIn != 0xFFFF) {
            state.append(",\"pm25In\":").append(pmIn);
        }
        if (pmOut != ComfortCommands.INVALID_TEMP && pmOut > 0 && pmOut != 0xFFFF) {
            state.append(",\"pm25Out\":").append(pmOut);
        }
        if (!Float.isNaN(outside)) {
            state.append(String.format(Locale.US, ",\"outsideTempC\":%.1f", outside));
        }
        state.append('}');
        StringBuilder message = new StringBuilder();
        message.append("Hız ").append(speed).append(" kilometre saat, vites ")
                .append(gearLabel(gear));
        if (!Float.isNaN(outside)) {
            message.append(", dış sıcaklık ").append(Math.round(outside)).append(" derece");
        }
        if (range != ComfortCommands.INVALID_TEMP && range >= 0) {
            message.append(", menzil ").append(range).append(" kilometre");
        }
        message.append(". Cam tavan ").append(posi > 1 ? "açık" : "kapalı").append('.');
        return ToolResult.ok(message.toString(), state.toString());
    }

    private static String gearLabel(int gear) {
        if (gear == 1) {
            return "P";
        }
        if (gear == 2) {
            return "R";
        }
        if (gear == 3) {
            return "N";
        }
        if (gear > 3) {
            return "D" + (gear - 3);
        }
        return "?";
    }

    private ToolResult sunroofSet(String action) {
        VehicleSunroofController controller = VehicleSunroofController.getInstance(appContext);
        if (!controller.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        if ("open".equals(action)) {
            boolean ok = controller.openSunroof(true);
            return ok ? ToolResult.ok("Cam tavanı açıyorum.") : ToolResult.fail("Cam tavan açılamadı.");
        }
        if ("close".equals(action)) {
            boolean ok = controller.closeSunroof(true);
            return ok ? ToolResult.ok("Cam tavanı kapatıyorum.") : ToolResult.fail("Cam tavan kapanamadı.");
        }
        if ("vent".equals(action)) {
            boolean ok = controller.tiltSunroof();
            return ok ? ToolResult.ok("Cam tavanı havalandırıyorum.")
                    : ToolResult.fail("Cam tavan havalandırılamadı.");
        }
        return ToolResult.fail("Geçersiz cam tavan işlemi.");
    }

    private ToolResult sunshadeSet(String action) {
        VehicleSunroofController controller = VehicleSunroofController.getInstance(appContext);
        if (!controller.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        if ("open".equals(action)) {
            return controller.openSunshadeOnly() ? ToolResult.ok("Güneşliği açıyorum.")
                    : ToolResult.fail("Güneşlik açılamadı.");
        }
        if ("close".equals(action)) {
            return controller.closeSunshadeOnly() ? ToolResult.ok("Güneşliği kapatıyorum.")
                    : ToolResult.fail("Güneşlik kapanamadı.");
        }
        return ToolResult.fail("Geçersiz güneşlik işlemi.");
    }

    private ToolResult sunroofStatus() {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        int posi = ComfortCommands.readSunroofPosition();
        int shade = ComfortCommands.readRaw(VDEventCarInfo.MODULE_CAR_SETTING, 10);
        String state = "{\"sunroofOpen\":" + (posi > 1)
                + ",\"sunshadeOpen\":" + (shade == 2)
                + ",\"positions\":" + posi + "}";
        String message = "Cam tavan " + (posi > 1 ? "açık" : "kapalı")
                + ", güneşlik " + (shade == 2 ? "açık" : "kapalı") + ".";
        return ToolResult.ok(message, state);
    }

    private ToolResult recirculationSet(boolean on) {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        if (!ComfortCommands.isHvacRunning()) {
            return ToolResult.fail("Klima kapalı; önce klimayı aç.");
        }
        ComfortCommands.sendRecirculation(on);
        return ToolResult.ok(on ? "İç sirkülasyonu açıyorum." : "Dış havaya alıyorum.");
    }

    private ToolResult seatLevel(String seat, String levelText, boolean heat, boolean massage) {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        if (!ComfortCommands.isEngineRunning()) {
            return ToolResult.fail("Motor çalışmıyor.");
        }
        int level = parseInt(levelText, 0);
        boolean driver = "driver".equals(seat);
        int id;
        if (massage) {
            id = driver ? 198 : 114;
        } else if (heat) {
            id = driver ? 95 : 96; // CAR_SETTING sürücü/yolcu ısıtma
        } else {
            id = driver ? 93 : 94; // havalandırma
        }
        ComfortCommands.sendSeatLevel(id, level);
        String label = massage ? "masaj" : (heat ? "ısıtma" : "havalandırma");
        String seatLabel = driver ? "sürücü" : "yolcu";
        return level == 0
                ? ToolResult.ok(seatLabel + " " + label + " kapatıldı.")
                : ToolResult.ok(seatLabel + " " + label + " " + level + ". seviye.");
    }

    private ToolResult steeringHeat(boolean on) {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        boolean current = ComfortCommands.isSteeringHeatOn();
        if (current == on) {
            return ToolResult.ok(on ? "Direksiyon ısıtma zaten açık."
                    : "Direksiyon ısıtma zaten kapalı.");
        }
        ComfortCommands.toggleSteeringHeat();
        return ToolResult.ok(on ? "Direksiyon ısıtmayı açıyorum." : "Direksiyon ısıtmayı kapatıyorum.");
    }

    private ToolResult seatMemory(String slotText) {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        int slot = parseInt(slotText, 1);
        if (!ComfortCommands.isSeatMemoryAvailable()) {
            return ToolResult.fail("Koltuk hafızası yalnız park halinde çalışır.");
        }
        ComfortCommands.restoreSeatMemory(slot);
        return ToolResult.ok("Koltuk hafızası " + slot + ". slot geri yükleniyor.");
    }

    private ToolResult driveMode(String mode) {
        if (!ComfortCommands.isConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        int value;
        String label;
        switch (mode == null ? "" : mode) {
            case "eco":
                value = 0;
                label = "Eko";
                break;
            case "sport":
                value = 2;
                label = "Spor";
                break;
            case "snow":
                value = 3;
                label = "Kar";
                break;
            case "mud":
                value = 4;
                label = "Çamur";
                break;
            case "offroad":
                value = 5;
                label = "Arazi";
                break;
            case "sand":
                value = 7;
                label = "Kum";
                break;
            default:
                value = 1;
                label = "Normal";
        }
        CarInfoProxy.getInstance().sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY,
                NewEnergyID.ID_DRIVE_MODE, value);
        return ToolResult.ok(label + " sürüş moduna geçiyorum.");
    }

    private ToolResult doorLock(String action) {
        VehicleQuickControls controls = VehicleQuickControls.getInstance(appContext);
        if (!controls.isCarInfoConnected()) {
            return ToolResult.fail("Araç bağlantısı yok.");
        }
        boolean lock = !"unlock".equalsIgnoreCase(action);
        controls.setCentralLock(lock);
        return ToolResult.ok(lock ? "Kapıları kilitliyorum." : "Kapıların kilidini açıyorum.");
    }

    // ------------------------------------------------------------ uygulama

    private ToolResult mediaControl(String action) {
        MediaController controller = activeMediaController();
        if (controller == null) {
            return ToolResult.fail("Aktif medya yok. Bildirim erişimi gerekli olabilir.");
        }
        PlaybackState state = controller.getPlaybackState();
        switch (action == null ? "" : action) {
            case "next":
                controller.getTransportControls().skipToNext();
                return ToolResult.ok("Sonraki parça.");
            case "previous":
                controller.getTransportControls().skipToPrevious();
                return ToolResult.ok("Önceki parça.");
            case "play":
                controller.getTransportControls().play();
                return ToolResult.ok("Oynatıyorum.");
            case "pause":
                controller.getTransportControls().pause();
                return ToolResult.ok("Duraklatıyorum.");
            default:
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    controller.getTransportControls().pause();
                    return ToolResult.ok("Duraklatıyorum.");
                }
                controller.getTransportControls().play();
                return ToolResult.ok("Oynatıyorum.");
        }
    }

    private MediaController activeMediaController() {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return null;
            }
            ComponentName component = new ComponentName(
                    appContext, LauncherMediaNotificationListener.class);
            List<MediaController> controllers = manager.getActiveSessions(component);
            if (controllers == null || controllers.isEmpty()) {
                return null;
            }
            for (MediaController controller : controllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    return controller;
                }
            }
            return controllers.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    private ToolResult volume(String action, String levelText) {
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return ToolResult.fail("Ses servisi bulunamadı.");
        }
        if ("percent".equals(action)) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int percent = Math.max(0, Math.min(100, parseInt(levelText, 50)));
            int index = Math.round(max * percent / 100f);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
            return ToolResult.ok("Ses yüzde " + percent + ".");
        }
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                "down".equals(action) ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE, 0);
        return ToolResult.ok("down".equals(action) ? "Sesi azaltıyorum." : "Sesi artırıyorum.");
    }

    private ToolResult navigateTo(String destination) {
        String target = destination == null ? "" : destination.trim();
        if (target.isEmpty()) {
            return ToolResult.fail("Hedef anlaşılmadı.");
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("google.navigation:q="
                            + android.net.Uri.encode(target)));
            intent.setPackage("com.google.android.apps.maps");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PackageManager pm = appContext.getPackageManager();
            if (pm.resolveActivity(intent, 0) == null) {
                intent = new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("geo:0,0?q=" + android.net.Uri.encode(target)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            appContext.startActivity(intent);
            return ToolResult.ok(target + " için navigasyonu başlatıyorum.");
        } catch (Throwable t) {
            return ToolResult.fail("Navigasyon açılamadı.");
        }
    }

    private ToolResult clusterNav(boolean open) {
        ClusterDisplayManager manager = new ClusterDisplayManager(appContext,
                new ClusterDisplayManager.ClusterCallback() {
                    @Override
                    public void onNavigationStateChanged(boolean isOpen) {
                    }

                    @Override
                    public String getTargetPackage() {
                        return com.eyups.roaddeck.util.TargetPackageStore.read(appContext);
                    }

                    @Override
                    public void log(String message) {
                    }
                });
        if (open) {
            manager.openClusterDisplay();
            return ToolResult.ok("Gösterge paneli navigasyonunu açıyorum.");
        }
        manager.closeClusterDisplay(true);
        return ToolResult.ok("Gösterge paneli navigasyonunu kapatıyorum.");
    }

    private ToolResult openApp(String name) {
        String query = WakeWordNormalizer.normalize(name);
        if (query.isEmpty()) {
            return ToolResult.fail("Uygulama adı anlaşılmadı.");
        }
        PackageManager pm = appContext.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcher, 0);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo info : apps) {
            String label = WakeWordNormalizer.normalize(
                    String.valueOf(info.loadLabel(pm)));
            if (label.isEmpty()) {
                continue;
            }
            int score = 0;
            if (label.equals(query)) {
                score = 100;
            } else if (label.startsWith(query)) {
                score = 80;
            } else if (label.contains(query)) {
                score = 60;
            } else if (query.contains(label)) {
                score = 40;
            }
            if (score > bestScore) {
                bestScore = score;
                best = info;
            }
        }
        if (best == null) {
            return ToolResult.fail("Uygulama bulunamadı: " + name);
        }
        String packageName = best.activityInfo.packageName;
        if (appContext.getPackageName().equals(packageName)) {
            return ToolResult.fail("Kendimi açamam; RoadDeck zaten burada.");
        }
        try {
            Intent open = pm.getLaunchIntentForPackage(packageName);
            if (open == null) {
                return ToolResult.fail("Uygulama açılamıyor.");
            }
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(open);
            return ToolResult.ok(String.valueOf(best.loadLabel(pm)) + " açılıyor.");
        } catch (Throwable t) {
            return ToolResult.fail("Uygulama açılamadı.");
        }
    }

    private ToolResult equalizer(String preset) {
        try {
            AudioEqualizerController.State state = AudioEqualizerController.read(appContext);
            if (state == null || !state.supported) {
                return ToolResult.fail("Ekolayzer desteklenmiyor.");
            }
            int[] bands = AudioEqualizerController.bandsForPreset(preset, state.centerHz,
                    state.minLevelMb, state.maxLevelMb);
            AudioEqualizerController.update(appContext, true, state.gainMb, bands, preset);
            return ToolResult.ok("Ekolayzer " + preset + " ayarına geçti.");
        } catch (Throwable t) {
            return ToolResult.fail("Ekolayzer uygulanamadı.");
        }
    }

    private ToolResult weather() {
        OpenMeteoWeatherClient client = new OpenMeteoWeatherClient(appContext);
        try {
            OpenMeteoWeatherSnapshot snapshot = client.getLastSnapshot();
            if (snapshot == null) {
                return ToolResult.fail("Hava durumu verisi yok.");
            }
            String message = "Hava " + Math.round(snapshot.temperatureC) + " derece, rüzgar "
                    + Math.round(snapshot.windSpeedKmh) + " kilometre saat.";
            String state = String.format(Locale.US,
                    "{\"tempC\":%.1f,\"windKmh\":%.1f,\"code\":%d,\"day\":%b}",
                    snapshot.temperatureC, snapshot.windSpeedKmh, snapshot.weatherCode,
                    snapshot.day);
            return ToolResult.ok(message, state);
        } finally {
            client.destroy();
        }
    }

    private ToolResult drivingSummary() {
        List<DrivingTrip> trips = DrivingTripStore.loadHistory(appContext);
        if (trips == null || trips.isEmpty()) {
            return ToolResult.fail("Kayıtlı sürüş yok.");
        }
        DrivingTrip last = trips.get(0);
        for (DrivingTrip trip : trips) {
            if (trip.startTimeMs > last.startTimeMs) {
                last = trip;
            }
        }
        String message = last.title + ". Puan " + last.score + ", "
                + String.format(Locale.US, "%.1f", last.distanceKm) + " kilometre.";
        String state = "{\"score\":" + last.score + ",\"km\":"
                + String.format(Locale.US, "%.1f", last.distanceKm) + "}";
        return ToolResult.ok(message, state);
    }

    private ToolResult timeDate() {
        Locale tr = new Locale("tr", "TR");
        Date now = new Date();
        String time = new SimpleDateFormat("HH:mm", tr).format(now);
        String date = new SimpleDateFormat("d MMMM EEEE", tr).format(now);
        return ToolResult.ok("Saat " + time + ", " + date + ".");
    }

    private ToolResult lastNavInstruction() {
        GoogleMapsNavSnapshot snapshot = GoogleMapsNavNotificationCoordinator.getLastSnapshot();
        if (snapshot == null || !snapshot.active) {
            return ToolResult.fail("Aktif navigasyon yok.");
        }
        String line = snapshot.formatManeuverDistanceLine();
        if (line == null || line.trim().isEmpty()) {
            line = snapshot.formatSummaryLine();
        }
        return ToolResult.ok(line);
    }

    private ToolResult assistantControl(String action) {
        if ("clear_history".equals(action)) {
            if (ui != null) {
                ui.clearHistory();
            }
            return ToolResult.ok("Geçmişi temizledim.");
        }
        if ("disable".equals(action)) {
            if (ui != null) {
                ui.disableAssistant();
            }
            return ToolResult.ok("Asistanı kapatıyorum.");
        }
        return ToolResult.ok("Tamam, duruyorum.");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** Analiz/telemetri için: araç yazımı yapılmadığını doğrulamada kullanılır. */
    public static boolean isAssistantVoiceAvailable(Context context) {
        return AssistantPrefs.isEnabled(context);
    }
}
