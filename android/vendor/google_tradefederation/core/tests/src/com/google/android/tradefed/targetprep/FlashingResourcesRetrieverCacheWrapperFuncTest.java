// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.junit.Assert;

import java.io.File;

/**
 * Function tests for {@link FlashingResourcesRetrieverCacheWrapper}.
 */
public class FlashingResourcesRetrieverCacheWrapperFuncTest extends TestCase {

    private IFlashingResourcesRetriever mCachedRetriever;
    private File mCacheDir;
    private File mDownloadedFile;
    private IFlashingResourcesRetriever mMockRetriever;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockRetriever = EasyMock.createStrictMock(IFlashingResourcesRetriever.class);
        mCacheDir = FileUtil.createTempDir("functest");
        mCachedRetriever = new FlashingResourcesRetrieverCacheWrapper(mCacheDir, mMockRetriever);
        mDownloadedFile = FileUtil.createTempFile("test.version1", ".img");
        FileUtil.writeToFile("test", mDownloadedFile);
    }

    @Override
    protected void tearDown() throws Exception {
        FileUtil.recursiveDelete(mCacheDir);
        FileUtil.deleteFile(mDownloadedFile);
        super.tearDown();
    }

    public void testRetrieveVerionWithDot() throws Exception {
        mMockRetriever.retrieveFile(EasyMock.eq("test"), EasyMock.eq("version1.1"));
        EasyMock.expectLastCall().andReturn(mDownloadedFile);
        EasyMock.replay(mMockRetriever);
        File file = null;
        try {
            file = mCachedRetriever.retrieveFile("test", "version1.1");
            EasyMock.verify(mMockRetriever);
            // the original downloaded file will be delete.
            Assert.assertFalse(mDownloadedFile.exists());
            String fileContent = FileUtil.readStringFromFile(file);
            Assert.assertEquals("test", fileContent);
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    public void testRetrieveOnce() throws Exception {
        mMockRetriever.retrieveFile(EasyMock.eq("test"), EasyMock.eq("version1"));
        EasyMock.expectLastCall().andReturn(mDownloadedFile);
        EasyMock.replay(mMockRetriever);

        File file = null;
        try {
            file = mCachedRetriever.retrieveFile("test", "version1");
            EasyMock.verify(mMockRetriever);
            // the original downloaded file will be delete.
            Assert.assertFalse(mDownloadedFile.exists());
            String fileContent = FileUtil.readStringFromFile(file);
            Assert.assertEquals("test", fileContent);
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    public void testRetrieveTwice() throws Exception {
        mMockRetriever.retrieveFile(EasyMock.eq("test"), EasyMock.eq("version1"));
        EasyMock.expectLastCall().andReturn(mDownloadedFile);
        EasyMock.replay(mMockRetriever);

        File file1 = null;
        File file2 = null;
        try {
            file1 = mCachedRetriever.retrieveFile("test", "version1");
            file2 = mCachedRetriever.retrieveFile("test", "version1");
            EasyMock.verify(mMockRetriever);
            // the original downloaded file will be delete.
            Assert.assertFalse(mDownloadedFile.exists());
            String fileContent = FileUtil.readStringFromFile(file1);
            Assert.assertEquals("test", fileContent);
            fileContent = FileUtil.readStringFromFile(file2);
            Assert.assertEquals("test", fileContent);
        } finally {
            FileUtil.deleteFile(file1);
            FileUtil.deleteFile(file2);
        }
    }
}
