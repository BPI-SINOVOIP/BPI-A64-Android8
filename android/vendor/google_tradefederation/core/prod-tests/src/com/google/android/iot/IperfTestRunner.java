// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.iot;

import com.android.ddmlib.MultiLineReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.SimpleStats;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test runner to run iperf tests
 */
public class IperfTestRunner {
    public static final String TCP = "tcp";
    public static final String UDP = "udp";
    public static final int UPLOAD = 0;
    public static final int DOWNLOAD = 1;
    public static final String IPERF_START_COMMAND = "am start com.magicandroidapps.iperf/.iperf";
    public static final String IPERF_PATH = "/data/data/com.magicandroidapps.iperf/bin/iperf";
    public static final String HOST_SERVER_COMMAND =
            String.format("iperf -s -B %s -f m", IotUtil.IPERF_HOST_SERVER);
    public static final String HOST_CLIENT_COMMAND = "iperf -f m -c %s -t %d";
    public static final String DEVICE_SERVER_COMMAND = "adb -s %s shell %s -s -f m";
    public static final String DEVICE_CLIENT_COMMAND = "%s -f m -c %s -t %d";
    // Value for a failed speed test
    public static final double INVALID_SPEED = -1.0;
    // Target bandwidth for UDP test is set to 100Mbits/s
    public static final int UDP_TARGET_BW = 100;
    // UDP loss rate threshold is 1%
    public static final double LOSSRATE_THRESHOLD = 1.0;
    // TCP/UDP traffic running time is 10 seconds
    public static final int TRAFFIC_TIMER = 10;
    // TCP/UDP traffic running iteration is 1
    public static final int TRAFFIC_ITERATION = 1;
    // Timer for client to wait before reading output: 5 seconds
    private static final long CLIENT_WAIT_TIMER = 5;
    // Maximum number of tests if there is not speed at the server
    private static final int MAX_ERRORS = 3;
    // Difference between upper and lower bound for udp expected bandwidth
    private static final int BW_THRESHOLD = 3;
    // Maximum iterations for UDP speed tests
    private static final int MAX_UDP_SPEED_TIMES = 20;

    private boolean mUdpTrafficTestFlag = false;
    private int mTestIterations = 3;
    private static int mTestTime = 60; // 60 seconds;
    private ITestDevice mTestDevice = null;
    private String mIpAddress = null;
    private IperfServer mServerRunnable = null;
    private Thread mServerThread;
    // record speed test results
    private SimpleStats mSpeeds = new SimpleStats();
    private boolean mMixMode = false;
    private static final boolean DEBUG = true;

    public IperfTestRunner(ITestDevice testDevice) throws DeviceNotAvailableException {
        mTestDevice = testDevice;
        if (!iperfIsStarted()) {
            // if iperf is installed but has never been started,
            // start it to create the executable.
            CLog.v("start iperf to create the executable");
            mTestDevice.executeShellCommand(IPERF_START_COMMAND);
            // wait 10 seconds for iperf to start
            getRunUtil().sleep(10 * 1000);
            Assert.assertTrue(iperfIsStarted());
        }
        if (DEBUG) {
            printIperfProcesses();
        }
        // clear iperf processes that are running on host and device
        clearDeviceIperfProcess();
        clearHostIperfProcess();
    }

    // Check whether iperf has ever been started
    private boolean iperfIsStarted() throws DeviceNotAvailableException {
        String cmd = "ls " + IPERF_PATH;
        String iperfExec = mTestDevice.executeShellCommand(cmd);
        CLog.v("iperfExec path: %s", iperfExec);
        String noFileStr = "No such file or directory";
        if (iperfExec == null) return false;
        if (iperfExec.contains(noFileStr)) {
            return false;
        }
        if (iperfExec.contains(IPERF_PATH)) {
            return true;
        } else {
            return false;
        }
    }

    public void setPerfTestIterations(int iteration) {
        mTestIterations = iteration;
    }

    public void setTestTime(int time) {
        mTestTime = time;
    }

    public void setUdpTrafficTestFlag(boolean flag) {
        mUdpTrafficTestFlag = flag;
    }

