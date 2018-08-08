// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import com.google.android.tradefed.util.GceAvdInfo;
import com.google.android.tradefed.util.GceAvdInfo.GceStatus;
import com.google.common.net.HostAndPort;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unit tests for {@link GceManager} */
@RunWith(JUnit4.class)
public class GceManagerTest {

    private GceManager mGceManager;
    private DeviceDescriptor mMockDeviceDesc;
    private GceAvdTestDeviceOptions mOptions;
    private IBuildInfo mMockBuildInfo;
    private IRunUtil mMockRunUtil;

    @Before
    public void setUp() {
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockDeviceDesc = Mockito.mock(DeviceDescriptor.class);
        mMockBuildInfo = new BuildInfo();
        mOptions = new GceAvdTestDeviceOptions();
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
    }

    /** Test {@link GceManager#extractInstanceName(String)} with a typical gce log. */
    @Test
    public void testExtractNameFromLog() {
        String log =
                "2016-09-13 00:05:08,261 |INFO| gcompute_client:283| "
                        + "Image image-gce-x86-phone-userdebug-fastbuild-linux-3266697-1f7cc554 "
                        + "has been created.\n2016-09-13 00:05:08,261 |INFO| gstorage_client:102| "
                        + "Deleting file: bucket: android-artifacts-cache, object: 9a76b1f96c7e4da"
                        + "19b90b0c4e97f9450-avd-system.tar.gz\n2016-09-13 00:05:08,412 |INFO| gst"
                        + "orage_client:107| Deleted file: bucket: android-artifacts-cache, object"
                        + ": 9a76b1f96c7e4da19b90b0c4e97f9450-avd-system.tar.gz\n2016-09-13 00:05:"
                        + "09,331 |INFO| gcompute_client:728| Creating instance: project android-t"
                        + "reehugger, zone us-central1-f, body:{'networkInterfaces': [{'network': "
                        + "u'https://www.googleapis.com/compute/v1/projects/android-treehugger/glo"
                        + "bal/networks/default', 'accessConfigs': [{'type': 'ONE_TO_ONE_NAT', 'na"
                        + "me': 'External NAT'}]}], 'name': 'gce-x86-phone-userdebug-fastbuild-lin"
                        + "ux-3266697-144fcf59', 'serviceAccounts': [{'email': 'default', 'scopes'"
                        + ": ['https://www.googleapis.com/auth/devstorage.read_only', 'https://www"
                        + ".googleapis.com/auth/logging.write']}], 'disks': [{'autoDelete': True, "
                        + "'boot': True, 'mode': 'READ_WRITE', 'initializeParams': {'diskName': 'g"
                        + "ce-x86-phone-userdebug-fastbuild-linux-3266697-144fcf59', 'sourceImage'"
                        + ": u'https://www.googleapis.com/compute/v1/projects/a";
        String result = mGceManager.extractInstanceName(log);
        assertEquals("gce-x86-phone-userdebug-fastbuild-linux-3266697-144fcf59", result);
    }

    /** Test {@link GceManager#extractInstanceName(String)} with a typical gce log. */
    @Test
    public void testExtractNameFromLog_newFormat() {
        String log =
                "2016-09-20 08:11:02,287 |INFO| gcompute_client:728| Creating instance: "
                        + "project android-treehugger, zone us-central1-f, body:{'name': "
                        + "'ins-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux', "
                        + "'disks': [{'autoDelete': True, 'boot': True, 'mode': 'READ_WRITE', "
                        + "'initializeParams': {'diskName': 'gce-x86-phone-userdebug-fastbuild-"
                        + "linux-3286354-eb1fd2e3', 'sourceImage': u'https://www.googleapis.com"
                        + "compute/v1/projects/android-treehugger/global/images/image-gce-x86-ph"
                        + "one-userdebug-fastbuild-linux-3286354-b6b99338'}, 'type': 'PERSISTENT'"
                        + "}, {'autoDelete': True, 'deviceName': 'gce-x86-phone-userdebug-fastbuil"
                        + "d-linux-3286354-eb1fd2e3-data', 'interface': 'SCSI', 'mode': 'READ_WRI"
                        + "TE', 'type': 'PERSISTENT', 'boot': False, 'source': u'projects/andro"
                        + "id-treehugger/zones/us-c}]}";
        String result = mGceManager.extractInstanceName(log);
        assertEquals("ins-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux", result);
    }

