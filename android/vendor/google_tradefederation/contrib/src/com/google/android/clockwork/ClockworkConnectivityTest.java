// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.CompanionAwareTest;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A {@link CompanionAwareTest} created for connectivity test using notifications */
@OptionClass(alias = "cw-connectivity-test")
public class ClockworkConnectivityTest extends CompanionAwareTest {

    @Option(name = "f-build-prefix", description = "Build alias prefix for F branch")
    private String mFPrefix = "NAA";

    private static final String COMPANION_NOTIFICATION_INSTR =
            "am instrument -w -r "
                    + "-e class android.test.clockwork.notification.NotificationSender"
                    + "#testSendQuickNotification -e title @TEST@NOTIFICATION@%s -e id %d "
                    + "android.test.clockwork.notification/android.test.InstrumentationTestRunner";
    private static final String CLOCKWORK_NOTIFICATION_CHECK =
            "dumpsys activity service "
                    + "NotificationCollectorService StreamManager -v | grep title";
    // The command for notification check for F has changed
    private static final String CLOCKWORK_NOTIFICATION_CHECK_F =
            "dumpsys activity service " + "StreamManagerService -v | grep title";
    // Commands for Wearable API connectivity
    private static final String WEARABLE_SERVICE_GCMCONTROLLER =
            " dumpsys activity service " + "WearableService | grep -A 15 GcmController";
    private static final String WEARABLE_SERVICE_NODESERVICE =
            "dumpsys activity service " + "WearableService NodeService | grep -A 6 Reachable";
    private static final String WEARABLE_TRANSPORT_CMD =
            "dumpsys activity service WearableService " + "WearableTransport";
    private static final String CLEAR_NOTIFICATION_CMD = "service call notification 1";
    private static final String IPTABLE_NAT = "iptables -vnL -t nat";
    private static final String IPTABLE_MANGLE = "iptables -vnL -t mangle";
    private static final String CHAIN_PROXY = "Chain PROXY_";
    private static final String WEARABLE_CLOUDNODE =
            "dumpsys activity service Wearable CloudNode " + "| grep \"cloud network id\"";
    private static final String SINGLE_ITR_TEST_SUCCESS_OUTPUT = "OK (1 test)";
    private static final long SLEEP_TIME_MS = 5 * 1000;
    private static final String CONNECTED = "true";
    private static final int DEFAULT_ID = 0;
    private static final String USB_DISCONNECT = "u::";
    private static final String USB_CONNECT = "::U";
    private static final long NCD_TIMEOUT_MS = 10 * 1000;
    private static final String CONNECT = "CONNECT";
    private static final String DISCONNECT = "DISCONNECT";
    private static final String DOWNLOADER_PATTERN =
            "Received \\d+\\.*\\d*kB at (\\d+\\.*\\d*)kBps in";
    private static final String DOWNLOAD_CMD = "downloader";
    private static final String MODEL = "ro.product.model";

    private ITestDevice mDevice;
    protected String mTestFailureReason = "";

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {}

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Validate the expect state is reached after a shell command is issued stack list command
     *
     * @param device DUT
     * @param command, the shell command we need to run
     * @param timeout timeout for the validation check in secs
     * @param interval Time interval between each check in secs
     * @return True if validation function return true within timeout period
     * @throws DeviceNotAvailableException
     */
    boolean cmdValidate(
            ITestDevice device,
            String command,
            long timeout,
            long interval,
            Callable<Boolean> validateFunc)
            throws DeviceNotAvailableException, Exception {
        if (command != null && !command.isEmpty()) {
            device.executeShellCommand(command);
        }
        long current = System.currentTimeMillis();
        long start = System.currentTimeMillis();
        boolean result = false;
        while (current - start < timeout * 1000 && !result) {
            CLog.d("Checking...");
            result = validateFunc.call();
            RunUtil.getDefault().sleep(interval * 1000);
            current = System.currentTimeMillis();
        }
        return result;
    }

