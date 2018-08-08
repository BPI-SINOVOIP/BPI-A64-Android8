// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.cloudsync.music.tests;

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.BugreportCollector;
import com.android.tradefed.result.BugreportCollector.Freq;
import com.android.tradefed.result.BugreportCollector.Noun;
import com.android.tradefed.result.BugreportCollector.Relation;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CloudMusicSyncTests implements IDeviceTest, IRemoteTest {

    @Option(name = "email", description = "Email must already exist on device."
        + " Email of account to activate for music sync")
    private String mEmail;

    @Option(name = "password", description = "Account password. Required for accessing the "
        + "cloud locker portion of the tests")
    private String mPassword;

    @Option(name = "skyjamBaseUrl", description = "Points to google-apis for skyjam. Required "
        + " for access to user's locker")
    private String mSkyjamBaseUrl;

    ITestDevice mTestDevice = null;
    private static final String METRICS_NAME = "musicsync";
    private static final String PACKAGE_NAME = "com.google.android.music.tests";

    private static final int SECONDS = 1000;
    private static final int MINUTES = 60 * SECONDS;

    private enum Schema {
        CLOUD_MUSIC_SYNC("music_metadata_sync"),
        CLOUD_PLAYLIST_SYNC("playlists_metadata_sync"),
        DEVICE_PLAYLIST_SYNC("device_playlist_sync"),
        OFFLINE_CLOUD_MUSIC("offline_cloud_music");

        private String mField;

        Schema(String f) {
            mField = f;
        }

        public String ruKey() {
            return mField;
        }
    }

    @Override
    public void setDevice(ITestDevice itd) {
        mTestDevice = itd;
    }

    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        HashMap<String, String> results = new HashMap<String, String>();

        // tests
        cloudMusicSyncTests(listener, results);
        cloudPlaylistsSyncTests(listener, results);
        devicePlaylistsSyncTests(listener, results);
        offlineMusicSyncTests(listener, results);

        reportMetrics(listener, results);
    }

    /**
     * Launch cloud music sync tests which are entirely performed on device. The
     * tests will query the device and the corresponding cloud locker for music
     * items. The tests will perform validation on the each items metadata such as
     * title, album, track number, year, genre etc...
     *
     * @throws DeviceNotAvailableException
     */
    private boolean cloudMusicSyncTests(ITestInvocationListener listener,
            Map<String, String> results) throws DeviceNotAvailableException {

        final String className =
            "com.google.android.music.functional.cloudsync.CloudMusicSyncTest";
        final String testName = "CloudMusicSyncTest";

        String cmd =
            "am instrument -e url \"" + mSkyjamBaseUrl + "\" -e account \"" + mEmail + "\""
            + " -e password \"" + mPassword + "\" -w " + PACKAGE_NAME + "/" + className;

        return executeAndLog(listener, testName, className, cmd,
                Schema.CLOUD_MUSIC_SYNC.ruKey(), results);
    }

    /**
     * Launch cloud playlists sync tests which are entirely performed on device.
     * The tests will query the device and the corresponding cloud locker for
     * music playlists and their list items. The tests will perform validation on
     * each playlist name and the songs it lists.
     *
     * @throws DeviceNotAvailableException
     */
    private boolean cloudPlaylistsSyncTests(ITestInvocationListener listener,
            Map<String, String> results) throws DeviceNotAvailableException {

        final String className =
            "com.google.android.music.functional.cloudsync.CloudPlaylistsSyncTest";
        final String testName = "CloudPlaylistsSyncTest";

        String cmd =
            "am instrument -e url \"" + mSkyjamBaseUrl + "\" -e account \"" + mEmail + "\""
            + " -e password \"" + mPassword + "\" -w " + PACKAGE_NAME + "/" + className;

        return executeAndLog(listener, testName, className, cmd,
                Schema.CLOUD_PLAYLIST_SYNC.ruKey(), results);
    }

    /**
     * Launch device playlists to cloud sync tests which are entirely performed on
     * device. The tests will: 1 - Create playlist on the device and then connect
     * to the corresponding cloud locker to check if the newly created playlists
     * on the device have been correctly sync'd to the locker. The tests validate
     * that created playlist and all items each lists are now visible in the
     * locker. 2 - Delete the newly created playlist and verify that this is
     * reflected in cloud locker.
     *
     * @throws DeviceNotAvailableException
     */
    private boolean devicePlaylistsSyncTests(ITestInvocationListener listener,
            Map<String, String> results) throws DeviceNotAvailableException {

        final String className =
            "com.google.android.music.functional.cloudsync.DevicePlaylistsSyncTest";
        final String testName = "DevicePlaylistsSyncTest";

        String cmd =
            "am instrument -e url \"" + mSkyjamBaseUrl + "\" -e account \"" + mEmail + "\""
            + " -e password \"" + mPassword + "\" -w " + PACKAGE_NAME + "/" + className;

        return executeAndLog(listener, testName, className, cmd,
                Schema.DEVICE_PLAYLIST_SYNC.ruKey(), results);
    }

    /**
     * Launch offline music pinning tests. Assuming the device is syncing
     * correctly with the cloud, the tests will select an Album and pin the album
     * and all its music to be available for offline listening. The tests will
     * then monitor the download from the cloud and verify files exist in local
     * storage.
     *
     *  The tests will unpin the downloaded album and monitors that all album
     * music is unpinned as well and that the local files in the filesystem have
     * been successfuly removed from the device.
     *
     * @throws DeviceNotAvailableException
     */
    private boolean offlineMusicSyncTests(ITestInvocationListener listener,
            Map<String, String> results) throws DeviceNotAvailableException {

        final String className =
            "com.google.android.music.functional.cloudsync.OfflineMusicTest";
        final String testName = "OfflineMusicTest";

        String cmd = "am instrument -w " + PACKAGE_NAME + "/" + className;

        return executeAndLog(listener, testName, className, cmd,
                Schema.OFFLINE_CLOUD_MUSIC.ruKey(), results);
    }

    /**
     * Helper to perform standard test invocation. This invokes device tests that
     * implements android.app.Instrumentation
     * @param listener
     * @param testName
     * @param className
     * @param testCmd
     * @return true for passed else false
     * @throws DeviceNotAvailableException
     */
    private boolean executeAndLog(ITestInvocationListener listener, String testName,
            String className, String testCmd, String ruKey, Map<String, String> results)
            throws DeviceNotAvailableException {
        CLog.i("Launching %s ...", testName);
        TestIdentifier ti = new TestIdentifier(testName, className);

        BugreportCollector bugListener = new BugreportCollector(listener, mTestDevice);
        bugListener.addPredicate(
                new BugreportCollector.Predicate(Relation.AFTER, Freq.EACH, Noun.TESTRUN));

        bugListener.testStarted(ti);
        BooleanResultReader resultReader = new BooleanResultReader();
        mTestDevice.executeShellCommand(testCmd, resultReader,
                15 * MINUTES, TimeUnit.MILLISECONDS, 2);

        if(resultReader.getResult()) {
            results.put(ruKey, "1");
            CLog.i("Finished %s: PASSED", testName);
        } else {
            results.put(ruKey, "0");
            CLog.i("Finished %s: FAILED", testName);
            bugListener.testFailed(ti, "");
        }
        bugListener.testEnded(ti, results);
        return resultReader.getResult();
    }

    /**
     * Report run metrics
     */
    void reportMetrics(ITestInvocationListener listener, Map<String, String> metrics) {
        listener.testRunStarted(METRICS_NAME, 0);
        listener.testRunEnded(0, metrics);
        CLog.i("Reported metrics for %s: %s", METRICS_NAME, metrics);
    }

    /**
     * Results parser helper class
     */
    private class BooleanResultReader extends MultiLineReceiver {
        private boolean mResult = false;

        public boolean getResult() {
            return mResult;
        }

        @Override
        public void processNewLines(String[] strings) {
            if (strings.length > 0 && strings[0].contains("CODE: -1")) {
                mResult = true;
            }
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
