package com.eyups.fakevdbus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.desaysv.ivi.vdb.event.VDEvent;

import org.json.JSONArray;
import org.json.JSONObject;

/** adb shell am broadcast -a com.eyups.fakevdbus.CONTROL --es json '{"event":768,"cmd":1,"values":[1,2]}' */
public final class ControlReceiver extends BroadcastReceiver {
    private static final String TAG = "FakeVdbusCtl";

    @Override
    public void onReceive(Context context, Intent intent) {
        String json = intent.getStringExtra("json");
        if (json == null) {
            // adb: --ei event 327684 --ei cmd 13 --eia values 2
            int eventId = intent.getIntExtra("event", -1);
            if (eventId < 0) {
                return;
            }
            int cmd = intent.getIntExtra("cmd", -1);
            int[] values = intent.getIntArrayExtra("values");
            Bundle b = new Bundle();
            b.putInt("CMD_ID", cmd);
            if (values != null) {
                b.putIntArray("VALUE", values);
                FakeVdbusService.putValues(eventId, cmd, values);
            }
            FakeVdbusService.putEvent(eventId, b);
            FakeVdbusService.publishFromControl(new VDEvent(eventId, new Bundle(b)));
            startService(context);
            Log.i(TAG, "event=" + eventId + " cmd=" + cmd + " values=" + (values == null ? "-" : values.length));
            return;
        }
        try {
            JSONObject o = new JSONObject(json);
            if (o.optBoolean("clear", false)) {
                FakeVdbusService.clearAll();
                return;
            }
            int eventId = o.getInt("event");
            int cmd = o.optInt("cmd", -1);
            JSONArray arr = o.optJSONArray("values");
            int[] values = null;
            if (arr != null) {
                values = new int[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    values[i] = arr.getInt(i);
                }
            }

            Bundle b = new Bundle();
            b.putInt("CMD_ID", cmd);
            if (values != null) {
                b.putIntArray("VALUE", values);
                FakeVdbusService.putValues(eventId, cmd, values);
            }
            FakeVdbusService.putEvent(eventId, b);
            FakeVdbusService.publishFromControl(new VDEvent(eventId, new Bundle(b)));

            startService(context);
            Log.i(TAG, "event=" + eventId + " cmd=" + cmd + " values=" + (values == null ? "-" : values.length));
        } catch (Throwable t) {
            Log.e(TAG, "invalid control json: " + json, t);
        }
    }

    private static void startService(Context context) {
        Intent svc = new Intent(context, FakeVdbusService.class);
        try {
            context.startService(svc);
        } catch (Throwable t) {
            try {
                context.startForegroundService(svc);
            } catch (Throwable ignored) {
            }
        }
    }
}
