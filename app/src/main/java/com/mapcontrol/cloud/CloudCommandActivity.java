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
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_command_title)
                .setMessage(getString(R.string.cloud_command_message, pending.name))
                .setCancelable(false)
                .setPositiveButton(R.string.cloud_command_accept, (dialog, which) -> {
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
