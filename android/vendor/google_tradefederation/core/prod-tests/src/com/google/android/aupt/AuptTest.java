// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.aupt;

import com.android.loganalysis.item.CompactMemInfoItem;
import com.android.loganalysis.item.KernelLogItem;
import com.android.loganalysis.parser.CompactMemInfoParser;
import com.android.loganalysis.parser.KernelLogParser;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogReceiver;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.testtype.UiAutomatorTest;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test harness for the Automated User profile tests (AUPT). The test harness
 * sets up the device, issues command to start the test and waits for test to
 * finish. The harness will also periodically pull the bugreports from the
 * device and upload them to the AUPT dashboard.
 */
public class AuptTest extends UiAutomatorTest implements IBuildReceiver, IRetriableTest {
    protected static final String DUMPHEAP_OUTPUT = "/data/local/tmp";
    private static final String PROGRESS_FILE = "progress.txt";
    private static final String SHUTTER_TIME_FILE = "shutter_time.txt";
    private static final String LINKS_FILE = "links.txt";
    private static final String XML_DUMP_FILE = "/data/local/tmp/error_dump.xml";
    private static final int AVAILABLE_TIMEOUT = 5 * 60 * 1000;
    private static final long EVENTS_LOGCAT_SIZE = 80 * 1024 * 1024;
    private static final String LAUNCH_DISPLAY_PATTERN = "^(?<date>[0-9-]*)\\s+(?<time>[0-9:.]*)"
            + "\\s+(?<entrythree>[0-9]*)\\s+(?<entryfour>[0-9]*)\\s+I am_activity_launch_time: "
            + "\\[[0-9]*,[0-9]*,(?<pkgactivityname>.*),(?<launchtime>.[0-9]*),"
            + "(?<extra>.[0-9]*)\\]?$";

    private static final String ANALYSIS_PY_FILE =
            "/google/data/ro/teams/tradefed/testdata/aupt/official-script/kmsg_analysis2.py";

    private static final String KERNEL_JSON_FOLDER = "json/kernel/";
    private static final String LOWMEM_JSON_FOLDER = "lowmemorykiller-json/";
    private static final String LAUNCH_JSON_FOLDER = "applaunch-json/";
    private static final String REPORT_AUTO_PATH = "/auto/android-test/www/mem-reports/";
    private static final String HTML_VIEWER_PATH =
            "http://android-test/www/mem-reports/lmk-viewer.html";
    private static final String HTML_LAUNCH_VIEWER_PATH =
            "http://android-test/www/mem-reports/applaunch-viewer.html";

    @Option(name = "bugreport-check-interval",
            description = "How often to check the device for new bugreports, in minutes")
    protected long mBugreportInterval = 30;

    @Option(name = "reporting", description = "enable reporting into backend services")
    protected boolean mReporting = true;

    @Option(name = "bugreport-retention-period",
            description = "How long should saved bugreports be kept (in days).")
    private int mBugreportRetention = 30;

    @Option(name = "retry-on-failure", description = "Retry the test on failure")
    private boolean mRetryOnFailure = false;

    @Option(name = "clear-logcat",
            description = "If enabled, clears the logcat before starting the test")
    private boolean mClearLogcat = false;

    @Option(name = "detect-kill", description = "Monitor the process name for pid changes")
    protected List<String> mDetectKill = new ArrayList<String>();

    @Option(name = "monitor-proc", description = "Generate memory graphs for these processes")
    protected List<String> mMonitoredProcs = new ArrayList<String>();

    @Option(name = "extra-log",
            description = "Regex that will match any other logs that need to be collected, " +
                "may be repeated.")
    protected List<String> mExtraLogs = new ArrayList<String>();

    @Option(
        name = "dumpheap-thresholds",
        description = "Threshold map for taking process dumpheaps"
    )
    protected Map<String, Long> mDumpheapThresholds = new HashMap<String, Long>();

    @Option(name="dumpheap-procs", description="Processes for which dumpheap needs to be taken")
    private List<String> mManagedProcs = new ArrayList<String>();

    @Option(name="native-dumpheap-proc", description="Processes for which take native heap dump")
    private List<String> mNativeProcs = new ArrayList<String>();

    @Option(name="native-dumpheap-program", description="What to set the program property when "+
            "taking native heap dumps")
    private String mDumpheapProgram = null;