    /**
     * Convenience method for capture bugreport and screen shot
     *
     * @param listener
     * @param number of iteration to put in as log file title
     * @throws DeviceNotAvailableException
     */
    void captureLogs(ITestInvocationListener listener, int currentIteration)
            throws DeviceNotAvailableException {
        captureLogs(listener, currentIteration, "");
    }

    /**
     * Convenience method for capture bugreport and screen shot with a prefix
     *
     * @param listener
     * @param interation number to put in as log file title
     * @param The prefix string to out on log file title
     * @throws DeviceNotAvailableException
     */
    void captureLogs(ITestInvocationListener listener, int currentIteration, String prefix)
            throws DeviceNotAvailableException {
        captureLogs(listener, currentIteration, "", getDevice());
        captureLogs(listener, currentIteration, "", getCompanion());
    }

    /**
     * Convenience method for capture bugreport and screen shot with a prefix on a device
     *
     * @param listener
     * @param interation number to put in as log file title
     * @param prefix The prefix string to out on log file title
     * @param device The device we want to get log from
     * @throws DeviceNotAvailableException
     */
    void captureLogs(
            ITestInvocationListener listener,
            int currentIteration,
            String prefix,
            ITestDevice device)
            throws DeviceNotAvailableException {
        String primaryTag =
                String.format("%s%03d_%s", prefix, currentIteration, device.getProductType());
        screenshot(device, listener, primaryTag);
        bugreport(device, listener, primaryTag);
    }

    void screenshot(ITestDevice device, ITestInvocationListener listener, String tag)
            throws DeviceNotAvailableException {
        InputStreamSource data = null;
        try {
            data = device.getScreenshot();
            listener.testLog(tag + "_screenshot", LogDataType.PNG, data);
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        } finally {
            StreamUtil.cancel(data);
        }
    }

    /**
     * Convenience method for capture bugreport and screen shot with a prefix on a device
     *
     * @param listener
     * @param interation number to put in as log file title
     * @param prefix The prefix string to out on log file title
     * @param device The device we want to get log from
     * @throws DeviceNotAvailableException
     */
    void bugreport(ITestDevice device, ITestInvocationListener listener, String tag)
            throws DeviceNotAvailableException {
        InputStreamSource data = null;
        try {
            if (isFeldspar(device)) {
                data = device.getBugreportz();
                listener.testLog(tag + "_bugreport", LogDataType.ZIP, data);
            } else {
                data = device.getBugreport();
                listener.testLog(tag + "_bugreport", LogDataType.TEXT, data);
            }
        } finally {
            StreamUtil.cancel(data);
        }
    }

    boolean sendNotification(int iteration) throws DeviceNotAvailableException {
        return sendNotification(iteration, DEFAULT_ID);
    }

    boolean sendNotification(int iteration, int id) throws DeviceNotAvailableException {
        String buffer = "";
        buffer =
                getCompanion()
                        .executeShellCommand(
                                String.format(COMPANION_NOTIFICATION_INSTR, iteration, id));
        if (buffer.indexOf(SINGLE_ITR_TEST_SUCCESS_OUTPUT) > 0) {
            return true;
        }
        CLog.d(buffer);
        return false;
    }

    boolean validateNotificationViaDumpsys(int iteration, int timeout)
            throws DeviceNotAvailableException {
        String retDumpsys = "";
        long current = System.currentTimeMillis();
        long end = current + timeout * 1000;
        while (current < end) {
            if (isFeldspar()) {
                retDumpsys = getDevice().executeShellCommand(CLOCKWORK_NOTIFICATION_CHECK_F);
            } else {
                retDumpsys = getDevice().executeShellCommand(CLOCKWORK_NOTIFICATION_CHECK);
            }
            if (retDumpsys.indexOf(String.format("title: @TEST@NOTIFICATION@%d ", iteration)) > 0) {
                long temp = timeout - (end - current) / 1000;
                CLog.d(
                        String.format(
                                "Notification wait time for iteration %d is %d", iteration, temp));
                return true;
            }
            RunUtil.getDefault().sleep(10000);
            current = System.currentTimeMillis();
        }
        CLog.d(retDumpsys);
        return false;
    }

