// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import com.google.android.tradefed.util.EarCompressionStrategy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;

/** Unit tests for {@link JacocoLogForwarder}. */
@RunWith(JUnit4.class)
public class JacocoLogForwarderTest {

    private static final String JACOCO_RESULT = "jacoco_report";
    private static final String JACOCO_ZIP_FILE = "/testdata/" + JACOCO_RESULT + ".zip";
    private static final String JACOCO_XML_FILE = "/testdata/" + JACOCO_RESULT + ".xml";

    @Rule public TemporaryFolder mTestTemp = new TemporaryFolder();
    private IBuildInfo mBuildInfo = new BuildInfo();
    private OptionSetter mOption;
    private JacocoLogForwarder mTfTest;
    private ITestInvocationListener mListener;
    private EarCompressionStrategy mStrategy;
    private IRunUtil mMockRunUtil;

    @Before
    public void setUp() throws ConfigurationException {
        mMockRunUtil = mock(IRunUtil.class);
        mStrategy =
                new EarCompressionStrategy() {
                    @Override
                    protected CommandResult runTimedCmd(long timeout, String[] command) {
                        // We fake the utility execution to avoid relying on external binary.
                        return mMockRunUtil.runTimedCmd(timeout, command);
                    }
                };
        mListener = mock(ITestInvocationListener.class);
        mTfTest =
                new JacocoLogForwarder() {
                    @Override
                    EarCompressionStrategy createCompressionStrategy() {
                        return mStrategy;
                    }
                };
        mBuildInfo = new BuildInfo();
        mOption = new OptionSetter(mTfTest);
        mTfTest.setBuild(mBuildInfo);
        mOption.setOptionValue("result-file-name", JACOCO_RESULT);
    }

    @Test
    public void testContainZipFile() throws IOException, DeviceNotAvailableException {
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        doReturn(res).when(mMockRunUtil).runTimedCmd(anyLong(), any());
        withLogFile(LogDataType.ZIP);
        mTfTest.run(mListener);
        verify(mListener, times(1)).testLog(any(), eq(LogDataType.EAR), any());
    }

    /**
     * Test that when the ear compress fails for any reason, we do not log anything and the tmp file
     * is cleaned up.
     */
    @Test
    public void testContainZipFile_failed() throws IOException, DeviceNotAvailableException {
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.FAILED);
        res.setStderr("oh I failed.");
        doReturn(res).when(mMockRunUtil).runTimedCmd(anyLong(), any());
        withLogFile(LogDataType.ZIP);
        mTfTest.run(mListener);
        // Verify this is not logged
        verify(mListener, times(0)).testLog(any(), eq(LogDataType.EAR), any());
    }

    @Test
    public void testCorruptedZipFile() throws IOException, DeviceNotAvailableException {

        withLogFile(LogDataType.XML);

        /** Create a zip file using the content from xml to mimic the corruption */
        File zip = FileUtil.createTempFile(JACOCO_RESULT, ".zip", mTestTemp.getRoot());
        FileUtil.writeToFile(getClass().getResourceAsStream(JACOCO_XML_FILE), zip);
        mBuildInfo.setFile(zip.getName(), zip, "1.0");

        mTfTest.run(mListener);

        // only the xml file result will be reported
        verify(mListener, times(1)).testLog(any(), eq(LogDataType.JACOCO_XML), any());
    }

    @Test
    public void testContainXMLFile() throws IOException, DeviceNotAvailableException {

        withLogFile(LogDataType.XML);
        mTfTest.run(mListener);
        verify(mListener, times(1)).testLog(any(), eq(LogDataType.JACOCO_XML), any());
    }

    @Test
    public void testNothingMatched() throws IOException, DeviceNotAvailableException {

        withLogFile(LogDataType.HTML);
        mTfTest.run(mListener);
        verify(mListener, times(0)).testLog(any(), any(), any());
    }

    @Test
    public void testContainMixingFiles() throws IOException, DeviceNotAvailableException {
        CommandResult res = new CommandResult();
        res.setStatus(CommandStatus.SUCCESS);
        doReturn(res).when(mMockRunUtil).runTimedCmd(anyLong(), any());

        withLogFile(LogDataType.ZIP);
        withLogFile(LogDataType.XML);
        withLogFile(LogDataType.HTML);

        mTfTest.run(mListener);
        verify(mListener, times(2)).testLog(any(), any(), any());
    }

    private void withLogFile(LogDataType type) throws IOException {
        if (type == LogDataType.ZIP) {
            File zip = FileUtil.createTempFile(JACOCO_RESULT, ".zip", mTestTemp.getRoot());
            FileUtil.writeToFile(getClass().getResourceAsStream(JACOCO_ZIP_FILE), zip);
            mBuildInfo.setFile(zip.getName(), zip, "1.0");
        } else {
            String fileName = String.format("%s.%s", JACOCO_RESULT, type.getFileExt());
            mBuildInfo.setFile(fileName, new File(fileName), "1.0");
        }
    }
}
