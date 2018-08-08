// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.smartmonkey;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.MonkeyLogItem;
import com.android.loganalysis.item.SmartMonkeyLogItem;
import com.android.loganalysis.parser.SmartMonkeyLogParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.DeviceFileReporter;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRetriableTest;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runner for stress tests which use the monkey command.
 */
public class SmartMonkeyMain implements IDeviceTest, IRemoteTest, IRetriableTest {

    public static final String MONKEY_LOG_NAME = "smartmonkey_log";
    public static final String BUGREPORT_NAME = "bugreport";

    /**
     * Allow a 15 second buffer between the monkey run time and the delta uptime.
     */
    public static final long UPTIME_BUFFER = 15 * 1000;

    private static final String LAUNCH_APP_CMD =
            "uiautomator runtest uiautomator.smartmonkey.jar geppetto.monkey.jar -c %s --monkey";

    @Option(name="test-name", description="Geppetto test name.")
    private String mTestName = null;

    @Option(name="target-invocations", description="Target number of sequence invocations.")
    private int mTargetInvocations = 1000;

    @Option(name="idle-time", description="How long to sleep before running monkey, in secs")
    private int mIdleTimeSecs = 30;

    @Option(name="monkey-timeout", description="How long to wait for the monkey to " +
            "complete, in minutes. Default is 4 hours.")
    private int mMonkeyTimeoutMinutes = 4 * 60;

    @Option(name="upload-file-pattern", description="File glob of on-device files to upload " +
            "if found. Takes two arguments: the glob, and the file type " +
            "(text/xml/zip/gzip/png/unknown).  May be repeated.")
    private Map<String, LogDataType> mUploadFilePatterns = new LinkedHashMap<String, LogDataType>();

    @Option(name="retry-on-failure", description="Retry the test on failure")
    private boolean mRetryOnFailure = false;

    @Option(name = "run-arg", description = "Additional test specific arguments to provide.")
    private Map<String, String> mArgMap = new LinkedHashMap<String, String>();

    private ITestDevice mTestDevice = null;
    private SmartMonkeyLogItem mSmartMonkeyLog = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(getDevice());

        TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), "smartmonkey");
        long startTime = System.currentTimeMillis();

        listener.testRunStarted(getClass().getCanonicalName(), 1);
        listener.testStarted(id);

        addRunArg("events", String.valueOf(mTargetInvocations));

        runMonkey(listener);

        Map<String, String> empty = Collections.emptyMap();
        listener.testEnded(id, empty);
        listener.testRunEnded(System.currentTimeMillis() - startTime, empty);
    }

    /**
     * Run the monkey one time and return a {@link MonkeyLogItem} for the run.
     */
    protected void runMonkey(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mIdleTimeSecs > 0) {
            CLog.i("Sleeping for %d seconds to allow device to settle...", mIdleTimeSecs);
            getRunUtil().sleep(mIdleTimeSecs * 1000);
            CLog.i("Done sleeping.");
        }

        // launch the list of apps that needs warm-up
        StringBuilder command = new StringBuilder();
        command.append(String.format(LAUNCH_APP_CMD, mTestName));

        // for tests that require specific args
        for (Map.Entry<String, String> entry : getTestRunArgMap().entrySet()) {
            command.append(" -e ");
            command.append(entry.getKey());
            command.append(" ");
            command.append(entry.getValue());
        }

        CLog.i("About to run monkey with at %d minute timeout: %s", mMonkeyTimeoutMinutes,
                command.toString());

        StringBuilder outputBuilder = new StringBuilder();
        long duration = 0;

        // Generate the monkey log prefix, which includes the device uptime
        outputBuilder.append(String.format("# %s - device uptime = %s: Monkey command used " +
                "for this test:\nadb shell %s\n\n", new Date().toString(), getUptime(),
                command.toString()));

        takeScreenShot(listener, "on_start");

        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        try {
            long start = System.currentTimeMillis();
            mTestDevice.executeShellCommand(command.toString(), receiver,
                    mMonkeyTimeoutMinutes * 60 * 1000, TimeUnit.MILLISECONDS, 0);
            duration = System.currentTimeMillis() - start;
        } finally {
            outputBuilder.append(receiver.getOutput());
            receiver.cancel();

            // Generate the monkey log suffix, which includes the device uptime.
            outputBuilder.append(String.format("\n# %s - device uptime = %s: Monkey command ran " +
                    "for: %d:%02d (mm:ss)\n", new Date().toString(), getUptime(),
                    duration / 1000 / 60, duration / 1000 % 60));

            // Wait for device to recover if it's not online.  If it hasn't recovered, ignore.
            try {
                mTestDevice.waitForDeviceOnline(2 * 60 * 1000);
            } catch (DeviceNotAvailableException e) {
                CLog.w("Device %s not available after 2 minutes.", mTestDevice.getSerialNumber());
            }
            takeBugreport(listener, BUGREPORT_NAME);
        }

        takeScreenShot(listener, "on_exit");
        mSmartMonkeyLog = createMonkeyLog(listener, MONKEY_LOG_NAME, outputBuilder.toString());
        checkResults();
        uploadTraces(listener);
    }

    /**
     * Takes a snapshot of the current display and adds it to the test results
     */
    protected void takeScreenShot(ITestInvocationListener listener, String prefix) {
        InputStreamSource data = null;
        try {
            data = getDevice().getScreenshot();
            listener.testLog(String.format("%s_screenshot", prefix), LogDataType.PNG, data);
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        } finally {
            StreamUtil.cancel(data);
        }
    }

    /**
     * Works in conjunction with the upload-file-pattern argument to pull specific files
     * off the device.
     */
    protected void uploadTraces(ITestInvocationListener listener) {
        DeviceFileReporter dfr = new DeviceFileReporter(mTestDevice, listener);
        dfr.addPatterns(mUploadFilePatterns);
        try {
            dfr.run();
        } catch (DeviceNotAvailableException e) {
            // Log but don't throw
            CLog.e("Device %s became unresponsive while pulling files",
                    mTestDevice.getSerialNumber());
            CLog.e(e);
        }
    }

    /**
     * Capture a bugreport and send it to a listener.
     */
    protected void takeBugreport(ITestInvocationListener listener, String bugreportName) {
        try (InputStreamSource bugreport = mTestDevice.getBugreport()) {
            listener.testLog(bugreportName, LogDataType.BUGREPORT, bugreport);
        }
    }

    /**
     * Create the monkey log, parse it, and send it to a listener.
     */
    protected SmartMonkeyLogItem createMonkeyLog(ITestInvocationListener listener,
            String monkeyLogName, String log) {

        try (InputStreamSource source = new ByteArrayInputStreamSource(log.getBytes())) {
            listener.testLog(monkeyLogName, LogDataType.TEXT, source);
            return new SmartMonkeyLogParser().parse(new BufferedReader(new InputStreamReader(
                    source.createInputStream())));
        } catch (IOException e) {
            CLog.e("Could not parse smartmonkey log");
            CLog.e(e);
            return null;
        }
    }

    /**
     * Get a {@link String} containing the number seconds since the device was booted.
     * <p>
     * {@code "0.00"} is returned if the device becomes unresponsive. Used in the monkey log prefix
     * and suffix.
     * </p>
     */
    protected String getUptime() {
        try {
            // uptime will typically have a format like "5278.73 1866.80".  Use the first one
            // (which is wall-time)
            return mTestDevice.executeShellCommand("cat /proc/uptime").split(" ")[0];
        } catch (DeviceNotAvailableException e) {
            // Log
            CLog.e("Device %s became unresponsive while getting the uptime.",
                    mTestDevice.getSerialNumber());
            return "0.00";
        }
    }

    /**
     * Perform set subtraction between two {@link Collection} objects.
     * <p>
     * The return value will consist of all of the elements of {@code keep}, excluding the elements
     * that are also in {@code exclude}. Exposed for unit testing.
     * </p>
     *
     * @param keep the minuend in the subtraction
     * @param exclude the subtrahend
     * @return the collection of elements in {@code keep} that are not also in {@code exclude}. If
     * {@code keep} is an ordered {@link Collection}, the remaining elements in the return value
     * will remain in their original order.
     */
    static Collection<String> setSubtract(Collection<String> keep, Collection<String> exclude) {
        if (exclude.isEmpty()) {
            return keep;
        }

        Collection<String> output = new ArrayList<String>(keep);
        output.removeAll(exclude);
        return output;
    }

    /**
     * Get {@link IRunUtil} to use. Exposed for unit testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mTestDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mTestDevice;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code false} if retry-on-failure is not set, if the monkey ran to completion,
     * crashed in an understood way, or if there were no packages to run, {@code true} otherwise.
     */
    @Override
    public boolean isRetriable() {
        return mRetryOnFailure;
    }

    /**
     * Check the results and return if valid or throw an assertion error if not valid.
     */
    private void checkResults() {
        if (!isRetriable()) {
            return;
        }

        Assert.assertNotNull("SmartMonkey log is null", mSmartMonkeyLog);
        Assert.assertNotNull("Start uptime is missing", mSmartMonkeyLog.getStartUptimeDuration());
        Assert.assertNotNull("Stop uptime is missing", mSmartMonkeyLog.getStopUptimeDuration());
        Assert.assertNotNull("Total duration is missing", mSmartMonkeyLog.getTotalDuration());

        long startUptime = mSmartMonkeyLog.getStartUptimeDuration();
        long stopUptime = mSmartMonkeyLog.getStopUptimeDuration();
        long totalDuration = mSmartMonkeyLog.getTotalDuration();

        Assert.assertTrue("Uptime failure",
                stopUptime - startUptime > totalDuration - UPTIME_BUFFER);

        // False count
        Assert.assertFalse("False count", mSmartMonkeyLog.getIsFinished() &&
            mSmartMonkeyLog.getTargetInvocations() - mSmartMonkeyLog.getIntermediateCount() > 100);

        // Monkey finished or crashed, so don't fail
        if (mSmartMonkeyLog.getIsFinished() || mSmartMonkeyLog.getFinalCount() > 0) {
            return;
        }

        // Missing count
        Assert.fail("Missing count");
    }

    /**
     * @return the arguments map to pass to the TestRunner.
     */
    public Map<String, String> getTestRunArgMap() {
        return mArgMap;
    }

    /**
     * @param runArgMap the arguments to pass to the TestRunner.
     */
    public void setTestRunArgMap(Map<String, String> runArgMap) {
        this.mArgMap = runArgMap;
    }

    /**
     * Add an argument to provide when running the SmartMonkey tests
     *
     * @param key the argument name
     * @param value the argument value
     */
    public void addRunArg(String key, String value) {
        getTestRunArgMap().put(key, value);
    }
}