    /**
     * Test {@link GceManager#extractInstanceName(String)} with a log that does not contains the gce
     * name.
     */
    @Test
    public void testExtractNameFromLog_notfound() {
        String log =
                "2016-09-20 08:11:02,287 |INFO| gcompute_client:728| Creating instance: "
                        + "project android-treehugger, zone us-central1-f, body:{'name': "
                        + "'name-80bd5bd1-3708674-gce-x86-phone-userdebug-fastbuild3c-linux',"
                        + "[{'autoDelete': True, 'boot': True, 'mode': 'READ_WRITE', 'initia "
                        + "{'diskName': 'gce-x86-phone-userdebug-fastbuild-linux-3286354-eb1 "
                        + "'sourceImage': u'https://www.googleapis.com/compute/v1/projects/an"
                        + "treehugger/global/images/image-gce-x86-phone-userdebug-fastbuild-g";
        String result = mGceManager.extractInstanceName(log);
        assertNull(result);
    }

    /** Test {@link GceManager#buildGceCmd(File, IBuildInfo)}. */
    @Test
    public void testBuildGceCommand() throws IOException {
        IBuildInfo mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuildInfo.getBuildAttributes())
                .andReturn(Collections.<String, String>emptyMap());
        EasyMock.expect(mMockBuildInfo.getBuildFlavor()).andReturn("FLAVOR");
        EasyMock.expect(mMockBuildInfo.getBuildBranch()).andReturn("BRANCH");
        EasyMock.expect(mMockBuildInfo.getBuildId()).andReturn("BUILDID");
        EasyMock.replay(mMockBuildInfo);
        File reportFile = null;
        try {
            reportFile = FileUtil.createTempFile("test-gce-cmd", "report");
            List<String> result = mGceManager.buildGceCmd(reportFile, mMockBuildInfo);
            List<String> expected =
                    ArrayUtil.list(
                            mOptions.getAvdDriverBinary(),
                            "create",
                            "--build_target",
                            "FLAVOR",
                            "--branch",
                            "BRANCH",
                            "--build_id",
                            "BUILDID",
                            "--config_file",
                            mOptions.getAvdConfigFile(),
                            "--report_file",
                            reportFile.getAbsolutePath(),
                            "-v",
                            "--logcat_file",
                            mGceManager.getGceBootLogcatLog().getAbsolutePath(),
                            "--serial_log_file",
                            mGceManager.getGceBootSerialLog().getAbsolutePath());
            assertEquals(expected, result);
            assertTrue(mGceManager.getGceBootLogcatLog().exists());
            assertTrue(mGceManager.getGceBootSerialLog().exists());
        } finally {
            FileUtil.deleteFile(reportFile);
            FileUtil.deleteFile(mGceManager.getGceBootLogcatLog());
            FileUtil.deleteFile(mGceManager.getGceBootSerialLog());
        }
        EasyMock.verify(mMockBuildInfo);
    }

    /** Ensure exception is thrown after a timeout from the acloud command. */
    @Test
    public void testStartGce_timeout() throws Exception {
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(File reportFile, IBuildInfo b)
                            throws IOException {
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        final String expectedException =
                "acloud errors: timeout after 1200000ms, " + "acloud did not return null";
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.TIMED_OUT);
        cmd.setStdout("output err");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(), (String[]) EasyMock.anyObject()))
                .andReturn(cmd);
        EasyMock.replay(mMockRunUtil);
        doReturn(null).when(mMockDeviceDesc).toString();
        try {
            mGceManager.startGce();
            fail("A TargetSetupError should have been thrown");
        } catch (TargetSetupError expected) {
            assertEquals(expectedException, expected.getMessage());
        }
        EasyMock.verify(mMockRunUtil);
    }

    /**
     * Test that a {@link GceAvdInfo} is properly created when the output of acloud and runutil is
     * fine.
     */
    @Test
    public void testStartGce() throws Exception {
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(File reportFile, IBuildInfo b)
                            throws IOException {
                        String valid =
                                " {\n"
                                        + "\"data\": {\n"
                                        + "\"devices\": [\n"
                                        + "{\n"
                                        + "\"ip\": \"104.154.62.236\",\n"
                                        + "\"instance_name\": \"gce-x86-phone-userdebug-22\"\n"
                                        + "}\n"
                                        + "]\n"
                                        + "},\n"
                                        + "\"errors\": [],\n"
                                        + "\"command\": \"create\",\n"
                                        + "\"status\": \"SUCCESS\"\n"
                                        + "}";
                        FileUtil.writeToFile(valid, reportFile);
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.SUCCESS);
        cmd.setStdout("output");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(), (String[]) EasyMock.anyObject()))
                .andReturn(cmd);

        EasyMock.replay(mMockRunUtil);
        GceAvdInfo res = mGceManager.startGce();
        EasyMock.verify(mMockRunUtil);
        assertNotNull(res);
        assertEquals(GceStatus.SUCCESS, res.getStatus());
    }

    /**
     * Test that in case of improper output from acloud we throw an exception since we could not get
     * the valid information we are looking for.
     */
    @Test
    public void testStartGce_failed() throws Exception {
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(File reportFile, IBuildInfo b)
                            throws IOException {
                        // We delete the potential report file to create an issue.
                        FileUtil.deleteFile(reportFile);
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.FAILED);
        cmd.setStdout("output");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(), (String[]) EasyMock.anyObject()))
                .andReturn(cmd);
        EasyMock.replay(mMockRunUtil);
        try {
            mGceManager.startGce();
            fail("Should have thrown an exception");
        } catch (TargetSetupError expected) {

        }
        EasyMock.verify(mMockRunUtil);
    }

    /**
     * Test that even in case of BOOT_FAIL if we can get some valid information about the GCE
     * instance, then we still return a GceAvdInfo to describe it.
     */
    @Test
    public void testStartGce_bootFail() throws Exception {
        mGceManager =
                new GceManager(mMockDeviceDesc, mOptions, mMockBuildInfo) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    protected List<String> buildGceCmd(File reportFile, IBuildInfo b)
                            throws IOException {
                        String validFail =
                                " {\n"
                                        + "\"data\": {\n"
                                        + "\"devices_failing_boot\": [\n"
                                        + "{\n"
                                        + "\"ip\": \"104.154.62.236\",\n"
                                        + "\"instance_name\": \"ins-x86-phone-userdebug-229\"\n"
                                        + "}\n"
                                        + "]\n"
                                        + "},\n"
                                        + "\"errors\": [\"device did not boot\"],\n"
                                        + "\"command\": \"create\",\n"
                                        + "\"status\": \"BOOT_FAIL\"\n"
                                        + "}";
                        FileUtil.writeToFile(validFail, reportFile);
                        List<String> tmp = new ArrayList<String>();
                        tmp.add("");
                        return tmp;
                    }
                };
        CommandResult cmd = new CommandResult();
        cmd.setStatus(CommandStatus.FAILED);
        cmd.setStdout("output");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(), (String[]) EasyMock.anyObject()))
                .andReturn(cmd);
        EasyMock.replay(mMockRunUtil);
        GceAvdInfo res = mGceManager.startGce();
        EasyMock.verify(mMockRunUtil);
        assertNotNull(res);
        assertEquals(GceStatus.BOOT_FAIL, res.getStatus());
    }

    /** Test a success case for collecting the bugreport with ssh. */
    @Test
    public void testGetSshBugreport() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        CommandResult res = new CommandResult(CommandStatus.SUCCESS);
        res.setStdout("bugreport success!\nOK:/bugreports/bugreport.zip\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                EasyMock.eq("ssh"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("UserKnownHostsFile=/dev/null"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("StrictHostKeyChecking=no"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("ServerAliveInterval=10"),
                                EasyMock.eq("-i"),
                                EasyMock.anyObject(),
                                EasyMock.eq("root@127.0.0.1"),
                                EasyMock.eq("bugreportz")))
                .andReturn(res);
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                EasyMock.eq("scp"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("UserKnownHostsFile=/dev/null"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("StrictHostKeyChecking=no"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("ServerAliveInterval=10"),
                                EasyMock.eq("-i"),
                                EasyMock.anyObject(),
                                EasyMock.eq("root@127.0.0.1:/bugreports/bugreport.zip"),
                                EasyMock.anyObject()))
                .andReturn(res);
        EasyMock.replay(mMockRunUtil);
        File bugreport = null;
        try {
            bugreport = GceManager.getBugreportzWithSsh(fakeInfo, mOptions, mMockRunUtil);
            assertNotNull(bugreport);
        } finally {
            EasyMock.verify(mMockRunUtil);
            FileUtil.deleteFile(bugreport);
        }
    }

    /**
     * Test a case where bugreportz command timeout or may have failed but we still get an output.
     * In this case we still proceed and try to get the bugreport.
     */
    @Test
    public void testGetSshBugreport_Fail() throws Exception {
        GceAvdInfo fakeInfo = new GceAvdInfo("ins-gce", HostAndPort.fromHost("127.0.0.1"));
        CommandResult res = new CommandResult(CommandStatus.FAILED);
        res.setStdout("bugreport failed!\n");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                EasyMock.eq("ssh"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("UserKnownHostsFile=/dev/null"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("StrictHostKeyChecking=no"),
                                EasyMock.eq("-o"),
                                EasyMock.eq("ServerAliveInterval=10"),
                                EasyMock.eq("-i"),
                                EasyMock.anyObject(),
                                EasyMock.eq("root@127.0.0.1"),
                                EasyMock.eq("bugreportz")))
                .andReturn(res);
        EasyMock.replay(mMockRunUtil);
        File bugreport = null;
        try {
            bugreport = GceManager.getBugreportzWithSsh(fakeInfo, mOptions, mMockRunUtil);
            assertNull(bugreport);
        } finally {
            EasyMock.verify(mMockRunUtil);
            FileUtil.deleteFile(bugreport);
        }
    }
}
