// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.clockwork.wifi;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import com.google.android.clockwork.ConnectivityHelper;
import com.google.android.tradefed.testtype.ClockworkTest;

import java.util.Collections;
import java.util.ArrayList;

/**
 * A {@link ClockworkTest} fast test verifies the SSID saved in companion phone is the same as the
 * one from watch
 */
@OptionClass(alias = "cw-fast-wifi-ssid")
public class CwFastWifiSsid extends ClockworkTest {

    @Option(
        name = "expected-ssid",
        description = "The SSID to match, default is null. If null, will match the ssid on phone"
    )
    private String mExpectedSsid = null;

    @Option(
        name = "test-run-name",
        description = "The name of the test run, used for reporting" + " default is CwFastWifiSsid"
    )
    private String mTestRunName = CwFastWifiSsid.class.getSimpleName();

    private static final int DUMP_SYS_TIMEOUT = 30;
    private static final int WIFI_SYNC_TIMEOUT = 5 * 60;
    private static final int DOWNLOADER_TIMEOUT = 20;

    private ConnectivityHelper mHelper;

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        mHelper = new ConnectivityHelper();
        long start = System.currentTimeMillis();
        listener.testRunStarted(mTestRunName, 1);

        TestIdentifier id = new TestIdentifier(getClass().getCanonicalName(), "Compare SSID");
        listener.testStarted(id);
        if (!mHelper.isWifiCredentialsSynced(getDevice(), WIFI_SYNC_TIMEOUT)) {
            listener.testFailed(id, "Wifi Data Item is not synced before timeout");
        }

        ArrayList<String> phoneSsidList = mHelper.getSavedSsids(getCompanion());
        ArrayList<String> watchSsidList = mHelper.getSavedSsids(getDevice());
        if (watchSsidList.size() == 0) {
            listener.testFailed(id, "No SSID was found in the watch");
        }
        if (mExpectedSsid == null) {
            phoneSsidList.removeAll(watchSsidList);
            if (phoneSsidList.size() != 0) {
                listener.testFailed(id, "Some SSID was not found in the watch");
                for (String ssid : phoneSsidList) {
                    CLog.d("Missing SSID %s", ssid);
                }
            }
        } else {
            if (!watchSsidList.contains(mExpectedSsid)) {
                CLog.d("SSID %s is not found", mExpectedSsid);
                listener.testFailed(
                        id, String.format("Watch saved network does not have %s", mExpectedSsid));
            } else {
                CLog.d("SSID %s is matched", mExpectedSsid);
            }
        }
        CLog.d("all done!");
        listener.testEnded(id, Collections.emptyMap());
        listener.testRunEnded(System.currentTimeMillis() - start, Collections.emptyMap());
    }
}
