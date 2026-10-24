package com.mapcontrol.vehicle;

import com.desaysv.ivi.extra.project.carinfo.AccountID;
import com.desaysv.ivi.extra.project.carinfo.AmbientLightID;
import com.desaysv.ivi.extra.project.carinfo.AutoParkingID;
import com.desaysv.ivi.extra.project.carinfo.AvmID;
import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.DVRID;
import com.desaysv.ivi.extra.project.carinfo.ExternalAmpID;
import com.desaysv.ivi.extra.project.carinfo.HUDID;
import com.desaysv.ivi.extra.project.carinfo.HvacID;
import com.desaysv.ivi.extra.project.carinfo.MeterMalfunctionID;
import com.desaysv.ivi.extra.project.carinfo.NewEnergyID;
import com.desaysv.ivi.extra.project.carinfo.OtaID;
import com.desaysv.ivi.extra.project.carinfo.QueryID;
import com.desaysv.ivi.extra.project.carinfo.RadarID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.extra.project.carinfo.RvcID;
import com.desaysv.ivi.extra.project.carinfo.VrID;
import com.desaysv.ivi.extra.project.carinfo.proxy.CarInfoProxy;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Tüm CarInfo modüllerini ID bazında tarar; iki anlık görüntü arasındaki farkı çıkarır.
 * Okumalar bloklayabilir, arka thread'de çağrılmalıdır.
 */
public final class IdScanner {

    public static final int MAX_ID = 255;

    public static final class Module {
        public final int id;
        public final String label;
        final Class<?> idClass;
        /** Varsayılan taramaya dahil mi (yaygın kullanılan modüller). */
        public final boolean core;

        Module(int id, String label, Class<?> idClass, boolean core) {
            this.id = id;
            this.label = label;
            this.idClass = idClass;
            this.core = core;
        }
    }

    public static final class Snapshot {
        public final long timeMs = System.currentTimeMillis();
        final Map<Long, int[]> values = new LinkedHashMap<>();

        public int size() {
            return values.size();
        }
    }

    public static final class Change {
        public final int module;
        public final int id;
        public final String name;
        public final int[] before;
        public final int[] after;

        Change(int module, int id, String name, int[] before, int[] after) {
            this.module = module;
            this.id = id;
            this.name = name;
            this.before = before;
            this.after = after;
        }

        public boolean writable() {
            return module != VDEventCarInfo.MODULE_READONLY_INFO;
        }
    }

    private static final List<Module> MODULES = Collections.unmodifiableList(Arrays.asList(
            new Module(VDEventCarInfo.MODULE_READONLY_INFO, "READONLY", ReadOnlyID.class, true),
            new Module(VDEventCarInfo.MODULE_CAR_SETTING, "CAR_SETTING", CarSettingID.class, true),
            new Module(VDEventCarInfo.MODULE_NEW_ENERGY, "NEW_ENERGY", NewEnergyID.class, true),
            new Module(VDEventCarInfo.MODULE_HVAC, "HVAC", HvacID.class, true),
            new Module(VDEventCarInfo.MODULE_RADAR, "RADAR", RadarID.class, true),
            new Module(VDEventCarInfo.MODULE_AVM, "AVM", AvmID.class, true),
            new Module(VDEventCarInfo.MODULE_VR, "VR", VrID.class, true),
            new Module(VDEventCarInfo.MODULE_HUD, "HUD", HUDID.class, false),
            new Module(VDEventCarInfo.MODULE_DVR, "DVR", DVRID.class, false),
            new Module(VDEventCarInfo.MODULE_EXTERNAL_AMPLIFIER, "EXT_AMP", ExternalAmpID.class, false),
            new Module(VDEventCarInfo.MODULE_METER_MALFUNCTION, "METER_MALF", MeterMalfunctionID.class, false),
            new Module(VDEventCarInfo.MODULE_RVC, "RVC", RvcID.class, false),
            new Module(VDEventCarInfo.MODULE_AUTO_PARKING, "AUTO_PARKING", AutoParkingID.class, false),
            new Module(VDEventCarInfo.MODULE_AMBIENT_LIGHT, "AMBIENT_LIGHT", AmbientLightID.class, false),
            new Module(VDEventCarInfo.MODULE_ACCOUNT, "ACCOUNT", AccountID.class, false),
            new Module(VDEventCarInfo.MODULE_OTA, "OTA", OtaID.class, false),
            new Module(VDEventCarInfo.MODULE_QUERY, "QUERY", QueryID.class, false)));