    public void setMixMode(boolean flag) {
        mMixMode = flag;
    }

    public boolean getMixMode() {
        return mMixMode;
    }
    /**
     * Measure TCP upload speed
     */
    public double getTcpUploadSpeed() throws DeviceNotAvailableException {
        return runSpeedTest(TCP, UPLOAD);
    }

    /**
     * Measure TCP download speed
     */
    public double getTcpDownloadSpeed() throws DeviceNotAvailableException {
        return runSpeedTest(TCP, DOWNLOAD);
    }

    /**
     * Measure UDP upload speed
     */
    public double getUdpUploadSpeed() throws DeviceNotAvailableException {
        setUdpTrafficTestFlag(false);
        return runSpeedTest(UDP, UPLOAD);
    }

    /**
     * Measure UDP download speed
     */
    public double getUdpDownloadSpeed() throws DeviceNotAvailableException {
        setUdpTrafficTestFlag(false);
        return runSpeedTest(UDP, DOWNLOAD);
    }

    /**
     * run iperf for tcp traffic test
     * @return
     * @throws DeviceNotAvailableException
     */
    public boolean runTcpTrafficTest() throws DeviceNotAvailableException {
        CLog.v("start TCP traffic test");
        setPerfTestIterations(TRAFFIC_ITERATION);
        setTestTime(TRAFFIC_TIMER); // set running time
        double ul = INVALID_SPEED;
        if (!mMixMode) {
            ul = runSpeedTest(TCP, UPLOAD);
        }
        double dl = runSpeedTest(TCP, DOWNLOAD);
        CLog.v("end TCP traffic test");
        if (mMixMode) {
            return dl > 0;
        } else {
            return (ul > 0) && (dl > 0);
        }
    }

    /**
     * run iperf for udp traffic test
     * @return
     * @throws DeviceNotAvailableException
     */
    public boolean runUdpTrafficTest() throws DeviceNotAvailableException {
        CLog.v("start UDP traffic test");
        setPerfTestIterations(TRAFFIC_ITERATION);
        setTestTime(TRAFFIC_TIMER); // set running time
        setUdpTrafficTestFlag(true);
        double ul = INVALID_SPEED;
        double dl = INVALID_SPEED;
        if (!mMixMode) {
            ul = runSpeedTest(UDP, UPLOAD);
        }
        dl = runSpeedTest(UDP, DOWNLOAD);
        CLog.v("end UDP traffic test");
        // reset the traffic test flag
        setUdpTrafficTestFlag(false);
        if (mMixMode) {
            return dl > 0;
        } else {
            return (ul > 0) && (dl > 0);
        }
    }

    /**
     * @return details speed results
     */
    public SimpleStats getSpeeds() {
        return mSpeeds;
    }

    /**
     * Run speed test with given protocol and speed type
     * @param protocol
     */
    public double runSpeedTest(String protocol, int speedType) throws DeviceNotAvailableException {
        // Initialize mSpeeds before running any speed test.
        mSpeeds = new SimpleStats();
        mIpAddress = mTestDevice.getIpAddress();
        if (mIpAddress == null) {
            CLog.v("failed to get ip address of the device");
            return INVALID_SPEED;
        }
        int i = 0;
        for (int count = 0; (i < mTestIterations) && (count < 2 * mTestIterations); count++) {
            // count is the total number of tests that require restarting the server
            // start server thread
            if (!startServer(protocol, speedType)) {
                CLog.v("start server thread failed");
                return INVALID_SPEED;
            }
            // run the speed test until an invalid speed is returned, reset server in that case
            while(i < mTestIterations) {
                double speed = runClientAndReadSpeed(protocol, speedType);
                if (mTestIterations > TRAFFIC_ITERATION) {
                    // only calculate average speed for performance test.
                    if (speed > INVALID_SPEED) {
                        CLog.v("speed %d: %f", i, speed);
                        mSpeeds.add(speed);
                        ++i;
                    } else {
                        // if returned speed is invalid, sth bad happen, reset server
                        CLog.v("speed is invalid, reset server");
                        stopServer();
                        break;
                    }
                } else {
                    // if this is for traffic test, run once and return the speed
                    return speed;
                }
            }
        }
        if (mSpeeds.meanOverOneStandardDeviationRange() != null) {
            return mSpeeds.meanOverOneStandardDeviationRange();
        } else {
            return INVALID_SPEED;
        }
    }

