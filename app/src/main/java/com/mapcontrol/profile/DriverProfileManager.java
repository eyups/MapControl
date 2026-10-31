package com.mapcontrol.profile;

import android.content.Context;
import android.content.SharedPreferences;

import com.mapcontrol.R;
import com.mapcontrol.util.TargetPackageStore;
import com.mapcontrol.vehicle.ComfortCommands;
import com.mapcontrol.vehicle.RecirculationAutomation;
import com.mapcontrol.vehicle.TemperatureAutomation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sürücü profilleri: hafıza niteliğindeki ayarlar profil başına saklanır, aktif profil değişince
 * ortak tercih dosyasına yüklenir; mevcut özellikler (hafıza modu, konfor, otomasyon) kodları değişmeden
 * kendi anahtarlarını okumaya devam eder. Aktif profildeki değişiklikler tercih dinleyicisiyle anında
 * profile yazılır.
 */
public final class DriverProfileManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_STATE = "driverProfilesState";

    private static final Set<String> SCOPED_KEYS = new HashSet<>(Arrays.asList(
            "driveModeSetting", "issSetting", "ldwSetting", "ldpSetting", "fcwSetting", "aebSetting",
            "cpdSetting", "spdLimitSetting",
            ComfortCommands.PREF_SUNROOF, ComfortCommands.PREF_SUNSHADE,
            RecirculationAutomation.PREF_ENABLED, TemperatureAutomation.PREF_ENABLED,
            "welcomeDesignId", "welcomeCustomText", "welcomeAudioFilePath", "welcomeAudioAutoPlay"));
    private static final String[] SCOPED_PREFIXES = {"tempAuto_", "climateAuto_"};
    private static final Set<String> BACKUP_PREF_KEYS = new HashSet<>(Arrays.asList(
            "welcomeDesignId", "welcomeCustomText", "welcomeAudioFilePath", "welcomeAudioAutoPlay"));

    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    /** Profile bağlı keyfi araç değeri (ID keşfinden eklenir); uygulanırken olduğu gibi yazılır. */
    public static final class MemoryItem {
        public final int module;
        public final int id;
        public String label;
        public int value;
        public boolean enabled;

        public MemoryItem(int module, int id, String label, int value, boolean enabled) {
            this.module = module;
            this.id = id;
            this.label = label;
            this.value = value;
            this.enabled = enabled;
        }
    }

    public static final class Profile {
        public final String id;
        public String name;
        /** OEM koltuk/ayna hafıza slotu: 0 yok, 1-3. */
        public int seatSlot;
        public boolean applyOnStart;
        /** Yüz tanıma karşılaması için üretilmiş metin ve ElevenLabs MP3 yolu. */
        public String welcomeMessage = "";
        public String welcomeAudioPath = "";
        /** {@link ProfileAvatars} indeksi: 0-9. */
        public int avatar;
        /** 1 = Bey, 2 = Hanım; eski kayıtlarda varsayılan Bey. */
        public int gender = GENDER_MALE;
        public final List<MemoryItem> items = new ArrayList<>();
        final JSONObject settings = new JSONObject();

        Profile(String id, String name) {
            this(id, name, 0);
        }

        Profile(String id, String name, int avatar) {
            this.id = id;
            this.name = name;
            this.avatar = ProfileAvatars.normalize(avatar);
        }
    }

    private static DriverProfileManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final List<Profile> profiles = new ArrayList<>();
    private String activeId;

    public static synchronized DriverProfileManager get(Context context) {
        if (instance == null) {
            instance = new DriverProfileManager(context.getApplicationContext());
        }
        return instance;
    }

    private DriverProfileManager(Context context) {
        this.appContext = context;
        this.prefs = TargetPackageStore.prefs(context);
        load();
        if (profiles.isEmpty()) {
            Profile first = new Profile(newId(), context.getString(R.string.profiles_default_name));
            captureScopedInto(first);
            profiles.add(first);
            activeId = first.id;
            save();
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public synchronized List<Profile> getProfiles() {
        return Collections.unmodifiableList(new ArrayList<>(profiles));
    }

    public synchronized Profile getActive() {
        return find(activeId);
    }

    public synchronized String getActiveId() {
        return activeId;
    }

    public synchronized Profile create(String name, boolean copyCurrentSettings) {
        return create(name, copyCurrentSettings, 0);
    }

    public synchronized Profile create(String name, boolean copyCurrentSettings, int avatar) {
        return create(name, copyCurrentSettings, avatar, GENDER_MALE);
    }

    public synchronized Profile create(String name, boolean copyCurrentSettings, int avatar,
            int gender) {
        syncActiveFromPrefs();
        Profile p = new Profile(newId(), name, avatar);
        p.gender = normalizeGender(gender);
        if (copyCurrentSettings) {
            captureScopedInto(p);
        }
        profiles.add(p);
        save();
        return p;
    }

    public synchronized void rename(String id, String name) {
        Profile p = find(id);
        if (p != null && !name.trim().isEmpty()) {
            p.name = name.trim();
            save();
        }
    }

    public synchronized void update(String id, String name, int avatar) {
        Profile p = find(id);
        update(id, name, avatar, p == null ? GENDER_MALE : p.gender);
    }

    public synchronized void update(String id, String name, int avatar, int gender) {
        Profile p = find(id);
        if (p == null) {
            return;
        }
        if (name != null && !name.trim().isEmpty()) {
            p.name = name.trim();
        }
        p.avatar = ProfileAvatars.normalize(avatar);
        p.gender = normalizeGender(gender);
        save();
    }

    public synchronized void setAvatar(String id, int avatar) {
        Profile p = find(id);
        if (p != null) {
            p.avatar = ProfileAvatars.normalize(avatar);
            save();
        }
    }

    /** Son profil silinemez. Aktif profil silinirse başka bir profil etkinleştirilir. */
    public synchronized boolean delete(String id) {
        if (profiles.size() <= 1) {
            return false;
        }
        Profile target = find(id);
        if (target == null) {
            return false;
        }
        if (id.equals(activeId)) {
            for (Profile other : profiles) {
                if (!other.id.equals(id)) {
                    activate(other.id);
                    break;
                }
            }
        }
        profiles.remove(target);
        save();
        return true;
    }

    /** Hafıza ayarlarını hedef profile geçirir; aktif profilin son hâli önce kendisine yazılır. */
    public synchronized boolean activate(String id) {
        Profile target = find(id);
        if (target == null || id.equals(activeId)) {
            return false;
        }
        syncActiveFromPrefs();
        activeId = id;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (isScoped(key)) {
                editor.remove(key);
            }
        }
        Iterator<String> keys = target.settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                putEncoded(editor, key, target.settings.getJSONObject(key));
            } catch (JSONException ignored) {
            }
        }
        editor.putString(PREF_STATE, serialize());
        editor.apply();
        return true;
    }

    public synchronized void setSeatSlot(String id, int slot) {
        Profile p = find(id);
        if (p != null) {
            p.seatSlot = Math.max(0, Math.min(ComfortCommands.SEAT_MEMORY_SLOTS, slot));
            save();
        }
    }

    /** Aktif profilin güncel scoped ayarlarını salt-okunur bir harita olarak verir. */
    public synchronized Map<String, Object> scopedSettings(String id) {
        Profile p = find(id);
        if (p == null) {
            return Collections.emptyMap();
        }
        if (id.equals(activeId)) {
            syncActiveFromPrefs();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        Iterator<String> keys = p.settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject encoded = p.settings.getJSONObject(key);
                result.put(key, encoded.opt("v"));
            } catch (JSONException ignored) {
            }
        }
        return result;
    }

    public synchronized void setWelcomeAudio(String id, String message, String audioPath) {
        Profile p = find(id);
        if (p == null) {
            return;
        }
        p.welcomeMessage = message == null ? "" : message;
        p.welcomeAudioPath = audioPath == null ? "" : audioPath;
        save();
    }

    public synchronized void setApplyOnStart(String id, boolean enabled) {
        Profile p = find(id);
        if (p != null) {
            p.applyOnStart = enabled;
            save();
        }
    }

    /** Aynı modül/ID zaten varsa değeri günceller. */
    public synchronized void addOrUpdateItem(String id, MemoryItem item) {
        Profile p = find(id);
        if (p == null) {
            return;
        }
        for (MemoryItem existing : p.items) {
            if (existing.module == item.module && existing.id == item.id) {
                existing.value = item.value;
                existing.label = item.label;
                save();
                return;
            }
        }
        p.items.add(item);
        save();
    }

    public synchronized void removeItem(String id, MemoryItem item) {
        Profile p = find(id);
        if (p != null && p.items.remove(item)) {
            save();
        }
    }

    /** Madde alanları (değer/etkin) değiştikten sonra çağrılır. */
    public synchronized void persist() {
        save();
    }

    private Profile find(String id) {
        for (Profile p : profiles) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static int normalizeGender(int gender) {
        return gender == GENDER_FEMALE ? GENDER_FEMALE : GENDER_MALE;
    }

    private static String newId() {
        return "p" + System.currentTimeMillis();
    }

    private static boolean isScoped(String key) {
        if (SCOPED_KEYS.contains(key)) {
            return true;
        }
        for (String prefix : SCOPED_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (key == null || !isScoped(key)) {
            return;
        }
        Profile active = find(activeId);
        if (active == null) {
            return;
        }
        Object value = sp.getAll().get(key);
        try {
            if (value == null) {
                active.settings.remove(key);
            } else {
                JSONObject encoded = encode(value);
                if (encoded != null) {
                    active.settings.put(key, encoded);
                }
            }
        } catch (JSONException ignored) {
            return;
        }
        save();
    }

    private void syncActiveFromPrefs() {
        Profile active = find(activeId);
        if (active != null) {
            captureScopedInto(active);
        }
    }

    private void captureScopedInto(Profile target) {
        Iterator<String> old = target.settings.keys();
        List<String> stale = new ArrayList<>();
        while (old.hasNext()) {
            stale.add(old.next());
        }
        for (String key : stale) {
            target.settings.remove(key);
        }
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!isScoped(entry.getKey())) {
                continue;
            }
            JSONObject encoded = encode(entry.getValue());
            if (encoded != null) {
                try {
                    target.settings.put(entry.getKey(), encoded);
                } catch (JSONException ignored) {
                }
            }
        }
    }

    private static JSONObject encode(Object value) {
        try {
            JSONObject o = new JSONObject();
            if (value instanceof Boolean) {
                o.put("t", "b");
            } else if (value instanceof Integer) {
                o.put("t", "i");
            } else if (value instanceof Long) {
                o.put("t", "l");
            } else if (value instanceof Float) {
                o.put("t", "f");
            } else if (value instanceof String) {
                o.put("t", "s");
            } else {
                return null;
            }
            o.put("v", value);
            return o;
        } catch (JSONException e) {
            return null;
        }
    }

    private static void putEncoded(SharedPreferences.Editor editor, String key, JSONObject o) throws JSONException {
        switch (o.getString("t")) {
            case "b":
                editor.putBoolean(key, o.getBoolean("v"));
                break;
            case "i":
                editor.putInt(key, o.getInt("v"));
                break;
            case "l":
                editor.putLong(key, o.getLong("v"));
                break;
            case "f":
                editor.putFloat(key, (float) o.getDouble("v"));
                break;
            case "s":
                editor.putString(key, o.getString("v"));
                break;
            default:
                break;
        }
    }

    private void save() {
        prefs.edit().putString(PREF_STATE, serialize()).apply();
    }

    private String serialize() {
        try {
            JSONArray arr = new JSONArray();
            for (Profile p : profiles) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("avatar", p.avatar);
                o.put("gender", p.gender);
                o.put("seatSlot", p.seatSlot);
                o.put("applyOnStart", p.applyOnStart);
                o.put("welcomeMessage", p.welcomeMessage);
                o.put("welcomeAudioPath", p.welcomeAudioPath);
                o.put("settings", p.settings);
                JSONArray items = new JSONArray();
                for (MemoryItem item : p.items) {
                    JSONObject io = new JSONObject();
                    io.put("m", item.module);
                    io.put("i", item.id);
                    io.put("l", item.label);
                    io.put("v", item.value);
                    io.put("e", item.enabled);
                    items.put(io);
                }
                o.put("items", items);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("active", activeId);
            root.put("profiles", arr);
            return root.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** F5: profilleri yedeklemek için JSON snapshot. */
    public synchronized String exportBackup() {
        return serialize();
    }

    /** F5: yedeği geri yükler; geçersiz içerik mevcut durumu bozmaz. */
    public synchronized boolean importBackup(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        String previous = prefs.getString(PREF_STATE, "");
        try {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.optJSONArray("profiles");
            if (array == null || array.length() == 0) {
                return false;
            }
            prefs.edit().putString(PREF_STATE, json).apply();
            profiles.clear();
            activeId = null;
            load();
            if (profiles.isEmpty()) {
                throw new JSONException("empty_profiles");
            }
            applyActiveSettings();
            save();
            return true;
        } catch (Exception error) {
            prefs.edit().putString(PREF_STATE, previous).apply();
            profiles.clear();
            activeId = null;
            load();
            return false;
        }
    }

    /** F5: yalnız sahne/karşılama tercihlerini dışa aktarır; sırlar hariçtir. */
    public synchronized JSONObject exportPreferences() {
        JSONObject output = new JSONObject();
        for (String key : BACKUP_PREF_KEYS) {
            Object value = prefs.getAll().get(key);
            JSONObject encoded = encode(value);
            if (encoded != null) {
                try {
                    output.put(key, encoded);
                } catch (JSONException ignored) {
                }
            }
        }
        return output;
    }

    /** F5: yalnız izinli sahne/karşılama tercihlerini geri yükler. */
    public synchronized boolean importPreferences(JSONObject data) {
        if (data == null) {
            return false;
        }
        SharedPreferences.Editor editor = prefs.edit();
        try {
            for (String key : BACKUP_PREF_KEYS) {
                if (data.has(key)) {
                    putEncoded(editor, key, data.getJSONObject(key));
                }
            }
            editor.apply();
            return true;
        } catch (JSONException error) {
            return false;
        }
    }

    private void applyActiveSettings() {
        Profile active = find(activeId);
        if (active == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (isScoped(key)) {
                editor.remove(key);
            }
        }
        Iterator<String> keys = active.settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                putEncoded(editor, key, active.settings.getJSONObject(key));
            } catch (JSONException ignored) {
            }
        }
        editor.apply();
    }

    private void load() {
        String json = prefs.getString(PREF_STATE, null);
        if (json == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.getJSONArray("profiles");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Profile p = new Profile(o.getString("id"), o.getString("name"),
                        o.optInt("avatar", i % ProfileAvatars.COUNT));
                p.gender = normalizeGender(o.optInt("gender", GENDER_MALE));
                p.seatSlot = o.optInt("seatSlot", 0);
                p.applyOnStart = o.optBoolean("applyOnStart", false);
                p.welcomeMessage = o.optString("welcomeMessage", "");
                p.welcomeAudioPath = o.optString("welcomeAudioPath", "");
                JSONObject settings = o.optJSONObject("settings");
                if (settings != null) {
                    Iterator<String> keys = settings.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        p.settings.put(key, settings.getJSONObject(key));
                    }
                }
                JSONArray items = o.optJSONArray("items");
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        JSONObject io = items.getJSONObject(j);
                        p.items.add(new MemoryItem(io.getInt("m"), io.getInt("i"),
                                io.optString("l", ""), io.optInt("v", 0), io.optBoolean("e", false)));
                    }
                }
                profiles.add(p);
            }
            activeId = root.optString("active", profiles.isEmpty() ? null : profiles.get(0).id);
            if (find(activeId) == null && !profiles.isEmpty()) {
                activeId = profiles.get(0).id;
            }
        } catch (JSONException e) {
            profiles.clear();
            activeId = null;
        }
    }
}