    private static final Map<Integer, Map<Integer, String>> NAME_CACHE = new HashMap<>();

    private IdScanner() {
    }

    public static List<Module> modules() {
        return MODULES;
    }

    public static String moduleLabel(int moduleId) {
        for (Module m : MODULES) {
            if (m.id == moduleId) {
                return m.label;
            }
        }
        return String.valueOf(moduleId);
    }

    /** ID sabit adı (ör. ID_CAR_SUNSHADE); bilinmiyorsa boş string. */
    public static synchronized String nameOf(int moduleId, int id) {
        Map<Integer, String> names = NAME_CACHE.get(moduleId);
        if (names == null) {
            names = new HashMap<>();
            for (Module m : MODULES) {
                if (m.id != moduleId) {
                    continue;
                }
                for (Field f : m.idClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                        try {
                            int value = f.getInt(null);
                            if (!names.containsKey(value)) {
                                names.put(value, f.getName());
                            }
                        } catch (IllegalAccessException ignored) {
                        }
                    }
                }
            }
            NAME_CACHE.put(moduleId, names);
        }
        String name = names.get(id);
        return name == null ? "" : name;
    }

    /** Seçili modülleri tarar; her okuma bloklayabileceğinden {@code cancelled} sık kontrol edilir. */
    public static Snapshot scan(Collection<Integer> moduleIds, IntConsumer progressPercent, BooleanSupplier cancelled) {
        Snapshot snapshot = new Snapshot();
        int total = moduleIds.size() * (MAX_ID + 1);
        int done = 0;
        for (int module : moduleIds) {
            for (int id = 0; id <= MAX_ID; id++) {
                if (cancelled.getAsBoolean()) {
                    return snapshot;
                }
                try {
                    int[] raw = CarInfoProxy.getInstance().getItemValues(module, id);
                    if (raw != null && raw.length > 0) {
                        snapshot.values.put(key(module, id), raw.clone());
                    }
                } catch (Throwable ignored) {
                }
                done++;
                if (progressPercent != null && done % 16 == 0) {
                    progressPercent.accept(done * 100 / total);
                }
            }
        }
        if (progressPercent != null) {
            progressPercent.accept(100);
        }
        return snapshot;
    }

    public static List<Change> diff(Snapshot before, Snapshot after) {
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Long, int[]> entry : after.values.entrySet()) {
            int[] old = before.values.get(entry.getKey());
            if (old == null || !Arrays.equals(old, entry.getValue())) {
                int module = (int) (entry.getKey() >> 16);
                int id = (int) (entry.getKey() & 0xFFFF);
                changes.add(new Change(module, id, nameOf(module, id),
                        old == null ? new int[0] : old, entry.getValue()));
            }
        }
        return changes;
    }

    public static String format(int[] values) {
        if (values == null || values.length == 0) {
            return "-";
        }
        return values.length == 1 ? String.valueOf(values[0]) : Arrays.toString(values);
    }

    public static String describe(Change c) {
        String name = c.name.isEmpty() ? "?" : c.name;
        return String.format(Locale.US, "[%s] %d %s: %s -> %s",
                moduleLabel(c.module), c.id, name, format(c.before), format(c.after));
    }

    public static String changesCsv(List<Change> changes) {
        StringBuilder sb = new StringBuilder("module,module_id,id,name,before,after\n");
        for (Change c : changes) {
            sb.append(moduleLabel(c.module)).append(',').append(c.module).append(',').append(c.id).append(',')
                    .append(c.name).append(',').append(csvCell(format(c.before))).append(',')
                    .append(csvCell(format(c.after))).append('\n');
        }
        return sb.toString();
    }

    public static String snapshotCsv(Snapshot snapshot) {
        StringBuilder sb = new StringBuilder("module,module_id,id,name,value\n");
        for (Map.Entry<Long, int[]> entry : snapshot.values.entrySet()) {
            int module = (int) (entry.getKey() >> 16);
            int id = (int) (entry.getKey() & 0xFFFF);
            sb.append(moduleLabel(module)).append(',').append(module).append(',').append(id).append(',')
                    .append(nameOf(module, id)).append(',').append(csvCell(format(entry.getValue()))).append('\n');
        }
        return sb.toString();
    }

    private static String csvCell(String value) {
        return value.contains(",") ? "\"" + value + "\"" : value;
    }

    private static long key(int module, int id) {
        return ((long) module << 16) | (id & 0xFFFF);
    }
}
