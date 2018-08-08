// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.testtype.host;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.android.tradefed.util.EarCompressionStrategy;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/** A fake test whose purpose is to forward the code coverage result files to reporter. */
@OptionClass(alias = "jacoco-log-forwarding")
public class JacocoLogForwarder implements IRemoteTest, IBuildReceiver {

    @Option(
        name = "result-file-name",
        description = "the file name of the code coverge results without file extension.",
        importance = Importance.IF_UNSET,
        mandatory = true
    )
    private String mResultFileName = null;

    private IBuildInfo mBuildInfo;

    /** {@inheritDoc} */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        Map<String, String> testCaseMap = Collections.emptyMap();
        listener.testRunStarted("CodeCoverageLogForwarding", 1);

        final String dotZip = LogDataType.ZIP.getFileExt();
        final String dotXml = LogDataType.JACOCO_XML.getFileExt();

        final TestIdentifier tid =
                new TestIdentifier(JacocoLogForwarder.class.getName(), "forwarding");
        listener.testStarted(tid);
        listener.testEnded(tid, testCaseMap);

        for (VersionedFile vf : mBuildInfo.getFiles()) {
            final File f = vf.getFile();
            String fileName = f.getName();
            if (fileName.endsWith(dotZip) && fileName.contains(mResultFileName)) {
                // convert from zip to ear
                File ear = null;
                try {
                    ear = zipToEar(f);
                    FileInputStreamSource stream = new FileInputStreamSource(ear);
                    listener.testLog("coverage", LogDataType.EAR, stream);
                    StreamUtil.cancel(stream);
                } catch (IOException e) {
                    CLog.e(e);
                } finally {
                    FileUtil.deleteFile(ear);
                }
            } else if (fileName.endsWith(dotXml) && fileName.contains(mResultFileName)) {
                // Report the xml result as is
                FileInputStreamSource stream = new FileInputStreamSource(f);
                listener.testLog("coverage", LogDataType.JACOCO_XML, stream);
                StreamUtil.cancel(stream);
            }
        }
        listener.testRunEnded(0, testCaseMap);
    }

    private File zipToEar(File zip) throws IOException {
        File parent = null;
        try {
            parent = ZipUtil.extractZipToTemp(zip, "coverage");
            EarCompressionStrategy ear = createCompressionStrategy();
            return ear.compress(parent);
        } finally {
            FileUtil.recursiveDelete(parent);
        }
    }

    /** Creates and returns a {@link EarCompressionStrategy}. */
    @VisibleForTesting
    EarCompressionStrategy createCompressionStrategy() {
        return new EarCompressionStrategy();
    }

    /** {@inheritDoc} */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
