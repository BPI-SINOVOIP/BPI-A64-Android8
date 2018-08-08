// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.sdk;

import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * A result reporter that appends {@link SdkDlTest} metrics results to a csv file.
 */
public class SdkDlCsvMetricsReporter implements ITestInvocationListener {

    @Option(name = "csv-file", description = "filesystem path to csv file to write results to",
            mandatory = true)
    private File mCsvFile = null;

    // static lock object for file access
    private static Object mLock = new Object();

    /**
     * Writes a row to csv file,
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
        SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss");
        String timestamp = formatter.format(new Date());
        String result = runMetrics.get(SdkDlTest.SUCCESS);
        String time = runMetrics.get(SdkDlTest.TIME);
        String rate = runMetrics.get(SdkDlTest.KB_PER_SEC);
        String entry = ArrayUtil.join(",", timestamp, result, time, rate);
        CLog.i("Updating file %s", mCsvFile.getAbsolutePath());
        synchronized (mLock) {
            try {
                writeHeaderIfNecessary(mCsvFile);
                appendToFile(entry + "\n", mCsvFile);
            } catch (IOException e) {
                CLog.e("Failed to write to %s", mCsvFile);
                CLog.e(e);
            }
        }
    }

    private void writeHeaderIfNecessary(File csvFile) throws IOException {
        if (!csvFile.exists()) {
            csvFile.createNewFile();
            FileUtil.writeToFile("End time,success,download time, KB/s\n", csvFile);
        }
    }

    private void appendToFile(String inputString, File destFile)
            throws IOException {
        Writer writer = new BufferedWriter(new FileWriter(destFile, true));
        try {
            writer.write(inputString);
        } finally {
            writer.close();
        }
    }
}
