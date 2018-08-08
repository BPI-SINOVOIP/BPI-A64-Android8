// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.frameworks;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Automated test for Contact Aggregator. This test class will push a test database to device,
 * and check if the result is expected after Contact Aggregator is
 * done. Test database and expected result file should be provided.
 * ContactsProvider.apk should have been installed to device when flash happened.
 *
 * Run the test under the directory where tradefed.sh is. The command is like this:
 * run google/framework/contact-aggregator
 * --aggregation-test-database /tmp/contacts_aggregation_test.db
 * --expected-aggregation-result /tmp/expected_aggregation_result.txt
 *
 * The expected aggregation result file contents should be like this:
 * Each line contains one or more raw contact ids that belong to one contact,
 * using ',' to split raw contact ids.
 */
@OptionClass(alias = "contact-aggregator-test")
public class ContactAggregatorTest implements IDeviceTest, IRemoteTest {

    private ITestDevice mDevice;

    private static final String CONTACT_RPOVIDER_DIR = "/data/data/com.android.providers.contacts";
    private static final String CONTACT_PROVIDER_FILE = CONTACT_RPOVIDER_DIR + "/files/";
    private static final String PROFILE_DATABASE_PATH =
            CONTACT_RPOVIDER_DIR + "/databases/profile.db";
    private static final String CONTACT_DATABASE_PATH =
            CONTACT_RPOVIDER_DIR + "/databases/contacts2.db";

    @Option(name = "aggregation-test-database",
            description = "The test contact database used to be aggregated.")
    private String contactTestDatabasePath = null;

    @Option(name = "expected-aggregation-result",
            description = "The expected result is used to check if aggregation result is expected.")
    private String expectedAggregationResultPath = null;

    @Option(name = "sleep-time", description = "Sleep time when waiting for aggregation.")
    private long sleepTime = 1000;

    @Option(name = "max-try-times", description = "Maximum times to check if aggregation is done.")
    private int maxTryTimes = 100;

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
        if (contactTestDatabasePath == null || expectedAggregationResultPath == null) {
            throw new IllegalStateException("Please set both of these parameters: " +
                    "aggregation_test_database, expected_aggregation_result.");
        }
        ITestDevice device = getDevice();

        // Push contact test db to the device.
        device.executeShellCommand("setprop debug.contacts.ksad 1");
        device.executeShellCommand("stop");
        device.executeShellCommand(String.format("rm -r \"%s\"*", CONTACT_PROVIDER_FILE));
        device.executeShellCommand(String.format("rm \"%s\"", PROFILE_DATABASE_PATH));
        device.pushFile(new File(contactTestDatabasePath), CONTACT_DATABASE_PATH);

        String selectVersionCommand = String.format("sqlite3 \'%s\' " +
                        "\" SELECT property_value FROM properties WHERE property_key = " +
                        "\'aggregation_v2\';\"",
                CONTACT_DATABASE_PATH);
        String beforeAggVersion = device.executeShellCommand(selectVersionCommand).trim();
        CLog.i(String.format("Database version is %s before aggregation.", beforeAggVersion));

        // Take effect.
        device.executeShellCommand("start");
        device.waitForDeviceAvailable();

        String databaseVersion = "";
        int tryTimes= 0;
        do {
            tryTimes++;
            databaseVersion = device.executeShellCommand(selectVersionCommand).trim();
            RunUtil.getDefault().sleep(sleepTime);
        } while(databaseVersion.equals(beforeAggVersion) && tryTimes < maxTryTimes);

        String errorMsg = "Aggregator is not triggered, and the database version is still "
                + databaseVersion;
        Assert.assertFalse(errorMsg, databaseVersion.equals(beforeAggVersion));

        // Pull result database out and store it to Log.
        device.executeShellCommand("stop");
        File resultDatabaseFile = device.pullFile(CONTACT_DATABASE_PATH);
        iTestInvocationListener.testLog("contact_aggregation_result.db",
                LogDataType.UNKNOWN, new FileInputStreamSource(resultDatabaseFile));
        device.executeShellCommand("start");

        // Check if the result is expected.
        parseTestResult(iTestInvocationListener, CONTACT_DATABASE_PATH);
    }

    /**
     * Parse aggregate result from local database in device.
     */
    private void parseTestResult(ITestInvocationListener listener, String mContactDatabasePath)
            throws DeviceNotAvailableException {
        String selectCommand = String.format(
                "sqlite3 \'%s\' \"SELECT contact_id, _id FROM raw_contacts ORDER BY contact_id;\"",
                mContactDatabasePath);
        String output = getDevice().executeShellCommand(selectCommand);
        String[] results = output.split("\\n");
        Map<Integer, Set<Integer>> actualResult = Maps.newHashMap();
        for (String result : results) {
            String[] row = result.trim().split("\\|");
            if (row.length == 2) {
                int contactId = Integer.parseInt(row[0]);
                int rawContactId = Integer.parseInt(row[1]);
                if (actualResult.containsKey(contactId)) {
                    actualResult.get(contactId).add(rawContactId);
                } else {
                    actualResult.put(contactId, Sets.newHashSet(rawContactId));
                }
            }
        }

        BufferedReader reader = null;
        Collection<Set<Integer>> expectedResult = Sets.newHashSet();
        try {
            reader = new BufferedReader(new FileReader(new File(expectedAggregationResultPath)));
            String line = reader.readLine();
            while (line != null) {
                String[] rawContactIds = line.split(",");
                Set<Integer> rawContactIdsSet = Sets.newHashSet();
                for (String rawContactIdString : rawContactIds) {
                    rawContactIdsSet.add(Integer.parseInt(rawContactIdString));
                }
                expectedResult.add(rawContactIdsSet);
                line = reader.readLine();
            }
        } catch (IOException e) {
            CLog.e("IOException when reading expected aggregation result file");
            CLog.e(e);
        } finally {
            StreamUtil.close(reader);
        }

        if (compareTwoResults(actualResult.values(), expectedResult)) {
            CLog.i("Test Passed, and the result is: " + actualResult.values());
        } else {
            String failMsg = String.format("Test Failed. Actual result is: %s, and the expected is: %s.",
                    actualResult.values(), expectedResult);
            CLog.e(failMsg);
            listener.testRunFailed(failMsg);
        }
    }

    /**
     * Returns true if result1 and result2 contain exactly same contents.
     */
    private boolean compareTwoResults(Collection<Set<Integer>> result1,
                                      Collection<Set<Integer>> result2) {
        if (result1.size() != result2.size()) {
            return false;
        }
        for (Set<Integer> item : result1) {
            if (!result2.contains(item)) {
                return false;
            }
        }
        return true;
    }
}