    @Option(name="dumpheap-backtrace", description="Enables backtrace on native heap dumps")
    private boolean mDumpheapBacktrace = false;

    @Option(name="dumpheap-interval",
            description="How often dumpheap command is called (in minutes)")
    private int mDumpheapInterval = 60;

    @Option(name="record-kmsg", description="Record kmsg")
    private boolean mRecordKmsg = false;

    @Option(name="record-events-log", description="Record events log")
    private boolean mRecordEvents = false;

    @Option(name="record-ipc-thread-state", description="Record IPCThreadState logcat messages")
    private boolean mRecordIpcThreadState = false;

    @Option(name = "package-name",
            description = "package name for which the launch time need to be parsed from"
                    + "events logcat file")
    private List<String> mPackageNames = new ArrayList<String>();

    @Option(name = "random-seed",
            description = "Seed value for randomizing test order")
    private long mRandomSeed = 0;

    @Option(name = "json-out",
            description = "JSON out directory")
    private File mJsonOutDir = new File(System.getProperty("java.io.tmpdir"));

    protected IBuildInfo mBuildInfo;

    private static final String KMSG_CMD = "cat /proc/kmsg";
    private static final String EVENTS_LOGCAT_CMD = "logcat -v threadtime -b events";
    private static final String IPC_LOGCAT_CMD = "logcat -v threadtime IPCThreadState:V *:S";
    private List<LogReceiver> mLogReceivers = new ArrayList<LogReceiver>();
    private String mOutputDir;
    private List<String> mDashboardLinks = new ArrayList<String>();
    private Map<String,File> mLaunchTimeFiles = new HashMap<String, File>();

    private LogReceiver mKernelLogReceiver = null;
    private LogReceiver mEventsLogReceiver = null;
    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mOutputDir = getTestRunArgMap().get("outputLocation");
        if (mOutputDir == null) {
            CLog.e("Location of output on device not specified. " +
                    "Please provide a valid outputLocation parameter");
            return;
        }
        mOutputDir = "${EXTERNAL_STORAGE}/" + mOutputDir;

        CLog.i("Removing old AUPT output");
        getDevice().executeShellCommand(String.format("rm %s/*.txt", mOutputDir));

        // process detect kill parameter, and pass it onto test harness
        String monitoredProcs = null;
        if (!mDetectKill.isEmpty()) {
            monitoredProcs = ArrayUtil.join(",", mDetectKill);
            addRunArg("detectKill", monitoredProcs);
        }

        if (!mMonitoredProcs.isEmpty()) {
            monitoredProcs = ArrayUtil.join(",", mMonitoredProcs);
            writeMemoryTrackFile(monitoredProcs);
        }

        if (mRandomSeed == 0) {
            mRandomSeed = System.currentTimeMillis();
        }
        addRunArg("seed", Long.toString(mRandomSeed));
        CLog.i("Using random seed value, %d", mRandomSeed);

        AuptDeviceLogUploader poster = null;

        if (mReporting) {
            poster =
                    new AuptDeviceLogUploader(
                            getDevice(),
                            mOutputDir,
                            mBugreportInterval,
                            listener,
                            mBuildInfo,
                            monitoredProcs,
                            mExtraLogs);

            if (mRecordIpcThreadState) {
                mLogReceivers.add(new LogReceiver(getDevice(), IPC_LOGCAT_CMD,
                        "logcat-IPCThreadState"));
            }

            if (mRecordKmsg) {
                mKernelLogReceiver = new LogReceiver(getDevice(), KMSG_CMD, "kmsg");
                mLogReceivers.add(mKernelLogReceiver);
            }

            if (mRecordEvents) {
                mEventsLogReceiver = new LogReceiver(getDevice(), EVENTS_LOGCAT_CMD, "events",
                        EVENTS_LOGCAT_SIZE, 5);
                mLogReceivers.add(mEventsLogReceiver);
            }
        }

        startLogReceivers();

        if (mClearLogcat) {
            getDevice().clearLogcat();
        }

        if (mReporting) {
            poster.start();
        }

