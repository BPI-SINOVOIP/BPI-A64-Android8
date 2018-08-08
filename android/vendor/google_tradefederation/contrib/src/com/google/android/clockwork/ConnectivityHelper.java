// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.clockwork;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.BackgroundDeviceAction;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A connectivity test provide basic functions TODO: This library will be merged with other util
 * library to share tools between features.
 */
@OptionClass(alias = "connectivity-helper")
public class ConnectivityHelper {

    @Option(name = "f-build-prefix", description = "Build alias prefix for F branch")
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
    private static final String WEARABLE_TRANSPORT_CONNECTION =
            "dumpsys activity service "
                    + "WearableService WearableTransport | grep -A 200 \"connection stats\"";
    private static final String FLOW_SERVICE = "dumpsys activity service FlowService";
    private static final String CLEAR_NOTIFICATION_CMD = "service call notification 1";
    private static final String AIRPLANEMODE_ON = "settings put global airplane_mode_on 1";
    private static final String AIRPLANEMODE_OFF = "settings put global airplane_mode_on 0";
    private static final String AIRPLANEMODE_BROADCAST =
            "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true";
    private static final String IPTABLE_NAT = "iptables -vnL -t nat";
    private static final String IPTABLE_MANGLE = "iptables -vnL -t mangle";
    private static final String CHAIN_PROXY = "Chain PROXY_";
    private static final String WEARABLE_CLOUDNODE =
            "dumpsys activity service Wearable CloudNode " + "| grep \"cloud network id\"";
    private static final String BLUETOOTH_DUMPSYS =
            "dumpsys bluetooth_manager | grep -A 2 " + "\"Bluetooth Status\"";
    private static final String SYNC_WIFI_DUMPSYS =
            "dumpsys activity service Wearable DataService | grep sync_wifi";
    private static final String BLUETOOTH_ENABLED = "enabled: true";
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
    private static final String PING_CMD = "ping -c 5 www.google.com";
    private static final String PING_RETURN = "5 packets transmitted, 5 received, 0% packet loss";
    private static final String CAT_WIFI_SSID =
            "cat /data/misc/wifi/wpa_supplicant.conf | grep ssid";
    private static final String CAT_WIFI_SSID_O =
            "cat /data/misc/wifi/WifiConfigStore.xml | grep SSID";
    private static final String FAKE_OTA_CMD = "/google/data/ro/teams/tradefed/utils/ota/fake-ota";
    private static final long OTA_CMD_TIMEOUT = 30 * 1000;
    private static final String MODEL = "ro.product.model";
    private static final String DISPLAY_NAME = "ro.product.display_name";
    private static final String SDCARD_PATH = "/sdcard/%s.%s";
    private static final String LOGCAT_GREP_BT_CONNECTION =
            "logcat -d | grep 'bt_btm_sec: btm_sec_disconnected clearing pending flag'";
    private static final String LOGCAT_MARKER_GREP_CMD = "logcat -t \"%s\" | grep \"%s\"";
    private static final String DUMPSYS_CONNECTIVITY = "dumpsys connectivity";
    private static final String PROXY_CONNECTED = "PROXY[], state:";
    private static final String WIFI_CONNECTED = "WIFI[], state: CONNECTED/CONNECTED";
    private static final String SSID_PATTERN_O = "name=\"SSID\">&quot;(.+)&quot;";
    private static final String SSID_PATTERN = "ssid=\"(.+)\"";
    protected String mTestFailureReason = "";
    private BackgroundDeviceAction mRecordingAction;

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
    public boolean cmdValidate(
            ITestDevice device,
            String command,
            long timeout,
            long interval,
            Callable<Boolean> validateFunc)
            throws DeviceNotAvailableException {
        boolean validated = false;
        if (command != null && !command.isEmpty()) {
            device.executeShellCommand(command);
        }
        long end = System.currentTimeMillis() + timeout * 1000;
        while (!validated && System.currentTimeMillis() < end) {
            try {
                if (validateFunc.call()) {
                    validated = true;
                } else {
                    RunUtil.getDefault().sleep(interval * 1000);
                }
            } catch (Exception e) {
                CLog.e(e);
            }
        }
        return validated;
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
    public void captureLogs(
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

    public void screenshot(ITestDevice device, ITestInvocationListener listener, String tag)
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
     * Check device type
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return true if it is a watch, false otherwise
     */
    public boolean isWatch(ITestDevice device) throws DeviceNotAvailableException {
        String deviceBuildPropertyString = device.getProperty("ro.build.characteristics");
        return device.getProperty("ro.build.characteristics").contains("watch");
    }

    /**
     * Convenience method for starting to capture screen recording on a device with 100*100
     * resolution
     *
     * @param tag The prefix string to out on log file title
     * @param device The device we want to get log from
     * @throws DeviceNotAvailableException
     */
    public void startScreenRecording(ITestDevice device, String tag)
            throws DeviceNotAvailableException {
        startScreenRecording(device, tag, 100);
    }

    /**
     * Convenience method for starting to capture screen recording on a device
     *
     * @param tag The prefix string to out on log file title
     * @param device The device we want to get log from
     * @param resolution The resolution for the video
     * @throws DeviceNotAvailableException
     */
    public void startScreenRecording(ITestDevice device, String tag, int resolution)
            throws DeviceNotAvailableException {
        String rawOption = "";
        String fileType = "mp4";
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        // Watch devices only support raw video
        if (isWatch(device)) {
            rawOption = String.format("--o raw-frames --size %dx%d", resolution, resolution);
            fileType = "raw";
        }
        String screenRecordCmd =
                String.format("screenrecord %s " + SDCARD_PATH, rawOption, tag, fileType);
        CLog.d("Start screen recording with '%s'", screenRecordCmd);
        mRecordingAction =
                new BackgroundDeviceAction(
                        screenRecordCmd, "Screen recording", device, receiver, 0);
        mRecordingAction.start();
    }

    /** Convenience method for stopping capture screen recording on a device */
    public void stopScreenRecording() {
        if (mRecordingAction != null) {
            CLog.d("Stop screen recording");
            mRecordingAction.cancel();
        } else {
            CLog.w("The recording was not started.");
        }
    }

    /**
     * Convenience method for upload capture recording to test log
     *
     * @param listener
     * @param interation number to put in as log file title
     * @param prefix The prefix string to out on log file title
     * @param device The device we want to get log from
     * @throws DeviceNotAvailableException
     */
    public void uploadScreenRecording(
            ITestDevice device, ITestInvocationListener listener, String tag)
            throws DeviceNotAvailableException {
        InputStreamSource data = null;
        String fileType = isWatch(device) ? "raw" : "mp4";
        String filePath = String.format(SDCARD_PATH, tag, fileType);
        File videoFile = device.pullFile(filePath);
        if (videoFile != null) {
            data = new FileInputStreamSource(videoFile);
            CLog.d("Upload screen recording");
            listener.testLog(tag + "_screenrecord", LogDataType.UNKNOWN, data);
        } else {
            CLog.e("Not able to get file from device");
        }
        removeScreenRecording(device, tag);
    }

    /**
     * Convenience method for removing captured recording to test log
     *
     * @param device The device we want to get log from
     * @param tag The name for the video file
     * @throws DeviceNotAvailableException
     */
    public void removeScreenRecording(ITestDevice device, String tag)
            throws DeviceNotAvailableException {
        CLog.d("Delete screen recording");
        String fileType = isWatch(device) ? "raw" : "mp4";
        device.executeShellCommand("rm " + String.format(SDCARD_PATH, tag, fileType));
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
    public void bugreport(ITestDevice device, ITestInvocationListener listener, String tag)
            throws DeviceNotAvailableException {
        InputStreamSource data = null;
        try {
            if (isFeldsparAndAbove(device)) {
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

    public boolean sendNotification(ITestDevice device, int iteration)
            throws DeviceNotAvailableException {
        return sendNotification(device, iteration, DEFAULT_ID);
    }

    public boolean sendNotification(ITestDevice device, int iteration, int id)
            throws DeviceNotAvailableException {
        String buffer = "";
        buffer =
                device.executeShellCommand(
                        String.format(COMPANION_NOTIFICATION_INSTR, iteration, id));
        if (buffer.indexOf(SINGLE_ITR_TEST_SUCCESS_OUTPUT) > 0) {
            return true;
        }
        CLog.d(buffer);
        return false;
    }

    /**
     * Check notification stream to check the notification we sent
     *
     * @param device The device we want to check for notification
     * @return true if notification is found
     * @throws DeviceNotAvailableException
     */
    public boolean validateNotificationViaDumpsys(ITestDevice device, int iteration, int timeout)
            throws DeviceNotAvailableException {
        return validateNotificationKeywordViaDumpsys(
                device, String.format("title: @TEST@NOTIFICATION@%d ", iteration), timeout);
    }

    /**
     * Check notification stream to valid we have tutorial waiting after pairing
     *
     * @param device The device we want to check for Tutorial
     * @return true if tutorial is found
     * @throws DeviceNotAvailableException
     */
    public boolean validateTutorialViaDumpsys(ITestDevice device, int timeout)
            throws DeviceNotAvailableException {
        return validateNotificationKeywordViaDumpsys(device, "title: Tutorial", timeout);
    }

    /**
     * Check notification stream to valid to find the key word we need
     *
     * @param device The device we want to check for notification keyword
     * @param keyword The keyword we want to match
     * @return true if keyword is found
     * @throws DeviceNotAvailableException
     */
    public boolean validateNotificationKeywordViaDumpsys(
            ITestDevice device, String keyword, int timeout) throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        long end = System.currentTimeMillis() + timeout * 1000;
        String retDumpsys = "";
        String command = "";
        if (isFeldsparAndAbove(device)) {
            command = CLOCKWORK_NOTIFICATION_CHECK_F;
        } else {
            command = CLOCKWORK_NOTIFICATION_CHECK;
        }
        boolean validated = false;
        while (System.currentTimeMillis() < end) {
            retDumpsys = device.executeShellCommand(command);
            if (retDumpsys.indexOf(keyword) >= 0) {
                validated = true;
                CLog.d(String.format("Keyword '%s' is found", keyword));
                break;
            } else {
                CLog.d(retDumpsys);
                RunUtil.getDefault().sleep(10 * 1000);
            }
        }
        return validated;
    }

    /**
     * Check WEARABLE SERVICE NodeService to determine if there is node connection for wearable. For
     * F branch or newer
     *
     * @param device The devices we want to check the NodeService status
     * @return Available nodes, 0 if no reachable nodes, -1 if command return wrong format
     * @throws DeviceNotAvailableException
     */
    public int checkNodeService(ITestDevice device) throws DeviceNotAvailableException {
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
        CLog.d(buffer);
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
     * Clear all existing notifications
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    public void clearAllNotifications(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(CLEAR_NOTIFICATION_CMD);
    }

    /**
     * Check if the device is connected with other node, and connected with internet This function
     * is for watch side verification only
     *
     * @param timeout Time out for connectivity checking
     * @param device target device
     * @throws DeviceNotAvailableException
     */
    public boolean validateConnectionState(ITestDevice device, int timeout)
            throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        long end = System.currentTimeMillis() + timeout * 1000;
        boolean validated = false;
        while (System.currentTimeMillis() < end) {
            if (isWearableTransportCurrentlyConnected(device) && isProxyConnected(device)) {
                validated = true;
                break;
            } else {
                RunUtil.getDefault().sleep(10 * 1000);
            }
        }
        if (validated) {
            long duration = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
            CLog.d(String.format("Connection time %d secs", duration));
        }
        return validated;
    }

    /**
     * Check if the device is connected with wifi, both GMS and internet. This function is for watch
     * side verification only
     *
     * @param timeout Time out for connectivity checking
     * @param device target device
     * @throws DeviceNotAvailableException
     */
    public boolean validateWifiConnectionState(ITestDevice device, int timeout)
            throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        long end = System.currentTimeMillis() + timeout * 1000;
        boolean validated = false;
        while (System.currentTimeMillis() < end) {
            if (checkNodeService(device) > 0 && isWifiInternetConnected(device)) {
                validated = true;
                break;
            } else {
                RunUtil.getDefault().sleep(10 * 1000);
            }
        }
        if (validated) {
            long duration = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
            CLog.d(String.format("Connection time %d secs", duration));
        }
        return validated;
    }

    /**
     * Check if the companion phone is connected with other node, and flow service is connected This
     * function is for phone side verification only
     *
     * @param timeout Time out for connectivity checking
     * @param device target device
     * @throws DeviceNotAvailableException
     */
    boolean validatePhoneConnectionState(ITestDevice device, int timeout)
            throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        long end = System.currentTimeMillis() + timeout * 1000;
        boolean validated = false;
        while (!validated && System.currentTimeMillis() < end) {
            if (isWearableTransportCurrentlyConnected(device) && isFlowServiceConnected(device)) {
                validated = true;
                long duration = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
                CLog.d(String.format("Connection time %d", duration));
            } else {
                RunUtil.getDefault().sleep(10 * 1000);
            }
        }
        if (validated) {
            long duration = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start);
            CLog.d(String.format("Connection time %d", duration));
        }
        return validated;
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
    public static long[] getWearableTransportConnectionStat(ITestDevice device)
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
    public static String getCloudNetworkId(ITestDevice device, long timeout)
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
     * @param download timeout in secs
     * @return Download speed, 0 if failed or not able to connect
     * @throws DeviceNotAvailableException
     */
    public float downloader(ITestDevice device, long downloadTime)
            throws DeviceNotAvailableException {
        CollectingOutputReceiver receiver = new CollectingOutputReceiver();
        device.executeShellCommand(DOWNLOAD_CMD, receiver, downloadTime, TimeUnit.SECONDS, 0);
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
     * @param device, Test device to inject the log
     * @param tag, tag field for logcat
     * @param priorityChar, Message priority
     * @param message, Message body
     * @throws DeviceNotAvailableException
     */
    public void logcatInfo(ITestDevice device, String tag, String priorityChar, String message)
            throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("log -p %s -t %s %s", priorityChar, tag, message));
    }

    /**
     * Confirm branch version if it is Fledspar or not based on API level
     *
     * @param device, Test device to check
     * @throws DeviceNotAvailableException
     */
    public boolean isFeldsparAndAbove(ITestDevice device) throws DeviceNotAvailableException {
        return device.getApiLevel() >= 24;
    }

    /**
     * Confirm branch version if it is Gold or not based on API level
     *
     * @param device, Test device to check
     * @throws DeviceNotAvailableException
     */
    public boolean isGoldAndAbove(ITestDevice device) throws DeviceNotAvailableException {
        return device.getApiLevel() >= 26;
    }

    /**
     * Check if the Proxy IPTable is setup correctly
     *
     * @param device Test device to validate iptable
     * @param connectionType The connectionType we want to check, eiter TCP or UDP
     * @param timeout Time out for connectivity checking
     */
    public boolean validateProxyIptable(ITestDevice device, String connectionType, int timeout) {
        long end = System.currentTimeMillis() + timeout * 1000;
        if (!"TCP".equals(connectionType) && !"UDP".equals(connectionType)) {
            Assert.fail("Incorrect connectionType, we only support TCP or UDP");
            return false;
        }
        boolean validated = false;
        String command = "";
        if ("TCP".equals(connectionType)) {
            command = IPTABLE_NAT;
        } else if ("UDP".equals(connectionType)) {
            command = IPTABLE_MANGLE;
        }
        String cmdOutput = "";
        while (!validated && System.currentTimeMillis() < end) {
            try {
                cmdOutput = device.executeShellCommand(command);
                if (cmdOutput.indexOf(CHAIN_PROXY + connectionType) >= 0) {
                    validated = true;
                    CLog.d(String.format("%s is found", CHAIN_PROXY + connectionType));
                }
                if (!validated) {
                    RunUtil.getDefault().sleep(10 * 1000);
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
            }
        }
        return validated;
    }

    /**
     * Check if sync_wifi_credentials has been sync through data item during pairing
     *
     * @param device, Test device to validate sync_wifi_credentials
     * @param timeout Time out for sync_wifi_credentials checking
     * @throws DeviceNotAvailableException
     */
    public boolean isWifiCredentialsSynced(ITestDevice device, int timeout)
            throws DeviceNotAvailableException {
        long end = System.currentTimeMillis() + timeout * 1000;
        boolean validated = false;
        String command = "";
        String cmdOutput = "";
        while (System.currentTimeMillis() < end) {
            cmdOutput = device.executeShellCommand(SYNC_WIFI_DUMPSYS);
            if (cmdOutput.indexOf("sync_wifi") >= 0) {
                validated = true;
                CLog.d("sync wifi is found");
                break;
            }
            RunUtil.getDefault().sleep(10 * 1000);
            CLog.d("Checking wifi sync");
        }
        return validated;
    }

    /**
     * Excute NCD.py command to connect/disconnect USB
     *
     * @throws DeviceNotAvailableException
     */
    public boolean ncdAction(String type, int bankId) {
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
    public String getModelName(ITestDevice device) throws DeviceNotAvailableException {
        String displayName = device.getProperty(DISPLAY_NAME);
        String modelName = device.getProperty(MODEL);
        modelName = (displayName == null) ? "\"" + modelName + "\"" : "\"" + displayName + "\"";
        return modelName.replaceAll(" ", "\\ ");
    }

    /**
     * Turn on/off airplane mode
     *
     * @param on
     */
    // TODO This code is will  merge with the same function in DevicePreparer once a
    // common util library is created. So we don't have duplicate
    public void airplaneModeOn(ITestDevice device, boolean on) throws DeviceNotAvailableException {
        if (on) {
            device.executeShellCommand(AIRPLANEMODE_ON);
        } else {
            device.executeShellCommand(AIRPLANEMODE_OFF);
        }
        device.executeShellCommand(AIRPLANEMODE_BROADCAST);
    }

    /**
     * Adding logcat message to both watch and cpmpanion so we can debug issue eaiser.
     *
     * @param watchDevice, companionDevice
     * @param msg, The message we want to inject
     * @throws DeviceNotAvailableException
     */
    public void logMsg(ITestDevice watchDevice, ITestDevice companionDevice, String msg)
            throws DeviceNotAvailableException {
        CLog.d(msg);
        logcatInfo(watchDevice, "PAIRING_UTIL", "i", msg);
        logcatInfo(companionDevice, "PAIRING_UTIL", "i", msg);
    }

    /**
     * Filter logcat message after certain marker and return the message
     *
     * @param device, device to run the command
     * @param pattern, The pattern we are looking for after marker
     * @param marker, We only check the logcat lines after this marker
     * @param filter, The grep filter applied after the marker
     * @param num, Num of lines we pipe to the grep filter after marker
     * @param timeout, Timeout second before we give up looking for logcat
     * @throws DeviceNotAvailableException
     * @return If the log is found
     */
    public boolean checkLogcatAfterMarker(
            ITestDevice device, String pattern, String time, String filter, long timeout)
            throws DeviceNotAvailableException {
        long end = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeout);
        boolean validated = false;
        String cmd = String.format(LOGCAT_MARKER_GREP_CMD, time, filter);
        CLog.d("Filter Logcat : " + cmd);
        Pattern pt = Pattern.compile(pattern);
        while (System.currentTimeMillis() < end) {
            String cmdOutput = device.executeShellCommand(cmd);
            CLog.d(cmdOutput);
            Matcher m = pt.matcher(cmdOutput);
            if (m.find()) {
                validated = true;
                CLog.d("Logcat pattern is found");
                break;
            }
            if (System.currentTimeMillis() < end) {
                RunUtil.getDefault().sleep(TimeUnit.SECONDS.toMillis(10));
            }
            CLog.d("Checking logcat");
        }
        return validated;
    }

    /**
     * Get device bluetooth enabled state
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return true if it is enabled, false otherwise
     */
    public boolean bluetoothEnabled(ITestDevice device) throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(BLUETOOTH_DUMPSYS);
        if (buffer.indexOf(BLUETOOTH_ENABLED) > 0) {
            return true;
        } else {
            CLog.d(buffer);
            return false;
        }
    }

    /**
     * Log attenuator value intomation to device for debug purpose
     *
     * @param device
     * @param startValue
     * @param endValue
     * @throws DeviceNotAvailableException
     */
    public void logAttenuatorValue(ITestDevice device, int startValue, int endValue)
            throws DeviceNotAvailableException {
        logcatInfo(
                device,
                "ATT",
                "i",
                String.format("Attenuator set from %d to %d", startValue, endValue));
    }

    /**
     * Ping google.com, if reachable, return true
     *
     * @param device
     * @param timeout in seconds
     * @param interval in seconds
     * @param interval ping command interval
     */
    public boolean pingTest(ITestDevice device, long timeout, long interval, boolean expectPass) {
        long end = System.currentTimeMillis() + timeout * 1000;
        String buffer = null;
        boolean result = !expectPass;

        String retDumpsys = "";
        String command = "";
        boolean validated = false;
        while (result != expectPass && System.currentTimeMillis() < end) {
            try {
                buffer = device.executeShellCommand(PING_CMD);
            } catch (DeviceNotAvailableException e) {
                CLog.e(e);
                break;
            }
            if (buffer != null && buffer.contains(PING_RETURN)) {
                result = true;
            } else {
                result = false;
            }
            if (result != expectPass) {
                RunUtil.getDefault().sleep(interval * 1000);
                CLog.d("ping is %b, expect %b", result, expectPass);
            } else {
                validated = true;
            }
        }
        return validated;
    }

    /**
     * Check logcat for a bluetooth disconnection signature and return the count of the number it
     * occurred
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return The number of lines
     */
    public int bluetoothDisconnectCount(ITestDevice device) throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(LOGCAT_GREP_BT_CONNECTION);
        CLog.d("BT disconnection log: %s", buffer);
        String lines[] = buffer.split("\r|\n");
        return lines.length;
    }

    /**
     * Check wearable transport and return the count of reconnection
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return The number of lines
     */
    public int wearableTransportReconnectCount(ITestDevice device)
            throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(WEARABLE_TRANSPORT_CONNECTION);
        CLog.d("WearableTransport returns: %s", buffer);
        String lines[] = buffer.split("\r|\n");
        // The first line is title
        return lines.length - 1;
    }

