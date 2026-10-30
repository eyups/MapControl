package com.mapcontrol.ui.cockpit;

import android.content.Context;
import android.content.SharedPreferences;

import com.mapcontrol.R;

import java.util.ArrayList;
import java.util.List;

/** IA mapping: 6 domains + settings; existing TAB_* ids are preserved. */
public final class CockpitNavModel {

    public static final int TAB_WIFI = 0;
    public static final int TAB_FILE = 1;
    public static final int TAB_PROJECTION = 3;
    public static final int TAB_LOG = 4;
    public static final int TAB_APPS = 5;
    public static final int TAB_DRIVE_MODE = 6;
    public static final int TAB_SETTINGS = 7;
    public static final int TAB_WELCOME_SOUND = 8;
    public static final int TAB_VEHICLE_INFO = 9;
    public static final int TAB_LAUNCHER = 10;
    public static final int TAB_EQUALIZER = 11;
    public static final int TAB_DRIVER_PROFILES = 13;
    public static final int TAB_DRIVING_HISTORY = 14;
    public static final int TAB_VALET = 16;
    public static final int TAB_HOME = 17;
    public static final int TAB_CLIMATE_AUTOMATION = 18;
    public static final int TAB_FACE_RECOGNITION = 19;
    public static final int TAB_CAMERA_360 = 20;
    public static final int TAB_SPLIT_SCREEN = 21;
    public static final int TAB_SUNROOF_AUTOMATION = 22;
    public static final int TAB_ASSIST = 23;

    public static final String PREF_LAST_DOMAIN = "cockpitLastDomain";
    public static final String PREF_LAST_TAB = "cockpitLastTab";
    public static final String PREF_RAIL_EXPANDED = "ckRailExpanded";

    public static final class Item {
        public final int tabId;
        public final int titleRes;
        public final int iconRes;

        Item(int tabId, int titleRes, int iconRes) {
            this.tabId = tabId;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }
    }

    public static final class Domain {
        public final int id;
        public final int titleRes;
        public final int iconRes;
        public final List<Item> items = new ArrayList<>();

        Domain(int id, int titleRes, int iconRes) {
            this.id = id;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }

        Domain add(int tabId, int titleRes, int iconRes) {
            items.add(new Item(tabId, titleRes, iconRes));
            return this;
        }

        public boolean contains(int tabId) {
            for (Item item : items) {
                if (item.tabId == tabId) {
                    return true;
                }
            }
            return false;
        }
    }

    private static List<Domain> domains;

    private CockpitNavModel() {
    }

    public static List<Domain> domains() {
        if (domains == null) {
            List<Domain> list = new ArrayList<>();
            list.add(new Domain(0, R.string.ck_domain_cockpit, R.drawable.ic_mdi_home)
                    .add(TAB_HOME, R.string.ck_sub_cockpit, R.drawable.ic_mdi_home));

            list.add(new Domain(1, R.string.ck_domain_vehicle, R.drawable.ic_mdi_car)
                    .add(TAB_VEHICLE_INFO, R.string.side_rail_vehicle_info,
                            R.drawable.ic_mdi_speedometer)
                    .add(TAB_DRIVE_MODE, R.string.ck_sub_drive_mode, R.drawable.ic_mdi_car)
                    .add(TAB_CLIMATE_AUTOMATION, R.string.ck_sub_climate,
                            R.drawable.ic_mdi_thermometer)
                    .add(TAB_SUNROOF_AUTOMATION, R.string.side_rail_sunroof_automation,
                            R.drawable.ic_mdi_car_sunroof)
                    .add(TAB_ASSIST, R.string.ck_sub_assist, R.drawable.ic_mdi_shield_car));

            list.add(new Domain(2, R.string.ck_domain_safety, R.drawable.ic_mdi_shield_car)
                    .add(TAB_DRIVING_HISTORY, R.string.side_rail_driving_report,
                            R.drawable.ic_mdi_road_variant)
                    .add(TAB_CAMERA_360, R.string.camera360_title, R.drawable.ic_mdi_camera)
                    .add(TAB_VALET, R.string.side_rail_valet, R.drawable.ic_mdi_shield_car)
                    .add(TAB_DRIVER_PROFILES, R.string.side_rail_driver_profiles,
                            R.drawable.ic_mdi_account)
                    .add(TAB_FACE_RECOGNITION, R.string.side_rail_face_recognition,
                            R.drawable.ic_mdi_account));

            list.add(new Domain(3, R.string.ck_domain_media, R.drawable.ic_mdi_music_note)
                    .add(TAB_EQUALIZER, R.string.side_rail_equalizer, R.drawable.ic_mdi_equalizer)
                    .add(TAB_WELCOME_SOUND, R.string.side_rail_welcome_sound,
                            R.drawable.ic_mdi_volume_high)
                    .add(TAB_PROJECTION, R.string.side_rail_projection, R.drawable.ic_mdi_map)
                    .add(TAB_SPLIT_SCREEN, R.string.side_rail_split_screen,
                            R.drawable.ic_mdi_view_split)
                    .add(TAB_LAUNCHER, R.string.ck_sub_launcher, R.drawable.ic_mdi_home));

            list.add(new Domain(4, R.string.ck_domain_apps, R.drawable.ic_mdi_package_variant)
                    .add(TAB_APPS, R.string.side_rail_apps, R.drawable.ic_mdi_cellphone)
                    .add(TAB_WIFI, R.string.side_rail_wifi, R.drawable.ic_mdi_wifi)
                    .add(TAB_FILE, R.string.side_rail_web, R.drawable.ic_mdi_web));

            list.add(new Domain(5, R.string.ck_domain_settings, R.drawable.ic_mdi_cog)
                    .add(TAB_SETTINGS, R.string.ck_sub_general, R.drawable.ic_mdi_cog)
                    .add(TAB_LOG, R.string.ck_sub_log, R.drawable.ic_mdi_web));

            domains = list;
        }
        return domains;
    }

    public static Domain domainForTab(int tabId) {
        for (Domain d : domains()) {
            if (d.contains(tabId)) {
                return d;
            }
        }
        return domains().get(0);
    }

    public static Domain domainById(int id) {
        for (Domain d : domains()) {
            if (d.id == id) {
                return d;
            }
        }
        return domains().get(0);
    }

    /** Apps tab requires the management disclaimer; all other tabs are unconditional. */
    public static boolean isAvailable(SharedPreferences prefs, int tabId) {
        if (tabId == TAB_APPS) {
            return prefs.getBoolean("appManagementDisclaimerAccepted", false);
        }
        return true;
    }

    public static String title(Context context, int tabId) {
        for (Domain d : domains()) {
            for (Item item : d.items) {
                if (item.tabId == tabId) {
                    return context.getString(item.titleRes);
                }
            }
        }
        return "";
    }
}
