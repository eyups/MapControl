package com.eyups.roaddeck.vehicle;

import android.content.Context;
import android.content.Intent;

import com.desaysv.ivi.extra.project.carinfo.CarSettingID;
import com.desaysv.ivi.extra.project.carinfo.ReadOnlyID;
import com.desaysv.ivi.vdb.event.id.carinfo.VDEventCarInfo;

/**
 * Yalnızca emülatördeki fake VDBus ControlReceiver'a test sinyali gönderir.
 * Gerçek araç VDBus'una veya OEM actuator'larına komut göndermez.
 */
public final class FakeVdbusBodyTest {

    private static final String ACTION = "com.eyups.fakevdbus.CONTROL";
    private static final String FAKE_CARINFO_PACKAGE = "com.desaysv.ivi.vds.carinfo";

    private FakeVdbusBodyTest() {
    }

    public static void setDoor(Context context, int doorId, boolean open) {
        send(context, VDEventCarInfo.MODULE_READONLY_INFO, doorId, open ? 1 : 0);
    }

    public static void setWindow(Context context, int windowId, boolean open) {
        send(context, VDEventCarInfo.MODULE_CAR_SETTING, windowId, open ? 1 : 0);
    }

    public static void setTrunk(Context context, boolean open) {
        send(context, VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_TRUNK_STATE,
                open ? 1 : 0);
    }

    public static void setHood(Context context, boolean open) {
        send(context, VDEventCarInfo.MODULE_READONLY_INFO, ReadOnlyID.ID_HOOD_STATE,
                open ? 1 : 0);
    }

    public static void setSunroof(Context context, boolean open) {
        send(context, VDEventCarInfo.MODULE_CAR_SETTING,
                CarSettingID.ID_CAR_SPF_POSI, open ? 2 : 1);
    }

    public static void setSunshade(Context context, boolean open) {
        send(context, VDEventCarInfo.MODULE_CAR_SETTING,
                CarSettingID.ID_CAR_SUNSHADE, open ? 2 : 1);
    }

    public static void setSpeed(Context context, int kmh) {
        send(context, VDEventCarInfo.MODULE_READONLY_INFO,
                ReadOnlyID.ID_CAR_SPEED, Math.max(0, kmh));
    }

    public static void closeAll(Context context) {
        for (int id : new int[]{
                ReadOnlyID.ID_DRIVE_DOOR_STATE,
                ReadOnlyID.ID_PASSENGER_DOOR_STATE,
                ReadOnlyID.ID_LFFT_BEHIND_DOOR_STATE,
                ReadOnlyID.ID_RIGHT_BEHIND_DOOR_STATE,
        }) {
            setDoor(context, id, false);
        }
        for (int id : new int[]{
                CarSettingID.ID_CAR_FL_WINDOW,
                CarSettingID.ID_CAR_FR_WINDOW,
                CarSettingID.ID_CAR_RL_WINDOW,
                CarSettingID.ID_CAR_RR_WINDOW,
        }) {
            setWindow(context, id, false);
        }
        setTrunk(context, false);
        setHood(context, false);
        setSunroof(context, false);
        setSunshade(context, false);
        setSpeed(context, 0);
    }

    private static void send(Context context, int event, int cmd, int value) {
        Intent intent = new Intent(ACTION)
                .setPackage(FAKE_CARINFO_PACKAGE)
                .putExtra("event", event)
                .putExtra("cmd", cmd)
                .putExtra("values", new int[]{value})
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
    }
}
