// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.google.android.tradefed.build.BigStoreFileDownloader;

import java.io.File;
import java.io.IOException;

/**
 * Thread for asynchronously downloading ota packages
 */
class OtaDownloaderThread extends Thread {

    /**
     * Listener for download completion
     */
    static interface IDownloadListener {
        public void downloadFinished(File[] files);
    }

    private final String mFileGlob;
    private final File mStorageDir;
    private final String mFlavor;
    private final String mBranch;
    private final String mBuildId;
    private final IDownloadListener mListener;

    OtaDownloaderThread(String fileGlob, File rootStorageDir,
        String flavor, String branch, String buildId, IDownloadListener listener) throws IOException {
        super(String.format("%s downloader", flavor));
        mFileGlob = fileGlob;
        mFlavor = flavor;
        mBranch = branch;
        mBuildId = buildId;
        mListener = listener;

        // create a separate storage directory for this download command so we know
        // all files in this directory are complete when download finishes
        mStorageDir = FileUtil.createTempDir(mFlavor + "_", rootStorageDir);
    }

    @Override
    public void run() {
        BigStoreFileDownloader downloader = new BigStoreFileDownloader();
        try {
            CLog.logAndDisplay(LogLevel.INFO, "Downloading files for %s %s matching %s",
                    mBuildId, mFlavor, mFileGlob);
            downloader.doDownload(mFileGlob, mStorageDir, mFlavor, mBranch, mBuildId,
                    false /* isKernel */, 1);
        } catch (BuildRetrievalError e) {
            CLog.e(e.getMessage());
        }
        File[] childFiles = mStorageDir.listFiles();
        if (childFiles.length <= 0) {
            CLog.w("No downloaded files for %s %s matching %s", mBuildId, mFlavor, mFileGlob);
        } else {
            mListener.downloadFinished(childFiles);
        }
    }
}