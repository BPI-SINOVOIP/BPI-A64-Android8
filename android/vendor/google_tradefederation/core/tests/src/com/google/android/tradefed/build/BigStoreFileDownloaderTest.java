// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;

/**
 * Unit tests for {@link BigStoreFileDownloader}
 */
public class BigStoreFileDownloaderTest extends TestCase {
    private BigStoreFileDownloader mDl = null;
    private IRunUtil mRunUtil = null;

    private Boolean mProdAccessIsExpired = null;
    private Boolean mShouldUseRuncron;
    private Boolean mZipFileValid;
    private String[] mBuiltCommand;
    private static final String[] STUB_BUILT_COMMAND = {"stub_command"};

    @Override
    public void setUp() throws Exception {
        mRunUtil = EasyMock.createNiceMock(IRunUtil.class);

        // Default to standard behaviors
        mProdAccessIsExpired = null;
        mShouldUseRuncron = false;
        mBuiltCommand = null;

        mDl = new BigStoreFileDownloader() {
            @Override
            IRunUtil getRunUtil() {
                return mRunUtil;
            }

            @Override
            boolean checkShouldUseRuncron() {
                return mShouldUseRuncron;
            }

            @Override
            void assertHasProdAccess() {
                if (mProdAccessIsExpired == null) {
                    super.assertHasProdAccess();
                } else if (mProdAccessIsExpired == true) {
                    throw new FatalHostError("faking that prodaccess is expired");
                } else {
                    // faking that prodaccess is still granted
                    return;
                }
            }

            @Override
            String[] buildCommand(String rFP, File dF, String t, String br, String b, boolean k) {
                if (mBuiltCommand == null) {
                    return super.buildCommand(rFP, dF, t, br, b, k);
                } else {
                    return mBuiltCommand;
                }
            }

            @Override
            String[] buildCommand(String rFP, File dF, String t, String p, String br, String b,
                    boolean k) {
                if (mBuiltCommand == null) {
                    return super.buildCommand(rFP, dF, t, p, br, b, k);
                } else {
                    return mBuiltCommand;
                }
            }

            @Override
            boolean isZipFileValid(File zipFile) {
                if (mZipFileValid == null) {
                    return super.isZipFileValid(zipFile);
                } else {
                    return mZipFileValid;
                }
            }
        };
    }

    /**
     * Make sure that expected args are present in the basic download case
     */
    public void testBasic() throws Exception {
        final String expPat = "file.img";
        final File expFile = new File("/path/to/file.img");
        String[] cmd = mDl.buildCommand(expPat, expFile, "target", "branch", "build", false);
        assertTrue(aryContains("--bid", cmd));
        assertTrue(aryContains("--branch", cmd));
        assertTrue(aryContains("--target", cmd));
        assertTrue(aryContains(expFile.getAbsolutePath(), cmd));
        assertTrue(aryContains(expPat, cmd));
    }

    /**
     * Make sure that the switch for krb/loas download modes works properly
     */
    public void testKrbSwitch() throws Exception {
        String[] cmd;
        cmd = mDl.buildCommand("pattern", new File("/"), "target", "branch", "build", false);
        assertFalse("--use_kerberos present by default", aryContains("--use_kerberos", cmd));
        assertFalse("--nouse_loas present by default", aryContains("--nouse_loas", cmd));

        mDl.setUseKrb(true);
        cmd = mDl.buildCommand("pattern", new File("/"), "target", "branch", "build", false);
        assertTrue("--use_kerberos missing in krb mode", aryContains("--use_kerberos", cmd));
        assertTrue("--nouse_loas missing in krb mode", aryContains("--nouse_loas", cmd));
    }

    /**
     * Make sure that the switch for kernel/non-kernel download modes works properly
     */
    public void testKernelSwitch() throws Exception {
        String[] cmd;
        cmd = mDl.buildCommand("pattern", new File("/"), "target", "branch", "build",
                true /* kernel download settings */);
        assertTrue("--kernel missing in kernel mode", aryContains("--kernel", cmd));
        assertFalse("--target present in kernel mode", aryContains("--target", cmd));

        cmd = mDl.buildCommand("pattern", new File("/"), "target", "branch", "build",
                false /* non-kernel download settings */);
        assertFalse("--kernel present in NON-kernel mode", aryContains("--kernel", cmd));
        assertTrue("--target missing in NON-kernel mode", aryContains("--target", cmd));
    }