    /**
     * Check connectivity state using GcmController
     *
     * @param connectionType, the type of connection we want to check, can be WIFI or PROXY
     * @throws DeviceNotAvailableException
     */
    String checkWearableService(String connectionType, ITestDevice device)
            throws DeviceNotAvailableException {
        String buffer = "";
        /* Sample output for the command
        6 13:36:30 D/ClockworkConnectivityTest: Check wearable service buffer
        02-16 13:36:30 D/ClockworkConnectivityTest:     GcmController
            GCM is desired: false
            Network: MOBILE, connected=false
            Network: WIFI, connected=false
            Network: MOBILE_MMS, connected=false
            Network: MOBILE_SUPL, connected=false
            Network: MOBILE_HIPRI, connected=false
            Network: BLUETOOTH, connected=false
            Network: MOBILE_FOTA, connected=false
            Network: MOBILE_IMS, connected=false
            Network: MOBILE_CBS, connected=false
            Network: MOBILE_IA, connected=false
            Network: PROXY, connected=true
            Network: VPN, connected=false
        */
        String pattern = String.format("%s, connected=(\\w+)", connectionType);
        Pattern r = Pattern.compile(pattern);
        buffer = device.executeShellCommand(WEARABLE_SERVICE_GCMCONTROLLER);
        Matcher m = r.matcher(buffer);
        if (m.find()) {
            return m.group(1);
        }
        CLog.d(String.format("Not able to find connection status is: %s", connectionType));
        CLog.d(buffer);
        return "";
    }

    /**
     * Check WEARABLE SERVICE NodeService to determine is there is node connection for wearable. For
     * F branch or newer
     *
     * @param device The devices we want to check the NodeService status
     * @return Available nodes, 0 if no reachable nodes, -1 if command return wrong format
     * @throws DeviceNotAvailableException
     */
    int checkNodeService(ITestDevice device) throws DeviceNotAvailableException {
        String buffer = "";
        /* Sample output for the command
             Reachable Nodes:
                    name :         id : hops : isNearby :  isWatch
                  N5 f66 :   2635dce2 :    1 :     true :    false
                   cloud :      cloud :    1 :    false :    false

            ############################
            Reachable Nodes:
                    name :         id : hops : isNearby :  isWatch
            no reachable nodes
        */
        String pattern_no_nodes = "no reachable nodes";
        String pattern_table = ".+:.+:.+:.+:.+";
        Pattern r1 = Pattern.compile(pattern_no_nodes);
        Pattern r2 = Pattern.compile(pattern_table);
        buffer = device.executeShellCommand(WEARABLE_SERVICE_NODESERVICE);
        Matcher m = r1.matcher(buffer);
        if (m.find()) {
            // No reachable nodes is found
            return 0;
        }
        int lineCount = 0;
        String[] lines = buffer.split("\r\n|\r|\n");
        for (String line : lines) {
            m = r2.matcher(line);
            if (m.find()) {
                lineCount++;
            }
        }
        CLog.d(String.format("Line count is %d", lineCount));
        return lineCount - 1;
    }

    /**
     * Check if the DUT is connected with other node, or connected with internet For now we only
     * check for node connnectivity
     *
     * @param connectionType The connectionType we want to check, eiter NODE or INTERNET
     * @param timeout Time out for connectivity checking
     * @return returnconnect state in boolean
     * @throws DeviceNotAvailableException
     */
    boolean validateConnectionState(String connectionType, int timeout)
            throws DeviceNotAvailableException {
        return validateConnectionState(connectionType, timeout, getDevice());
    }

