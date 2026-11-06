package com.eyups.roaddeck.assistant.command;

import com.eyups.roaddeck.assistant.wake.WakeWordNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Vosk gramerinden gelen kisa metni dogrulanmis arac komutlarina cevirir. */
public final class LocalCommandParser {

    public static final class Command {
        public final String tool;
        public final Map<String, String> args;
        public final String reply;
        public Command(String tool, Map<String, String> args, String reply) {
            this.tool = tool; this.args = args; this.reply = reply;
        }
    }

    private static final Locale TR = new Locale("tr", "TR");

    private LocalCommandParser() {}

    /** Vosk gramerine verilecek ifadeler. */
    public static String[] phrases() {
        return new String[]{
                "cam tavanı aç", "cam tavanı kapat", "cam tavanı havalandırma",
                "tavanı aç", "tavanı kapat", "perdeyi aç", "perdeyi kapat",
                "cam tavan durumu", "sirkülasyon aç", "sirkülasyon kapat",
                "iç hava aç", "iç hava kapat",
                "sürücü koltuğunu ısıt", "yolcu koltuğunu ısıt",
                "direksiyonu ısıt", "direksiyon kapat",
                "spor moda geç", "ekonomi moda geç", "normal moda geç", "kar moda geç",
                "kapıları kilit", "kapıları aç",
                "müzik çal", "müziği durdur", "sonraki şarkı", "önceki şarkı",
                "sesi aç", "sesi kapat", "sesi yükselt", "sesi azalt",
                "araç durumu", "hız kaç", "menzil ne kadar"
        };
    }

    public static Command parse(String raw) {
        String text = WakeWordNormalizer.normalize(raw);
        if (text.isEmpty()) return null;
        // Vosk bazen "bir" gibi dolgu ekler; basta temizle.
        text = text.replaceFirst("^(bir |acaba |lütfen )+", "").trim();

        if (has(text, "cam tavan", "tavan")) {
            if (has(text, "durum", "açık mı", "kapalı mı")) return cmd("sunroof_status", null, "Cam tavan durumuna bakıyorum.");
            if (has(text, "havalandır", "arala")) return cmd("sunroof_set", arg("action", "vent"), "Cam tavanı havalandırıyorum.");
            if (has(text, "kapat")) return cmd("sunroof_set", arg("action", "close"), "Cam tavanı kapatıyorum.");
            if (has(text, "aç")) return cmd("sunroof_set", arg("action", "open"), "Cam tavanı açıyorum.");
        }
        if (has(text, "güneşlik", "güneşliği", "perde")) {
            if (has(text, "kapat")) return cmd("sunshade_set", arg("action", "close"), "Güneşliği kapatıyorum.");
            if (has(text, "aç")) return cmd("sunshade_set", arg("action", "open"), "Güneşliği açıyorum.");
        }
        if (has(text, "sirkülasyon", "iç hava", "ic hava")) {
            boolean on = !has(text, "kapat", "kapatma");
            return cmd("recirculation_set", arg("on", on ? "true" : "false"),
                    on ? "İç sirkülasyonu açıyorum." : "İç sirkülasyonu kapatıyorum.");
        }
        if (has(text, "koltuk", "koltuğ", "koltugu")) {
            String seat = has(text, "yolcu", "sağ", "sag") ? "passenger" : "driver";
            int level = level(text, 2);
            if (has(text, "soğut", "sogut", "havalandır")) {
                return cmd("seat_vent_set", args("seat", seat, "level", String.valueOf(level)), "Koltuk havalandırmasını ayarlıyorum.");
            }
            if (has(text, "masaj")) {
                return cmd("seat_massage_set", args("seat", seat, "level", String.valueOf(level)), "Koltuk masajını ayarlıyorum.");
            }
            if (has(text, "ısıt", "isit")) {
                return cmd("seat_heat_set", args("seat", seat, "level", String.valueOf(level)), "Koltuk ısıtmayı ayarlıyorum.");
            }
        }
        if (has(text, "direksiyon")) {
            boolean on = !has(text, "kapat", "kapatma");
            return cmd("steering_heat_set", arg("on", on ? "true" : "false"),
                    on ? "Direksiyon ısıtmayı açıyorum." : "Direksiyon ısıtmayı kapatıyorum.");
        }
        if (has(text, "mod")) {
            String mode = null;
            if (has(text, "spor")) mode = "sport";
            else if (has(text, "eko", "econom", "ekonomi")) mode = "eco";
            else if (has(text, "kar", "snow")) mode = "snow";
            else if (has(text, "normal")) mode = "normal";
            if (mode != null) return cmd("drive_mode_set", arg("mode", mode), "Sürüş modunu değiştiriyorum.");
        }
        if (has(text, "kilitle", "kilit")) return cmd("door_lock_set", arg("action", "lock"), "Kapıları kilitliyorum.");
        if (has(text, "kapıları aç", "kilit aç", "kilidi aç")) return cmd("door_lock_set", arg("action", "unlock"), "Kapıların kilidini açıyorum.");
        if (has(text, "müzik", "müziğ", "muzik", "şarkı", "sarki")) {
            if (has(text, "duraklat", "durdur", "kapat")) return cmd("media_control", arg("action", "pause"), "Müziği duraklatıyorum.");
            if (has(text, "sonraki", "ileri")) return cmd("media_control", arg("action", "next"), "Sonraki parçaya geçiyorum.");
            if (has(text, "önceki", "onceki", "geri")) return cmd("media_control", arg("action", "previous"), "Önceki parçaya geçiyorum.");
            return cmd("media_control", arg("action", "play"), "Müziği çalıyorum.");
        }
        if (has(text, "ses")) {
            if (has(text, "yükselt", "yukselt", "artır", "artir", "aç")) return cmd("volume_set", arg("action", "up"), "Sesi yükseltiyorum.");
            if (has(text, "azalt", "kıs", "kis", "kapat")) return cmd("volume_set", arg("action", "down"), "Sesi azaltıyorum.");
        }
        if (has(text, "araç durumu", "arac durumu", "hız", "hiz", "menzil", "vites")) {
            return cmd("vehicle_status", null, "Araç durumunu okuyorum.");
        }
        return null;
    }

    private static Command cmd(String tool, Map<String, String> args, String reply) {
        return new Command(tool, args == null ? new LinkedHashMap<>() : args, reply);
    }

    private static Map<String, String> arg(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static Map<String, String> args(String k1, String v1, String k2, String v2) {
        Map<String, String> m = arg(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static boolean has(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(WakeWordNormalizer.normalize(n))) return true;
        }
        return false;
    }

    private static int level(String text, int def) {
        if (text.contains("üç") || text.contains("uc")) return 3;
        if (text.contains("iki")) return 2;
        if (text.contains("bir")) return 1;
        for (int i = 3; i >= 0; i--) if (text.contains(String.valueOf(i))) return i;
        return def;
    }
}
