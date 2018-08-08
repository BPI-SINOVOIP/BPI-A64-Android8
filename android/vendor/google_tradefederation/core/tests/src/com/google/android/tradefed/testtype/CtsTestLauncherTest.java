//Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.testtype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link CtsTestLauncher}. */
@RunWith(JUnit4.class)
public class CtsTestLauncherTest {

    private CtsTestLauncher mCtsTestLauncher;
    private FolderBuildInfo mBuildInfo;
    private ITestInvocationListener mMockListener;
    private IRunUtil mMockRunUtil;
    private ITestDevice mMockTestDevice;
    private IConfiguration mMockConfig;
    private IInvocationContext mFakeContext;

    private File mCtsRoot;
    private File mTfPath;

    private static final String FAKE_SERIAL = "FAKESERIAL";
    private static final String ENV_NAME = "TF_JAR_DIR";
    protected static final String[] TF_JAR =
        { "google-tradefed.jar", "google-tf-prod-tests.jar" };

    private List<String> mListJarNames = new ArrayList<String>();

    @Before
    public void setUp() throws Exception {
        mCtsRoot = FileUtil.createTempDir("cts-launcher-root");
        mTfPath = FileUtil.createTempDir("tf-path");
        mFakeContext = new InvocationContext();
        mFakeContext.setTestTag("TEST_TAG");
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockTestDevice = EasyMock.createMock(ITestDevice.class);
        mMockConfig = EasyMock.createMock(IConfiguration.class);

        mBuildInfo = new FolderBuildInfo("buildId", "buildName");
        mBuildInfo.setRootDir(mCtsRoot);
        mBuildInfo.setDeviceSerial(FAKE_SERIAL);
        EasyMock.expect(mMockTestDevice.getSerialNumber()).andReturn(FAKE_SERIAL);

        mCtsTestLauncher =
                new CtsTestLauncher() {
                    @Override
                    protected IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }

                    @Override
                    void createSubprocessTmpDir(List<String> args) {
                        // ignore
                    }

                    @Override
                    void createHeapDumpTmpDir(List<String> args) {
                        // ignore
                    }
                };
        mCtsTestLauncher.setBuild(mBuildInfo);
        mCtsTestLauncher.setDevice(mMockTestDevice);
        mCtsTestLauncher.setConfigName("cts");
        mCtsTestLauncher.addRunJar("hosttestlib.jar");
        mCtsTestLauncher.setConfiguration(mMockConfig);
        mCtsTestLauncher.setInvocationContext(mFakeContext);

        mListJarNames.add("hosttestlib.jar");
        mListJarNames.add("tradefed.jar");
        mListJarNames.add("cts-tradefed.jar");

        System.setProperty(ENV_NAME, mTfPath.getAbsolutePath());
        for (String tfJar : TF_JAR) {
            File tempFile = new File(mTfPath, tfJar);
            tempFile.createNewFile();
            mBuildInfo.setFile(tfJar, tempFile, null);
        }
        File ctsPath = new File(new File(mCtsRoot, "android-cts"), "tools");
        ctsPath.mkdirs();
        for (String ctsJar : mListJarNames) {
            File tempFile = new File(ctsPath, ctsJar);
            tempFile.createNewFile();
        }

        EasyMock.expect(mMockConfig.getCommandOptions()).andStubReturn(new CommandOptions());

