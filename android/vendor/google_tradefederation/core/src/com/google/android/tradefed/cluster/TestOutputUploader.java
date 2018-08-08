// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.loganalysis.util.ArrayUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/** A class to upload test output files to GCS/HTTP. */
public class TestOutputUploader {

    private static final long UPLOAD_TIMEOUT_MS = 5 * 60 * 1000;

    private String mUploadUrl = null;
    private String mProtocol = null;
    private IRunUtil mRunUtil = null;

    public void setUploadUrl(final String url) throws MalformedURLException {
        final URL urlObj = new URL(url);
        mUploadUrl = url;
        mProtocol = urlObj.getProtocol();
    }

    public void uploadFile(File file) {
        CLog.i("Uploading %s to %s", file.getAbsolutePath(), mUploadUrl);
        final List<String> cmdArgs = buildUploadCommandArgs(file);
        final CommandResult result =
                getRunUtil().runTimedCmd(UPLOAD_TIMEOUT_MS, cmdArgs.toArray(new String[0]));
        if (!result.getStatus().equals(CommandStatus.SUCCESS)) {
            final String msg =
                    String.format(
                            "failed to upload %s: command status=%s",
                            file.getAbsolutePath(), result.getStatus());
            CLog.e(msg);
            CLog.e("stdout:\n'''\n%s'''\n", result.getStdout());
            CLog.d("stderr:\n'''\n%s'''\n", result.getStderr());
            throw new RuntimeException(msg);
        }
        // If the upload URL is for GAE blobstore, it needs to be updated to a new one.
        if (mUploadUrl.contains("/_ah/upload/")) {
            final String newUploadUrl = result.getStdout();
            try {
                setUploadUrl(newUploadUrl);
            } catch (MalformedURLException e) {
                CLog.w("invalid upload url(%s); ignoring", newUploadUrl);
            }
        }
    }

    private List<String> buildUploadCommandArgs(File file) {
        if (mUploadUrl == null) {
            throw new IllegalStateException("upload url is not set");
        }
        if ("gs".equals(mProtocol)) {
            return ArrayUtil.list("gsutil", "cp", file.getAbsolutePath(), mUploadUrl);
        }
        return ArrayUtil.list(
                "curl", "-X", "POST", "-F file=@" + file.getAbsolutePath(), "-f", mUploadUrl);
    }

    IRunUtil getRunUtil() {
        if (mRunUtil == null) {
            mRunUtil = new RunUtil();
        }
        return mRunUtil;
    }
}
