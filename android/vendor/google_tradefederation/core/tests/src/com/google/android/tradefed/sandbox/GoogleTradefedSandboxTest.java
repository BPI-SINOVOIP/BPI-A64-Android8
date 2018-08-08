// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.sandbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.build.IFolderBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.util.FileUtil;

import com.google.android.tradefed.build.TfLaunchControlProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;

/** Unit tests for {@link GoogleTradefedSandbox}. */
@RunWith(JUnit4.class)
public class GoogleTradefedSandboxTest {

    private static final String[] CMD_ARGS =
            new String[] {
                "empty", "--build-id", "8", "--branch", "git_master", "--test-tag", "tag"
            };
    private GoogleTradefedSandbox mSandbox;
    private TfLaunchControlProvider mMockProvider;

    @Before
    public void setUp() {
        mMockProvider = Mockito.spy(TfLaunchControlProvider.class);
        mSandbox =
                new GoogleTradefedSandbox() {
                    @Override
                    TfLaunchControlProvider createBuildProvider() {
                        return mMockProvider;
                    }
                };
    }

    @After
    public void tearDown() {
        if (mSandbox != null) {
            mSandbox.tearDown();
        }
    }

    /**
     * Test {@link GoogleTradefedSandbox#getTradefedEnvironment(String[])} when some of the required
     * keys are not present.
     */
    @Test
    public void testGetTradefedEnvironment_missingKey() {
        String[] args = new String[] {};
        try {
            mSandbox.getTradefedEnvironment(args);
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            assertEquals("Could not find key --build-id in args.", expected.getMessage());
        }
    }

    /**
     * Test {@link GoogleTradefedSandbox#getTradefedEnvironment(String[])} when the value of a
     * required key is not present.
     */
    @Test
    public void testGetTradefedEnvironment_missingValue() {
        String[] args = new String[] {"--build-id"};
        try {
            mSandbox.getTradefedEnvironment(args);
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            assertEquals("Could not find proper value for --build-id", expected.getMessage());
        }
    }

    /**
     * Test {@link GoogleTradefedSandbox#getTradefedEnvironment(String[])} when the Tradefed build
     * is not downloaded.
     */
    @Test
    public void testGetTradefedEnvironment_downloadFail() throws Exception {
        Mockito.doReturn(null).when(mMockProvider).getBuild();
        try {
            mSandbox.getTradefedEnvironment(CMD_ARGS);
            fail("Should have thrown an exception.");
        } catch (ConfigurationException expected) {
            assertEquals("Failed to download the versioned Tradefed build", expected.getMessage());
        }
    }

    /**
     * Test {@link GoogleTradefedSandbox#getTradefedEnvironment(String[])} when download was
     * successful and we obtain a working dir.
     */
    @Test
    public void testGetTradefedEnvironment() throws Exception {
        IFolderBuildInfo fakeDownload = new FolderBuildInfo("8", "tag");
        File rootDir = FileUtil.createTempDir("download-root-dir");
        FileUtil.createTempFile("test", ".txt", rootDir);
        fakeDownload.setRootDir(rootDir);
        try {
            Mockito.doReturn(fakeDownload).when(mMockProvider).getBuild();
            File workingDir = mSandbox.getTradefedEnvironment(CMD_ARGS);
            assertNotNull(workingDir);
            assertEquals(1, workingDir.list().length);
            // Calling get environment again does not result in a new download.
            File workingDir2 = mSandbox.getTradefedEnvironment(CMD_ARGS);
            assertEquals(workingDir, workingDir2);
        } finally {
            Mockito.verify(mMockProvider, Mockito.times(1)).getBuild();
            FileUtil.recursiveDelete(rootDir);
        }
    }
}
