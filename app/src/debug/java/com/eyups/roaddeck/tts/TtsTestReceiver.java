package com.eyups.roaddeck.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.eyups.roaddeck.BuildConfig;

/** Debug-only adb tetikleyicisi: flavor TTS'ini doğrudan çalıştırır. */
public final class TtsTestReceiver extends BroadcastReceiver {
    private static final String TAG = "TtsTestReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String text = intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty()) {
            text = "Merhaba, RoadDeck TTS testi.";
        }
        int role = intent.getIntExtra("role", -1);
        if (role > 0) {
            context.getSharedPreferences(RoadDeckSpeech.PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(RoadDeckSpeech.KEY_IFLYTEK_ROLE, role).apply();
        }
        Log.i(TAG, "flavor=" + BuildConfig.TTS_FLAVOR
                + " direct=" + BuildConfig.TTS_DIRECT
                + " lib=" + BuildConfig.TTS_LIB_NAME
                + " res=" + BuildConfig.TTS_RES_PATH
                + " text=" + text);
        IflytekTts.speak(context, text, (ok, message) ->
                Log.i(TAG, "result ok=" + ok + " message=" + message),
                () -> Log.i(TAG, "finished"));
    }
}
