// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.GTestXmlResultParser;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import java.io.File;

/**
 * A {@link IRemoteTest} for parsing and reporting the results of a gtest-generated unit test log.
 */
public class GTestTestlogForwarder implements IRemoteTest, IBuildReceiver {

    private IBuildInfo mBuildInfo;

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (VersionedFile vf : mBuildInfo.getFiles()) {
            logFile(vf.getFile(), listener);
        }
    }

    private void logFile(File fileToExport, ITestInvocationListener listener) {
        String name = fileToExport.getName();
        String ext = "." + LogDataType.XML.getFileExt();
        if (name.endsWith(ext)) {
            name = name.substring(0, name.length() - ext.length());
        }
        try (FileInputStreamSource inputStream = new FileInputStreamSource(fileToExport)) {
            listener.testLog(name, LogDataType.XML, inputStream);
            new GTestXmlResultParser(name, listener).parseResult(fileToExport, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