    /**
     * Clear all existing notifications
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    void clearAllNotifications(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(CLEAR_NOTIFICATION_CMD);
    }

    /**
     * Check if the device is connected with other node, or connected with internet For now we only
     * check for node connnectivity
     *
     * @param connectionType The connectionType we want to check, eiter NODE or INTERNET
     * @param timeout Time out for connectivity checking
     * @param device target device
     * @throws DeviceNotAvailableException
     */
    boolean validateConnectionState(String connectionType, int timeout, ITestDevice device)
            throws DeviceNotAvailableException {
        long current = System.currentTimeMillis();
        long end = current + timeout * 1000;
        int connectionNum = -1;
        String currentState;
        while (current < end) {
            if (connectionType.equals("NODE")) {
                connectionNum = checkNodeService(device);
                if (connectionNum > 0) {
                    long temp = timeout - (end - current) / 1000;
                    CLog.d(String.format("Connection time %d number %d", temp, connectionNum));
                    return true;
                }
            } else {
                currentState = checkWearableService(connectionType, device);
                if (currentState.equals(CONNECTED)) {
                    long temp = timeout - (end - current) / 1000;
                    CLog.d(
                            String.format(
                                    "%s connection time is %d return %s",
                                    connectionType, temp, currentState));
                    return true;
                }
            }
            RunUtil.getDefault().sleep(10000);
            current = System.currentTimeMillis();
        }
        CLog.d(String.format("Connection status is: %d", connectionNum));
        return false;
    }

    /**
     * Collect the Connection stat from WearableService WearableTransport Return the an array
     * including total send/receive bytes and total duration in seconds Current output connection
     * stats 1970-02-01 18:19:03, writes/reads (5082/5310), bytes (4044855/54830300), duration 07:22
     *
     * @param device Target android device
     * @return long array including total sent bytes, total received bytes, and total duration
     * @throws DeviceNotAvailableException
     */
    static long[] getWearableTransportConnectionStat(ITestDevice device)
            throws DeviceNotAvailableException {
        long totalSentBytes = 0;
        long totalReceivedBytes = 0;
        long duration = 0;
        int mins = 0;
        int secs = 0;
        String retDumpsys = device.executeShellCommand(WEARABLE_TRANSPORT_CMD);
        String pattern = "bytes \\((\\d+)/(\\d+)\\), duration (\\d+)\\:(\\d+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(retDumpsys);
        if (m.find()) {
            totalSentBytes = Long.parseLong(m.group(1));
            totalReceivedBytes = Long.parseLong(m.group(2));
            mins = Integer.parseInt(m.group(3));
            secs = Integer.parseInt(m.group(4));
            CLog.d(String.format("Currently we got : %s", m.group(0)));
        }
        duration = mins * 60 + secs;
        long[] results = {totalSentBytes, totalReceivedBytes, duration};
        return results;
    }

    /**
     * Check for the cloud network id for device within a time limit Return null if the cloud
     * network id is not found Current output cloud network id: 6542753821219228887
     *
     * @param device Target android device
     * @param timeout Time out for cloud network id checking in secs
     * @return Network ID string, null if not found or null
     * @throws DeviceNotAvailableException
     */
    static String getCloudNetworkId(ITestDevice device, long timeout)
            throws DeviceNotAvailableException {
        long current = System.currentTimeMillis();
        long end = current + timeout * 1000;
        String networkId, retDumpsys;
        String[] sections;
        while (current < end) {
            retDumpsys = device.executeShellCommand(WEARABLE_CLOUDNODE);
            sections = retDumpsys.split(":");
            if (sections.length == 2) {
                networkId = sections[1].trim();
                if (!networkId.equals("null")) {
                    return networkId;
                } else {
                    CLog.i(
                            String.format(
                                    "%s device cloud network id is not ready '%s'",
                                    device.getProductType(), retDumpsys.trim()));
                }
            } else {
                CLog.e(
                        String.format(
                                "Cloud network ID format is incorrect section length %d",
                                sections.length));
                CLog.e(String.format("Dumpsys '%s'", retDumpsys));
                return null;
            }
            RunUtil.getDefault().sleep(10000);
            current = System.currentTimeMillis();
        }
        CLog.e("Not able to get Cloud network ID");
        return null;
    }