    private boolean startServer(String protocol, int speedType) throws DeviceNotAvailableException {
        // Stop all iperf process on the host and device
        CLog.v("START IPERF SERVER");
        if (DEBUG) {
            CLog.v("\n\niperf processes before cleaning....");
            printIperfProcesses();
        }
        clearDeviceIperfProcess();
        if (!mMixMode) {
            // for mix functional mode, all devices are running functional test at the same time
            // clear iperf process on host side could destroy other devices' traffic tests.
            clearHostIperfProcess();
        }

        if (DEBUG) {
            CLog.v("iperf processes after cleaning....\n\n");
        }

        mServerRunnable = new IperfServer(protocol, speedType);
        synchronized (mServerRunnable) {
            mServerThread = new Thread(mServerRunnable);
            mServerThread.start();
            try {
                CLog.v("Wait 10 seconds for server thread to start");
                mServerRunnable.wait(10 * 1000);
            } catch (InterruptedException e) {
                CLog.e("server thread is interrupted: " + e.toString());
            }
        }
        return ((mServerRunnable != null) && (mServerThread != null)
                && (mServerThread.getState().equals(Thread.State.RUNNABLE)));
    }

    /**
     * Stop server thread
     */
    private void stopServer() throws DeviceNotAvailableException {
        if ((mServerThread != null) && (mServerRunnable != null)) {
            CLog.v("state of the server thread: " + mServerThread.getState().toString());
            if (DEBUG) {
                CLog.v("Before stopping server, iperf processes: ");
                printIperfProcesses();
                CLog.v("\n");
            }
            mServerRunnable.terminate();
            try {
                mServerThread.join(10 * 1000);
            } catch (InterruptedException e) {
                // Don't need to do anything here.
                // If waiting for server thread to stop is interrupted,
                // it won't affect the next speed test as a clean up will be executed before
                // starting the server next time.
                CLog.v("wait for server thread to terminate is interrupted: " + e.toString());
            }
            if (DEBUG) {
                CLog.v("After stopping server, iperf processes: ");
                printIperfProcesses();
            }
        }
    }

    private double[] getUdpServerSpeedAndLossRate() {
        if (mServerRunnable != null) {
            return mServerRunnable.readUdpServerSpeedAndLossRate();
        } else {
            double[] res = new double[2];
            res[0] = INVALID_SPEED;
            res[1] = -1;
            return res;
        }
    }

    private double getTcpServerSpeed() {
        if (mServerRunnable != null) {
            return mServerRunnable.readTcpServerSpeed();
        } else {
            return INVALID_SPEED;
        }
    }

