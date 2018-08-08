// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link IRemoteTest} for parsing and reporting the results of a robolectric test log.
 */
public class RobolectricTestlogForwarder implements IRemoteTest, IBuildReceiver {

    private static final Pattern TIME_ELAPSED_REGEX = Pattern.compile("Time: ([0-9.]+)");

    private IBuildInfo mBuildInfo;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (VersionedFile vf : mBuildInfo.getFiles()) {
            logFile(vf.getFile(), listener);
        }
    }

    private void logFile(File fileToExport, ITestInvocationListener listener) {
        if (fileToExport != null) {
            try (FileInputStreamSource inputStream = new FileInputStreamSource(fileToExport)) {
                String name = fileToExport.getName();
                String ext = "." + LogDataType.TEXT.getFileExt();
                if (name.endsWith(ext)) {
                    name = name.substring(0, name.length() - ext.length());
                }
                // TODO(mikewallstedt): Update our usage of the Sponge HTTP API, such that these
                // can be attached as test logs (instead of build logs).
                listener.testLog(name, LogDataType.TEXT, inputStream);
                parseTestLog(listener, fileToExport);
            }
        }
    }

    @VisibleForTesting
    void parseTestLog(ITestInvocationListener listener, File fileToExport) {
        String testName = fileToExport.getName().replaceAll("-test-output.*txt", "");
        // TODO(mikewallstedt): Parse real values from the log
        TestIdentifier id = new TestIdentifier(testName, "Unknown Test Method");
        listener.testRunStarted(testName, 1);
        try {
            String contents = StreamUtil.getStringFromStream(new FileInputStream(fileToExport));
            listener.testStarted(id);
            if (contents.contains("FAILURES!!!")) {
                listener.testFailed(
                    id, String.format(
                        "Failure detected. Details in: Build Logs > %s.",
                        fileToExport.getName()));
            }

            double elapsedSeconds = 0;
            Matcher matcher = TIME_ELAPSED_REGEX.matcher(contents);
            if (matcher.find()) {
                elapsedSeconds = Double.parseDouble(matcher.group(1));
            }
            listener.testRunEnded((long)(elapsedSeconds * 1000), ImmutableMap.of());

            listener.testEnded(id, ImmutableMap.of());
        } catch (IOException e) {
            listener.testFailed(id, StreamUtil.getStackTrace(e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
