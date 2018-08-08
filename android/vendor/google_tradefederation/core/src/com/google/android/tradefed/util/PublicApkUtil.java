// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class to download files from x20 to the corp TF environment. In particular, it is used in
 * conjunction with the <a href="http://go/app-compatibility-readme">App Compatibility pipeline</a>.
 */
public class PublicApkUtil {
    private static final Pattern DATE_FORMAT = Pattern.compile("\\d{8}");
    private static final long DOWNLOAD_TIMEOUT_MS = 60 * 1000;
    private static final int DOWNLOAD_RETRIES = 3;
    private static final String LATEST_FILE = "latest.txt";

    /**
     * Helper method which constructs the dated CNS directory from the base directory and either the
     * supplied date option or the most recent directory.
     *
     * @param baseDir The base directory with the "latest" file and dated subdirectories.
     * @param subDir A specific target directory, or null if using the latest file.
     * @return The {@link File} of the x20 dir where the APKs are stored.
     * @throws IOException
     */
    public static File constructApkDir(String baseDir, String subDir) throws IOException {
        if (subDir != null) {
            return new File(baseDir, subDir);
        }
        File latestFile = null;
        try {
            latestFile =
                    downloadFile(
                            new File(baseDir, LATEST_FILE), DOWNLOAD_TIMEOUT_MS, DOWNLOAD_RETRIES);
            String date = FileUtil.readStringFromFile(latestFile).trim();
            if (DATE_FORMAT.matcher(date).matches()) {
                return new File(baseDir, date);
            }
            return null;
        } finally {
            FileUtil.deleteFile(latestFile);
        }
    }

    /**
     * A configurable helper method for downloading a remote file.
     *
     * @param remoteFile The remote {@link File} location.
     * @param downloadTimeout The download timeout in milliseconds.
     * @param downloadRetries The download retry count, in case of failure.
     * @return The local {@link File} that was downloaded.
     * @throws IOException
     */
    public static File downloadFile(File remoteFile, long downloadTimeout, int downloadRetries)
            throws IOException {
        CLog.i("Attempting to download %s", remoteFile);
        File tmpFile = FileUtil.createTempFile(remoteFile.getName(), null);
        FileUtil.copyFile(remoteFile, tmpFile);
        return tmpFile;
    }

    /**
     * Helper method which downloads the ranking file and returns the list of apks.
     *
     * @param flavor The APK variant to pick.
     * @param dir The {@link File} of the dated x20 dir.
     * @return The list of {@link ApkInfo} objects.
     * @throws IOException
     */
    public static List<ApkInfo> getApkList(String flavor, File dir) throws IOException {
        File apkFile = new File(dir, String.format("%s_ranking.csv", flavor));
        List<ApkInfo> apkList = new ArrayList<>();
        File copiedFile = null;
        BufferedReader br = null;
        try {
            copiedFile = downloadFile(apkFile, DOWNLOAD_TIMEOUT_MS, DOWNLOAD_RETRIES);
            br = new BufferedReader(new FileReader(copiedFile));
            String line;
            boolean firstLine = true;
            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                } else {
                    try {
                        apkList.add(ApkInfo.fromCsvLine(line));
                    } catch (IllegalArgumentException e) {
                        CLog.e("Ranking file not formatted properly, skipping.");
                        CLog.e(e);
                    }
                }
            }
        } finally {
            StreamUtil.close(br);
            FileUtil.deleteFile(copiedFile);
        }
        return apkList;
    }

    /**
     * Helper class which holds information about the ranking list such as rank, package name, etc.
     */
    public static class ApkInfo {
        public final int rank;
        public final String packageName;
        public final String versionString;
        public final String versionCode;
        public final String fileName;

        private ApkInfo(int rank, String packageName, String versionString, String versionCode,
                String fileName) {
            this.rank = rank;
            this.packageName = packageName;
            this.versionString = versionString;
            this.versionCode = versionCode;
            this.fileName = fileName;
        }

        public static ApkInfo fromCsvLine(String line) {
            String[] cols = QuotationAwareTokenizer.tokenizeLine(line, ",");
            int rank = -1;
            try {
                rank = Integer.parseInt(cols[0]);
            } catch (NumberFormatException e) {
                // rethrow as IAE with content of problematic line
                throw new IllegalArgumentException(
                        String.format("Invalid line (rank field not a number): %s", line), e);
            }
            if (cols.length != 5) {
                throw new IllegalArgumentException(
                        String.format("Invalid line (expected 5 data columns): %s", line));
            }
            return new ApkInfo(rank, cols[1], cols[2], cols[3], cols[4]);
        }

        @Override
        public String toString() {
            return String.format("Package: %s v%s (%s), rank: %d, file: %s", packageName,
                    versionCode, versionString, rank, fileName);
        }
    }
}
