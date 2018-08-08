// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ConnectivityTestRunner} that test android wear for wifi thorughput using iperf3
 * connection via notification from companion to clockwork every 5 mins for a long period of time
 */
@OptionClass(alias = "cw-wifi-throughput")
public class ClockworkWifiThroughput extends ClockworkConnectivityTest {

    @Option(
        name = "test-time",
        description = "The total time to run iperf3 in throughput test " + "default to 60 secs"
    )
    private int mTestTime = 60;

    @Option(name = "iteration", description = "Total throughput iteration number")
    private int mIteration = 5;

    @Option(name = "downloader", description = "Run downloader test")
    private Boolean mDownloader = true;

    @Option(name = "ap-ssid", description = "SSID for wifi throughput id")
    protected String mTestSsid = "wifi-throughput";

    @Option(name = "test-run-name", description = "Test run name for different reports")
    protected String mTestRunName = "ClockworkWifiThroughput";

    private static final String DISABLE_WIFI_CMD = "svc wifi disable";
    private static final String CHECK_WIFI_CMD = "dumpsys netstats | grep wlan0";
    private static final String IP_ADDR_CMD = "ip addr";
    private static final String IPERF_CLIENT_CMD = "iperf3 -c %s -i 10 -t %d";
    private static final String IPERF_SERVER_CMD = "iperf3 -s";

    private static final String IP_PATTERN = "inet addr:(192.168.1.\\d+)";
    private static final String SENDER_PATTERN = "(\\d+\\.\\d+) Mbits/sec.+sender";
    private static final String RECEIVER_PATTERN = "(\\d+\\.\\d+) Mbits/sec.+receiver";

    private static final String WIFI_ENABLE_CMD = "svc wifi enable";
    private static final String WIFI_START_SETTING =
            "am startservice com.google.android.apps."
                    + "wearable.settings/com.google.android.clockwork.settings.wifi.WifiSettingsService";
    private static final String WIFI_STOP_SETTING =
            "am stopservice com.google.android.apps."
                    + "wearable.settings/com.google.android.clockwork.settings.wifi.WifiSettingsService";
    private static final String WIFI_SET_SSID =
            "dumpsys activity service com.google.android.apps."
                    + "wearable.settings/com.google.android.clockwork.settings.wifi.WifiSettingsService %s"
                    + " 0 \"\"";
    private static final String WIFI_SETTING_CMD = "settings put system clockwork_wifi_setting on";

    private static final long TEST_WAIT_TIME = 120 * 1000;
    private static final long SHORT_WAIT_TIME = 20 * 1000;

    /** Validate watch has valid wifi connection before we start testing for throughput */
    private boolean validateWifiConnection(long timeout) throws DeviceNotAvailableException {
        long current = System.currentTimeMillis();
        long end = current + timeout;
        String result = "";
        while (current < end && result.equals("")) {
            CLog.d("Checking wifi...");
            result = getDevice().executeShellCommand(CHECK_WIFI_CMD);
            RunUtil.getDefault().sleep(10 * 1000);
            current = System.currentTimeMillis();
        }
        return result.equals("") ? false : true;
    }

    private String getHostIPAddress() {
        CommandResult cr = RunUtil.getDefault().runTimedCmd(TEST_WAIT_TIME, "ifconfig");
        CommandStatus cs = cr.getStatus();
        String watchIp = "";
        if (cs == CommandStatus.SUCCESS) {
            Pattern r = Pattern.compile(IP_PATTERN);
            Matcher m = r.matcher(cr.getStdout());
            if (m.find()) {
                watchIp = m.group(1);
            }
        } else {
            Assert.fail("Not able to get Host IP address");
        }
        CLog.d("Host mac address is '%s'", watchIp);
        return watchIp;
    }

    private float getThroughputData(String buffer, String pattern) {
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(buffer);
        if (m.find()) {
            return Float.parseFloat(m.group(1));
        } else {
            CLog.d("Throuput Buffer = %s", buffer);
            Assert.fail("Not able to get throuput data.");
            return 0;
        }
    }

