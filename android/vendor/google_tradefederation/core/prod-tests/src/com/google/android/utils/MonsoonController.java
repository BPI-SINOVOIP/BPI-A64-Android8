/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.utils;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This class wraps the interaction logic to control a monsoon power monitor. */
public class MonsoonController implements IMonsoonController {

    private static final int MAX_SAMPLES = 2147483647;
    private static final String CHARSET_NAME = "UTF-8";
    private File mMonsoonRawFile;
    private Process mSamplingProcess;
    private Writer mMonsoonFileWriter;
    private RuntimeException mMeasurementException;

    public enum InteractionMode {
        USE_SERIAL_NUMBER,
        USE_SERIAL_PORT
    }

    private static final long CMD_TIMEOUT_SEC = 30 * 1000;

    private static final long SMALL_SLEEP_TIME_SECS = 3 * 1000; // 3 seconds

    private static final Pattern MONSOON_SERIAL_OUTPUT_REGEX =
            Pattern.compile(".*serialNumber:\\s*(\\S+).*");

    /** Argument that precedes the voltage to be set to the monsoon */
    private static final String PARAM_VOLTAGE = "--voltage";

    /** Argument to dump a monsoon serial status */
    private static final String PARAM_STATUS = "--status";

    /** Argument that precedes which monsoon will be manipulated. */
    private static final String PARAM_SERIALNO = "--serialno";

    /** Argument that precedes the monsoon's serial port */
    private static final String PARAM_PORT = "--device";

    /** Argument that precedes which 'usb pass through' option will be performed */
    private static final String PARAM_USBPASSTHROUGH = "--usbpassthrough";

    /** Argument to turn off the USB connection */
    private static final String OFF = "off";

    /** Argument to turn on the USB connection */
    private static final String ON = "on";

    /** Argument to check on the monsoon current state */
    private static final String STATUS_PARAM = "--status";

    /** Argument that precedes what should the start current be */
    private static final String START_CURRENT_PARAM = "--startcurrent";

    /** Argument that precedes what should the current be */
    private static final String CURRENT_PARAM = "--current";

    private String mSerialPort = null;
    private final String mSerialNumber;
    private final String mExecutablePath;
    private InteractionMode mInteractionMode;

    /**
     * Creates an instance of MonsoonUsbSwitch.
     *
     * @param executablePath the path to the monsoon executable library.
     * @param serial the serial number of the monsoon power monitor to be handled.
     * @param mode specifies if the serial port or the serial number should be used to interact with
     *     the monsoon power monitor.
     */
    public MonsoonController(String executablePath, String serial, InteractionMode mode) {
        mExecutablePath = executablePath;
        mSerialNumber = serial;
        mInteractionMode = mode;
    }

    /**
     * Creates an instance of MonsoonUsbSwitch.
     *
     * @param executablePath the path to the monsoon executable library.
     * @param serial the serial number of the monsoon power monitor to be handled.
     */
    public MonsoonController(String executablePath, String serial) {
        this(executablePath, serial, InteractionMode.USE_SERIAL_NUMBER);
    }

    @Override
    public void connectUsb() {
        getRunUtil()
                .runTimedCmd(
                        CMD_TIMEOUT_SEC,
                        mExecutablePath,
                        getIdentifierParam(),
                        getIdentifier(),
                        PARAM_USBPASSTHROUGH,
                        ON);
    }

    @Override
    public void disconnectUsb() {
        getRunUtil()
                .runTimedCmd(
                        CMD_TIMEOUT_SEC,
                        mExecutablePath,
                        getIdentifierParam(),
                        getIdentifier(),
                        PARAM_USBPASSTHROUGH,
                        OFF);
    }

    @Override
    public String getMonsoonSerialNumber() {
        return mSerialNumber;
    }

    @Override
    public String getMonsoonSerialPort() {
        if (mSerialPort != null) {
            return mSerialPort;
        }

        File[] files = getProbableSerialPorts();
        for (File port : files) {
            final String portName = port.getAbsolutePath();
            if (isSerialPortSerialNumberMatch(portName)) {
                mSerialPort = portName;
                return mSerialPort;
            }
        }

        return null;
    }