    /**
     * Make sure that the switch for runcron/non-runcron download modes works properly
     */
    public void testRuncronSwitch() throws Exception {
        final BigStoreFileDownloader yesRuncron = new BigStoreFileDownloader() {
            @Override
            IRunUtil getRunUtil() {
                return mRunUtil;
            }

            @Override
            boolean checkShouldUseRuncron() {
                return true;
            }
        };
        final BigStoreFileDownloader noRuncron = mDl;

        String[] noCmd = noRuncron.buildCommand("pattern", new File("/"), "target", "branch",
                "build", false);
        String[] yesCmd = yesRuncron.buildCommand("pattern", new File("/"), "target", "branch",
                "build", false);

        assertFalse("runcron present in non-runcron mode", aryContains("runcron", noCmd));
        assertTrue("runcron prefix missing in runcron mode", aryContains("runcron", yesCmd));
    }

    /**
     * Verify the normal case where ProdAccess is still active
     */
    public void testAssertHasProdAccess() {
        CommandResult mockResult = new CommandResult();
        mockResult.setStdout("LOAS expires in 0d 3h 40m");
        mockResult.setStatus(CommandStatus.SUCCESS);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.<String> anyObject())).andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        mDl.assertHasProdAccess();
        EasyMock.verify(mRunUtil);
    }

    /**
     * Verify the negative case where ProdAccess is expired
     */
    public void testHasProdAccess_expired() {
        CommandResult mockResult = new CommandResult();
        mockResult.setStderr("No valid LOAS certs");
        mockResult.setStatus(CommandStatus.FAILED);
        EasyMock.expect(mRunUtil.runTimedCmd(
                    EasyMock.anyInt(),
                    EasyMock.<String> anyObject())).andReturn(mockResult).times(1);
        EasyMock.replay(mRunUtil);
        try {
            mDl.assertHasProdAccess();
            fail("was expecting FatalHostError to be thrown");
        } catch (RuntimeException e) {
            // expected
        }
        EasyMock.verify(mRunUtil);
    }

    /**
     * Verify the negative case that the download fails because prodaccess expired -
     * CLog.wtf() should be called in this case.
     */
    public void testDoDownload_prodAccessExpired() throws BuildRetrievalError {
        // set up a mock CommandResult that returns SUCCESS
        CommandResult mockResult = new CommandResult();
        mockResult.setStatus(CommandStatus.SUCCESS);

        try {
            mProdAccessIsExpired = true;
            mDl.doDownload("", new File("file"), "", "", "", false, 1);
            fail("was expecting FatalHostError to be thrown");
        } catch (FatalHostError e) {
            // expected
        }
     }

    /**
     * Make sure that our corrupt Zip download detection logic works as expected
     */
    @SuppressWarnings("serial")
    public void testCorruptDownload() throws Exception {
        mZipFileValid = false;
        mProdAccessIsExpired = false;
        mBuiltCommand = STUB_BUILT_COMMAND;
        mRunUtil = EasyMock.createStrictMock(IRunUtil.class);

        CommandResult success = new CommandResult(CommandStatus.SUCCESS);
        // varargs is the Achilles' Heel of EasyMock
        EasyMock.expect(mRunUtil.runTimedCmd(
                EasyMock.anyInt(),
                EasyMock.eq(STUB_BUILT_COMMAND[0])))
                .andStubReturn(success);
        EasyMock.replay(mRunUtil);

        try {
            // Construct a special File instance that will bypass the other checks so that we
            // actually get to the corruptedness check
            final File destFile = new File("foo.zip") {
                @Override
                public boolean exists() {
                    return true;
                }
            };
            mDl.doDownload("foo.zip", destFile, "target", "branch", "build", false, 3);
            fail("BuildRetrievalError not thrown for repeated corrupt download");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }

    /**
     * Make sure that non-zip files pass the validity test.
     */
    public void testZipFileValid_nonZip() {
        // txt file should pass even though it doesn't exist
        assertTrue(mDl.isZipFileValid(new File("/does/not/exist/foo.txt")));

        // For zip files, nonexistence causes validation failure
        assertFalse(mDl.isZipFileValid(new File("/does/not/exist/foo.zip")));
        assertFalse(mDl.isZipFileValid(new File("/does/not/exist/foo.apk")));
    }

    private boolean aryContains(String needle, String[] haystack) {
        for (String check : haystack) {
            if (needle.equals(check)) {
                return true;
            }
        }
        return false;
    }
}

