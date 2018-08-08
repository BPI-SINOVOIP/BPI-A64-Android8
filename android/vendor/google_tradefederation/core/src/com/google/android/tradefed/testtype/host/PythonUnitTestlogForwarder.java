// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.PythonUnitTestResultParser;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ArrayUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** A {@link IRemoteTest} for parsing and reporting the results of a python unittest log. */
public class PythonUnitTestlogForwarder implements IRemoteTest, IBuildReceiver {

    private IBuildInfo mBuildInfo;

    /** {@inheritDoc} */
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

    private void parseTestLog(ITestInvocationListener listener, File fileToExport) {
        String testName = fileToExport.getName();
        try {
            FileInputStream fileStream = new FileInputStream(fileToExport);
            String fileContents = StreamUtil.getStringFromStream(fileStream);
            fileStream.close();
            MultiLineReceiver parser =
                    new PythonUnitTestResultParser(ArrayUtil.list(listener), testName);
            parser.processNewLines(fileContents.split("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Reading test contents failed", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
