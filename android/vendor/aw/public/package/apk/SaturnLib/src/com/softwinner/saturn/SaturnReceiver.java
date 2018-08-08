package com.softwinner.saturn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SaturnReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent service = new Intent("com.softwinner.saturn.SATURN_SERVICE");
            service.setPackage("com.softwinner.fireplayer");
            service.putExtra("first", true);
            context.startService(service);
        }
    }
}
