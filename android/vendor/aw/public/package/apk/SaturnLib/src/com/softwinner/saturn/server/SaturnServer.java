package com.softwinner.saturn.server;

import android.app.AppOpsManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.Message;
import android.util.Log;

public class SaturnServer extends Service {
    private final static String TAG = "SaturnServer";
    private final static boolean DEBUG = false;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Bundle data = (Bundle) msg.obj;
                    int op = data.getInt("op", AppOpsManager.OP_NONE);
                    int mode = data.getInt("mode", AppOpsManager.MODE_DEFAULT);
                    String pkg = data.getString("package");
                    setOpMode(op, mode, pkg);
                    break;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return new Messenger(mHandler).getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String callingApp = null;
        int op = AppOpsManager.OP_NONE;
        int mode = AppOpsManager.MODE_DEFAULT;
        if (intent != null) {
            callingApp = intent.getStringExtra("package");
            op = intent.getIntExtra("op", op);
            mode = intent.getIntExtra("mode", mode);
        }
        setOpMode(op, mode, callingApp);
        return super.onStartCommand(intent, flags, startId);
    }

    private void setOpMode(int op, int mode, String pkg) {
        if (pkg != null && op != AppOpsManager.OP_NONE) {
            if (pkg.startsWith("com.softwinner")) {
                if (DEBUG) Log.e(TAG, "has permission");
                AppOpsManager mAppOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                PackageInfo packageInfo = null;
                try {
                    packageInfo = getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                    if (DEBUG) Log.e(TAG, "setMode: " + packageInfo.packageName + ", " + op + ", " + mode);
                    mAppOpsManager.setMode(op, packageInfo.applicationInfo.uid, packageInfo.packageName, mode);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                if (DEBUG) Log.e(TAG, "no permission");
            }
        }
    }
}
