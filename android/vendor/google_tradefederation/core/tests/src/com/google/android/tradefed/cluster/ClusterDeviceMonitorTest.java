// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.cluster;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.Map;

public class ClusterDeviceMonitorTest extends TestCase {

    private static final String PRODCERTSTATUS_KEY = "LOAS status";
    private static final String KRBSTATUS_KEY = "Kerberos status";
    private static final String PRODCERTSTATUS_CMD = "prodcertstatus";
    private static final String KRBSTATUS_CMD = "krbstatus";
    private IRunUtil mRunUtil = null;
    private ClusterDeviceMonitor mClusterDeviceMonitor = null;
    private OptionSetter mClusterDeviceMonitorSetter = null;

    @Override
    public void setUp() throws Exception {
        mRunUtil = EasyMock.createMock(IRunUtil.class);
        mClusterDeviceMonitor = new ClusterDeviceMonitor() {
            @Override
            public IRunUtil getRunUtil() {
                return mRunUtil;
            }
        };
        mClusterDeviceMonitorSetter = new OptionSetter(mClusterDeviceMonitor);
    }

    void setOptions() throws Exception {
        mClusterDeviceMonitorSetter.setOptionValue("host-info-cmd", PRODCERTSTATUS_KEY,
                PRODCERTSTATUS_CMD);
        mClusterDeviceMonitorSetter.setOptionValue("host-info-cmd", KRBSTATUS_KEY, KRBSTATUS_CMD);
    }

    // Test getting additional host information
    public void testGetAdditionalHostInfo() throws Exception {
        setOptions();
        String prodcertstatusOutput = "LOAS cert expires in 13h 5m";
        CommandResult prodcertstatusMockResult = new CommandResult();
        prodcertstatusMockResult.setStdout(prodcertstatusOutput);
        prodcertstatusMockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmdSilently(
                EasyMock.anyInt(),
                EasyMock.eq(PRODCERTSTATUS_CMD))).andReturn(prodcertstatusMockResult).times(1);

        String krbstatusOutput = "android-test ticket expires in 65d 19h";
        CommandResult krbstatusMockResult = new CommandResult();
        krbstatusMockResult.setStdout(krbstatusOutput);
        krbstatusMockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmdSilently(
                EasyMock.anyInt(),
                EasyMock.eq(KRBSTATUS_CMD))).andReturn(krbstatusMockResult).times(1);
        EasyMock.replay(mRunUtil);

        Map<String, String> expected = new HashMap<>();
        expected.put(PRODCERTSTATUS_KEY, prodcertstatusOutput);
        expected.put(KRBSTATUS_KEY, krbstatusOutput);

        assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
        EasyMock.verify(mRunUtil);
    }

    // Test getting additional host information with no commands to run
    public void testGetAdditionalHostInfo_noCommands() {
        Map<String, String> expected = new HashMap<>();
        assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
    }

    // Test getting additional host information with failures
    public void testGetAdditionalHostInfo_commandFailed() throws Exception {
        setOptions();
        String prodcertstatusOutput = "LOAS cert expires in 13h 5m";
        CommandResult prodcertstatusMockResult = new CommandResult();
        prodcertstatusMockResult.setStdout(prodcertstatusOutput);
        prodcertstatusMockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmdSilently(
                EasyMock.anyInt(),
                EasyMock.eq(PRODCERTSTATUS_CMD))).andReturn(prodcertstatusMockResult).times(1);

        String krbstatusOutput = "android-test ticket expires in 65d 19h";
        String krbstatusError = "Some terrible failure";
        CommandResult krbstatusMockResult = new CommandResult();
        krbstatusMockResult.setStdout(krbstatusOutput);
        krbstatusMockResult.setStderr(krbstatusError);
        krbstatusMockResult.setStatus(CommandStatus.FAILED);
        EasyMock.expect(mRunUtil.runTimedCmdSilently(
                EasyMock.anyInt(),
                EasyMock.eq(KRBSTATUS_CMD))).andReturn(krbstatusMockResult).times(1);
        EasyMock.replay(mRunUtil);

        Map<String, String> expected = new HashMap<>();
        expected.put(PRODCERTSTATUS_KEY, prodcertstatusOutput);
        expected.put(KRBSTATUS_KEY, krbstatusError);

        assertEquals(expected, mClusterDeviceMonitor.getAdditionalHostInfo());
        EasyMock.verify(mRunUtil);
    }

}
