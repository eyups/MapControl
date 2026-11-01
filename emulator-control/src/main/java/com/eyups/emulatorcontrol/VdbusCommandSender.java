package com.eyups.emulatorcontrol;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Fake VDBus servislerine explicit broadcast gönderen ortak yardımcı. */
final class VdbusCommandSender {
    static final String CONTROL_ACTION = "com.eyups.fakevdbus.CONTROL";
    static final String CONTROL_RECEIVER = "com.eyups.fakevdbus.ControlReceiver";
    static final String CARINFO = "com.desaysv.ivi.vds.carinfo";
    static final String SMS = "com.desaysv.ivi.vds.sms";
    static final String CARLAN = "com.desaysv.ivi.vds.carlan";

    private VdbusCommandSender() {
    }

    static void send(Context context, String target, int event, int cmd, int[] values) {
        Intent intent = new Intent(CONTROL_ACTION);
        intent.setComponent(new ComponentName(target, CONTROL_RECEIVER));
        intent.putExtra("event", event);
        intent.putExtra("cmd", cmd);
        intent.putExtra("values", values);
        context.sendBroadcast(intent);
    }

    static void clear(Context context, String target) {
        Intent intent = new Intent(CONTROL_ACTION);
        intent.setComponent(new ComponentName(target, CONTROL_RECEIVER));
        intent.putExtra("json", "{\"clear\":true}");
        context.sendBroadcast(intent);
    }
}
