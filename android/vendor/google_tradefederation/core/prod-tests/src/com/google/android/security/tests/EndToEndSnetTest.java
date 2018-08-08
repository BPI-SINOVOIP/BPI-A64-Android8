// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.security.tests;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.JavaCrashItem;
import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.item.MiscLogcatItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.Assert;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class EndToEndSnetTest implements IBuildReceiver, IDeviceTest, IRemoteTest {

    private static final String SAFETY_NET_LOGCAT_TAG_NAME = "com.google.android.snet.Snet";
    private static final String SAFETY_NET_CATEGORY_NAME = "SAFETY_NET";
    private static final String SNET_HELLO_MSG = "Hello Snet!";

    private static final int ONE_SEC_MS = 1 * 1000;
    private static final int THIRTY_SEC_MS = 30 * ONE_SEC_MS;
    private static final int ONE_MIN_MS = 60 * 1000;
    private static final int FIVE_MINS_MS = 5 * ONE_MIN_MS;
    private static final int ONE_WEEK_MS = 604800000;
    private static final int THREE_WEEKS_MS = 3 * ONE_WEEK_MS;

    private static final int SNET_WAKE_SHORT_INTERVAL_MS = THIRTY_SEC_MS;
    private static final int SNET_WAKE_LONG_INTERVAL_MS = THREE_WEEKS_MS;
    private static final int SNET_SIGNAL_COLLECTION_TIMEOUT_MS = 2 * ONE_MIN_MS + THIRTY_SEC_MS;

    private static final String[] SNET_DIR_PREFIX = {"/data/data/", "/data/user/0/"};
    private static final String SNET_INSTALL_DIR = "com.google.android.gms/snet/installed";
    private static final String SNET_DOWNLOAD_DIR = "com.google.android.gms/snet/download";
    private static final String SNET_DATA_FILES = "/data/system/dropbox/snet@*.dat";
    private static final String SNET_GCORE_DATA_FILES = "/data/system/dropbox/snet_gcore@*.dat";
    private static final String SNET_JAR_FILENAME = "SafetyNet.jar";
    private static final String[] SNET_JAR_CONTENTS = {"classes.dex", "META-INF", "version"};
    private static final String SNET_USER_DEFAULT = "u0_a8";

    private static final Pattern SNET_VERSION_PATTERN = Pattern.compile("VERSION:(\\d*)$");
    private static final String GSERVICES_OVERRIDE_PKG =
            "com.google.gservices.intent.action.GSERVICES_OVERRIDE";

    private static final String CHECKIN_INST_CLASS = ".CheckinCompleteInstrumentation";
    private static final String COMPUTE_DIGEST_INST_CLASS = ".ComputeDigestInstrumentation";
    private static final String SNET_INSTRUMENTATION_PKG =
            "com.google.android.gms.test.security.snet.instrumentation";
    private static final String FULL_CHECKIN_INST_NAME =
            String.format("%s/%s", SNET_INSTRUMENTATION_PKG, CHECKIN_INST_CLASS);
    private static final String FULL_COMPUTE_DIGEST_INST_NAME =
            String.format("%s/%s", SNET_INSTRUMENTATION_PKG, COMPUTE_DIGEST_INST_CLASS);
    private static final long CHECKIN_INST_TIMEOUT_S = 60;
    private static final long CHECKIN_INST_TIMEOUT_DELTA_S = 5;

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final DateFormat DATE_FORMAT_SNET_DEBUG_STATUS = new SimpleDateFormat(
            "yyyyMMdd_HHmmss");

    private ITestInvocationListener mListener;
    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private String mSnetInstallDir;
    private String mSnetDownloadDir;
    private String mSnetUser;
    private Date mTestStartTime;
    private Long mTimeOffset;
    private boolean mPriorSnetDataDeleted = false;

    /**
     * The current test failed because of failureReason. This is used to halt the current
     * test upon a failure condition being met.
     */
    private static class TestFailedException extends Exception {
        private static final long serialVersionUID = 1L;
        public TestFailedException(String failureReason) {
            super(failureReason);
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * Clean up Snet gservices_overrides and remove Snet jar and data
     */
    private void cleanupSnet() throws DeviceNotAvailableException {
        // set to empty instead of resetting to default, since default would point to Prod Snet
        setGServicesOverride("snet_debug_status", "");
    }

    /**
     * Runs the EndToEndTest test suite
     *
     * @param listener The test invocation listener.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mListener = listener;
        mListener.testRunStarted(EndToEndSnetTest.class.getName(), 0);

        try {
            // test with a valid device
            if (!deviceValidTest()) {
                CLog.d("Not running on a Gingerbread or newer device. Skipping remaining tests.");
                return;
            }

            cleanupSnet();

            // find the install and download directories for Snet
            getSnetDirectories();
            snetDirectoriesTest();

            // start capturing Logcat
            getDevice().startLogcat();

            // test production version of SafetyNet jar to download and setup SafetyNet
            snetStartTest(true);

            // now that production snet has potentially started, wait for signals to be collected
            CLog.d("Waiting for production Snet to finish...\n");
            RunUtil.getDefault().sleep(SNET_SIGNAL_COLLECTION_TIMEOUT_MS);

            // delete the SafetyNet dropbox files for the production jar
            mPriorSnetDataDeleted = deleteSnetData();

            // install the dev SafetyNet.jar from the build
            if (!testSafetyNetDevJarInstall()) {
                CLog.d("Could not install the dev jar. Skipping remaining tests.");
                return;
            }

            // test dev version of SafetyNet jar
            resetSnetStartTest();
            snetStartTest(false);

            // now that dev snet has potentially started, wait for signals to be collected
            CLog.d("Waiting for Snet data collection to settle...");
            RunUtil.getDefault().sleep(SNET_SIGNAL_COLLECTION_TIMEOUT_MS);

            // check if Snet data was collected
            snetDataCollectionTest();

            // stop Logcat capture
            getDevice().stopLogcat();

            cleanupSnet();

            CLog.d("Forcing Checkin to trigger Snet upload...");
            forceCheckinTest();
        } finally {
            mListener.testRunEnded(0, Collections.<String, String>emptyMap());
        }
    }

    /**
     * Checks whether test can communicate with device by requesting the device API level.
     * It is required that the test device runs Android Gingerbread or newer.
     *
     * @return True if can communicate with device and device API level is at least 9.
     * @throws DeviceNotAvailableException
     */
    public boolean deviceValidTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                "deviceValidTest");
        mListener.testStarted(testId);
        try {
            int apiLevel = getDevice().getApiLevel();
            if (apiLevel < 9) {
                mListener.testFailed(testId, "Device needs to run Android Gingerbread or newer.");
                return false;
            }
            return true;
        } catch (DeviceNotAvailableException e) {
            mListener.testFailed(testId, "tradefed could not reach device.");
            throw e;
        } finally {
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
        }
    }

    /**
     * Gets the Snet download and install directories based on the Android API Level
     *
     */
    private void getSnetDirectories() throws DeviceNotAvailableException {
        if (mSnetInstallDir != null && mSnetDownloadDir != null) {
            return;
        }
        int apiLevel = getDevice().getApiLevel();
        CLog.d("Device %s API level: %d", getDevice().getSerialNumber(), apiLevel);
        String prefix = (apiLevel <= 20) ? SNET_DIR_PREFIX[0] : SNET_DIR_PREFIX[1];
        mSnetInstallDir = prefix + SNET_INSTALL_DIR;
        mSnetDownloadDir = prefix + SNET_DOWNLOAD_DIR;
        CLog.d("Snet install directory: %s", mSnetInstallDir);
        CLog.d("Snet download directory: %s", mSnetDownloadDir);
    }

    /**
     * Retrieves the owner of the Gms directory parenting snet.
     *
     * @return The owner of the Gms directory, {@code SNET_USER_DEFAULT} otherwise.
     * @throws DeviceNotAvailableException
     */
    private String getSnetUser() throws DeviceNotAvailableException {
        if (mSnetUser == null) {
            String result = getDevice().executeShellCommand(
                    String.format("ls -l -d %s/../..", mSnetInstallDir));
            try {
                mSnetUser = result.trim().split(" ")[1];
            } catch (Exception e) {
                mSnetUser = null;
            }
        }
        return (mSnetUser != null) ? mSnetUser : SNET_USER_DEFAULT;
    }

    /**
     * Checks whether Snet directories can be decided based on Android API level
     *
     */
    public void snetDirectoriesTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                "snetDirectoriesTest");
        getSnetDirectories();
        if (mSnetInstallDir == null) {
            mListener.testFailed(testId, "Could not get the Snet install directory.");
        }
        if (mSnetDownloadDir == null) {
            mListener.testFailed(testId, "Could not get the Snet download directory.");
        }
        mListener.testEnded(testId, Collections.<String, String>emptyMap());
    }

    /**
     * Starts up Snet by setting a short wake interval and waits for Snet hello.
     * For dev builds, sets a Snet debug status so that logs can be easily identified on backend.
     * At the end of the test, resets the wake interval to prevent device from generating
     * additional logs.
     *
     * @param usingProduction using the production version of the SafetyNet jar.
     */
    public void snetStartTest(boolean usingProduction) throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                String.format("snetStart%sTest", usingProduction ? "Prod" : "Dev"));
        mListener.testStarted(testId);

        mTestStartTime = getLatestLogcatTimestamp();
        CLog.d("Test Logcat start time: %s", DATE_FORMAT.format(mTestStartTime));

        Date systemTime = Calendar.getInstance().getTime();
        CLog.d("System time: %s", DATE_FORMAT.format(systemTime));

        mTimeOffset = mTestStartTime.getTime() - systemTime.getTime();
        CLog.d("Time offset in milliseconds: %d", mTimeOffset);

        // clear exiting Logcat data capture
        CLog.d("Clearing Logcat.");
        getDevice().clearLogcat();

        // set consent for Snet and enable
        setGServicesOverride("snet_force_run", "true");
        setGServicesOverride("snet_service_remote_enable", "true");

        // set the Snet debug status for EndToEnd test for dev builds
        if (!usingProduction) {
            String snetDebugStatus = String.format("EndToEndTest_%s_%d_%s",
                    DATE_FORMAT_SNET_DEBUG_STATUS.format(systemTime), getDevice().getApiLevel(),
                    getDevice().getProductType());
            setGServicesOverride("snet_debug_status", snetDebugStatus);
            CLog.d("Set the Snet debug status to: %s", snetDebugStatus);
        }

        CLog.d("Issuing GMS Core Override for a short Snet wake interval.");
        setGServicesOverride("snet_wake_interval_ms",
                Integer.toString(SNET_WAKE_SHORT_INTERVAL_MS));

        waitForSnetMessage(SNET_HELLO_MSG, mTestStartTime);

        try {
            assertSnetIsRunning();
            assertSnetSaid(SNET_HELLO_MSG, mTestStartTime);
            assertNoGcoreCrashesAfter(mTestStartTime);
        } catch (TestFailedException tfe) {
            mListener.testFailed(testId, tfe.getMessage());
        } finally {
            CLog.d("Issuing GMS Core Override for a long Snet wake interval.");
            resetGServicesOverride("snet_wake_interval_ms");
        }

        mListener.testEnded(testId, Collections.<String, String>emptyMap());
    }

    private void resetSnetStartTest() {
        mTestStartTime = null;
    }

    /**
     * Installs the Safety-Net dev jar and tests for a successful installation.
     *
     * @return True if the Safety-Net dev jar successfully installs on the device.
     * @throws DeviceNotAvailableException
     */
    public boolean testSafetyNetDevJarInstall() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                "devJarInstallTest");
        mListener.testStarted(testId);

        File hostDevJarFile = mBuildInfo.getFile(SNET_JAR_FILENAME);

        if (hostDevJarFile == null) {
            CLog.d("Could not get jar file from build info");
            mListener.testFailed(testId, "Could not get the dev jar file from build info.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        } else {
            CLog.d("SafetyNet jar file: %s Exists: %b Can read: %b", hostDevJarFile.toString(),
                    hostDevJarFile.exists(), hostDevJarFile.canRead());
        }

        File tmpDir = null;
        try {
            tmpDir = FileUtil.createTempDir("SnetJarManipulation_");
            CLog.d("Snet jar manipulation directory: %s", tmpDir);
        } catch (IOException e) {
            mListener.testFailed(testId, "Could not create temp directory for jar manipulation.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        }

        // extract the jar file
        if (!extractJarFile(hostDevJarFile, tmpDir)) {
            mListener.testFailed(testId, "Could not extract original dev jar file.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        }

        // modify the jar version number
        if (!modifyJarVersion(tmpDir)) {
            mListener.testFailed(testId, "Could not write new jar version number.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        }

        // create a new jar with the new build number
        if (!createModifiedInstallFiles(tmpDir)) {
            mListener.testFailed(testId, "Could not create a modified jar.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        }

        // install the modified jar on the device
        if (!installModifiedJarFile(tmpDir)) {
            mListener.testFailed(testId, "Could not install the modified jar.");
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
            return false;
        }

        mListener.testEnded(testId, Collections.<String, String>emptyMap());
        return true;
    }

    private boolean extractJarFile(File jar, File jarDirectory) {
        try (ZipFile zipFile = new ZipFile(jar)) {
            ZipUtil2.extractZip(zipFile, jarDirectory);
        } catch (ZipException e) {
            CLog.d("Could not instantiate zip file.");
            return false;
        } catch (IOException e) {
            CLog.d("Could not extract original jar file.");
            return false;
        }
        return true;
    }

    private boolean modifyJarVersion(File jarDirectory) {
        try {
            File versionFile = new File(jarDirectory, "version");
            FileUtil.writeToFile("1000", versionFile);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private boolean createModifiedInstallFiles(File jarDirectory) {
        return createModifiedJarFile(jarDirectory) && createMetadataFile(jarDirectory);
    }

    private boolean createModifiedJarFile(File jarDirectory) {
        File zipFile = new File(jarDirectory, SNET_JAR_FILENAME);
        ZipOutputStream out = null;
        try {
            FileOutputStream fileStream = new FileOutputStream(zipFile);
            out = new ZipOutputStream(new BufferedOutputStream(fileStream));
            for (String content : SNET_JAR_CONTENTS) {
                File f = new File(jarDirectory, content);
                ZipUtil.addToZip(out, f, new LinkedList<String>());
            }
        } catch (IOException e) {
            zipFile.delete();
            CLog.d("Could not create new jar file.");
            return false;
        } catch (RuntimeException e) {
            zipFile.delete();
            CLog.d("Could not create new jar file.");
            return false;
        } finally {
            StreamUtil.close(out);
        }
        return true;
    }

    private boolean createMetadataFile(File jarDirectory) {
        File metadata = new File(jarDirectory, "m");
        try {
            metadata.createNewFile();
        } catch (IOException e) {
            CLog.d("Could not create metadata file.");
            return false;
        }
        return true;
    }

    private boolean installModifiedJarFile(File hostJarDirectory)
            throws DeviceNotAvailableException {
        CLog.d("Installing....");
        CLog.d("Removing old device SafetyNet download files.");
        getDevice().executeShellCommand(String.format("rm %s/*", mSnetDownloadDir));

        // set the user and group on install directory
        CLog.d("Setting installation directory owner to %s.", getSnetUser());
        getDevice().executeShellCommand(
                String.format("chown %s.%s %s", getSnetUser(), getSnetUser(), mSnetInstallDir));

        // push SafetyNet jar installation files
        CLog.d("Pushing installation files.");
        File localMetadata = new File(hostJarDirectory, "m");
        File localJar = new File(hostJarDirectory, SNET_JAR_FILENAME);

        boolean metadataPush = getDevice().pushFile(localMetadata,
                String.format("%s/m", mSnetInstallDir));
        boolean jarPush = getDevice().pushFile(localJar,
                String.format("%s/snet.jar", mSnetInstallDir));

        if (!metadataPush || !jarPush) {
            if (!metadataPush) {
                CLog.d("Could not push metadata file to the device.");
            }
            if (!jarPush) {
                CLog.d("Could not push jar file to the device.");
            }
            return false;
        }

        // set correct owners for the installation files
        CLog.d("Setting the owner of the installation files to %s.", getSnetUser());
        getDevice().executeShellCommand(
                String.format("chown %s.%s %s/m", getSnetUser(), getSnetUser(), mSnetInstallDir));
        getDevice().executeShellCommand(
                String.format("chown %s.%s %s/snet.jar", getSnetUser(), getSnetUser(),
                mSnetInstallDir));

        // delete any existing snet dalvik-cache
        CLog.d("Deleting old dalvik-cache.");
        getDevice().executeShellCommand(
                String.format("rm -f %s/../dalvik-cache/snet.dex", mSnetInstallDir));
        getDevice().executeShellCommand(
                String.format("rm -rf %s/../dalvik-cache", mSnetInstallDir));

        // install
        getDevice().executeShellCommand(
                String.format("mv %s/m %s/metadata", mSnetInstallDir, mSnetInstallDir));

        // compute and compare digests for the install
        String localMetadataDigest = null;
        String localJarDigest = null;
        try {
            localMetadataDigest = FileUtil.calculateMd5(localMetadata);
            CLog.d("Local metadata digest: %s", localMetadataDigest);
        } catch (IOException e) {
            CLog.d("IOException while calculating Md5 for local metadata.");
        }
        try {
            localJarDigest = FileUtil.calculateMd5(localJar);
            CLog.d("Local jar digest: %s", localJarDigest);
        } catch (IOException e) {
            CLog.d("IOException while calculating Md5 for local build jar.");
        }

        String deviceMetadataDigest = remoteMd5(String.format("%s/metadata", mSnetInstallDir));
        String deviceJarDigest = remoteMd5(String.format("%s/snet.jar", mSnetInstallDir));

        CLog.d("Device metadata digest: %s", deviceMetadataDigest);
        CLog.d("Device jar digest: %s", deviceJarDigest);

        // assume that the digests match if digests cannot be computed
        boolean digestsMatch = true;

        if (((localMetadataDigest != null) && !localMetadataDigest.isEmpty()) &&
                ((deviceMetadataDigest != null) && !deviceMetadataDigest.isEmpty())) {
            if (!deviceMetadataDigest.toLowerCase().contains(localMetadataDigest.toLowerCase())) {
                CLog.d("Metadata digests do not match!");
                digestsMatch = false;
            } else {
                CLog.d("Metadata digests match.");
            }
        }

        if (((localJarDigest != null) && !localJarDigest.isEmpty()) &&
                ((deviceJarDigest != null) && !deviceJarDigest.isEmpty())) {
            if (!deviceJarDigest.toLowerCase().contains(localJarDigest.toLowerCase())) {
                CLog.d("Jar digests do not match!");
                digestsMatch = false;
            } else {
                CLog.d("Jar digests match.");
            }
        }

        if (digestsMatch) {
            CLog.d("Installed.");
        }

        return digestsMatch;
    }

    /**
     * Checks whether any Snet data files were generated
     *
     */
    public void snetDataCollectionTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                "dataCollectedTest");
        mListener.testStarted(testId);
        try {
            String lsResult = getDevice().executeShellCommand(
                    String.format("ls %s", SNET_DATA_FILES));
            if ((lsResult == null) || lsResult.equals("")) {
                mListener.testFailed(testId, "Did not find any Snet data files.");
            } else {
                CLog.d("ls returned:\n%s", lsResult);
                boolean foundSomething = false;
                for (String result : lsResult.split("\n")) {
                    Long timestamp = extractSnetDataFileTimestamp(result);
                    if (timestamp == null) {
                        continue;
                    }

                    CLog.d("Found snet data: %s", result);

                    // add the system time offset to allow for comparison against device time
                    Date dataDate = new Date(timestamp.longValue() + mTimeOffset);
                    CLog.d("Snet data time: %s", DATE_FORMAT.format(dataDate));

                    if (dataDate.after(mTestStartTime)) {
                        foundSomething = true;
                        CLog.d("Data timing matches. Accepting.");
                    } else {
                        CLog.d("Data timing doesn't match.");
                        if (mPriorSnetDataDeleted) {
                            foundSomething = true;
                            CLog.d("Prior data was deleted. Accepting.");
                        } else {
                            CLog.d("Prior data was not deleted. Skipping.");
                        }
                    }
                }
                if (!foundSomething) {
                    mListener.testFailed(testId, "Snet run did not collect any data.");
                }
            }
        } catch (DeviceNotAvailableException e) {
            mListener.testFailed(testId, "Could not execute ls command on device.");
            throw e;
        } finally {
            mListener.testEnded(testId, Collections.<String, String>emptyMap());
        }
    }

    /**
     * Deletes data files generated by snet.
     *
     * @return True if snet data deleted successfully
     * @throws DeviceNotAvailableException
     */
    private boolean deleteSnetData() throws DeviceNotAvailableException {
       CLog.d("Deleting snet dropbox data...");
       getDevice().executeShellCommand(String.format("rm -f %s", SNET_DATA_FILES));
       getDevice().executeShellCommand(String.format("rm -f %s", SNET_GCORE_DATA_FILES));

       // only need to check if any actual data files remain
       String dataResult = getDevice().executeShellCommand(
                    String.format("ls %s", SNET_DATA_FILES));
       dataResult = (dataResult == null) ? "" : dataResult.replaceAll("(\\r|\\n)", "");

       if (dataResult.equals("") || dataResult.contains("No such file or directory")) {
           CLog.d("All snet dropbox data deleted.");
           return true;
       }

       CLog.d("Snet data remaining.");
       return false;
    }

    /**
     * Forces a checkin and checks whether it was performed successfully
     *
     */
    public void forceCheckinTest() throws DeviceNotAvailableException {
        TestIdentifier testId = new TestIdentifier(EndToEndSnetTest.class.getName(),
                "forceCheckinTest");
        mListener.testStarted(testId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> callable = new Callable<String>() {
            @Override
            public String call() throws DeviceNotAvailableException {
                String command = String.format("am instrument -e wait %d -w %s",
                        CHECKIN_INST_TIMEOUT_S, FULL_CHECKIN_INST_NAME);
                return getDevice().executeShellCommand(command);
            }
        };
        Future<String> future = executor.submit(callable);

        // force checkin here
        forceCheckin();

        String checkinResult;
        try {
            checkinResult = future.get(CHECKIN_INST_TIMEOUT_S + CHECKIN_INST_TIMEOUT_DELTA_S,
                    TimeUnit.SECONDS);
            CLog.d("Checkin result: %s", checkinResult);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            checkinResult = null;
            CLog.d("Exception while obtaining the checkin instrumentation result.");
        }
        executor.shutdown();

        if ((checkinResult == null) || !checkinResult.contains("success")) {
            mListener.testFailed(testId, "Could not checkin snet logs.");
        }

        mListener.testEnded(testId, Collections.<String, String>emptyMap());
    }

    /**
     * Takes a file path for a Snet data file and returns the corresponding
     * timestamp. If the formatting does not match returns null.
     *
     * @param filePath the file path of the Snet data file
     * @return timestamp if the formatting matches otherwise null.
     */
    private Long extractSnetDataFileTimestamp(String filePath) {
        if ((filePath == null) || filePath.equals("")) {
            return null;
        }
        String[] atSplit = filePath.split("@");
        if ((atSplit == null) || atSplit.length != 2) {
            return null;
        }
        String[] dotSplit = atSplit[1].split(Pattern.quote("."));
        if ((dotSplit == null) || dotSplit.length != 2) {
            return null;
        }
        try {
            return Long.parseLong(dotSplit[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    /**
     * Waits for Snet Hello message to occur, based on searching logcat for expectedMsg to appear in
     * any Snet log entry that occurs after the startTime.
     *
     * @param expectedMsg the message that Snet is supposed to say upon update
     * @param startTime the earliest place in the logcat that expectedMsg should appear
     */
    private void waitForSnetMessage(String expectedMsg, Date startTime) {
        final long TIMEOUT_MS = 10 * SNET_WAKE_SHORT_INTERVAL_MS;
        long endTime = System.currentTimeMillis() + TIMEOUT_MS;
        CLog.d("Waiting up to %s ms for Snet message...", TIMEOUT_MS);
        while (System.currentTimeMillis() < endTime) {
            if (didSnetLogMsg(expectedMsg, startTime)) {
                CLog.d("Snet message received.");
                return;
            }
            RunUtil.getDefault().sleep(ONE_SEC_MS);
        }
        CLog.d("Snet message timed out.");
    }

    /**
     * Asserts that Snet is running, throwing a TestFailedException if it's not.
     *
     * @throws TestFailedException
     */
    private void assertSnetIsRunning() throws DeviceNotAvailableException, TestFailedException {
        CLog.d("Checking to see if Snet is running...");
        if (!isSnetRunning()) {
            throw new TestFailedException("Snet is not running.");
        } else {
            CLog.d("Snet is running.");
        }
    }

    /**
     * Asserts that Snet said the expectedMsg anywhere after startTime in logcat, throwing a
     * TestFailedException if it didn't match.
     *
     * @param expectedMsg the message you expected Snet to say in logcat
     * @param startTime the earliest place in logcat that you expect the msg to appear
     * @throws TestFailedException
     */
    private void assertSnetSaid(String expectedMsg, Date startTime)
            throws TestFailedException {
        CLog.d("Checking for message '%s'...", expectedMsg);
        if (!didSnetLogMsg(expectedMsg, startTime)) {
            CLog.d("Snet did not log message.");
            throw new TestFailedException(String.format(
                    "Snet did not say '%s' after %s during test execution",
                    expectedMsg, startTime));
        } else {
            CLog.d("Expected '%s' message verified.", expectedMsg);
        }
    }

    /**
     * Asserts that Snet did NOT say the notExpectedMsg anywhere after startTime in logcat,
     * fails the test if it was said.
     *
     * @param notExpectedMsg the message you expect Snet to NOT say in logcat
     * @param startTime the earliest place in logcat that you expect the msg to not appear
     * @throws TestFailedException
     */
    private void assertSnetNotSaid(String notExpectedMsg, Date startTime)
            throws TestFailedException {
        if (didSnetLogMsg(notExpectedMsg, startTime)) {
            throw new TestFailedException(String.format(
                    "Snet said '%s' after %s during test execution",
                    notExpectedMsg, startTime));
        }
    }

    /**
     * Asserts that GmsCore had no crashes appear in the logs anytime after startTime,
     * fails the test if there were any.
     *
     * @param startTime the earliest place in logcat that you expect there to be no Gms crashes
     * @throws TestFailedException
     */
    private void assertNoGcoreCrashesAfter(Date startTime) throws TestFailedException {
        CLog.d("Checking for GMS Core crashes...");
        if (hasGmsCoreCrashAfter(startTime)) {
            throw new TestFailedException(
                    "A GmsCore crash occurred during testing - see host logs for more details.");
        } else {
            CLog.d("No GMS Core crashes detected.");
        }
    }

    /**
     * Verifies whether the Snet process is currently running on the device.
     *
     * @return True if running, false otherwise.
     * @throws DeviceNotAvailableException
     */
    private boolean isSnetRunning() throws DeviceNotAvailableException {
        String result = getDevice().executeShellCommand(
                "su -c ps | grep com.google.android.gms:snet");
        if ((result != null) && (result.length() > 0)) {
            return true;
        }
        return false;
    }

    /**
     * Verifies whether Snet logged the expected message sometime after startTime.
     *
     * @param msg The message that's expected to appear in the logs.
     * @param startTime Only look at messages that occurred after startTime.
     * @return True if Snet said the expected message after startTime, false otherwise.
     */
    private boolean didSnetLogMsg(String msg, Date startTime) {
        LogcatItem logcat = getLogcatItem(SAFETY_NET_LOGCAT_TAG_NAME, SAFETY_NET_CATEGORY_NAME);
        List<MiscLogcatItem> snetLogs = logcat.getMiscEvents(SAFETY_NET_CATEGORY_NAME);
        if ((snetLogs == null) || (snetLogs.size() <= 0)) {
            return false;
        }
        for (MiscLogcatItem snetLog : snetLogs) {
            CLog.d("Logcat Tag: %s Event time: %s", snetLog.getTag(),
                    DATE_FORMAT.format(snetLog.getEventTime()));
            if (snetLog.getEventTime().after(startTime)) {
                if (snetLog.getStack().contains(msg)) {
                    return true;
                }
            } else {
                CLog.d("Message arrived before the start time.");
            }
        }
        return false;
    }

    /**
     * Verifies whether there were any crashes with GmsCore after the specified device timestamp.
     *
     * @param startTime Device timestamp - only look for GmsCore crashes occurring after this.
     * @return True if a GmsCore crash occurred after the specified time, false otherwise.
     */
    private boolean hasGmsCoreCrashAfter(Date startTime) {
        final String GMS_PACKAGE = "com.google.android.gms";
        LogcatItem logcat = getLogcatItem();
        List<JavaCrashItem> crashes = logcat.getJavaCrashes();
        for (JavaCrashItem crash : crashes) {
            if (crash.getEventTime().after(startTime) && crash.getApp() != null &&
                    crash.getApp().contains(GMS_PACKAGE)) {
                CLog.i("%s in %s occurred at %s (device-time) during testing:\n%s",
                        crash.getException(), crash.getApp(), crash.getEventTime(),
                        crash.getStack());
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the specified directory (recursive) on the device.
     *
     * @param dirPath directory path to remove
     * @throws DeviceNotAvailableException
     */
    private void rmDeviceDir(String dirPath) throws DeviceNotAvailableException {
        String cmd = String.format("rm -r %s", dirPath);
        getDevice().executeShellCommand(cmd);
    }

    /**
     * Compute the MD5 digest of a file on the device.
     *
     * @return string also containing the MD5 digest or null if digest cannot be computed
     * @param path the file path on the device
     * @throws DeviceNotAvailableException
     */
    private String remoteMd5(String path) throws DeviceNotAvailableException {
        String cmd = String.format("am instrument -e target %s -w %s", path,
                FULL_COMPUTE_DIGEST_INST_NAME);
        String result = getDevice().executeShellCommand(cmd);

        // if the instrumentation successfully computes a MD5 return it
        if ((result != null) && !result.isEmpty() && !result.contains("Could not compute") &&
                result.contains("digest")) {
            CLog.d("MD5 successfully computed using instrumentation: %s", result);
            return result;
        }

        CLog.d("Could not compute MD5 using instrumentation. Falling back on md5 shell command.");

        String remoteMd5Exists = getDevice().executeShellCommand("type md5");

        if ((remoteMd5Exists == null) || remoteMd5Exists.contains("not found")) {
            CLog.d("Remote md5 tool doesn't exist. Skipping digest comparison.");
            return null;
        }

        return getDevice().executeShellCommand(String.format("md5 %s", path));
    }

    /**
     * Sets the specified GServices Override key to the specified value.
     *
     * @param key Name of the key to be set.
     * @param value Value to be set.
     * @throws DeviceNotAvailableException
     */
    private void setGServicesOverride(String key, String value)
            throws DeviceNotAvailableException {
        String cmd = String.format("am broadcast -a %s -e '%s' '%s'",
                GSERVICES_OVERRIDE_PKG, key, value);
        getDevice().executeShellCommand(cmd);
    }

    /**
     * Resets the specified GServices Override key back to default.
     *
     * @param key Name of the key to reset.
     * @throws DeviceNotAvailableException
     */
    private void resetGServicesOverride(String key) throws DeviceNotAvailableException {
        String cmd = String.format("am broadcast -a %s --esn '%s'", GSERVICES_OVERRIDE_PKG, key);
        getDevice().executeShellCommand(cmd);
    }

    /**
     * Forces the Checkin upload to take place immediately.
     *
     * @throws DeviceNotAvailableException
     */
    private void forceCheckin() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("am broadcast -a android.server.checkin.CHECKIN");
    }

    /**
     * Get the most recent logcat entry's timestamp.
     *
     * @return the device's current time
     * @throws DeviceNotAvailableException
     */
    private Date getLatestLogcatTimestamp() throws DeviceNotAvailableException {
        LogcatItem logcat = getLogcatItem();
        Assert.assertNotNull("logcat is null", logcat);
        Date stopTime = logcat.getStopTime();
        Assert.assertNotNull("logcat stopTime is null", stopTime);
        Assert.assertTrue("logcat stopTime is empty", stopTime.getTime() > 0);
        return stopTime;
    }

    /**
     * Gets a vanilla LogcatItem from the device's logcat stream
     * without using a specific tag or category.
     *
     * @return the parsed LogcatItem
     */
    private LogcatItem getLogcatItem() {
        return getLogcatItem(null, null);
    }

    /**
     * Gets a parsed LogcatItem from the device's logcat stream.
     *
     * @param tag Logcat Tag of interest
     * @param category Proposed category name to batch logs
     * @return the parsed LogcatItem
     */
    private LogcatItem getLogcatItem(String tag, String category) {
        
        LogcatItem logcat = null;
        try (InputStreamSource logcatSource = getDevice().getLogcat()) {
            LogcatParser parser = new LogcatParser();
            parser.addPattern(null, null, tag, category);
            logcat = parser.parse(new BufferedReader(new InputStreamReader(
                    logcatSource.createInputStream())));
        } catch (IOException e) {
            CLog.e(String.format("Failed to fetch and parse bugreport for device %s: %s",
                    getDevice().getSerialNumber(), e));
        }
        Assert.assertNotNull("logcat logs are null", logcat);
        return logcat;
    }
}
