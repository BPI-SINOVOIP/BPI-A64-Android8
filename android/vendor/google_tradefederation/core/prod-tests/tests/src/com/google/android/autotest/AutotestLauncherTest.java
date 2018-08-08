//Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.autotest;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link AutotestLauncher}.
 */
public class AutotestLauncherTest {

    private static final String FAKE_SERIAL = "FAKESERIAL";
    private static final String FAKE_TEST_NAME = "dummy_test";

    private AutotestLauncher mAutotestLauncher;
    private ITestInvocationListener mMockListener;
    private IRunUtil mMockRunUtil;
    private ITestDevice mMockTestDevice;
    private File mAutotestDir;
    private File mAutotestResultsDir;

    @Before
    public void setUp() throws Exception {
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);

        mAutotestLauncher = new AutotestLauncher();
        mAutotestLauncher.setRunUtil(mMockRunUtil);
        mAutotestLauncher.setDevice(mMockTestDevice);
        mAutotestLauncher.setTestName(FAKE_TEST_NAME);

        mAutotestDir = FileUtil.createTempDir("test_AutotestLauncher_");
        mAutotestLauncher.setAutotestDir(mAutotestDir.getAbsolutePath());
        mAutotestResultsDir = new File(Paths.get(mAutotestDir.getAbsolutePath(), "results"
                ).toString());
        mAutotestResultsDir.mkdir();
        mAutotestLauncher.setAutotestResultsDir(mAutotestResultsDir);
    }


    @After
    public void tearDown() throws Exception {
        if (mAutotestDir != null) {
            FileUtil.recursiveDelete(mAutotestDir);
        }
    }

    /**
     * Create a dummy status.log file.
     */
    private void setupStatusLog() throws IOException {
        File statusLog = new File(Paths.get(mAutotestDir.getAbsolutePath(), "results",
                "results-1-"+FAKE_TEST_NAME, "status.log").toString());
        FileUtil.mkdirsRWX(statusLog.getParentFile());
        FileUtil.writeToFile("", statusLog);
    }

    /**
     * Set up EasyMock for runTimedCmd command called through RunUtil.
     *
     * @param success true if the command should succeed.
     */
    private void setupRunUtilMock(boolean success) {
        CommandResult cr;
        if (success) {
            cr = new CommandResult(CommandStatus.SUCCESS);
        }
        else {
            cr = new CommandResult(CommandStatus.FAILED);
        }
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                EasyMock.eq(Paths.get(mAutotestDir.getAbsolutePath(), "site_utils",
                        "test_droid.py").toString()),
                EasyMock.eq("-s"), EasyMock.eq(FAKE_SERIAL), EasyMock.eq("--results_dir"),
                (String)EasyMock.anyObject(),
                EasyMock.eq(FAKE_TEST_NAME))).andReturn(cr);
    }

    /**
     * Set up autotest code in tmp directory.
     */
    private void setupAutotest() throws IOException {
        File testDroidScript = new File(Paths.get(mAutotestDir.getAbsolutePath(), "site_utils",
                "test_droid.py").toString());
        FileUtil.mkdirsRWX(testDroidScript.getParentFile());
        FileUtil.writeToFile("", testDroidScript);
    }

    /**
     * Setup EasyMock for mMockListener
     *
     * @param success true if the test is expected to succeed.
     */
    private void setupMockListener(boolean success) {
        mMockListener.testRunStarted(EasyMock.eq(FAKE_TEST_NAME), EasyMock.eq(0));
        mMockListener.testStarted((TestIdentifier)EasyMock.anyObject());

        if (!success) {
            mMockListener.testFailed((TestIdentifier)EasyMock.anyObject(),
                    (String)EasyMock.anyObject());
        }
        mMockListener.testEnded((TestIdentifier)EasyMock.anyObject(),
                EasyMock.eq(Collections.<String, String>emptyMap()));
        mMockListener.testRunEnded(EasyMock.eq((long)0),
                EasyMock.eq(Collections.<String, String>emptyMap()));
    }

    /**
     * Test {@link AutotestLauncher#run(ITestInvocationListener)} with missing Autotest setup
     */
    @Test(expected = RuntimeException.class)
    public void testRun_MissingAutotest() throws DeviceNotAvailableException, IOException {
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(1);
        setupMockListener(false);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockTestDevice, mMockRunUtil, mMockListener);

        try {
            mAutotestLauncher.run(mMockListener);
        }
        finally {
            EasyMock.verify(mMockTestDevice, mMockRunUtil, mMockListener);
        }
    }

    /**
     * Test {@link AutotestLauncher#run(ITestInvocationListener)} with test_droid command succeeded
     */
    @Test
    public void testRun_Succeed() throws DeviceNotAvailableException, IOException {
        setupAutotest();
        setupRunUtilMock(true);
        setupStatusLog();
        setupMockListener(true);
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(2);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockTestDevice, mMockRunUtil, mMockListener);

        mAutotestLauncher.run(mMockListener);
        EasyMock.verify(mMockTestDevice, mMockRunUtil, mMockListener);
    }

    /**
     * Test {@link AutotestLauncher#run(ITestInvocationListener)} with test_droid command succeeded
     * without status.log
     */
    @Test(expected = RuntimeException.class)
    public void testRun_SucceedWithoutStatusLog() throws DeviceNotAvailableException, IOException {
        setupAutotest();
        setupRunUtilMock(true);
        setupMockListener(false);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(2);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockTestDevice, mMockRunUtil, mMockListener);

        try {
            mAutotestLauncher.run(mMockListener);
        }
        finally {
            EasyMock.verify(mMockTestDevice, mMockRunUtil, mMockListener);
        }
    }

    /**
     * Test {@link AutotestLauncher#run(ITestInvocationListener)} with test_droid command failed
     */
    @Test
    public void testRun_Fail() throws DeviceNotAvailableException, IOException {
        setupAutotest();
        setupRunUtilMock(false);
        setupStatusLog();
        setupMockListener(false);
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(2);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockTestDevice, mMockRunUtil, mMockListener);

        mAutotestLauncher.run(mMockListener);
        EasyMock.verify(mMockTestDevice, mMockRunUtil, mMockListener);
    }

    /**
     * Test {@link AutotestLauncher#run(ITestInvocationListener)} with test_droid command failed
     * without status.log
     */
    @Test(expected = RuntimeException.class)
    public void testRun_FailWithoutStatusLog() throws DeviceNotAvailableException, IOException {
        setupAutotest();
        setupRunUtilMock(false);
        setupMockListener(false);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL).times(2);
        EasyMock.expectLastCall();
        EasyMock.replay(mMockTestDevice, mMockRunUtil, mMockListener);

        try {
            mAutotestLauncher.run(mMockListener);
        }
        finally {
            EasyMock.verify(mMockTestDevice, mMockRunUtil, mMockListener);
        }
    }
}
