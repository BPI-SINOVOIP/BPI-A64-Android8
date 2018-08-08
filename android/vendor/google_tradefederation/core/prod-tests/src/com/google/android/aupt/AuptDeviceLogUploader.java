// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.aupt;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A helper class to monitor the AUPT test as it is running on the device. The class will start a
 * separate thread to periodically check the device for new bugreports and upload them to the
 * dashboard.
 */
public class AuptDeviceLogUploader extends Thread {
    static final String REPORT_BASE_PATH = "/auto/android-test/www/mem-reports/json/";
    static final String REPORT_BASE_URL =
            "http://android-test/www/mem-reports/report_syshealth.html?d=json/";
    static final String MEMINFO_PROCESS_CMD =
            "/google/data/ro/teams/tradefed/testdata/aupt/mem_process3.py";
    public static final String MEMORY_HEALTH_FILE = "memory-health.txt";
    public static final String MEMORY_HEALTH_DETAILS_FILE = "memory-health-details.txt";

    private static final String ANALYSIS_PY_FILE =
            "/google/data/ro/teams/tradefed/testdata/aupt/official-script/kmsg_analysis2.py";
    private static final String REPORT_AUTO_PATH = "/auto/android-test/www/mem-reports/";

    private static final String UNUSABLE_INDEX_JSON_FOLDER = "unusable-index-json/";
    private static final String UNUSABLE_INDEX_VIEWER_PATH =
            "http://android-test/www/mem-reports/unusable-index-viewer.html";

    private static final String PAGETYPEINFO_JSON_FOLDER = "pagetypeinfo-json/";
    private static final String PAGETYPEINFO_VIEWER_PATH =
            "http://android-test/www/mem-reports/pagetypeinfo-viewer.html";
    private static final String BUGREPORTZ_DIR =
            "/data/user_de/0/com.android.shell/files/bugreports/";

    private static final Predicate<IFileEntry> BUGREPORTZ_FILTER =
            (f -> f.getFullPath().contains("bugreport") && !f.getFullPath().contains("dumpstate"));

    private boolean mCancelled = false;

    private ITestDevice mDevice;
    private String mOutputDir;
    private long mPullInterval;
    private IBuildInfo mBuildInfo;
    private String mMonitoredProcs;
    private List<String> mExtraLogs;
    private boolean mEnableBugreports = true;

    private List<String> mUnusableIndexFiles;
    private List<String> mPagetypeinfoFiles;

    private ITestInvocationListener mResultReporter;

    /**
     * Creates a new AuptDeviceLogUploader.
     *
     * @param device a device on which the tests are running.
     * @param outputDir directory on the device where to look for new bugreports.
     * @param dashboardUrl url of the dashboard where to upload the bugreports.
     * @param pullInterval how often to check the device for new bugreports (in minutes).
     * @param extraLogs list of regexes that will match any other misc logs a test might produce.
     */
    public AuptDeviceLogUploader(
            ITestDevice device,
            String outputDir,
            long pullInterval,
            ITestInvocationListener listener,
            IBuildInfo buildInfo,
            String monitoredProcs,
            List<String> extraLogs) {
        this(device, outputDir, pullInterval, listener, buildInfo, monitoredProcs,
                extraLogs, true);
    }

    /**
     * Creates a new AuptDeviceLogUploader.
     *
     * @param device a device on which the tests are running.
     * @param outputDir directory on the device where to look for new bugreports.
     * @param dashboardUrl url of the dashboard where to upload the bugreports.
     * @param pullInterval how often to check the device for new bugreports (in minutes).
     * @param extraLogs list of regexes that will match any other misc logs a test might produce.
     * @param enableBugreports disable uploading bug reports from this device
     */
    public AuptDeviceLogUploader(
            ITestDevice device,
            String outputDir,
            long pullInterval,
            ITestInvocationListener listener,
            IBuildInfo buildInfo,
            String monitoredProcs,
            List<String> extraLogs,
            boolean enableBugreports) {
        super();

        mDevice = device;
        mOutputDir = outputDir;
        mPullInterval = pullInterval;
        mResultReporter = listener;
        mBuildInfo = buildInfo;
        mMonitoredProcs = monitoredProcs;
        mExtraLogs = extraLogs;

        mUnusableIndexFiles = new ArrayList<String>();
        mPagetypeinfoFiles = new ArrayList<String>();
        mEnableBugreports = enableBugreports;
    }

    /**
     * Tells the uploader to stop monitoring the device and quit. The uploader will do one final
     * check to make sure it got all the bugreports after which the uploader thread will terminate.
     */
    public synchronized void cancel() {
        mCancelled = true;
        this.interrupt();
    }

    public synchronized boolean isCancelled() {
        return mCancelled;
    }

