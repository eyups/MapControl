package com.mapcontrol.assistant.tools;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Asistan güvenlik politikası: saf Java karar tablosu, JVM unit test'li.
 *
 * <ul>
 *   <li>{@link Kind#ALLOW} — okuma, medya, navigasyon, ekran açma, doğrulanmış konfor yazımları.</li>
 *   <li>{@link Kind#CONFIRM} — kilit açma, sürüş modu, koltuk hafızası, tüm camlar.</li>
 *   <li>{@link Kind#BLOCK} — ADAS/güvenlik sistemleri, ham ID yazımı, uygulama kaldırma,
 *       araç bağlantısı yokken veya güç modu ON değilken yazma.</li>
 * </ul>
 */
public final class AssistantSafetyPolicy {

    public enum Kind {
        ALLOW, CONFIRM, BLOCK
    }

    public static final class Decision {
        public final Kind kind;
        public final String reason;
        public final String spoken;

        Decision(Kind kind, String reason, String spoken) {
            this.kind = kind;
            this.reason = reason;
            this.spoken = spoken;
        }

        public boolean isAllowed() {
            return kind == Kind.ALLOW;
        }

        public boolean isBlocked() {
            return kind == Kind.BLOCK;
        }

        public boolean needsConfirmation() {
            return kind == Kind.CONFIRM;
        }

        @Override
        public String toString() {
            return kind + "(" + reason + ")";
        }
    }

    /** Aracın direksiyon/klima/konfor ayarlarına yazan araçlar. */
    private static final Set<String> WRITE_TOOLS = new HashSet<>(Arrays.asList(
            "sunroof_set", "sunshade_set", "window_set", "recirculation_set",
            "climate_set", "seat_heat_set", "seat_vent_set", "seat_massage_set",
            "steering_heat_set", "seat_memory_restore", "drive_mode_set", "door_lock_set"));

    /** Asla etkinleşmeyen/yürütülmeyen araçlar (model uydursa bile). */
    private static final Set<String> BLOCKED_TOOLS = new HashSet<>(Arrays.asList(
            "adas_set", "aeb_set", "fcw_set", "ldw_set", "ldp_set", "iss_set",
            "raw_vdbus_write", "write_raw", "app_uninstall", "uninstall_app",
            "disable_safety", "set_speed_limit"));

    /** Kayıtlı araçlar; listede olmayan ad (model uydurması) reddedilir. */
    private static final Set<String> KNOWN_TOOLS = new HashSet<>(Arrays.asList(
            "report_transcript", "vehicle_status", "sunroof_set", "sunshade_set",
            "sunroof_status", "recirculation_set", "seat_heat_set", "seat_vent_set",
            "seat_massage_set", "steering_heat_set", "seat_memory_restore",
            "drive_mode_set", "door_lock_set", "window_set", "climate_set",
            "media_control", "volume_set", "navigate_to", "cluster_nav_toggle",
            "open_app", "open_screen", "equalizer_preset", "weather_now",
            "driving_report_summary", "time_date", "read_last_nav_instruction",
            "assistant_control"));

    /** Onay gerektiren yazımlar (araç duruyorken bile). */
    private static final Set<String> CONFIRM_TOOLS = new HashSet<>(Arrays.asList(
            "drive_mode_set", "seat_memory_restore"));

    private AssistantSafetyPolicy() {
    }

    public static boolean isWriteTool(String tool) {
        return tool != null && WRITE_TOOLS.contains(tool);
    }

    public static boolean isBlockedTool(String tool) {
        if (tool == null) {
            return true;
        }
        if (BLOCKED_TOOLS.contains(tool)) {
            return true;
        }
        // adas_*, safety_* ve ham ID yazımı adları her zaman reddedilir.
        return tool.startsWith("adas_") || tool.startsWith("safety_")
                || tool.startsWith("raw_") || tool.contains("uninstall");
    }

    /**
     * @param tool araç adı (kayıtlı olmayan ad → BLOCK)
     * @param args doğrulanmış argümanlar (null olabilir)
     * @param ctx  araç bağlamı (null → bilinmiyor kabul edilir)
     */
    public static Decision evaluate(String tool, Map<String, String> args, VehicleContext ctx) {
        if (tool == null || tool.trim().isEmpty()) {
            return new Decision(Kind.BLOCK, "missing_tool", "Bu işlemi yapamıyorum.");
        }
        if (isBlockedTool(tool)) {
            return new Decision(Kind.BLOCK, "blocked_tool",
                    "Güvenlik sistemlerini sesle değiştirmiyorum.");
        }
        if (!KNOWN_TOOLS.contains(tool)) {
            return new Decision(Kind.BLOCK, "unknown_tool", "Bu işlemi yapamıyorum.");
        }
        if (ctx == null) {
            ctx = VehicleContext.unknown();
        }
        if (!isWriteTool(tool)) {
            // Okuma, medya, navigasyon, uygulama açma: doğrudan.
            return new Decision(Kind.ALLOW, "read_only", "");
        }

        if (!ctx.connected) {
            return new Decision(Kind.BLOCK, "no_vehicle_link", "Araç bağlantısı yok.");
        }
        if (!ctx.isOn()) {
            return new Decision(Kind.BLOCK, "power_off", "Araç çalışmıyor.");
        }

        if ("door_lock_set".equals(tool)) {
            boolean unlock = "unlock".equalsIgnoreCase(value(args, "action"));
            if (unlock && ctx.isMoving()) {
                return new Decision(Kind.BLOCK, "unlock_while_moving",
                        "Hareket halinde kilidi açamam.");
            }
            if (unlock) {
                return new Decision(Kind.CONFIRM, "unlock_confirm", "Kilidi açmamı onaylıyor musun?");
            }
            return new Decision(Kind.ALLOW, "lock", "");
        }

        if ("seat_memory_restore".equals(tool)) {
            if (!ctx.isParked() || ctx.isMoving()) {
                return new Decision(Kind.BLOCK, "seat_memory_not_parked",
                        "Koltuk hafızası yalnız park halinde çalışır.");
            }
            return new Decision(Kind.CONFIRM, "seat_memory_confirm",
                    "Koltuk hafızasını geri yüklememi onaylıyor musun?");
        }

        if ("window_set".equals(tool)) {
            String which = value(args, "which");
            String action = value(args, "action");
            if ("all".equalsIgnoreCase(which) && "open".equalsIgnoreCase(action)
                    && ctx.speedKmh > 80) {
                return new Decision(Kind.CONFIRM, "all_windows_fast",
                        "Yüksek hızda tüm camları açmamı onaylıyor musun?");
            }
            return new Decision(Kind.CONFIRM, "windows_confirm", "Camları açmamı onaylıyor musun?");
        }

        if (CONFIRM_TOOLS.contains(tool)) {
            return new Decision(Kind.CONFIRM, "confirm_tool", "Onaylıyor musun?");
        }

        // Doğrulanmış konfor yazımları (cam tavan, güneşlik, koltuk, direksiyon, sirkülasyon).
        return new Decision(Kind.ALLOW, "verified_write", "");
    }

    private static String value(Map<String, String> args, String key) {
        if (args == null) {
            return "";
        }
        String value = args.get(key);
        return value == null ? "" : value;
    }
}