    /**
     * Use Downloader to measure current throughput and internet connectivity Return Throughput data
     *
     * @param device Target android device
     * @return Download speed, 0 if failed or not able to connect
     * @throws DeviceNotAvailableException
     */
    static float downloader(ITestDevice device) throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(DOWNLOAD_CMD, receiver, 15, TimeUnit.MINUTES, 0);
        String buffer = receiver.getOutput();
        CLog.d("Downloader output:");
        CLog.d(buffer);
        String[] bufferLines = buffer.split("\r");
        float value = 0;
        for (int i = 0; i < bufferLines.length; i++) {
            Pattern r = Pattern.compile(DOWNLOADER_PATTERN);
            Matcher m = r.matcher(bufferLines[i]);
            if (m.find()) {
                value = Float.parseFloat(m.group(1));
            }
        }
        return value;
    }

    /**
     * Inject logcat information to device so we can sync the log messages for thes same event
     *
     * @param device Test device to inject the log
     * @param tag tag field for logcat
     * @param priorityChar Message priority
     * @param message Message body
     * @throws DeviceNotAvailableException
     */
    void logcatInfo(ITestDevice device, String tag, String priorityChar, String message)
            throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("log -p %s -t %s %s", priorityChar, tag, message));
    }

    /**
     * Confirm branch version if it is Fledspar or not based on build alias
     *
     * @throws DeviceNotAvailableException
     */
    boolean isFeldspar() throws DeviceNotAvailableException {
        return isFeldspar(getDevice());
    }

    /**
     * Confirm branch version if it is Fledspar or not based on build alias
     *
     * @param device, Test device to check
     * @throws DeviceNotAvailableException
     */
    boolean isFeldspar(ITestDevice device) throws DeviceNotAvailableException {
        String buildAlias = device.getBuildAlias().substring(0, 3);
        return mFPrefix.compareTo(buildAlias) <= 0;
    }

    /**
     * Check if the Proxy IPTable is setup correctly
     *
     * @param device Test device to validate iptable
     * @param connectionType The connectionType we want to check, eiter TCP or UDP
     * @param timeout Time out for connectivity checking
     * @throws DeviceNotAvailableException
     */
    boolean validateProxyIptable(ITestDevice device, String connectionType, int timeout)
            throws DeviceNotAvailableException {
        long current = System.currentTimeMillis();
        long end = current + timeout * 1000;
        String cmdOutput;
        while (current < end) {
            if ("TCP".equals(connectionType)) {
                cmdOutput = device.executeShellCommand(IPTABLE_NAT);
            } else if ("UDP".equals(connectionType)) {
                cmdOutput = device.executeShellCommand(IPTABLE_MANGLE);
            } else {
                Assert.fail("Incorrect connectionType, we only support TCP or UDP");
                return false;
            }
            if (cmdOutput.indexOf(CHAIN_PROXY + connectionType) >= 0) {
                CLog.d(String.format("%s is found", CHAIN_PROXY + connectionType));
                return true;
            }
            RunUtil.getDefault().sleep(10000);
            current = System.currentTimeMillis();
        }
        CLog.d(String.format("Proxy IpTable is invalide"));
        return false;
    }
    /**
     * Excute NCD.py command to connect/disconnect USB
     *
     * @throws DeviceNotAvailableException
     */
    boolean ncdAction(String type, int bankId) {
        String ncdCmd = "";
        if (type.equals(CONNECT)) {
            ncdCmd = USB_CONNECT;
        } else if (type.equals(DISCONNECT)) {
            ncdCmd = USB_DISCONNECT;
        } else {
            CLog.d("Incorrection NCD action type %s", type);
            return false;
        }
        CLog.d(String.format("NCD command is 'ncd.py -d %d -e %s'", bankId, ncdCmd));
        CommandResult cr =
                RunUtil.getDefault()
                        .runTimedCmd(
                                NCD_TIMEOUT_MS,
                                "ncd.py",
                                "-d",
                                Integer.toString(bankId),
                                "-e",
                                ncdCmd);
        CommandStatus cs = cr.getStatus();
        if (cs == CommandStatus.SUCCESS) {
            return true;
        } else {
            CLog.d("NCD command failed");
            return false;
        }
    }

    /**
     * Get device model name from property
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    String getModelName(ITestDevice device) throws DeviceNotAvailableException {
        String modelName = "\"" + device.getProperty(MODEL) + "\"";
        return modelName.replaceAll(" ", "\\ ");
    }
}