    /** Main loop which periodically check the device for new bugreports. */
    @Override
    public void run() {
        while (!isCancelled()) {
            getRunUtil().sleep(mPullInterval * 60 * 1000);
            try {
                processLogs(mOutputDir);
                processZippedBugreports();
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
            }
        }

        try {
            processLogs(mOutputDir);
            processZippedBugreports();
            if (mEnableBugreports) {
                takeFinalBugreport();
            }
            processExtendedMeminfo();
            processUnusableIndexData();
            processPagetypeinfoRatioData();
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /**
     * Saves the last bugreport when the test is finished.
     */
    private void takeFinalBugreport() {
        InputStreamSource stream = mDevice.getBugreportz();
        mResultReporter.testLog("final_bugreportz", LogDataType.BUGREPORTZ, stream);
        StreamUtil.cancel(stream);
    }

    private void processExtendedMeminfo() throws DeviceNotAvailableException, IOException {
        File tmpDir = null;
        File memoryHealth = null;
        File memoryHealthDetails = null;

        // memoryHealthDetails does not post to the dashboard so do not fail entire memoryHealth
        // code block if memoryHealthDetails does not exist
        try {
            memoryHealthDetails = pullFile(MEMORY_HEALTH_DETAILS_FILE, mOutputDir);
        } catch (DeviceNotAvailableException e) {
            CLog.w("Could not pull file: %s", MEMORY_HEALTH_DETAILS_FILE);
        }
        try {
            memoryHealth = pullFile(MEMORY_HEALTH_FILE, mOutputDir);

            String dir = "${EXTERNAL_STORAGE}/meminfo";
            IFileEntry outputDir = mDevice.getFileEntry(dir);
            if (outputDir == null) return;

            // pull meminfo into a local tmp folder
            tmpDir = FileUtil.createTempDir("meminfo");
            for (IFileEntry file : outputDir.getChildren(false)) {
                File pulledFile = new File(tmpDir, file.getName());
                if (!mDevice.pullFile(file.getFullPath(), pulledFile)) {
                    CLog.w("Could not pull file: %s", file.getFullPath());
                    continue;
                }
                mDevice.executeShellCommand("rm " + file.getFullPath());
            }
            // zip up the folder containing all mem info files, and store it with testLog
            File meminfoZip = ZipUtil.createZip(tmpDir);
            FileInputStreamSource stream = new FileInputStreamSource(meminfoZip);
            mResultReporter.testLog("meminfo.zip", LogDataType.ZIP, stream);
            StreamUtil.cancel(stream);
            meminfoZip.delete();

            // call external script to generate meminfo
            // figure out a filename
            StringBuilder sb = new StringBuilder();
            String id = mBuildInfo.getBuildAttributes().get("build_alias");
            if (id == null) {
                id = mBuildInfo.getBuildId();
            }
            String jsonPath = sb.append(id)
                    .append('-')
                    .append(mBuildInfo.getBuildFlavor())
                    .append('-')
                    .append(mBuildInfo.getTestTag())
                    .append('-')
                    .append(new Random(System.currentTimeMillis()).nextInt())
                    .append(".json").toString();
            CommandResult cr;
            if (memoryHealth != null) {
                cr = getRunUtil().runTimedCmd(10 * 1000, MEMINFO_PROCESS_CMD,
                        "-h", memoryHealth.getAbsolutePath(), mMonitoredProcs,
                        tmpDir.getAbsolutePath(), REPORT_BASE_PATH + jsonPath);
            } else {
                cr = getRunUtil().runTimedCmd(10 * 1000, MEMINFO_PROCESS_CMD,
                        mMonitoredProcs, tmpDir.getAbsolutePath(), REPORT_BASE_PATH + jsonPath);
            }
            CLog.v("process meminfo stdout: " + cr.getStdout());
            CLog.v("process meminfo stderr: " + cr.getStderr());
            if (cr.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Failed to process memory info");
            } else {
                // testLog the generated report URL
                String url = REPORT_BASE_URL + jsonPath;
                CLog.v("Report URL: " + url);
                ByteArrayInputStreamSource s = new ByteArrayInputStreamSource(url.getBytes());
                mResultReporter.testLog("meminfo.report.url", LogDataType.TEXT, s);
                StreamUtil.cancel(s);
            }
        } finally {
            // clean up
            FileUtil.recursiveDelete(tmpDir);
            FileUtil.deleteFile(memoryHealth);
            FileUtil.deleteFile(memoryHealthDetails);
        }
    }

    private void processUnusableIndexData() {
        if (mUnusableIndexFiles.isEmpty()) {
            CLog.e("Failed to find any unusable_index files");
        } else {
            String allFiles = ArrayUtil.join(",", mUnusableIndexFiles);

            // Create output file names
            StringBuilder sb = new StringBuilder();
            String id = mBuildInfo.getBuildAttributes().get("build_alias");
            if (id == null) {
                id = mBuildInfo.getBuildId();
            }
            String outFilename = sb.append(id)
                    .append('-')
                    .append(mBuildInfo.getBuildFlavor())
                    .append('-')
                    .append(mBuildInfo.getTestTag())
                    .append('-')
                    .append(new Random(System.currentTimeMillis()).nextInt())
                    .append(".ui.json").toString();

            String viewPath = UNUSABLE_INDEX_JSON_FOLDER + outFilename;
            String fullPath = REPORT_AUTO_PATH + viewPath;

            CommandResult cr = getRunUtil().runTimedCmd(10 * 1000, ANALYSIS_PY_FILE,
                    String.format("--output=%s", fullPath), "--batch", "--unusable", allFiles);

            if (CommandStatus.SUCCESS.equals(cr.getStatus())) {
                try {
                    // Post the results
                    File tmpFile = FileUtil.createTempFile("unusable_index", ".txt");

                    String fullLink = String.format("%s?d=%s", UNUSABLE_INDEX_VIEWER_PATH,
                            viewPath);
                    BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(tmpFile)));
                    out.write(fullLink);
                    out.flush();
                    out.close();

                    InputStreamSource source = new FileInputStreamSource(tmpFile);

                    mResultReporter.testLog("unusable_index", LogDataType.TEXT, source);

                    // Delete the temporary files
                    for (String filename : mUnusableIndexFiles) {
                        (new File(filename)).delete();
                    }
                } catch (IOException e) {
                    CLog.e(e);
                }
            } else {
                CLog.w("Lowmemorykiller python script was unsuccessful");
                CLog.e(cr.getStderr());
            }
        }
    }

