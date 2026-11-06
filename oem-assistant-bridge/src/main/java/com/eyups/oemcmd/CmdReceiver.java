package com.eyups.oemcmd;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

/**
 * OEM sesli asistaninin "com.iflytek.autofly.handMessage" yayinini yakalar.
 * Emulator'de komutlari fake VDBus'a yazar; yeni komut eslemesi buradadir.
 */
public class CmdReceiver extends BroadcastReceiver {
    private static final String TAG = "OemCmd";
    private static final String FAKE = "com.eyups.fakevdbus.CONTROL";
    private static final String FAKE_PKG = "com.desaysv.ivi.vds.carinfo";
    private static final String FAKE_CLS = "com.eyups.fakevdbus.ControlReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String value = intent.getStringExtra("value");
        Log.i(TAG, "recv action=" + intent.getAction() + " value=" + value);
        if (value == null) return;
        try {
            JSONObject root = new JSONObject(value);
            String focus = root.optString("focus");
            JSONObject sem = root.optJSONObject("semantic");
            String service = sem != null ? sem.optString("service") : "";
            String intentName = sem != null ? sem.optString("intent") : "";
            String operation = sem != null ? sem.optString("operation") : "";
            JSONObject slots = sem != null ? sem.optJSONObject("slots") : null;
            String name = slots != null ? slots.optString("name") : "";
            String content = root.optString("content");
            Log.i(TAG, "focus=" + focus + " service=" + service + " intent=" + intentName
                    + " operation=" + operation + " name=" + name + " content=" + content);

            if ("carControl".equals(service)) {
                if ("open_car_device".equals(intentName)) {
                    int v = "OPEN".equals(operation) ? 2 : 1;
                    if ("sunroof".equals(name)) {
                        send(ctx, 327681, 6, v);   // CAR_SETTING / cam tavan
                    } else if ("sunshade".equals(name)) {
                        send(ctx, 327681, 10, v);  // CAR_SETTING / guneslik
                    } else if ("window".equals(name)) {
                        send(ctx, 327681, 100, v); // on sol cam (ornek)
                    }
                }
            }

            // --- Yeni komut ekleme ornegi (yerel NLU'ya dokunmadan) ---
            // NLU cumleyi anlamasa bile content/normal_text uzerinden eslenir.
            String text = content != null ? content : "";
            if (text.contains("ambiyans") || text.contains("ortam isigi")) {
                send(ctx, 327681, 7, 2);       // CAR_SETTING / ortam isigi
            }
        } catch (Throwable t) {
            Log.e(TAG, "parse failed", t);
        }
    }

    private void send(Context ctx, int event, int cmd, int value) {
        Log.i(TAG, "-> fake VDBus event=" + event + " cmd=" + cmd + " value=" + value);
        Intent i = new Intent(FAKE);
        i.setComponent(new ComponentName(FAKE_PKG, FAKE_CLS));
        i.putExtra("event", event);
        i.putExtra("cmd", cmd);
        i.putExtra("values", new int[]{value});
        ctx.sendBroadcast(i);
    }
}