    /**
     * Check wearable transport and return the current connection state
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return The number of lines
     */
    public boolean isWearableTransportCurrentlyConnected(ITestDevice device)
            throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(WEARABLE_TRANSPORT_CONNECTION);
        CLog.d("WearableTransport returns: %s", buffer);
        String pattern = "Current: \\d+-\\d+-\\d+";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(buffer);
        return m.find() ? true : false;
    }

    /**
     * Check dumpsys to make sure companion proxy is connected
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return True if it is connected
     */
    public boolean isProxyConnected(ITestDevice device) throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(DUMPSYS_CONNECTIVITY);
        return buffer.indexOf(PROXY_CONNECTED) > 0 ? true : false;
    }

    /**
     * Check dumpsys to make sure Wifi internet is connected
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return True if it is connected
     */
    public boolean isWifiInternetConnected(ITestDevice device) throws DeviceNotAvailableException {
        String buffer = device.executeShellCommand(DUMPSYS_CONNECTIVITY);
        return buffer.indexOf(WIFI_CONNECTED) > 0 ? true : false;
    }

    /**
     * Check dumpsys to make sure FlowService is connected to a node
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return True if it is connected to more than 1 node
     */
    public boolean isFlowServiceConnected(ITestDevice device) throws DeviceNotAvailableException {
        int connectedNode = -1;
        // Emerald device did not have Flow Service/Proxy, return true to skip the check
        if (!isFeldsparAndAbove(device)) {
            return true;
        }
        String retDumpsys = device.executeShellCommand(FLOW_SERVICE);

        String pattern = "Connected Nodes : (\\d+)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(retDumpsys);
        if (m.find()) {
            connectedNode = Integer.parseInt(m.group(1));
            CLog.d("FlowService returns: %s", m.group(0));
        } else {
            CLog.e("FlowService failed to return. Saw %s", retDumpsys);
        }
        return connectedNode > 0 ? true : false;
    }

    /**
     * Factory default device
     *
     * @throws DeviceNotAvailableException
     * @throws ConfigurationException
     * @throws TargetSetupError
     */
    public void resetClockwork(ITestDevice device) throws DeviceNotAvailableException {
        // performs a wipe of userdata an cache
        CLog.v("Begining to factory default " + device.getSerialNumber());
        device.rebootIntoBootloader();
        CommandResult result = device.fastbootWipePartition("userdata");
        CLog.v(
                String.format(
                        "format %s userdata - stdout: %s stderr: %s",
                        device.getSerialNumber(), result.getStdout(), result.getStderr()));
        result = device.fastbootWipePartition("cache");
        CLog.v(
                String.format(
                        "format %s cache - stdout: %s stderr: %s",
                        device.getSerialNumber(), result.getStdout(), result.getStderr()));
        device.executeFastbootCommand("reboot");
        device.waitForDeviceAvailable();
        device.enableAdbRoot();
        device.executeShellCommand("svc power stayon true");
    }

    /**
     * Return a list of saved SSID on device
     *
     * @param device
     * @throws DeviceNotAvailableException
     * @return True if it is connected to more than 1 node
     */
    public ArrayList<String> getSavedSsids(ITestDevice device) throws DeviceNotAvailableException {
        ArrayList<String> ssidList = new ArrayList<String>();
        String command;
        String pattern;
        if (isGoldAndAbove(device)) {
            command = CAT_WIFI_SSID_O;
            pattern = SSID_PATTERN_O;
        } else {
            command = CAT_WIFI_SSID;
            pattern = SSID_PATTERN;
        }
        String buffer = device.executeShellCommand(command);
        CLog.d("SSID list %s", buffer);

        Matcher m;
        Pattern r = Pattern.compile(pattern);
        String[] lines = buffer.split("\r\n|\r|\n");
        for (String line : lines) {
            m = r.matcher(line);
            if (m.find()) {
                ssidList.add(m.group(1));
            }
        }
        return ssidList;
    }

    /**
     * Create bug report and screen shot for both phone and watch
     *
     * @param listener Test invocation listener
     * @param i The number postfix for the bug report and screen shot
     * @param watch Watch device
     * @param phone Phone device
     * @throws DeviceNotAvailableException
     * @return True if it is connected to more than 1 node
     */
    public void reportFailure(
            ITestInvocationListener listener, int i, ITestDevice watch, ITestDevice phone)
            throws DeviceNotAvailableException {
        captureLogs(listener, i, "Watch", watch);
        captureLogs(listener, i, "Phone", phone);
    }
}