    @Override
    public boolean freeResources() {
        if (getMonsoonSerialPort() == null) {
            // nothing can be done if the without the serial port.
            CLog.i("No serial port available, can't free resources.");
            return false;
        }

        CLog.i("Going to reset port %s", getMonsoonSerialPort());
        int pid = getPid();
        if (pid > 0) {
            killProcess(pid);
            getRunUtil().sleep(SMALL_SLEEP_TIME_SECS);
        }
        return true;
    }

    @Override
    public void setMonsoonVoltage(double voltage) {
        CLog.i("Setting monsoon voltage to %.2f", voltage);
        getRunUtil()
                .runTimedCmd(
                        CMD_TIMEOUT_SEC,
                        mExecutablePath,
                        getIdentifierParam(),
                        getIdentifier(),
                        PARAM_VOLTAGE,
                        Double.toString(voltage));
        getRunUtil().sleep(SMALL_SLEEP_TIME_SECS);
    }

    @Override
    public void setMonsoonStartCurrent(double current) {
        CLog.i("Setting monsoon start current to %.2f", current);
        getRunUtil()
                .runTimedCmd(
                        CMD_TIMEOUT_SEC,
                        mExecutablePath,
                        getIdentifierParam(),
                        getIdentifier(),
                        START_CURRENT_PARAM,
                        Double.toString(current));
    }

    @Override
    public void setMonsoonCurrent(double current) {
        CLog.i("Setting monsoon current to %.2f", current);
        getRunUtil()
                .runTimedCmd(
                        CMD_TIMEOUT_SEC,
                        mExecutablePath,
                        getIdentifierParam(),
                        getIdentifier(),
                        CURRENT_PARAM,
                        Double.toString(current));
    }

    @Override
    public String dumpMonsoonStatus() {
        CLog.d("Dumping monsoon status");
        CommandResult result =
                getRunUtil()
                        .runTimedCmd(
                                CMD_TIMEOUT_SEC,
                                mExecutablePath,
                                getIdentifierParam(),
                                getIdentifier(),
                                STATUS_PARAM);
        String output = result.getStdout().trim();
        CLog.d(output);
        return output;
    }

