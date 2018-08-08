// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.io.IOException;

/**
 * Tools related to package signing and other scripts in releasetools.
 */
public class ReleaseToolsUtil {

    private static final String DEV_X509 =
            "/google/data/ro/teams/tradefed/testdata/ota-incremental/devkey.x509.pem";
    private static final String DEV_PK8 =
            "/google/data/ro/teams/tradefed/testdata/ota-incremental/devkey.pk8";
    private static final long DEFAULT_SIGN_TIMEOUT = 1000 * 60 * 5;

    public static File signOtaPackage(File otaToolsDir, File pkg)
            throws IOException {
        return signOtaPackage(otaToolsDir, pkg, DEFAULT_SIGN_TIMEOUT);
    }

    /**
     * Sign an OTA package with dev keys.
     * @param otaToolsDir {@link File} pointing to otatools.
     * @param pkg package to be signed
     * @param timeout amount of time to wait for signing to complete
     * @return a {@link File} pointing to a newly signed OTA package
     * @throws IOException if an IO error occurs
     */
    public static File signOtaPackage(File otaToolsDir, File pkg, long timeout)
            throws IOException {
        String libPath = new File(otaToolsDir, "lib64").getAbsolutePath();
        String signapk = new File(otaToolsDir, "framework/signapk.jar").getAbsolutePath();
        IRunUtil runUtil = RunUtil.getDefault();
        File signedPackage = FileUtil.createTempFile("otatest", "incremental-s.zip");
        String[] cmd = {"java",
                String.format("-Djava.library.path=%s", libPath),
                "-jar", signapk,
                "-w", DEV_X509, DEV_PK8,
                pkg.getAbsolutePath(),
                signedPackage.getAbsolutePath()};
        CLog.i("Signing OTA package with command %s", ArrayUtil.join(" ", (Object[])cmd));
        CommandResult c = runUtil.runTimedCmd(timeout, cmd);
        if (c.getStatus() != CommandStatus.SUCCESS) {
            CLog.e("Failed to sign package");
            CLog.i(c.getStdout());
            CLog.e(c.getStderr());
            throw new RuntimeException("signapk.jar failed");
        }
        return signedPackage;
    }
}

