package com.mapcontrol.assistant.tools;

import android.content.Context;
import android.content.SharedPreferences;

import com.mapcontrol.assistant.AssistantPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini'ye sunulan araç kataloğu. Yalnızca doğrulanmış ID'ler etkin;
 * doğrulanmamış araçlar feature flag arkasında gizlidir. LLM ham modül/ID görmez.
 */
public final class AssistantToolRegistry {

    /** Doğrulanmamış cam/klima ID'leri; araç keşfi tamamlanana kadar kapalı. */
    public static final String KEY_UNVERIFIED = "assistantEnableUnverifiedTools";

    private final List<ToolSpec> specs = new ArrayList<>();

    public AssistantToolRegistry(Context context) {
        SharedPreferences prefs = AssistantPrefs.prefs(context);
        boolean unverified = prefs.getBoolean(KEY_UNVERIFIED, false);

        // Sesli istekte kullanıcının söylediğini chat balonu için geri bildir.
        specs.add(new ToolSpec("report_transcript",
                "Kullanıcının sesli isteğinde söylediği cümleyi aynen yaz. "
                        + "Her sesli istekte önce bunu çağır.")
                .param("text", ToolSpec.TYPE_STRING, "Duyulan cümle", null, true));

        specs.add(new ToolSpec("vehicle_status",
                "Hız, vites, güç modu, dış sıcaklık, cam tavan ve menzil durumunu okur."));

        specs.add(new ToolSpec("sunroof_set",
                "Cam tavanı açar, kapatır veya havalandırır.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"open", "close", "vent"}, true));

        specs.add(new ToolSpec("sunshade_set",
                "Cam tavan güneşliğini açar veya kapatır.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"open", "close"}, true));

        specs.add(new ToolSpec("sunroof_status",
                "Cam tavan ve güneşlik konumunu okur."));

        specs.add(new ToolSpec("recirculation_set",
                "Klima iç sirkülasyonunu açar veya kapatır.")
                .param("on", ToolSpec.TYPE_BOOLEAN, "true aç, false kapat", null, true));

        specs.add(new ToolSpec("seat_heat_set",
                "Koltuk ısıtmayı ayarlar; 0 kapalı, 1-3 seviye.")
                .param("seat", ToolSpec.TYPE_STRING, "Koltuk",
                        new String[]{"driver", "passenger"}, true)
                .param("level", ToolSpec.TYPE_INTEGER, "Seviye 0-3", null, true)
                .range(0, 3));

        specs.add(new ToolSpec("seat_vent_set",
                "Koltuk havalandırmayı ayarlar; 0 kapalı, 1-3 seviye.")
                .param("seat", ToolSpec.TYPE_STRING, "Koltuk",
                        new String[]{"driver", "passenger"}, true)
                .param("level", ToolSpec.TYPE_INTEGER, "Seviye 0-3", null, true)
                .range(0, 3));

        specs.add(new ToolSpec("seat_massage_set",
                "Koltuk masajını ayarlar; 0 kapalı, 1-3 seviye.")
                .param("seat", ToolSpec.TYPE_STRING, "Koltuk",
                        new String[]{"driver", "passenger"}, true)
                .param("level", ToolSpec.TYPE_INTEGER, "Seviye 0-3", null, true)
                .range(0, 3));

        specs.add(new ToolSpec("steering_heat_set",
                "Direksiyon ısıtmayı açar veya kapatır.")
                .param("on", ToolSpec.TYPE_BOOLEAN, "true aç, false kapat", null, true));

        specs.add(new ToolSpec("seat_memory_restore",
                "Koltuk ve ayna hafıza slotunu geri yükler; yalnız park halinde.")
                .param("slot", ToolSpec.TYPE_INTEGER, "Slot 1-3", null, true)
                .range(1, 3));

        specs.add(new ToolSpec("drive_mode_set",
                "Sürüş modunu değiştirir.")
                .param("mode", ToolSpec.TYPE_STRING, "Mod", new String[]{
                        "eco", "normal", "sport", "snow", "mud", "offroad", "sand"}, true));

        specs.add(new ToolSpec("door_lock_set",
                "Merkezi kilidi kilitler veya açar.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"lock", "unlock"}, true));

        if (unverified) {
            specs.add(new ToolSpec("window_set",
                    "Camları açar veya kapatır.")
                    .param("which", ToolSpec.TYPE_STRING, "Cam",
                            new String[]{"all", "front_left", "front_right",
                                    "rear_left", "rear_right"}, true)
                    .param("action", ToolSpec.TYPE_STRING, "İşlem",
                            new String[]{"open", "close", "vent"}, true));
            specs.add(new ToolSpec("climate_set",
                    "Klimayı açar/kapatır, sıcaklık veya fan seviyesi ayarlar.")
                    .param("on", ToolSpec.TYPE_BOOLEAN, "true aç, false kapat", null, false)
                    .param("temperature", ToolSpec.TYPE_INTEGER, "Hedef °C (16-30)", null, false)
                    .range(16, 30)
                    .param("fan", ToolSpec.TYPE_INTEGER, "Fan 1-7", null, false)
                    .range(1, 7));
        }

        specs.add(new ToolSpec("media_control",
                "Medyayı oynatır, duraklatır, sonraki/önceki parçaya geçer.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"play_pause", "play", "pause", "next", "previous"}, true));

        specs.add(new ToolSpec("volume_set",
                "Medya sesini artırır, azaltır veya yüzde olarak ayarlar.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"up", "down", "percent"}, true)
                .param("level", ToolSpec.TYPE_INTEGER, "Yüzde 0-100", null, false)
                .range(0, 100));

        specs.add(new ToolSpec("navigate_to",
                "Haritada adres veya yer adına navigasyon başlatır.")
                .param("destination", ToolSpec.TYPE_STRING, "Hedef", null, true));

        specs.add(new ToolSpec("cluster_nav_toggle",
                "Gösterge paneli navigasyon yansıtmasını açar veya kapatır.")
                .param("open", ToolSpec.TYPE_BOOLEAN, "true aç, false kapat", null, true));

        specs.add(new ToolSpec("open_app",
                "Yüklü bir uygulamayı adıyla açar.")
                .param("name", ToolSpec.TYPE_STRING, "Uygulama adı", null, true));

        specs.add(new ToolSpec("open_screen",
                "RoadDeck içindeki bir ekranı açar.")
                .param("screen", ToolSpec.TYPE_STRING, "Ekran", new String[]{
                        "camera360", "driving_report", "climate_automation", "sunroof",
                        "equalizer", "vehicle_info", "home", "settings"}, true));

        specs.add(new ToolSpec("equalizer_preset",
                "Ekolayzer hazır ayarını uygular.")
                .param("preset", ToolSpec.TYPE_STRING, "Ayar",
                        new String[]{"flat", "bass", "vocal", "treble"}, true));

        specs.add(new ToolSpec("weather_now",
                "Son bilinen hava durumunu okur."));

        specs.add(new ToolSpec("driving_report_summary",
                "Son sürüşün kısa özetini okur."));

        specs.add(new ToolSpec("time_date",
                "Saat ve tarihi okur."));

        specs.add(new ToolSpec("read_last_nav_instruction",
                "Son navigasyon yönergesini okur."));

        specs.add(new ToolSpec("assistant_control",
                "Asistanı durdurur, geçmişi temizler veya asistanı kapatır.")
                .param("action", ToolSpec.TYPE_STRING, "İşlem",
                        new String[]{"stop", "clear_history", "disable"}, true));
    }

    public List<ToolSpec> specs() {
        return new ArrayList<>(specs);
    }

    public ToolSpec find(String name) {
        if (name == null) {
            return null;
        }
        for (ToolSpec spec : specs) {
            if (spec.name.equals(name)) {
                return spec;
            }
        }
        return null;
    }

    public boolean isEnabledUnverified(Context context) {
        return AssistantPrefs.prefs(context).getBoolean(KEY_UNVERIFIED, false);
    }
}