    @Override
    public void startMeasurement(final int frequency) {
        // Dump monsoon data into text file
        Thread thread =
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            String[] monsoonCmd =
                                    new String[] {
                                        "monsoon",
                                        "--serialno",
                                        mSerialNumber,
                                        "--voltage",
                                        "4.2",
                                        "--hz",
                                        Integer.toString(frequency),
                                        "--samples",
                                        Integer.toString(MAX_SAMPLES),
                                        "--t"
                                    };

                            CLog.e("Run monsoon command : %s", Arrays.toString(monsoonCmd));

                            mSamplingProcess = new ProcessBuilder(monsoonCmd).start();
                            InputStreamReader isr =
                                    new InputStreamReader(
                                            mSamplingProcess.getInputStream(), CHARSET_NAME);
                            BufferedReader reader = new BufferedReader(isr);

                            CLog.v("Starting monsoon measurement");
                            mMonsoonRawFile = File.createTempFile("monsoon_raw", ".txt");
                            CLog.v(
                                    "Monsoon data to be stored at: %s",
                                    mMonsoonRawFile.getAbsolutePath());
                            mMonsoonFileWriter =
                                    new BufferedWriter(new FileWriter(mMonsoonRawFile, true));

                            String line;

                            while ((line = reader.readLine()) != null) {
                                mMonsoonFileWriter.write(String.format("%s\n", line));
                            }
                        } catch (IOException e) {
                            CLog.e("Fails to capture the monsoon data %s", e.toString());
                            mMeasurementException = new RuntimeException(e);
                            CLog.e(e);
                        }
                    }
                };
        thread.start();
    }

    @Override
    public void stopMeasurement() {
        if (mMeasurementException != null) {
            CLog.e("Measurement failed and couldn't complete.");
            throw mMeasurementException;
        }

        if (mSamplingProcess == null) {
            throw new IllegalStateException("Measurement was never started.");
        }

        try {
            CLog.e("Stopping monsoon measurement");
            mSamplingProcess.destroy();
            mMonsoonFileWriter.flush();
            mMonsoonFileWriter.close();
            mMonsoonFileWriter = null;

            int exitVal = mSamplingProcess.waitFor();
            mSamplingProcess = null;

            // error code 0 means everything went fine.
            // error code 143 means the process was terminated by SIGTERM which is expected
            // from this implementation.
            if (exitVal != 0 && exitVal != 143) {
                CLog.e("Exited with error code: " + exitVal);
            }
        } catch (IOException | InterruptedException e) {
            CLog.e(e);
        }
    }

    @Override
    public File getResultFile() {
        return mMonsoonRawFile;
    }

    @Override
    public boolean isUsbConnected() {
        String status = dumpMonsoonStatus();
        Pattern usbPassthroughOnPattern = Pattern.compile(".*usbPassthroughMode\\:\\s+1.*");
        return usbPassthroughOnPattern.matcher(status).matches();
    }

    /** Get {@link IRunUtil} to use. Exposed so unit tests can mock. */
    protected IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /** Exposed so unit tests can mock */
    protected File[] getProbableSerialPorts() {
        final String serialPortDir = "/dev/";
        final String serialPortFilePrefix = "ttyACM";
        File directory = new File(serialPortDir);
        return directory.listFiles(
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.startsWith(serialPortFilePrefix);
                    }
                });
    }

    private String getIdentifierParam() {
        sanitizeInteractionMode();
        switch (mInteractionMode) {
            case USE_SERIAL_NUMBER:
                return PARAM_SERIALNO;

            case USE_SERIAL_PORT:
                return PARAM_PORT;

            default:
                String message = String.format("Case %s is not supported", mInteractionMode);
                throw new IllegalArgumentException(message);
        }
    }

    private String getIdentifier() {
        sanitizeInteractionMode();
        switch (mInteractionMode) {
            case USE_SERIAL_NUMBER:
                return getMonsoonSerialNumber();

            case USE_SERIAL_PORT:
                return getMonsoonSerialPort();

            default:
                String message = String.format("Case %s is not supported", mInteractionMode);
                throw new IllegalArgumentException(message);
        }
    }

    private void sanitizeInteractionMode() {
        if (mInteractionMode == InteractionMode.USE_SERIAL_PORT && getMonsoonSerialPort() == null) {
            CLog.e("Couldn't find monsoon serial port. Falling back to use serial number.");
            mInteractionMode = InteractionMode.USE_SERIAL_NUMBER;
        }
    }

    private boolean isSerialPortSerialNumberMatch(String portName) {
        CommandResult result =
                getRunUtil()
                        .runTimedCmd(
                                CMD_TIMEOUT_SEC,
                                mExecutablePath,
                                PARAM_PORT,
                                portName,
                                PARAM_STATUS);
        String[] output = result.getStdout().trim().split(System.lineSeparator());

        for (String line : output) {
            Matcher m = MONSOON_SERIAL_OUTPUT_REGEX.matcher(line);
            if (m.matches()) {
                if (m.group(1).equals(getMonsoonSerialNumber())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void killProcess(int pid) {
        getRunUtil().runTimedCmd(CMD_TIMEOUT_SEC, "kill", "-2", String.valueOf(pid));
        getRunUtil().sleep(SMALL_SLEEP_TIME_SECS);
    }

    private int getPid() {
        CommandResult result =
                getRunUtil().runTimedCmd(CMD_TIMEOUT_SEC, "lsof", "-t", getMonsoonSerialPort());
        if (result.getStatus() != CommandStatus.SUCCESS) {
            CLog.i("lsof command failed.");
            return 0;
        }

        String output = result.getStdout().trim();

        if ("".equals(output)) {
            CLog.i("There is no process currently using this monsoon");
            return 0;
        }

        try {
            return (Integer.parseInt(output));
        } catch (NumberFormatException e) {
            CLog.e("Couldn't parse lsof output: %s", output);
            CLog.e(e);
            return 0;
        }
    }
}