        File ctsPathTest = new File(new File(mCtsRoot, "android-cts"), "testcases");
        ctsPathTest.mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty(ENV_NAME);
        FileUtil.recursiveDelete(mCtsRoot);
        FileUtil.recursiveDelete(mTfPath);
    }

    /** Test {@link CtsTestLauncher#buildClasspath()} with empty env */
    @Test
    public void testBuildClasspath_emptyEnv() throws Exception {
        System.clearProperty(ENV_NAME);
        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (NullPointerException expected) {
            assertEquals("TF_JAR_DIR env variable is not set", expected.getMessage());
        }
    }

    /** Test {@link CtsTestLauncher#buildClasspath()} with File exception because not TF jar */
    @Test
    public void testBuildClasspath_returnNotFoundNoTfJar() {
        // Remove only TF jars
        for (String tfJar : TF_JAR) {
            File tempFile = new File(mTfPath, tfJar);
            tempFile.delete();
        }

        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (FileNotFoundException expected) {
            assertTrue(expected.getMessage().contains("Couldn't find the jar file"));
        }
    }

    /** Test {@link CtsTestLauncher#buildClasspath()} with File exception because no CTS jar */
    @Test
    public void testBuildClasspath_returnNotFoundNoCtsJar() {
        // Remove CTS Jars
        for (String ctsJar : mListJarNames) {
            File tempFile = new File(new File(new File(mCtsRoot, "android-cts"), "tools"), ctsJar);
            tempFile.delete();
        }

        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (FileNotFoundException expected) {
            assertTrue(expected.getMessage().contains("Couldn't find the jar file"));
        }
    }

    /**
     * Test {@link CtsTestLauncher#buildClasspath()} with File exception because no CTS build
     * directory.
     */
    @Test
    public void testBuildClasspath_returnNotFoundNoCtsBuildDir() {
        FolderBuildInfo wrongBuildInfo = new FolderBuildInfo("buildId", "buildName");
        wrongBuildInfo.setRootDir(new File("/wrong/not/exist"));
        wrongBuildInfo.setDeviceSerial(FAKE_SERIAL);

        mCtsTestLauncher.setBuild(wrongBuildInfo);

        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (FileNotFoundException expected) {
            assertEquals(
                    expected.getMessage(), "Couldn't find the build directory: /wrong/not/exist");
        }
    }

    /**
     * Test {@link CtsTestLauncher#buildClasspath()} with Illegal Argument exception because
     * BuildInfo got the wrong type
     */
    @Test
    public void testBuildClasspath_returnIllegalBuildInfoType() throws Exception {
        BuildInfo wrongBuildInfo = new BuildInfo("buildId", "buildName");
        wrongBuildInfo.setDeviceSerial(FAKE_SERIAL);

        mCtsTestLauncher.setBuild(wrongBuildInfo);

        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (IllegalArgumentException expected) {
            assertEquals(expected.getMessage(), "Build info needs to be of type IFolderBuildInfo");
        }
    }

    /** Test {@link CtsTestLauncher#buildClasspath()} with return classpath and no exception. */
    @Test
    public void testBuildClasspath_returnClasspath() throws Exception {
        String res = mCtsTestLauncher.buildClasspath();
        assertNotNull(res);
    }

    /**
     * Test {@link CtsTestLauncher#buildJavaCmd(String)} with proper return
     * for the basic command template
     */
    @Test
    public void testBuildJavaCmd_returnCmd() {
        List<String> expected = new ArrayList<String>();
        expected.add("java");
        expected.add("-cp");
        expected.add("FAKE_CP");
        expected.add("com.android.tradefed.command.CommandRunner");
        expected.add("cts");
        expected.add("--log-level");
        expected.add("VERBOSE");
        expected.add("--log-level-display");
        expected.add("VERBOSE");
        expected.add("--serial");
        expected.add(FAKE_SERIAL);
        expected.add("--build-id");
        expected.add("buildId");
        expected.add("--cts-install-path");
        expected.add(mCtsRoot.getAbsolutePath());

        EasyMock.replay(mMockTestDevice, mMockConfig);
        List<String> actual = mCtsTestLauncher.buildJavaCmd("FAKE_CP");
        Assert.assertArrayEquals(expected.toArray(), actual.toArray());
        EasyMock.verify(mMockTestDevice, mMockConfig);
    }

    /**
     * Test {@link CtsTestLauncher#buildJavaCmd(String)} with proper return
     * for the basic command template
     */
    @Test
    public void testBuildJavaCmd_returnCmd_withBuildInfo() {
        List<String> expected = new ArrayList<String>();
        expected.add("java");
        expected.add("-cp");
        expected.add("FAKE_CP");
        expected.add("com.android.tradefed.command.CommandRunner");
        expected.add("cts");
        expected.add("--log-level");
        expected.add("VERBOSE");
        expected.add("--log-level-display");
        expected.add("VERBOSE");
        expected.add("--serial");
        expected.add(FAKE_SERIAL);
        expected.add("--build-id");
        expected.add("buildId");
        expected.add("--build-flavor");
        expected.add("buildFlavor");
        expected.add("--build-attribute");
        expected.add("build_target=buildTarget");
        expected.add("--cts-install-path");
        expected.add(mCtsRoot.getAbsolutePath());

        mBuildInfo.setBuildFlavor("buildFlavor");
        mBuildInfo.addBuildAttribute("build_target", "buildTarget");

        EasyMock.replay(mMockTestDevice);
        List<String> actual = mCtsTestLauncher.buildJavaCmd("FAKE_CP");
        Assert.assertArrayEquals(expected.toArray(), actual.toArray());
    }

    /**
     * Test {@link CtsTestLauncher#buildJavaCmd(String)} with proper return
     * for the basic command template when running without root
     */
    @Test
    public void testBuildJavaCmd_returnCmdNonRoot() {
        List<String> expected = new ArrayList<String>();
        mCtsTestLauncher.setRunAsRoot(false);
        expected.add("java");
        expected.add("-cp");
        expected.add("FAKE_CP");
        expected.add("com.android.tradefed.command.CommandRunner");
        expected.add("cts");
        expected.add("--log-level");
        expected.add("VERBOSE");
        expected.add("--log-level-display");
        expected.add("VERBOSE");
        expected.add("--serial");
        expected.add(FAKE_SERIAL);
        expected.add("--build-id");
        expected.add("buildId");
        expected.add("--cts-install-path");
        expected.add(mCtsRoot.getAbsolutePath());
        expected.add("--no-enable-root");

        EasyMock.replay(mMockTestDevice, mMockConfig);
        List<String> actual = mCtsTestLauncher.buildJavaCmd("FAKE_CP");
        Assert.assertArrayEquals(expected.toArray(), actual.toArray());
        EasyMock.verify(mMockTestDevice, mMockConfig);
    }

    /**
     * Test {@link CtsTestLauncher#buildJavaCmd(String)} with proper return
     * for the basic command template with V2
     */
    @Test
    public void testBuildJavaCmd_returnCmdV2() {
        mCtsTestLauncher.setCtsVersion(2);
        List<String> expected = new ArrayList<String>();
        expected.add("java");
        expected.add("-cp");
        expected.add("FAKE_CP");
        expected.add(String.format("-DCTS_ROOT=%s", mCtsRoot.getAbsolutePath()));
        expected.add("com.android.tradefed.command.CommandRunner");
        expected.add("cts");
        expected.add("--log-level");
        expected.add("VERBOSE");
        expected.add("--log-level-display");
        expected.add("VERBOSE");
        expected.add("--serial");
        expected.add(FAKE_SERIAL);
        expected.add("--build-id");
        expected.add("buildId");

        EasyMock.replay(mMockTestDevice, mMockConfig);
        List<String> actual = mCtsTestLauncher.buildJavaCmd("FAKE_CP");
        Assert.assertArrayEquals(expected.toArray(), actual.toArray());
        EasyMock.verify(mMockTestDevice, mMockConfig);
    }

    /**
     * Test {@link CtsTestLauncher#buildJavaCmd(String)} is adding invocation-data to the java cmd
     * when passed from the main invocation.
     */
    @Test
    public void testBuildJavaCmd_invocationData() throws Exception {
        IConfiguration mockConfig = EasyMock.createMock(IConfiguration.class);
        CommandOptions cmdOption = new CommandOptions();
        OptionSetter setter = new OptionSetter(cmdOption);
        setter.setOptionValue("invocation-data", "CL_NUMBER", "12345678");
        EasyMock.expect(mockConfig.getCommandOptions()).andReturn(cmdOption);
        OptionSetter s = new OptionSetter(mCtsTestLauncher);
        s.setOptionValue("inject-invocation-data", "true");
        mCtsTestLauncher.setCtsVersion(2);
        List<String> expected = new ArrayList<String>();
        expected.add("java");
        expected.add("-cp");
        expected.add("FAKE_CP");
        expected.add(String.format("-DCTS_ROOT=%s", mCtsRoot.getAbsolutePath()));
        expected.add("com.android.tradefed.command.CommandRunner");
        expected.add("cts");
        expected.add("--log-level");
        expected.add("VERBOSE");
        expected.add("--log-level-display");
        expected.add("VERBOSE");
        expected.add("--serial");
        expected.add(FAKE_SERIAL);
        expected.add("--build-id");
        expected.add("buildId");
        expected.add("--invocation-data");
        expected.add("CL_NUMBER");
        expected.add("12345678");

        mCtsTestLauncher.setConfiguration(mockConfig);
        EasyMock.replay(mMockTestDevice, mockConfig);
        List<String> actual = mCtsTestLauncher.buildJavaCmd("FAKE_CP");
        Assert.assertArrayEquals(expected.toArray(), actual.toArray());
        EasyMock.verify(mMockTestDevice, mockConfig);
    }

    /**
     * Test {@link CtsTestLauncher#buildClasspath()} with return classpath and no exception with V2
     */
    @Test
    public void testBuildClasspath_returnClasspathV2() throws Exception {
        mCtsTestLauncher.setCtsVersion(2);
        String res = mCtsTestLauncher.buildClasspath();
        assertNotNull(res);
        assertTrue(res.contains("android-cts/testcases/"));
    }

    /**
     * Test {@link CtsTestLauncher#buildClasspath()} with File exception because no testcases folder
     */
    @Test
    public void testBuildClasspath_returnNotFoundTestcases() {
        mCtsTestLauncher.setCtsVersion(2);
        // Remove Testcases folder
        new File(new File(mCtsRoot, "android-cts"), "testcases").delete();
        try {
            mCtsTestLauncher.buildClasspath();
            fail("Should have thrown an exception.");
        } catch (FileNotFoundException expected) {
        }
    }

    /**
    * Test {@link CtsTestLauncher#run(ITestInvocationListener)} with an incorrect version
    */
    @Test
    public void testRun_badVersion() throws DeviceNotAvailableException {
        mCtsTestLauncher.setCtsVersion(3);
        try {
            mCtsTestLauncher.run(mMockListener);
            fail();
        } catch (RuntimeException e) {
            // expected
        }
        mCtsTestLauncher.setCtsVersion(0);
        try {
            mCtsTestLauncher.run(mMockListener);
            fail();
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
    * Test {@link CtsTestLauncher#run(ITestInvocationListener)} with no additional jar
    */
    @Test
    public void testRun_noRunJar() throws DeviceNotAvailableException {
        mCtsTestLauncher = new CtsTestLauncher();
        mCtsTestLauncher.setBuild(mBuildInfo);
        mCtsTestLauncher.setConfigName("cts");
        mCtsTestLauncher.setCtsVersion(1);
        try {
            mCtsTestLauncher.run(mMockListener);
            fail();
        } catch (RuntimeException e) {
            // expected
        }
    }

    /**
    * Test {@link CtsTestLauncher#run(ITestInvocationListener)} with a success case.
    */
    @Test
    public void testRun_success() throws Exception {
        mCtsTestLauncher = new CtsTestLauncher() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
            @Override
            public List<String> buildJavaCmd(String classpath) {
                List<String> fake = new ArrayList<String>();
                fake.add("java");
                mCtsTestLauncher.getDevice().getSerialNumber();
                return fake;
            }
        };
        OptionSetter setter = new OptionSetter(mCtsTestLauncher);
        setter.setOptionValue("use-event-streaming", "false");
        mCtsTestLauncher.setBuild(mBuildInfo);
        mCtsTestLauncher.setConfigName("cts");
        mCtsTestLauncher.setDevice(mMockTestDevice);
        mCtsTestLauncher.addRunJar("hosttestlib.jar");
        mCtsTestLauncher.setInvocationContext(mFakeContext);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        result.setStdout("stdout");
        result.setStderr("stderr");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                (OutputStream) EasyMock.anyObject(),
                                (OutputStream) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject()))
                .andReturn(result);
        mMockRunUtil.unsetEnvVariable(EasyMock.eq("TF_GLOBAL_CONFIG"));
        EasyMock.expectLastCall();
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        setElapsedTimeExpectation();
        EasyMock.replay(mMockRunUtil, mMockListener, mMockTestDevice);
        mCtsTestLauncher.run(mMockListener);
        EasyMock.verify(mMockRunUtil, mMockListener, mMockTestDevice);
    }

    /**
    * Test {@link CtsTestLauncher#run(ITestInvocationListener)} with a command failure.
    */
    @Test
    public void testRun_failure() throws Exception {
        mCtsTestLauncher = new CtsTestLauncher() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
            @Override
            public List<String> buildJavaCmd(String classpath) {
                List<String> fake = new ArrayList<String>();
                fake.add("java");
                mCtsTestLauncher.getDevice().getSerialNumber();
                return fake;
            }
        };
        OptionSetter setter = new OptionSetter(mCtsTestLauncher);
        setter.setOptionValue("use-event-streaming", "false");
        mCtsTestLauncher.setBuild(mBuildInfo);
        mCtsTestLauncher.setConfigName("cts");
        mCtsTestLauncher.setDevice(mMockTestDevice);
        mCtsTestLauncher.addRunJar("hosttestlib.jar");
        mCtsTestLauncher.setInvocationContext(mFakeContext);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.FAILED);
        result.setStdout("stdout");
        result.setStderr("stderr");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                (OutputStream) EasyMock.anyObject(),
                                (OutputStream) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject()))
                .andReturn(result);
        mMockRunUtil.unsetEnvVariable(EasyMock.eq("TF_GLOBAL_CONFIG"));
        EasyMock.expectLastCall();
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        setElapsedTimeExpectation();
        EasyMock.replay(mMockRunUtil, mMockListener, mMockTestDevice);
        try {
            mCtsTestLauncher.run(mMockListener);
            fail("CtsTestLauncher should have thrown an exception");
        } catch (RuntimeException e) {
            // expected
        }
        EasyMock.verify(mMockRunUtil, mMockListener, mMockTestDevice);
    }

    /**
    * Test {@link CtsTestLauncher#run(ITestInvocationListener)} with a command timeout.
    */
    @Test
    public void testRun_timeout() throws Exception {
        mCtsTestLauncher = new CtsTestLauncher() {
            @Override
            protected IRunUtil getRunUtil() {
                return mMockRunUtil;
            }
            @Override
            public List<String> buildJavaCmd(String classpath) {
                List<String> fake = new ArrayList<String>();
                fake.add("java");
                mCtsTestLauncher.getDevice().getSerialNumber();
                return fake;
            }
        };
        OptionSetter setter = new OptionSetter(mCtsTestLauncher);
        setter.setOptionValue("use-event-streaming", "false");
        mCtsTestLauncher.setBuild(mBuildInfo);
        mCtsTestLauncher.setConfigName("cts");
        mCtsTestLauncher.setDevice(mMockTestDevice);
        mCtsTestLauncher.addRunJar("hosttestlib.jar");
        mCtsTestLauncher.setInvocationContext(mFakeContext);
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.TIMED_OUT);
        result.setStdout("stdout");
        result.setStderr("stderr");
        EasyMock.expect(
                        mMockRunUtil.runTimedCmd(
                                EasyMock.anyLong(),
                                (OutputStream) EasyMock.anyObject(),
                                (OutputStream) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject(),
                                (String) EasyMock.anyObject()))
                .andReturn(result);
        mMockRunUtil.unsetEnvVariable(EasyMock.eq("TF_GLOBAL_CONFIG"));
        EasyMock.expectLastCall();
        mMockListener.testLog((String)EasyMock.anyObject(), EasyMock.eq(LogDataType.TEXT),
                (InputStreamSource)EasyMock.anyObject());
        EasyMock.expectLastCall().times(3);
        setElapsedTimeExpectation();
        EasyMock.replay(mMockRunUtil, mMockListener, mMockTestDevice);
        try {
            mCtsTestLauncher.run(mMockListener);
            fail("CtsTestLauncher should have thrown an exception");
        } catch (RuntimeException e) {
            assertEquals("cts Tests subprocess failed due to:\n" +
                    " Timeout after 1h 0s\n", e.getMessage());
        }
        EasyMock.verify(mMockRunUtil, mMockListener, mMockTestDevice);
    }

    /** Test that when there is no heap dump available, we do not log anything and clean the dir. */
    @Test
    public void testLogAndCleanHeapDump_Empty() throws Exception {
        File heapDumpDir = FileUtil.createTempDir("heap-dump");
        try {
            EasyMock.replay(mMockRunUtil, mMockListener);
            mCtsTestLauncher.logAndCleanHeapDump(heapDumpDir, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockListener);
            // ensure the dir was cleaned
            assertFalse(heapDumpDir.exists());
        } finally {
            FileUtil.recursiveDelete(heapDumpDir);
        }
    }

    /** Test that when the heap dump is available, we log it and clean the dir. */
    @Test
    public void testLogAndCleanHeapDump() throws Exception {
        File heapDumpDir = FileUtil.createTempDir("heap-dump");
        File hprof = FileUtil.createTempFile("java.999.", ".hprof", heapDumpDir);
        try {
            mMockListener.testLog(
                    EasyMock.eq(hprof.getName()),
                    EasyMock.eq(LogDataType.HPROF),
                    EasyMock.anyObject());
            EasyMock.expectLastCall();
            EasyMock.replay(mMockRunUtil, mMockListener);
            mCtsTestLauncher.logAndCleanHeapDump(heapDumpDir, mMockListener);
            EasyMock.verify(mMockRunUtil, mMockListener);
            // ensure the dir was cleaned
            assertFalse(heapDumpDir.exists());
        } finally {
            FileUtil.recursiveDelete(heapDumpDir);
        }
    }

    private void setElapsedTimeExpectation() {
        mMockListener.testRunStarted("elapsed-time", 1);
        TestIdentifier tid = new TestIdentifier("elapsed-time", "TEST_TAG");
        mMockListener.testStarted(tid);
        mMockListener.testEnded(EasyMock.eq(tid), EasyMock.anyObject());
        mMockListener.testRunEnded(EasyMock.anyLong(), EasyMock.anyObject());
    }
}