    /**
     * run TCP client and return TCP throughput
     * @param speedType is UPLOAD or DOWNLOAD
     * @return throughput value
     * @throws DeviceNotAvailableException
     */
    private double runClientAndReadTcpSpeed(int speedType) throws DeviceNotAvailableException {
        String command;
        if (speedType == UPLOAD) {
            command = String.format(DEVICE_CLIENT_COMMAND, IPERF_PATH,
                    IotUtil.IPERF_HOST_SERVER, mTestTime);
        } else {
            command = String.format(HOST_CLIENT_COMMAND, mIpAddress, mTestTime);
        }
        CLog.v("START IPERF CLIENT, command: %s", command);
        if (speedType == UPLOAD) {
            mTestDevice.executeShellCommand(command);
        } else {
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(command);
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                String line;
                List<String> lines = new ArrayList<String>();
                while ((line = br.readLine()) != null) {
                    CLog.v("line: %s", line);
                    lines.add(line);
                }
                int status = p.waitFor();
                if (status != 0) {
                    CLog.v("client proess didn't exit successfully");
                    return INVALID_SPEED;
                }
            } catch (IOException e) {
                CLog.e("start client command failed: " + e.toString());
            } catch (InterruptedException e) {
                CLog.e("Wait for client to finish interrupted" + e.toString());
            }
        }
        getRunUtil().sleep(mTestTime * 1000);
        return getTcpServerSpeed();
    }

    /**
     * Run UDP client and get UDP throughput
     * @param speedType is UPLOAD or DOWNLOAD
     * @return throughput value
     * @throws DeviceNotAvailableException
     */
    private double runClientAndReadUdpSpeed(int speedType)
            throws DeviceNotAvailableException {
        String command_prefix;
        String command;
        double target_bw = UDP_TARGET_BW;
        double lower = 0;
        double upper= target_bw;

        if (mUdpTrafficTestFlag) {
            target_bw = 10.0;
        }
        if (speedType == UPLOAD) {
            command_prefix = String.format(DEVICE_CLIENT_COMMAND, IPERF_PATH,
                    IotUtil.IPERF_HOST_SERVER, mTestTime);
        } else {
            command_prefix = String.format(HOST_CLIENT_COMMAND, mIpAddress, mTestTime);
        }
        int testRun = 0;
        int errorTimes = 0;
        double[] speedAndLossRate = new double[2];
        int udpRunTime = 0;
        while ((testRun < mTestIterations) && (udpRunTime < MAX_UDP_SPEED_TIMES)) {
            ++udpRunTime;
            CLog.v("lower: %f, upper: %f, target_bw: %f", lower, upper, target_bw);
            command = String.format("%s -u -b %.2fm", command_prefix, target_bw);
            CLog.v("START IPERF CLIENT, command: %s", command);
            if (speedType == UPLOAD) {
                mTestDevice.executeShellCommand(command);
            } else {
                try {
                    Process p = Runtime.getRuntime().exec(command);
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(p.getInputStream()));
                    String line;
                    List<String> lines = new ArrayList<String>();
                    while ((line = br.readLine()) != null) {
                        CLog.v("line: %s", line);
                        lines.add(line);
                    }
                    int status = p.waitFor();
                    if (status != 0) {
                        CLog.v("client process didn't exit successfully");
                        return INVALID_SPEED;
                    }
                } catch (IOException e) {
                    CLog.e("start client command failed: " + e.toString());
                } catch (InterruptedException e) {
                    CLog.e("Wait for client to finish interrupted" + e.toString());
                }
            }

            getRunUtil().sleep(CLIENT_WAIT_TIMER * 1000);
            speedAndLossRate = getUdpServerSpeedAndLossRate();
            double speed = speedAndLossRate[0];
            double lossRate = speedAndLossRate[1];

            if (mUdpTrafficTestFlag) {
                mSpeeds.add(speed);
                testRun++;
            } else {
                // Using binary search to find the target bandwidth
                if (lossRate >= LOSSRATE_THRESHOLD) {
                    upper = target_bw;
                    target_bw = (lower + upper) / 2;
                } else if (lossRate >= 0) {
                    if ((upper - lower) < BW_THRESHOLD) {
                        testRun++;
                        mSpeeds.add(speed);
                        CLog.v("testRun: %d", testRun);
                        CLog.v("speed: %f", speed);
                    } else {
                        lower = target_bw;
                        target_bw = (lower + upper) / 2;
                    }
                } else {
                    // if lossRate < 0, run the test again
                    errorTimes++;
                }
                if (errorTimes > MAX_ERRORS) {
                    CLog.v("Hit max error times (%d) due to loss rate is not valid.", MAX_ERRORS);
                    break;
                }
            }
            // wait for 30 seconds before the next run
            getRunUtil().sleep(30 * 1000);
        }
        if (mSpeeds.mean() == null) {
            return INVALID_SPEED;
        } else {
            return mSpeeds.mean().doubleValue();
        }
    }

    private double runClientAndReadSpeed(String protocol, int speedType)
            throws DeviceNotAvailableException {
        if (UDP.equals(protocol)) {
            return runClientAndReadUdpSpeed(speedType);
        } else {
            return runClientAndReadTcpSpeed(speedType);
        }
    }

    private List<String> getProcessIds(String result) {
        List<String> processIds = new ArrayList<String>();
        if (result == null) {
            return processIds;
        }
        String[] lines = result.split(System.getProperty("line.separator"));
        for (String line : lines) {
            if ((line != null) && (line.contains("iperf"))) {
                CLog.v("line: " + line);
                String pId = line.trim().split("\\s+")[1];
                processIds.add(pId);
            }
        }
        return processIds;
    }

    private void clearHostIperfProcess() throws DeviceNotAvailableException {
        CLog.v("\nClear iperf process on host:\n");
        String command = String.format("ps aux | grep -v %s | grep [i]perf", IPERF_PATH);
        CommandResult result = getRunUtil().runTimedCmd(10 * 1000, "/bin/bash", "-c", command);
        if (!(result.getStatus().equals(CommandStatus.SUCCESS))) {
            CLog.e("failed to grep iperf processes: %s", result.getStderr());
        }
        List<String> processIds = getProcessIds(result.getStdout());
        // stop all iperf processes
        for (String pId: processIds) {
            stopIperfProcesses(pId, false);
        }
    }

    private void clearDeviceIperfProcess() throws DeviceNotAvailableException {
        CLog.v("\nClear iperf process on device:");
        String output = mTestDevice.executeShellCommand("ps | grep " + IPERF_PATH);
        List<String> processIds = getProcessIds(output);
        for (String pId: processIds) {
            stopIperfProcesses(pId, true);
        }
    }

    private void stopIperfProcesses(String processId, boolean deviceFlag)
            throws DeviceNotAvailableException {
        String command = String.format("kill -9 %s", processId);
        if (deviceFlag) {
            mTestDevice.executeShellCommand(command);
        } else {
            getRunUtil().runTimedCmd(10 * 1000, "/bin/bash", "-c", command);
        }
    }

    private void printIperfProcesses() throws DeviceNotAvailableException {
        CLog.v("print iperf processes on device:");
        String command = String.format("ps | grep %s", IPERF_PATH);
        String output = mTestDevice.executeShellCommand(command);

        CLog.v("\nprint iperf processes on host:");
        command = String.format("ps aux | grep -v %s | grep [i]perf", IPERF_PATH);
        CommandResult result = getRunUtil().runTimedCmd(10 * 1000, "/bin/bash", "-c", command);
        CLog.v(result.getStdout());
    }

    protected static IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    public class IperfServer implements Runnable {
        public static final int MAX_HEAD_LINES = 5;
        public static final String HEAD_LINE_DIVISION = "----------";
        protected String command;
        protected String commandSuffix = null;
        protected List<String> lines = new ArrayList<String>();
        protected Process p;
        protected String protocol;
        protected int speedType;
        protected boolean stop = false;
        protected BufferedReader br = null;
        protected ServerUdpTrafficParser udpParser = new ServerUdpTrafficParser();
        protected TcpSpeedParser tcpParser = new TcpSpeedParser();

        public IperfServer(String protocol, int speedType) {
            this.protocol = protocol;
            this.speedType = speedType;

            if (speedType == UPLOAD) {
                // testing upload speed, run iperf server at host
                command = HOST_SERVER_COMMAND;
            } else {
                // testing download speed, run iperf server at device side
                command = String.format(DEVICE_SERVER_COMMAND, mTestDevice.getSerialNumber(),
                        IPERF_PATH);
            }
            if (UDP.equals(protocol)) {
                command += " -u";
            }
        }
        @Override
        public void run() {
            CLog.v("server command: %s", command);
            try {
                p = Runtime.getRuntime().exec(command);
                br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                // read server head lines
                // e.g. a TCP server head line is:
                // ------------------------------------------------------------
                // Server listening on TCP port 5001
                // Binding to local address 192.168.2.1
                // TCP window size: 85.3 KByte (default)
                // ------------------------------------------------------------
                int lineNum = 0;
                int divLines = 0;
                while (lineNum < MAX_HEAD_LINES) {
                    line = br.readLine();
                    lineNum++;
                    if (line != null) {
                        CLog.v("line: %s", line);
                        sb.append(line);
                        if (line.contains(HEAD_LINE_DIVISION)) {
                            divLines++;
                        }
                    }
                    if (divLines >= 2) {
                        break;
                    }
                }
                if (isHeadLineValid(sb.toString())) {
                    synchronized(this) {
                        CLog.v("Server thread starts successfully, notifiy others");
                        this.notify();
                    }
                } else {
                    // stop the thread if starting server failed.
                    CLog.v("Validating headline failed");
                    stop = true;
                }
            } catch (IOException e) {
                CLog.e("Failed to start a process: " + Arrays.asList(e.getStackTrace()));
                if (p != null) {
                    p.destroy();
                }
            }

            try {
                String line;
                while(!stop) {
                    if ((line = br.readLine()) != null) {
                        CLog.v("line: %s", line);
                        lines.add(line);
                    }
                }
            } catch (IOException e) {
                CLog.e("Stop server process!");
            } finally {
                  if (p != null) {
                      p.destroy();
                  }
            }
        }

        public double[] readUdpServerSpeedAndLossRate() {
            udpParser.processNewLines(lines.toArray(new String[lines.size()]));
            lines.clear();
            double[] speedLossRate = new double[2];
            speedLossRate[0] = udpParser.getSpeed();
            speedLossRate[1] = udpParser.getLossRate();
            return speedLossRate;
        }

        public double readTcpServerSpeed() {
            tcpParser.processNewLines(lines.toArray(new String[lines.size()]));
            lines.clear();
            return tcpParser.getSpeed();
        }

        public void terminate() throws DeviceNotAvailableException {
            CLog.v("STOP SERVER THREAD");
            stop = true;

            // Send data to the pipe to unblock the I/O
            // save the current test state
            int curIteration = mTestIterations;
            int curTestTime = mTestTime;
            boolean curUdpTrafficFlag = mUdpTrafficTestFlag;
            setPerfTestIterations(TRAFFIC_ITERATION);
            setTestTime(2); // set running time
            if (UDP.equals(protocol)) {
                // set UDP Traffic test
                setUdpTrafficTestFlag(true);
            }
            runClientAndReadSpeed(protocol, speedType);
            // restore test state
            setPerfTestIterations(curIteration);
            setTestTime(curTestTime);
            setUdpTrafficTestFlag(curUdpTrafficFlag);
        }

        protected boolean isHeadLineValid(String headLines) {
            return ((headLines != null) && headLines.contains("Server listening on")
                    && (headLines.contains("TCP window size")
                            || headLines.contains("UDP buffer size")));
        }
    }

    public static class SpeedParser extends MultiLineReceiver {
        static final protected String FAILED_PATTERN = "Connection refused";
        static final protected String NUMBER_REGEX = "\\d+(?:\\.\\d+)?";
        //    [  3]  0.0- 9.2 sec  4.25 MBytes  3.14 Mbits/sec
        static final protected String BANDWIDTH_REGEX = "\\[\\s+\\d+\\]\\s+"
                + "(" + NUMBER_REGEX + ")-(?:\\s)?(" + NUMBER_REGEX + ")\\s+sec\\s+"
                + "(" + NUMBER_REGEX + ")\\s+[M|G]Bytes\\s+"
                + "(" + NUMBER_REGEX + ")\\s+Mbits\\/sec";
        static final double THRESHOLD = 2.0;
        boolean mCancel = false;
        double mSpeed = INVALID_SPEED;

        @Override
        public boolean isCancelled() {
            return mCancel;
        }

        @Override
        public void processNewLines(String[] lines) {}

        public double getSpeed() {
            return mSpeed;
        }

        public boolean validateReceiveTime(double receiveTime) {
            return (receiveTime <= mTestTime * THRESHOLD);
        }
    }

    public static class TcpSpeedParser extends SpeedParser {
        //    [  3]  0.0-12.2 sec  4.25 MBytes  3.14 Mbits/sec
        protected static final Pattern BANDWIDTH_PATTERN = Pattern.compile(BANDWIDTH_REGEX);
        @Override
        public void processNewLines(String[] lines) {
            mSpeed = INVALID_SPEED;
            for (String line : lines) {
                if (line.contains(FAILED_PATTERN)) {
                    mSpeed = INVALID_SPEED;
                    break;
                }
                Matcher match = BANDWIDTH_PATTERN.matcher(line);
                if (match.matches()) {
                    // If the receiver received the package too late, ignore the test
                    double receiveTime = Double.parseDouble(match.group(2));
                    if (!validateReceiveTime(receiveTime)) {
                        mSpeed = INVALID_SPEED;
                    } else {
                        // mSpeed returns 3.14 in this case
                        mSpeed = Double.parseDouble(match.group(4));
                    }
                }
            }
        }
    }

    // Parse results from UDP server side
    public static class ServerUdpTrafficParser extends SpeedParser {
        //[ ID] Interval       Transfer     Bandwidth        Jitter   Lost/Total Datagrams
        //[  3]  0.0-62.0 sec  34.7 MBytes  4.69 Mbits/sec   9.522 ms  374/25121 (1.5%)
        //[  3]  0.0-62.0 sec  1 datagrams received out-of-order
        //read failed: Connection refused
        protected static final String UDP_TRAFFIC_REGEX =
                BANDWIDTH_REGEX
                        + "\\s+"
                        + "("
                        + NUMBER_REGEX
                        + ")\\s+ms\\s+"
                        + "("
                        + NUMBER_REGEX
                        + ")\\/\\s*("
                        + NUMBER_REGEX
                        + ")\\s+\\(.*\\)";
        protected static final Pattern UDP_TRAFFIC_PATTERN = Pattern.compile(UDP_TRAFFIC_REGEX);
        double mLossRate;

        @Override
        public void processNewLines(String[] lines) {
            mSpeed = INVALID_SPEED;
            mLossRate = -1;
            for (String line : lines) {
                Matcher match = UDP_TRAFFIC_PATTERN.matcher(line);
                if (match.matches()) {
                    // mSpeed returns 4.69 in this case
                    mSpeed = Double.parseDouble(match.group(4));
                    mLossRate = (100 * Double.parseDouble(match.group(6))) /
                            Double.parseDouble(match.group(7));
                }
            }
        }

        public double getLossRate() {
            return mLossRate;
        }
    }

    public static class UdpSpeedParser extends SpeedParser {
        protected static final String UDP_BD_REPORT = "Server Report";
        //[  3] Server Report:
        //[  3]  0.0-60.0 sec  5.67 GBytes   812 Mbits/sec   0.020 ms    0/4140837 (0%)
        protected static final String UDP_BANDWIDTH_REGEX =
                BANDWIDTH_REGEX
                        + "\\s+"
                        + "("
                        + NUMBER_REGEX
                        + ")\\s+ms\\s+"
                        + "("
                        + NUMBER_REGEX
                        + ")\\/\\s*("
                        + NUMBER_REGEX
                        + ")\\s+\\(.*\\)";
        protected static final Pattern UDP_BANDWIDTH_PATTERN = Pattern.compile(UDP_BANDWIDTH_REGEX);
        double mLossRate;

        @Override
        public void processNewLines(String[] lines) {
            boolean bd_flag = false;
            for (String line : lines) {
                if (line.contains(FAILED_PATTERN)) {
                    mSpeed = INVALID_SPEED;
                    break;
                }
                if (line.contains(UDP_BD_REPORT)) {
                    bd_flag = true;
                }
                if (bd_flag) {
                    Matcher match = UDP_BANDWIDTH_PATTERN.matcher(line);
                    if (match.matches()) {
                        // Calculate loss rate instead of reading it from the output
                        // as the loss rate could be presented in scientific notation
                        // e.g (1e+02%)
                        mLossRate = (100 * Double.parseDouble(match.group(6))) /
                                Double.parseDouble(match.group(7));
                        CLog.v("loss rate is: %f", mLossRate);
                        // mSpeed returns 812 in this case
                        mSpeed = Double.parseDouble(match.group(4));
                    }
                }
            }
        }

        public double getLossRate() {
            return mLossRate;
        }
    }
}
