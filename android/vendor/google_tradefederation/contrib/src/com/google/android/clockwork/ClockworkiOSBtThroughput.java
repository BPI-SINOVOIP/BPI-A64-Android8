// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.RunUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ConnectivityTestRunner} that test android wear for iOS BT thorughput using downloader to
 * download a big file fomr web
 */
@OptionClass(alias = "cw-ios-bt-throughput")
public class ClockworkiOSBtThroughput extends ClockworkConnectivityTest {

    @Option(
        name = "test-time",
        description = "The total time to run downloader in throughput test " + "default to 120 secs"
    )
    private int mTestTime = 120;

    @Option(name = "iteration", description = "Total throughput iteration number")
    private int mIteration = 5;

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "ClockworkWifiThroughput";

    private static final String IP_PATTERN = "inet addr:(192.168.1.\\d+)";
    private static final String SENDER_PATTERN = "(\\d+\\.\\d+) Mbits/sec.+sender";
    private static final String RECEIVER_PATTERN = "(\\d+\\.\\d+) Mbits/sec.+receiver";

    private static final long TEST_WAIT_TIME = 120 * 1000;
    private static final long SHORT_WAIT_TIME = 20 * 1000;

    /**
     * This test will run downloader command multiple times to get average Calculate results and
     * report
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        float totalDownloadSpeed = 0;

        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();

        for (int i = 0; i < mIteration; i++) {
            TestIdentifier id =
                    new TestIdentifier(
                            getClass().getCanonicalName(),
                            String.format("Downloader iteration%d", i));
            CLog.d("Starting downloader iteration %d", i);
            listener.testStarted(id);
            CLog.i("Start downloader from watch");
            float downloadSpeed = downloader(getDevice());
            if (downloadSpeed > 0) {
                CLog.i(String.format("Download speed %.2f", downloadSpeed));
                totalDownloadSpeed += downloadSpeed;
            } else {
                listener.testFailed(id, "Not able to get downloader speed");
            }
            listener.testEnded(id, Collections.<String, String>emptyMap());
            RunUtil.getDefault().sleep(SHORT_WAIT_TIME);
        }

        // Report results
        Map<String, String> metrics = new HashMap<String, String>();

        CLog.i(String.format("Total downloader speed %.2f", totalDownloadSpeed));
        metrics.put("Downloader", String.format("%.2f", totalDownloadSpeed / mIteration));
        CLog.d("Downloader speed = %.2f", totalDownloadSpeed / mIteration);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }
}
