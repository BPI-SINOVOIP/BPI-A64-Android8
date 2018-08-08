// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.FileDownloadCacheWrapper;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

/**
 * Functional tests for {@link BigStoreFileDownloader}.
 * <p/>
 * Download known good builds from launch control, and verify success.
 */
public class BigStoreFileDownloaderFuncTest extends TestCase {

    private BigStoreFileDownloader mDownloader;
    private static final String TEST_FILE_PATH =
            "git_eclair-release-linux-passion-tests/22607/userdata.img";
    private static final long EXPECTED_FILE_SIZE = 7839744;

    public BigStoreFileDownloaderFuncTest() {
    }

    public BigStoreFileDownloaderFuncTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDownloader = new BigStoreFileDownloader();
    }

    /**
     * Functional test for {@link BigStoreFileDownloader#downloadFile(String)}.
     */
    public void testDownloadFile() throws BuildRetrievalError  {
        File tmpFile = mDownloader.downloadFile(TEST_FILE_PATH);
        try {
            assertTrue(tmpFile.exists());
            assertEquals(EXPECTED_FILE_SIZE, tmpFile.length());
        } finally {
            tmpFile.delete();
        }
    }

    private static class ShortTimeRunUtil extends RunUtil {
        int mNumCalls = 0;

        @Override
        public CommandResult runTimedCmd(final long timeout, final String... command) {
            // hack, only slow down the download call
            if (command.length > 1 && command[0].equals(BigStoreFileDownloader.FETCH_CMD[0])) {
                mNumCalls++;
                if (mNumCalls == 1) {
                    return super.runTimedCmd(1*1000, command);
                }
            }
            return super.runTimedCmd(timeout, command);
        }
    }

    /**
     * Functional test for {@link BigStoreFileDownloader#downloadFile(String)} retries.
     */
    public void testDownloadFile_retry() throws BuildRetrievalError  {
        final ShortTimeRunUtil runUtil = new ShortTimeRunUtil();
        BigStoreFileDownloader downloader = new BigStoreFileDownloader() {
            @Override
            IRunUtil getRunUtil() {
                // override the run util object to use with one that has a very short initial
                //timeout, that will cause first download attempt to fail
                return runUtil;
            }
        };
        File tmpFile = downloader.downloadFile(TEST_FILE_PATH);
        try {
            // ensure the mocking of runutil worked
            assertTrue(runUtil.mNumCalls >= 2);
            assertTrue(tmpFile.exists());
            assertEquals(EXPECTED_FILE_SIZE, tmpFile.length());
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Integration/performance test for {@link BigStoreFileDownloader#downloadFile(String)}, that
     * compares download times with a cache
     */
    public void testDownloadFile_withCache() throws BuildRetrievalError, IOException  {
        File cacheDir = FileUtil.createTempDir("bigstore_func");
        File tmpFile1 = null;
        File tmpFile2 = null;
        try {
            FileDownloadCacheWrapper cache = new FileDownloadCacheWrapper(cacheDir, mDownloader);
            long startTime = System.currentTimeMillis();
            // measure first download - should do real download
            tmpFile1 = cache.downloadFile(TEST_FILE_PATH);
            long realDownloadTime = System.currentTimeMillis() - startTime;
            assertEquals(EXPECTED_FILE_SIZE, tmpFile1.length());
            CLog.i("Downloaded file in %d ms", realDownloadTime);
            startTime = System.currentTimeMillis();
            tmpFile2 = cache.downloadFile(TEST_FILE_PATH);
            long cacheDownloadTime = System.currentTimeMillis() - startTime;
            assertEquals(EXPECTED_FILE_SIZE, tmpFile2.length());
            CLog.i("Retrieving file from cache in %d ms", cacheDownloadTime);
            // expect at least a 75% improvement from using cache
            assertTrue(cacheDownloadTime < (realDownloadTime * 0.25));
            // and less than 3 seconds
            assertTrue(cacheDownloadTime < 3 * 1000);
        } finally {
            FileUtil.recursiveDelete(cacheDir);
            if (tmpFile1 != null) {
                tmpFile1.delete();
            }
            if (tmpFile2 != null) {
                tmpFile2.delete();
            }
        }
    }

    /**
     * Functional test for {@link BigStoreFileDownloader#downloadFile(String)} for a remoteFilePath
     * which doesn't exist.
     */
    public void testDownloadFile_missing()  {
        try {
            mDownloader.downloadFile("gghgh/ggg/gg.zip");
            fail("downloadFile unexpectedly succeeded for nonexistent file");
        } catch (BuildRetrievalError e) {
            // expected
        }
    }
}
