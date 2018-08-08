/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softwinner.preinstall;

import android.app.PackageInstallObserver;
import android.content.pm.IPackageManager;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;

public final class Preinstall {
    private static final String TAG = "Preinstall";

    IPackageManager mPm;

    private static final String PREINSTALL_NOT_RUNNING_ERR =
        "Error: Could not access the Package Manager.  Is the system running?";

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = new Preinstall().run(args);
        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            System.err.println("Error: " + e);
            if (e instanceof RemoteException) {
                System.err.println(PREINSTALL_NOT_RUNNING_ERR);
            }
        }
        System.exit(exitCode);
    }

    public int run(String[] args) throws RemoteException {
        mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));

        if (mPm == null) {
            Log.e(TAG, PREINSTALL_NOT_RUNNING_ERR);
            System.err.println(PREINSTALL_NOT_RUNNING_ERR);
            return 1;
        }

        boolean firstBoot = mPm.isFirstBoot();
        if (!firstBoot) {
            Log.i(TAG, "preinstall not first boot");
            return 0;
        }
        Log.i(TAG, "preinstall start");
        if (args.length > 0) {
            for (String path : args) {
                preinstall(path);
            }
        } else {
            preinstall("/system/preinstall");
            preinstall("/vendor/preinstall");
        }
        return 0;
    }

    private void preinstall(String path) throws RemoteException {
        if (path == null) {
            return;
        }
        File[] files = new File(path).listFiles();
        if (files == null) {
            return;
        }
        for (File apkFile : files) {
            String apkFilePath = Uri.fromFile(apkFile).getPath();
            Log.i(TAG, "preinstall apk " + apkFilePath);
            PackageInstallObserver obs = new PackageInstallObserver();
            mPm.installPackageAsUser(apkFilePath, obs.getBinder(), 0, null, UserHandle.USER_SYSTEM);
        }
    }

    private static int showUsage() {
        System.err.println("usage: preinstall [path ...]");
        return 1;
    }
}
