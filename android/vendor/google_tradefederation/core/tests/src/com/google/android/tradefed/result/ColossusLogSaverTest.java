// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Unit tests for {@link ColossusLogSaver}.
 * <p>
 * Depends on filesystem I/O.
 * </p>
 */
public class ColossusLogSaverTest extends TestCase {
    private static final String BUILD_ID = "88888";
    private static final String BRANCH = "somebranch";
    private static final String TEST_TAG = "sometest";
    private static final String[] MOCK_LOGFILE_NAMES = {"device_logcat.txt", "host_log.txt",
            "bugreport.txt", "screenshot.png"};
    private static final String JAVA_TMP_DIR = System.getProperty("java.io.tmpdir");

    private IBuildInfo mMockBuild = null;
    private ColossusLogSaver mSaver = null;
    private IRunUtil mMockRunUtil = null;

    private IInvocationContext mStubContext = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockRunUtil = EasyMock.createMock(IRunUtil.class);
        mMockBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mMockBuild.getBuildBranch()).andReturn(BRANCH).anyTimes();
        EasyMock.expect(mMockBuild.getBuildId()).andReturn(BUILD_ID).anyTimes();
        EasyMock.expect(mMockBuild.getTestTag()).andReturn(TEST_TAG).anyTimes();
        EasyMock.replay(mMockBuild);

        mStubContext = new InvocationContext();
        mStubContext.addDeviceBuildInfo("STUB_DEVICE", mMockBuild);

        mSaver = new ColossusLogSaver() {

            @Override
            IRunUtil getRunUtil() {
                return mMockRunUtil;
            }

            @Override
            String[] listStagedFiles(File parentDir) {
                final String[] outFiles = new String[MOCK_LOGFILE_NAMES.length];
                for (int i = 0; i < MOCK_LOGFILE_NAMES.length; ++i) {
                    outFiles[i] = new File(parentDir, MOCK_LOGFILE_NAMES[i]).getPath();
                }
                return outFiles;
            }

            @Override
            void cleanupStagingDir(File stagingDir) {
                // ignore
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        FileUtil.recursiveDelete(new File(String.format("%s/stage-colossus/", JAVA_TMP_DIR)));
    }

    public void testExecFileutil() throws Exception {
        final String cnsRoot = "/cns/path/to/root";
        OptionSetter setter = new OptionSetter(mSaver);
        setter.setOptionValue("log-file-path", cnsRoot);

        // Expect five generated arguments
        final List<Capture<String>> args = Arrays.asList(new Capture<String>(),
                new Capture<String>(),
                new Capture<String>(), new Capture<String>(), new Capture<String>());
        final Iterator<Capture<String>> argsIter = args.iterator();
        final Capture<String> destDirArg = new Capture<>();
        final CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        // Sample call: runTimedCmd(40000, "fileutil", "mkdir", "-p",
        // "/cns/path/to/root/somebranch/88888/sometest/inv_1921678752149961660")
        // runTimedCmd(40000, "fileutil", "cp", "-R", "-m", "0644",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/device_logcat.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/host_log.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/bugreport.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/screenshot.png",
        // "/cns/path/to/root/somebranch/88888/sometest/inv_1921678752149961660")
        // sadface, easymock doesn't really support varargs
        mMockRunUtil.runTimedCmd(
                EasyMock.anyLong(),
                EasyMock.eq("fileutil"),
                EasyMock.eq("mkdir"),
                EasyMock.eq("-p"),
                EasyMock.capture(destDirArg));
        EasyMock.expectLastCall().andReturn(result);
        mMockRunUtil.runTimedCmd(
                EasyMock.anyLong(),
                EasyMock.eq("fileutil"),
                EasyMock.eq("cp"),
                EasyMock.eq("-R"),
                EasyMock.eq("-m"),
                EasyMock.eq("0644"),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()));
        EasyMock.expectLastCall().andReturn(result);

        mSaver.invocationStarted(mStubContext);
        EasyMock.replay(mMockRunUtil);

        // this should trigger a copy attempt
        mSaver.invocationEnded(0);
        EasyMock.verify(mMockRunUtil);

        assertTrue(destDirArg.getValue().startsWith(cnsRoot));
        // The last arg is the destination. It should start with cnsRoot, and should be followed
        // by branch/build_id/tag/inv_###
        final String dest = args.get(args.size() - 1).getValue();
        assertTrue(dest.startsWith(cnsRoot));
        final String genPath = dest.substring(cnsRoot.length());
        final String expectedGenPathPattern = String.format("/%s/%s/%s/inv_\\d{10,}",
                BRANCH, BUILD_ID, TEST_TAG);
        assertTrue(genPath.matches(expectedGenPathPattern));

        // The exact same generated path should be used in the source path
        for (int i = 0; i < MOCK_LOGFILE_NAMES.length; ++i) {
            final String name = MOCK_LOGFILE_NAMES[i];
            final String expected = String.format("%s/stage-colossus%s/%s", JAVA_TMP_DIR, genPath,
                    name);
            assertEquals(expected, args.get(i).getValue());
        }
    }

    public void testExecFileutil_withUser() throws Exception {
        final String user = System.getProperty("user.name");
        assertNotNull("Couldn't determine user name; it was null", user);
        assertTrue("Couldn't determine user name; it was empty", !user.isEmpty());

        final String cnsRoot = "/cns/path/to/${USER}/root";
        final String expRoot = String.format("/cns/path/to/%s/root", user);
        OptionSetter setter = new OptionSetter(mSaver);
        setter.setOptionValue("log-file-path", cnsRoot);

        // Expect five generated arguments
        final List<Capture<String>> args = Arrays.asList(new Capture<String>(),
                new Capture<String>(),
                new Capture<String>(), new Capture<String>(), new Capture<String>());
        final Iterator<Capture<String>> argsIter = args.iterator();
        final Capture<String> destDirArg = new Capture<>();
        final CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        // Sample call: runTimedCmd(40000, "fileutil", "mkdir", "-p",
        // "/cns/path/to/user/root/somebranch/88888/sometest/inv_1921678752149961660")
        // runTimedCmd(40000, "fileutil", "cp", "-R", "-m", "0644",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/device_logcat.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/host_log.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/bugreport.txt",
        // "/tmp/stage-colossus/somebranch/88888/sometest/inv_1921678752149961660/screenshot.png",
        // "/cns/path/to/root/somebranch/88888/sometest/inv_1921678752149961660")
        // sadface, easymock doesn't really support varargs
        mMockRunUtil.runTimedCmd(
                EasyMock.anyLong(),
                EasyMock.eq("fileutil"),
                EasyMock.eq("mkdir"),
                EasyMock.eq("-p"),
                EasyMock.capture(destDirArg));
        EasyMock.expectLastCall().andReturn(result);
        mMockRunUtil.runTimedCmd(
                EasyMock.anyLong(),
                EasyMock.eq("fileutil"),
                EasyMock.eq("cp"),
                EasyMock.eq("-R"),
                EasyMock.eq("-m"),
                EasyMock.eq("0644"),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()),
                EasyMock.capture(argsIter.next()));
        EasyMock.expectLastCall().andReturn(result);

        mSaver.invocationStarted(mStubContext);
        EasyMock.replay(mMockRunUtil);

        // this should trigger a copy attempt
        mSaver.invocationEnded(0);
        EasyMock.verify(mMockRunUtil);

        assertTrue(destDirArg.getValue().startsWith(expRoot));
        // The last arg is the destination. It should start with cnsRoot, and should be followed
        // by branch/build_id/tag/inv_###
        final String dest = args.get(args.size() - 1).getValue();
        assertTrue(dest.startsWith(expRoot));
        final String genPath = dest.substring(expRoot.length());
        final String expectedGenPathPattern = String.format("/%s/%s/%s/inv_\\d{10,}",
                BRANCH, BUILD_ID, TEST_TAG);
        assertTrue(genPath.matches(expectedGenPathPattern));

        // The exact same generated path should be used in the source path
        for (int i = 0; i < MOCK_LOGFILE_NAMES.length; ++i) {
            final String name = MOCK_LOGFILE_NAMES[i];
            final String expected = String.format("%s/stage-colossus%s/%s", JAVA_TMP_DIR, genPath,
                    name);
            assertEquals(expected, args.get(i).getValue());
        }
    }
}
