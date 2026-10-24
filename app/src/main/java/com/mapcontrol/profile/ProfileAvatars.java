package com.mapcontrol.profile;

import com.mapcontrol.R;

/** Profiller için 10 hazır avatar: renkli daire + beyaz ikon. */
public final class ProfileAvatars {

    public static final int COUNT = 10;

    private static final int[] ICONS = {
            R.drawable.ic_mdi_account,
            R.drawable.ic_mdi_car,
            R.drawable.ic_mdi_leaf,
            R.drawable.ic_mdi_music_note,
            R.drawable.ic_mdi_speedometer,
            R.drawable.ic_mdi_shield_car,
            R.drawable.ic_mdi_snowflake,
            R.drawable.ic_mdi_terrain,
            R.drawable.ic_mdi_waves,
            R.drawable.ic_mdi_map_marker,
    };

    private static final int[] COLORS = {
            R.color.avatar_1,
            R.color.avatar_2,
            R.color.avatar_3,
            R.color.avatar_4,
            R.color.avatar_5,
            R.color.avatar_6,
            R.color.avatar_7,
            R.color.avatar_8,
            R.color.avatar_9,
            R.color.avatar_10,
    };

    private ProfileAvatars() {
    }

    public static int normalize(int avatar) {
        return avatar >= 0 && avatar < COUNT ? avatar : 0;
    }

    public static int iconRes(int avatar) {
        return ICONS[normalize(avatar)];
    }

    public static int colorRes(int avatar) {
        return COLORS[normalize(avatar)];
    }
}
