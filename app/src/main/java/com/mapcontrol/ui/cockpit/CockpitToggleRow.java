package com.mapcontrol.ui.cockpit;

import android.content.Context;

/** Standard toggle row: icon + title/subtitle + 72x40 CockpitToggle. */
public class CockpitToggleRow extends CockpitListRow {

    private final CockpitToggle toggle;

    public CockpitToggleRow(Context context, int iconRes, CharSequence title,
            CharSequence subtitle, boolean checked) {
        super(context, iconRes, title, subtitle);
        toggle = new CockpitToggle(context);
        toggle.setChecked(checked, false);
        trailing(toggle);
    }

    public CockpitToggle getToggle() {
        return toggle;
    }

    public boolean isChecked() {
        return toggle.isChecked();
    }

    public void setCheckedSilently(boolean checked) {
        toggle.setChecked(checked, false);
    }
}
