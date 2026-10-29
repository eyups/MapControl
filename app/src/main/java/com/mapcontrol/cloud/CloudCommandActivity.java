package com.mapcontrol.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import com.mapcontrol.R;

/** Araçta hedef komutu için onay penceresi. */
public final class CloudCommandActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CommandExecutor.PendingView pending = CommandExecutor.pendingView();
        if (pending == null) {
            finish();
            return;
        }
        boolean tts = "tts.say".equals(pending.type);
        boolean scene = "comfort.scene".equals(pending.type);
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_command_title)
                .setMessage(getString(
                        tts ? R.string.cloud_command_tts_message
                                : scene ? R.string.cloud_command_scene_message
                                : R.string.cloud_command_message,
                        pending.message))
                .setCancelable(false)
                .setPositiveButton(
                        tts ? R.string.cloud_command_tts_accept
                                : scene ? R.string.cloud_command_scene_accept
                                : R.string.cloud_command_accept,
                        (dialog, which) -> {
                    CommandExecutor.resolvePending(true);
                    finish();
                })
                .setNegativeButton(R.string.cloud_command_reject, (dialog, which) -> {
                    CommandExecutor.resolvePending(false);
                    finish();
                })
                .show();
    }
}
