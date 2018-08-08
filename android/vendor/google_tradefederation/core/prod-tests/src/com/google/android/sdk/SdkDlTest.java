// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.sdk;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple test that measures time to download a SDK file from dl.google.com.
 */
public class SdkDlTest implements IRemoteTest {

    static final String KB_PER_SEC = "kb_per_sec";
    static final String TIME = "download_time_sec";
    static final String SUCCESS = "success";

    @Option(name = "file-url", description = "the http URL of the file to download")
    private String mFileUrl = "http://dl.google.com/android/android-sdk_r20-linux.tgz";

    // TODO: use sha1sum instead
    @Option(name = "file-size", description = "the expected file size in bytes of --file-url")
    private long mExpectedFileSize = 82589455;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        BufferedInputStream bi = null;
        BufferedOutputStream bo = null;
        HttpURLConnection con = null;
        IHttpHelper httpHelper = createHttpHelper();
        URL url;
        File f = null;
        listener.testRunStarted("sdk_dl", 0);
        long startTime = System.currentTimeMillis();
        long fileSize = 0;
        try {
            url = new URL(mFileUrl);
            // the mimetype provided here doesn't really matter, just arbitrarily use
            // application/zip
            con = httpHelper.createConnection(url, "GET", "application/zip");
            bi = new BufferedInputStream(con.getInputStream(), 128 * 1024);
            f = FileUtil.createTempFile("downloaded_file", "tgz");
            CLog.i("Downloading file %s", mFileUrl);
            bo = new BufferedOutputStream(new FileOutputStream(f), 128 * 1024);
            StreamUtil.copyStreams(bi, bo);
        } catch (MalformedURLException e) {
            CLog.e(e);
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            StreamUtil.close(bi);
            StreamUtil.close(bo);
            if (con != null) {
                con.disconnect();
            }
            if (f != null) {
                fileSize = f.length();
                f.delete();
            }
        }
        long elapsedTime = System.currentTimeMillis() - startTime;
        boolean success = fileSize == mExpectedFileSize;
        if (!success) {
            CLog.w("Expected file size %d, got %d", mExpectedFileSize, fileSize);
        }
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put(SUCCESS, Boolean.toString(success));
        double timeSec = ((double)elapsedTime) / 1000;
        metrics.put(TIME, Double.toString(timeSec));
        double downloadRate = calculateRate(fileSize, timeSec);
        metrics.put(KB_PER_SEC, Double.toString(downloadRate));
        CLog.i("kb_per_sec=%.1f", downloadRate);
        listener.testRunEnded(elapsedTime, metrics);
    }

    double calculateRate(long fileSize, double elapsedTimeSec) {
        // convert everything to double to avoid loss of precision
        double fileSizeD = fileSize;
        return (fileSizeD / 1024) / elapsedTimeSec;
    }

    /**
     * Factory method for creating a {@link IHttpHelper}.
     */
    IHttpHelper createHttpHelper() {
        return new HttpHelper();
    }
}
