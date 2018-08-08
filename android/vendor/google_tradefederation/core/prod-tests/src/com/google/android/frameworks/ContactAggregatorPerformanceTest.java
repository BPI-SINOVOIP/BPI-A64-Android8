// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.frameworks;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;

import java.io.File;

/**
 * Performance test for Contact aggregator.
 * This test is used to test the spent time to aggregate contacts from a given test database.
 * The test database should be put in /tmp/contacts2.db. If you put your db in other places,
 * then you should also set aggregation-perf-test-db to your local db path.
 * wait-time (ms) is set as the waiting time to check if aggregation finished.
 * check-times is the maximum times to check if aggregation finished.
 *
 * Run the test under the directory where tradefed.sh is, and the command is:
 * run google/framework/contact-aggregator-performance
 * --aggregation-perf-test-db /tmp/contacts2.db --wait-time 2000 --check-times 2
 *
 * Ignore the option settings if you don't need to reset them.
 *
 */
@OptionClass(alias = "contact-aggregator-performance-test")
public class ContactAggregatorPerformanceTest implements IDeviceTest, IRemoteTest {
    private ITestDevice mDevice;

    private static final String CONTACT_RPOVIDER_DIR = "/data/data/com.android.providers.contacts";
    private static final String CONTACT_PROVIDER_FILE = CONTACT_RPOVIDER_DIR + "/files/";
    private static final String PROFILE_DATABASE_PATH =
            CONTACT_RPOVIDER_DIR + "/databases/profile.db";
    private static final String CONTACT_DATABASE_PATH =
            CONTACT_RPOVIDER_DIR + "/databases/contacts2.db";
    private static final String CLASS_TAG = "ContactsProvider";
    private static final String SEPARATOR = "------------------------------------------";
    private static final String AGGREGATION_LOG_PREFIX = "Aggregation algorithm upgraded for";

    @Option(name = "aggregation-perf-test-db",
            description = "The test contact database used to test aggregation performance.")
    private String contactTestDatabasePath = "/tmp/contacts2.db";

    @Option(name = "wait-time", description = "Time (ms) to wait for aggregation finished.")
    private long waitTime = 2000;

    @Option(name = "check-times", description = "maximum times to check if aggregation finished.")
    private long checkTimes = 2;

    @Override
    public void setDevice(ITestDevice iTestDevice) {
        mDevice = iTestDevice;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void run(ITestInvocationListener iTestInvocationListener)
            throws DeviceNotAvailableException {
        mDevice.executeShellCommand("setprop debug.contacts.ksad 1");
        // Clear log.
        mDevice.executeAdbCommand("logcat", "-c");
        mDevice.executeShellCommand("stop");
        // Push contact test db to the device.
        mDevice.executeShellCommand(String.format("rm -r \"%s\"*", CONTACT_PROVIDER_FILE));
        mDevice.executeShellCommand(String.format("rm \"%s\"", PROFILE_DATABASE_PATH));
        mDevice.pushFile(new File(contactTestDatabasePath), CONTACT_DATABASE_PATH);

        // Take effect.
        mDevice.executeShellCommand("start");
        mDevice.waitForDeviceAvailable();

        int i = 0;
        String performanceLog = null;
        while (i < checkTimes) {
            i++;
            // Wait for aggregation finished.
            RunUtil.getDefault().sleep(waitTime);
            // Read log from device.
            String logcat = mDevice.executeAdbCommand("logcat", "-s", "-d", CLASS_TAG + ":I");
            // Get aggregation time from log.
            performanceLog = fetchTimeFromLog(logcat);
            if (performanceLog != null) {
                CLog.i(SEPARATOR);
                CLog.i("Performance is: " + performanceLog);
                CLog.i(SEPARATOR);
                break;
            }
            mDevice.executeAdbCommand("logcat", "-c");
        }
        if (performanceLog == null) {
            CLog.e(SEPARATOR);
            CLog.e("Didn't find aggregation information, please try again.");
            CLog.e(SEPARATOR);
        }
    }

    private String fetchTimeFromLog(String log) {
        String[] rows = log.split("\r\n");
        for (String row : rows) {
            if (row.contains(AGGREGATION_LOG_PREFIX)) {
                return row;
            }
        }
        return null;
    }
}