    /**
     * This test will use iperf3 tool to measure wifi throughput for watch download/upload speed
     * Verify Watch is conected saved wifi network and has valid ip address Verify Host Ip is
     * reachable from watch Setup Host iperf3 server Start iperf3 from watch as client to connect to
     * server Analyse result from Server Restart server and run the test again for iteration times
     * If downloader option is on, run downloader command multiple times to get average Calculate
     * results and report
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {

        float totalSendValue = 0;
        float totalReceiveValue = 0;
        float totalDownloadSpeed = 0;

        listener.testRunStarted(mTestRunName, mIteration);
        long start = System.currentTimeMillis();
        CLog.i("Turn on wifi and wait for 120s");
        getDevice().executeShellCommand(WIFI_ENABLE_CMD);
        getDevice().executeShellCommand(WIFI_START_SETTING);
        getDevice().executeShellCommand(String.format(WIFI_SET_SSID, mTestSsid));
        getDevice().executeShellCommand(WIFI_STOP_SETTING);
        getDevice().executeShellCommand(WIFI_SETTING_CMD);

        RunUtil.getDefault().sleep(TEST_WAIT_TIME);

        CLog.i("Verify WIFI on watch");
        if (!validateWifiConnection(TEST_WAIT_TIME)) {
            // No wifi, test fail
            Assert.fail("There is no wifi connection on watch");
        }

        CLog.i("Get Ip address on Host");
        String hostIP = getHostIPAddress();
        Process server = null;
        try {
            CLog.i("Start iPerf server at background");
            server = RunUtil.getDefault().runCmdInBackground("iperf3", "-s");
            for (int i = 0; i < mIteration; i++) {
                TestIdentifier id =
                        new TestIdentifier(
                                getClass().getCanonicalName(),
                                String.format("iPerf3 iteration%d", i));
                CLog.d("Starting iperf3 iteration %d", i);
                listener.testStarted(id);
                CLog.i("Start iPerf client from watch");
                String buffer =
                        getDevice()
                                .executeShellCommand(
                                        String.format(IPERF_CLIENT_CMD, hostIP, mTestTime));
                totalSendValue += getThroughputData(buffer, SENDER_PATTERN);
                totalReceiveValue += getThroughputData(buffer, RECEIVER_PATTERN);
                listener.testEnded(id, Collections.<String, String>emptyMap());
                RunUtil.getDefault().sleep(SHORT_WAIT_TIME);
            }
            if (mDownloader) {
                for (int i = 0; i < mIteration; i++) {
                    TestIdentifier id2 =
                            new TestIdentifier(
                                    getClass().getCanonicalName(),
                                    String.format("Downloader iteration%d", i));
                    CLog.d("Starting downloader iteration %d", i);
                    listener.testStarted(id2);
                    CLog.i("Start downloader from watch");
                    float downloadSpeed = downloader(getDevice());
                    if (downloadSpeed > 0) {
                        CLog.i(String.format("Download speed %.2f", downloadSpeed));
                        totalDownloadSpeed += downloadSpeed;
                    } else {
                        listener.testFailed(id2, "Not able to get downloader speed");
                    }
                    listener.testEnded(id2, Collections.<String, String>emptyMap());
                    RunUtil.getDefault().sleep(SHORT_WAIT_TIME);
                }
            }

        } catch (IOException e) {
            CLog.e(e);
            throw new RuntimeException(e);
        } finally {
            server.destroy();
        }

        // Report results
        Map<String, String> metrics = new HashMap<String, String>();
        metrics.put("throughput_send", String.format("%.2f", totalSendValue / mIteration));
        metrics.put("throughput_receive", String.format("%.2f", totalReceiveValue / mIteration));
        if (mDownloader) {
            CLog.i(String.format("Total downloader speed %.2f Mbits/sec", totalDownloadSpeed));
            metrics.put("Downloader", String.format("%.2f", totalDownloadSpeed / mIteration));
            CLog.d("Average Downloader speed = %.2f Mbits/sec", totalDownloadSpeed / mIteration);
        }
        CLog.d(
                "All done!, throughput_send = %f Mbits/sec received = %f Mbits/sec",
                totalSendValue / mIteration, totalReceiveValue / mIteration);
        listener.testRunEnded(System.currentTimeMillis() - start, metrics);
    }
}