    private void processPagetypeinfoRatioData() {
        if (mPagetypeinfoFiles.isEmpty()) {
            CLog.e("Failed to find any pagetypeinfo files");
        } else {
            String allFiles = ArrayUtil.join(",", mPagetypeinfoFiles);

            // Create output file names
            StringBuilder sb = new StringBuilder();
            String id = mBuildInfo.getBuildAttributes().get("build_alias");
            if (id == null) {
                id = mBuildInfo.getBuildId();
            }
            String outFilename = sb.append(id)
                    .append('-')
                    .append(mBuildInfo.getBuildFlavor())
                    .append('-')
                    .append(mBuildInfo.getTestTag())
                    .append('-')
                    .append(new Random(System.currentTimeMillis()).nextInt())
                    .append(".pti.json").toString();

            String viewPath = PAGETYPEINFO_JSON_FOLDER + outFilename;
            String fullPath = REPORT_AUTO_PATH + viewPath;

            CommandResult cr = getRunUtil().runTimedCmd(10 * 1000, ANALYSIS_PY_FILE,
                    String.format("--output=%s", fullPath), "--batch", "--pagetypeinfo", allFiles);

            if (CommandStatus.SUCCESS.equals(cr.getStatus())) {
                try {
                    // Post the results
                    File tmpFile = FileUtil.createTempFile("pagetypeinfo_ratio", ".txt");

                    String fullLink = String.format("%s?d=%s", PAGETYPEINFO_VIEWER_PATH,
                            viewPath);
                    BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(tmpFile)));
                    out.write(fullLink);
                    out.flush();
                    out.close();

                    InputStreamSource source = new FileInputStreamSource(tmpFile);

                    mResultReporter.testLog("pagetypeinfo_ratio", LogDataType.TEXT, source);

                    // Delete the temporary files
                    for (String filename : mPagetypeinfoFiles) {
                        (new File(filename)).delete();
                    }
                } catch (IOException e) {
                    CLog.e(e);
                }
            } else {
                CLog.w("Pagetypeinfo python script was unsuccessful");
                CLog.e(cr.getStderr());
            }
        }
    }

    private void processZippedBugreports() throws DeviceNotAvailableException {
        IFileEntry directory = mDevice.getFileEntry(BUGREPORTZ_DIR);
        if (directory == null) {
            return;
        }
        CLog.d("Pulling zipped bugreports from the device, now.");
        // Bugreports create many files with the same base name, but various extensions. We want to
        // upload only files with .zip contents and no remaining .tmp contents. That means the file
        // is complete and populated with data. We avoid simply uploading all .zip contents because
        // the .zip file itself is created before it is populated with information.
        Map<String, List<IFileEntry>> groupings =
                directory
                        .getChildren(false)
                        .stream()
                        .filter(BUGREPORTZ_FILTER)
                        .collect(Collectors.groupingBy(f -> FileUtil.getBaseName(f.getName())));
        for (Map.Entry<String, List<IFileEntry>> group : groupings.entrySet()) {
            String name = group.getKey();
            List<IFileEntry> files = group.getValue();
            // Skip over any groups with a .tmp file around.
            if (files.stream().anyMatch(f -> f.getFullPath().endsWith(".tmp"))) {
                CLog.d("Skipped processing group: %s. Reason: found .tmp file.", name);
                continue;
            }
            Optional<IFileEntry> zipfile =
                    files.stream().filter(f -> f.getFullPath().endsWith(".zip")).findAny();
            if (!zipfile.isPresent()) {
                CLog.d("Skipped processing group: %s. Reason: did not find .zip file.", name);
                continue;
            } else {
                // Found a valid candidate to upload.
                CLog.d("Processing zipped bugreport with this prefix: %s.", name);
                File pulled = null;
                try {
                    // Test log only the zipped bugreport and no others (e.g. dumpstate).
                    pulled = mDevice.pullFile(zipfile.get().getFullPath());
                    try (FileInputStreamSource source = new FileInputStreamSource(pulled)) {
                        mResultReporter.testLog(
                                FileUtil.getBaseName(pulled.getName()),
                                LogDataType.BUGREPORTZ,
                                source);
                    }
                    mDevice.executeShellCommand(
                            String.format(
                                    "rm %s",
                                    files.stream()
                                            .map(IFileEntry::getFullPath)
                                            .collect(Collectors.joining(" "))));
                } finally {
                    // Clean up the left-over host resources.
                    FileUtil.deleteFile(pulled);
                }
            }
        }
    }

    private void processLogs(String dir) throws DeviceNotAvailableException {
        IFileEntry outputDir = mDevice.getFileEntry(dir);
        if (outputDir == null) {
            return;
        }
        for (IFileEntry file : outputDir.getChildren(false)) {
            if (!file.getFullPath().endsWith(".txt") && !file.getFullPath().endsWith(".zip")) {
                continue;
            }

            File pulledFile = mDevice.pullFile(file.getFullPath());
            if (pulledFile == null) {
                CLog.w("Could not pull file: %s", file.getFullPath());
                continue;
            }

            FileInputStreamSource stream = new FileInputStreamSource(pulledFile);
            boolean fileLogged = false;
            boolean deleteTemp = true;

            // Get File attributes to differentiate
            String basename = FileUtil.getBaseName(pulledFile.getName());
            String extension = FileUtil.getExtension(pulledFile.getName());

            if (basename.contains("bugreport") && mEnableBugreports) {
                if (extension.equals("zip") || basename.contains("dumpstate")) {
                    CLog.w("This method should no longer be used for pulling zipped bugreports.");
                } else {
                    mResultReporter.testLog(basename, LogDataType.BUGREPORT, stream);
                }
                fileLogged = true;
            } else if (basename.contains("compact-companion-meminfo")) {
                mResultReporter.testLog(basename, LogDataType.COMPACT_MEMINFO, stream);
                fileLogged = true;
            } else if (basename.contains("compact-meminfo")) {
                mResultReporter.testLog(basename, LogDataType.COMPACT_MEMINFO, stream);
                fileLogged = true;
            } else if (basename.contains("gfxinfo")) {
                mResultReporter.testLog(basename, LogDataType.GFX_INFO, stream);
                fileLogged = true;
            } else if (basename.contains("cpuinfo")) {
                mResultReporter.testLog(basename, LogDataType.CPU_INFO, stream);
                fileLogged = true;
            } else if (basename.contains("unusable")) {
                mUnusableIndexFiles.add(pulledFile.getAbsolutePath());
                fileLogged = true;
                deleteTemp = false;
            } else if (basename.contains("pagetypeinfo")) {
                mPagetypeinfoFiles.add(pulledFile.getAbsolutePath());
                fileLogged = true;
                deleteTemp = false;
            } else if (basename.contains("ion") || matchesExtraLogs(pulledFile.getAbsolutePath())) {
                mResultReporter.testLog(basename, LogDataType.TEXT, stream);
                fileLogged = true;
            }

            StreamUtil.cancel(stream);
            if (deleteTemp) {
                pulledFile.delete();
            }
            if (fileLogged) {
                mDevice.executeShellCommand("rm " + file.getFullPath());
            }
        }
    }

    private boolean matchesExtraLogs(String filename) {
        for (String log : mExtraLogs) {
            Pattern p = Pattern.compile(log);
            if (p.matcher(filename).find()) {
                return true;
            }
        }
        return false;
    }

    private File pullFile(String filename, String dir) throws DeviceNotAvailableException {
        File file = mDevice.pullFile(dir + "/" + filename);
        if (file == null) {
            CLog.w("Could not pull %s from %s", filename, dir);
            return null;
        }

        FileInputStreamSource stream = null;
        try {
            stream = new FileInputStreamSource(file);
            mResultReporter.testLog(file.getName(), LogDataType.TEXT, stream);
        } finally {
            StreamUtil.cancel(stream);
        }
        return file;
    }

    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