        setupDumpheap();
        Dumpheap dh = new Dumpheap(listener, DUMPHEAP_OUTPUT);
        Thread dumpheapThread = new Thread(dh);
        dumpheapThread.setName("AuptTest#dumpheapThread");
        dumpheapThread.setDaemon(true);
        dumpheapThread.start();
        try {
            runTest(listener);
        } finally {
            getDevice().stopLogcat();
            if (mReporting) {
                poster.cancel();
                try {
                    poster.join();
                    dh.cancel();
                    dumpheapThread.join();
                } catch (InterruptedException e) {
                    CLog.e(e);
                    return;
                }
            }
            runPostProcessingScripts(listener);
            createLinksFile();
            pullErrorSnapshots(listener);
            takeDumpheap(listener, DUMPHEAP_OUTPUT);
            if (mReporting) {
                postLogs(listener);
            }
            stopLogReceivers();
        }
    }

    private void createLinksFile () {
        if (mDashboardLinks.isEmpty()) {
            return;
        }

        File linksFile = null;
        try {
            linksFile = FileUtil.createTempFile("links", ".txt");
            String linksText = ArrayUtil.join("\n", mDashboardLinks);

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(linksFile)));
            out.write(linksText);
            out.flush();
            out.close();

            getDevice().pushFile(linksFile, mOutputDir + "/" + LINKS_FILE);
        } catch (IOException e) {
            CLog.w("Could not write links to file");
            CLog.e(e);
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        } finally {
            if (linksFile != null) {
                linksFile.delete();
            }
        }
    }

    private void runPostProcessingScripts (ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        // Create output file names
        StringBuilder sb = new StringBuilder();
        String id = mBuildInfo.getBuildAttributes().get("build_alias");
        if (id == null) {
            id = mBuildInfo.getBuildId();
        }
        String filePath = sb.append(id)
                .append('-')
                .append(mBuildInfo.getBuildFlavor())
                .append('-')
                .append(mBuildInfo.getTestTag())
                .append('-')
                .append(new Random(System.currentTimeMillis()).nextInt()).toString();


        // Run kernel post-processing scripts
        if (mKernelLogReceiver != null) {
            InputStream stream = mKernelLogReceiver.getData().createInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            try {
                KernelLogItem item = new KernelLogParser().parse(reader);
                String path = mJsonOutDir + KERNEL_JSON_FOLDER + filePath + ".json";
                FileUtil.writeToFile(item.toJson().toString(), new File(path));
            } catch (IOException e) {
                CLog.e(e);
                CLog.e("Failed to parse kernel logs");
            }

            // TODO(mrosenfeld): Remove; legacy processing with Python
            File outFile = null;
            try {
                // Create a kernel message file
                InputStream inStream = mKernelLogReceiver.getData().createInputStream();
                outFile = FileUtil.createTempFile("kmsg", ".txt");
                OutputStream outStream = new FileOutputStream(outFile);
                StreamUtil.copyStreams(inStream, outStream);
                outStream.flush();
                outStream.close();

                // Process for lowmemorykiller
                String lmkPath = filePath.toString() + ".lmk.json";
                findLowMemKills(outFile, lmkPath);

                // Process for fragmentation
                String fragPath = filePath.toString() + ".frag.json";
                findFragmentation(outFile, fragPath);
            } catch (IOException e) {
                CLog.e(e);
            } finally {
                if (outFile != null) {
                    outFile.delete();
                }
            }
        } else {
            CLog.w("Not logging kernel messages; cannot process kernel messages.");
        }

        if (mEventsLogReceiver != null) {
            // Process activity launch time
            FileInputStreamSource fileInputSrc = null;
            try {
                processLaunchTime();
                for (Map.Entry<String, File> launchFileEntry : mLaunchTimeFiles.entrySet()) {
                    String launchPath = filePath + "." + launchFileEntry.getKey().replace("/", "-")
                            + ".json";
                    displayLaunchTime(launchFileEntry.getValue(), launchPath);
                    fileInputSrc = new FileInputStreamSource(launchFileEntry.getValue());
                    listener.testLog(launchFileEntry.getKey(), LogDataType.TEXT,
                            fileInputSrc);
                }
            } catch (IOException e) {
                CLog.e(e);
            } finally {
                StreamUtil.cancel(fileInputSrc);
                for (File launchTimeFile : mLaunchTimeFiles.values()) {
                    FileUtil.deleteFile(launchTimeFile);
                }
            }
        } else {
            CLog.w("Not logging the events buffer; cannot process launch times.");
        }

        //Process camera shutter open time
        String shutterRawFileStr = mOutputDir + "/" + SHUTTER_TIME_FILE;
        File shutterTimeFile = null;
        FileInputStreamSource fileInputSrc = null;
        try {
            shutterTimeFile = getDevice().pullFile(shutterRawFileStr);
            if (shutterTimeFile != null) {
                String shutterOutputPath = filePath.toString() + ".shutterTime.json";
                fileInputSrc = new FileInputStreamSource(shutterTimeFile);
                listener.testLog("CameraShutterTime", LogDataType.TEXT, fileInputSrc);
                displayLaunchTime(shutterTimeFile, shutterOutputPath);
            }
        } finally {
            StreamUtil.cancel(fileInputSrc);
            FileUtil.deleteFile(shutterTimeFile);
        }

        // TODO: run bugreport post-processing scripts
    }

    /**
     * To process the activity launch time from the events log cat file
     * @throws IOException
     */
    private void processLaunchTime() throws IOException {
        Pattern displayPattern = Pattern.compile(LAUNCH_DISPLAY_PATTERN);
        InputStream inStream = mEventsLogReceiver.getData().createInputStream();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inStream));
        String line = null;
        while ((line = bufferedReader.readLine()) != null) {
            Matcher match = matches(displayPattern, line);
            if (match != null) {
                if (!mPackageNames.isEmpty()) {
                    // To parse only the given list of package names
                    for (String packageName : mPackageNames) {
                        if (match.group("pkgactivityname").contains(packageName)) {
                            String launchOutput = String.format("%s %s %s %s",
                                    match.group("date"), match.group("time"),
                                    match.group("pkgactivityname"), match.group("launchtime"))
                                    .trim();
                            writeToLaunchFile(launchOutput, match.group("pkgactivityname"));
                            break;
                        }
                    }
                } else {
                    String launchOutput = String.format("%s %s %s %s",
                            match.group("date"), match.group("time"),
                            match.group("pkgactivityname"), match.group("launchtime")).trim();
                    writeToLaunchFile(launchOutput, match.group("pkgactivityname"));
                }
            }
        }
    }

    /**
     * Create and write to launch file for each package/activity name.If the file already exists
     * append to the file
     * @param launchOutput data that will be written in the launch file
     * @param pkgActivityName file created using  pkgActivityName
     * @throws IOException
     */
    private void writeToLaunchFile(String launchOutput, String pkgActivityName) throws IOException {
        File file = null;
        if (mLaunchTimeFiles.containsKey(pkgActivityName)) {
            file = mLaunchTimeFiles.get(pkgActivityName);
        } else {
            file = FileUtil.createTempFile(pkgActivityName, ".txt");
            mLaunchTimeFiles.put(pkgActivityName, file);
        }
        FileUtil.writeToFile(launchOutput + "\n", file, true);
    }

    /**
     * Checks whether {@code line} matches the given {@link Pattern}.
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against
     *         {@code pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

    private void findLowMemKills(File kmsg, String output) {
        String viewPath = LOWMEM_JSON_FOLDER + output;
        String fullPath = REPORT_AUTO_PATH + viewPath;

        CommandResult cr = getRunUtil().runTimedCmd(10 * 1000, ANALYSIS_PY_FILE,
                String.format("--output=%s", fullPath), "--file", "--lowmemorykiller",
                kmsg.getAbsolutePath());

        if (cr.getStatus() == CommandStatus.SUCCESS) {
            String fullLink = String.format("%s?d=%s", HTML_VIEWER_PATH, viewPath);
            CLog.v(String.format("Link to HTML viewer at %s", fullLink));
            mDashboardLinks.add(fullLink);
        } else {
            CLog.e(cr.getStderr());
        }
    }

    private void findFragmentation(File kmsg, String output) {
        String viewPath = LOWMEM_JSON_FOLDER + output;
        String fullPath = REPORT_AUTO_PATH + viewPath;

        CommandResult cr = getRunUtil().runTimedCmd(10 * 1000, ANALYSIS_PY_FILE,
                String.format("--output=%s", fullPath), "--file", "--fragmentation",
                kmsg.getAbsolutePath());

        if (cr.getStatus() == CommandStatus.SUCCESS) {
            String fullLink = String.format("%s?d=%s", HTML_VIEWER_PATH, viewPath);
            CLog.v(String.format("Link to HTML viewer at %s", fullLink));
            mDashboardLinks.add(fullLink);
        } else {
            CLog.e(cr.getStderr());
        }
    }

    private void displayLaunchTime(File launchTimeFile,String output){
        String viewPath = LAUNCH_JSON_FOLDER + output;
        String fullPath = REPORT_AUTO_PATH + viewPath;

        CommandResult cr = getRunUtil().runTimedCmd(10 * 1000, ANALYSIS_PY_FILE,
                String.format("--output=%s", fullPath), "--file", "--launchtime",
                launchTimeFile.getAbsolutePath());

        if (cr.getStatus() == CommandStatus.SUCCESS) {
            //Use launch viewer
            String fullLink = String.format("%s?d=%s", HTML_LAUNCH_VIEWER_PATH, viewPath);
            CLog.v(String.format("Link to HTML viewer at %s", fullLink));
            mDashboardLinks.add(fullLink);
        } else {
            CLog.e(cr.getStderr());
        }
    }

    private void writeMemoryTrackFile(String monitoredProcs) throws DeviceNotAvailableException {
        try {
            File temp = File.createTempFile("track_memory", ".txt");
            FileUtil.writeToFile(monitoredProcs, temp);
            getDevice().pushFile(temp, "${EXTERNAL_STORAGE}/track_memory.txt");
        } catch (IOException e) {
            CLog.w("Could not write to monitored procs to file");
            CLog.e(e);
        }
    }

    private void postLogs(ITestInvocationListener listener) {
        for (LogReceiver r : mLogReceivers) {
            r.postLog(listener);
        }
    }

    private void startLogReceivers() {
        for (LogReceiver r : mLogReceivers) {
            r.start();
        }
    }

    private void stopLogReceivers() {
        for (LogReceiver r : mLogReceivers) {
            r.stop();
        }
    }

    /**
     * Handles device side execution.
     * <p>
     * Hook for subclass to override test execution behavior
     * @param listener
     * @throws DeviceNotAvailableException
     */
    protected void runTest(ITestInvocationListener listener)
            throws DeviceNotAvailableException {
        super.run(listener);
    }

    /**
     * Pull the progress file from the devices and save it.
     * @param listener
     */
    private void pullErrorSnapshots(ITestInvocationListener listener) {
        pullAndTestLog(listener, mOutputDir + "/" + PROGRESS_FILE, "progress", LogDataType.TEXT);
        pullAndTestLog(listener, mOutputDir + "/" + LINKS_FILE, "links", LogDataType.TEXT);
        pullAndTestLog(listener, XML_DUMP_FILE, "error_dump", LogDataType.XML);
    }

    private void pullAndTestLog(ITestInvocationListener listener, String deviceFilePath,
            String logFileName, LogDataType dataType) {
        try {
            getDevice().waitForDeviceAvailable(AVAILABLE_TIMEOUT);
            if (getDevice().doesFileExist(deviceFilePath)) {
                File pulledFile = getDevice().pullFile(deviceFilePath);
                if (pulledFile == null) {
                    CLog.w("Unable to pull file: " + deviceFilePath);
                    return;
                }
                try (FileInputStreamSource stream = new FileInputStreamSource(pulledFile)) {
                    listener.testLog(logFileName, dataType, stream);
                }
                pulledFile.delete();
            }
        } catch (DeviceNotAvailableException e) {
            CLog.w("Could not retrieve progress file: %s", e.getMessage());
        }
    }

    private void setupDumpheap() throws DeviceNotAvailableException {
        setupDumpheap(getDevice());
    }

    protected void setupDumpheap(ITestDevice device) throws DeviceNotAvailableException {
        if (mNativeProcs.isEmpty()) {
            return;
        }

        if (mDumpheapProgram != null) {
            device.executeShellCommand(
                    String.format("setprop libc.debug.malloc.program %s", mDumpheapProgram));
        }

        if (!mDumpheapBacktrace) {
            device.executeShellCommand("setprop libc.debug.malloc.nobacktrace 1");
        }

        device.executeShellCommand("setprop libc.debug.malloc 1");
        device.executeShellCommand("stop");
        RunUtil.getDefault().sleep(5000);
        device.executeShellCommand("start");
        device.waitForDeviceAvailable();
    }

    private void takeDumpheap(ITestInvocationListener listener, String outputDir)
            throws DeviceNotAvailableException {
        for (String proc : mManagedProcs) {
            File dump = takeDumpheap(outputDir, proc, null, true);
            saveDumpheap(listener, dump);
        }

        for (String proc : mNativeProcs) {
            File dump = takeDumpheap(outputDir, proc, null, false);
            saveDumpheap(listener, dump);
        }
    }


    private void saveDumpheap(ITestInvocationListener listener, File dumpheap) {
        if (dumpheap == null) {
            return;
        }
        try (FileInputStreamSource stream = new FileInputStreamSource(dumpheap)) {
            listener.testLog(FileUtil.getBaseName(dumpheap.getName()), LogDataType.HPROF, stream);
        }
    }

    private File takeDumpheap(String outDir, String proc, String suffix, boolean managed)
            throws DeviceNotAvailableException {
        return takeDumpheap(outDir, proc, suffix, managed, getDevice());
    }

    private File takeDumpheap(String outDir, String proc, String suffix, boolean managed,
            ITestDevice device)
            throws DeviceNotAvailableException {
        String outputFile = outDir + "/" + proc;
        if (!managed) {
            outputFile += "_n";
        }

        if (suffix != null) {
            outputFile += ("_" + suffix);
        }
        outputFile += ".hprof";

        String command = String.format("am dumpheap %s %s %s", !managed ? "-n" : "",
                proc, outputFile);
        device.executeShellCommand(command);
        RunUtil.getDefault().sleep(5000);
        File pulledFile = device.pullFile(outputFile);
        if (pulledFile == null) {
            CLog.w("Unable to pull file: %s", outputFile);
        }
        return pulledFile;
    }

    class Dumpheap implements Runnable {
        private ITestInvocationListener mListener;
        private String mOutputDir;
        private boolean mCanceled = false;
        private ITestDevice mDevice = getDevice();
        private String mSuffix = "";

        public Dumpheap(ITestInvocationListener listener, String outputDir) {
            mListener = listener;
            mOutputDir = outputDir;
        }

        public Dumpheap(ITestInvocationListener listener, String outputDir,
                ITestDevice device, String suffix) {
            mListener = listener;
            mOutputDir = outputDir;
            mDevice = device;
            mSuffix = suffix;
        }

        public synchronized void cancel() {
            mCanceled = true;
            this.notifyAll();
        }

        @Override
        public void run() {
            int count = 0;
            while (!mCanceled) {
                count += 1;
                try {
                    // TODO(b/63529729): Fixing will simplify this logic.
                    for (Map.Entry<String, Long> entry : mDumpheapThresholds.entrySet()) {
                        String output =
                                mDevice.executeShellCommand(
                                        String.format(
                                                "dumpsys meminfo -c | grep %s", entry.getKey()));
                        if (output.isEmpty()) {
                            CLog.d("Skipping %s -- no process found.", entry.getKey());
                        } else {
                            CompactMemInfoItem item =
                                    new CompactMemInfoParser()
                                            .parse(Arrays.asList(output.split("\n")));
                            for (Integer pid : item.getPids()) {
                                if (item.getName(pid).equals(entry.getKey())) {
                                    if (item.getPss(pid) >= entry.getValue()) {
                                        File dump =
                                                takeDumpheap(
                                                        mOutputDir,
                                                        entry.getKey(),
                                                        "trigger",
                                                        true);
                                        saveDumpheap(mListener, dump);
                                        break;
                                    } else {
                                        CLog.d(
                                                "Found process %s had PSS %d < %d threshold.",
                                                entry.getKey(), item.getPss(pid), entry.getValue());
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    for (String proc : mManagedProcs) {
                        File dump = takeDumpheap(mOutputDir, proc,
                                String.format("%02d", count) + mSuffix, true, mDevice);
                        saveDumpheap(mListener, dump);
                    }

                    for (String proc : mNativeProcs) {
                        File dump = takeDumpheap(mOutputDir, proc,
                                String.format("%02d", count) + mSuffix, false, mDevice);
                        saveDumpheap(mListener, dump);
                    }
                } catch (DeviceNotAvailableException e) {
                    CLog.e(e);
                }

                synchronized (this) {
                    try {
                        this.wait(mDumpheapInterval * 60 * 1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    if (mCanceled) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRetriable() {
        return mRetryOnFailure;
    }
}
