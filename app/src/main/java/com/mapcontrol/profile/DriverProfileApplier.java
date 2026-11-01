package com.mapcontrol.profile;

import android.content.Context;

import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;
import com.mapcontrol.vehicle.ComfortCommands;

import java.util.function.Consumer;

/** Aktif sürücü profilinin araç hafızasını uygular: OEM koltuk/ayna slotu ve profil maddeleri. */
public final class DriverProfileApplier {

    private static final long ITEM_SPACING_MS = 500L;

    private DriverProfileApplier() {
    }

    /** Kontak açılışında; yalnızca profilde "kontakta uygula" açıksa çalışır. Arka thread'de çağrılır. */
    public static void applyOnIgnition(Context context, Consumer<String> log) {
        DriverProfileManager.Profile profile = DriverProfileManager.get(context).getActive();
        if (profile == null || !profile.applyOnStart) {
            return;
        }
        apply(profile, log);
    }

    /** Elle uygulama ("Şimdi uygula"); aynı güvenlik koşullarına tabidir. Arka thread'de çağrılır. */
    public static void applyNow(Context context, Consumer<String> log) {
        DriverProfileManager.Profile profile = DriverProfileManager.get(context).getActive();
        if (profile != null) {
            apply(profile, log);
        }
    }

    /** Profil geri yüklemesinden sonra seçili sürüş modunu araçta uygular. */
    public static void applyDriveModeNow(Context context, Consumer<String> log) {
        DriverProfileManager.Profile profile = DriverProfileManager.get(context).getActive();
        if (profile == null) {
            return;
        }
        Object raw = DriverProfileManager.get(context).scopedSettings(profile.id)
                .get("driveModeSetting");
        if (!(raw instanceof Number)) {
            return;
        }
        int mode = ((Number) raw).intValue();
        if (mode < 0) {
            return;
        }
        if (!ComfortCommands.isConnected()) {
            log.accept("[Profil] " + profile.name + ": sürüş modu için araç bağlantısı yok");
            return;
        }
        int speed = ComfortCommands.readSpeedKmh();
        if (speed != 0) {
            log.accept("[Profil] " + profile.name + ": araç hareketli, sürüş modu uygulanmadı");
            return;
        }
        try {
            CarInfoProxy carInfo = CarInfoProxy.getInstance();
            if (!carInfo.isServiceConnnected()) {
                carInfo.init(context);
                log.accept("[Profil] " + profile.name + ": sürüş modu bağlantısı kuruluyor");
                return;
            }
            int value = (mode == 6 || mode == 7) ? 7 : mode;
            carInfo.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, 4, value);
            log.accept("[Profil] " + profile.name + ": sürüş modu uygulandı (" + mode + ")");
        } catch (Exception error) {
            log.accept("[Profil] " + profile.name + ": sürüş modu hatası " + error.getMessage());
        }
    }

    private static void apply(DriverProfileManager.Profile profile, Consumer<String> log) {
        if (!ComfortCommands.isConnected()) {
            log.accept("[Profil] " + profile.name + ": araç bağlantısı yok, uygulanmadı");
            return;
        }
        int speed = ComfortCommands.readSpeedKmh();
        if (speed != 0) {
            log.accept("[Profil] " + profile.name + ": araç hareketli veya hız okunamadı (" + speed + "), uygulanmadı");
            return;
        }
        if (profile.seatSlot >= 1 && profile.seatSlot <= ComfortCommands.SEAT_MEMORY_SLOTS) {
            if (ComfortCommands.isSeatMemoryAvailable()) {
                ComfortCommands.restoreSeatMemory(profile.seatSlot);
                log.accept("[Profil] " + profile.name + ": koltuk/ayna hafıza " + profile.seatSlot + " geri yüklendi");
            } else {
                log.accept("[Profil] " + profile.name + ": koltuk hafızası kullanılamıyor (vites P/N değil veya özellik kapalı)");
            }
        }
        int applied = 0;
        for (DriverProfileManager.MemoryItem item : profile.items) {
            if (!item.enabled) {
                continue;
            }
            ComfortCommands.writeRaw(item.module, item.id, item.value);
            applied++;
            log.accept("[Profil] " + profile.name + ": " + item.label + " = " + item.value);
            try {
                Thread.sleep(ITEM_SPACING_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.accept("[Profil] " + profile.name + " uygulandı (" + applied + " madde)");
    }
}
