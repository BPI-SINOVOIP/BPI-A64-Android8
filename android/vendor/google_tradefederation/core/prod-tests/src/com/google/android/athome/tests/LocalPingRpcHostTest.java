// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.athome.tests;

import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.RunUtil;

import org.junit.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test that runs the ping rpc tests on a single device.
 * <p/>
 * Assumes that the PingEndpoint and PingClient apks are already installed on device.
 */
public class LocalPingRpcHostTest implements IRemoteTest, IDeviceTest {

    private InstrumentationTest mInstrTest;
    private ITestDevice mDevice;

    @Option(name = "iterations", description = "the number of iterations to perform.")
    private int mIterations = 1;

    @Option(name = "payload_size", description = "the ping payload size to use, in bytes.")
    private int mPayloadSize = 1024;

    @Option(name = "run-name", description = "the name used to report the ping metrics.")
    private String mRunName = "local_ping";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mDevice);

        int port = startPingEndpoint(mDevice);
        runPingTests(mDevice, listener, null, port);
    }

    /**
     * Runs the ping tests on given device
     *
     * @param device the {@link ITestDevice} to run the tests on
     * @param listener the {@link ITestInvocationListener} to pass metrics to
     * @param address the IP address of remote ping endpoint to contact. <code>null</code> for
     *            localhost
     * @param port the port of remote ping endpoint to contact.
     * @throws DeviceNotAvailableException
     */
    protected void runPingTests(ITestDevice device, ITestInvocationListener listener,
            String address, int port) throws DeviceNotAvailableException {
        mInstrTest = new InstrumentationTest();
        mInstrTest.setDevice(device);
        mInstrTest.setPackageName("com.android.athome.ping.client");
        if (address != null) {
            mInstrTest.addInstrumentationArg("address", address);
        }
        mInstrTest.addInstrumentationArg("port", Integer.toString(port));
        mInstrTest.addInstrumentationArg("iterations", Integer.toString(mIterations));
        mInstrTest.addInstrumentationArg("payload_size", Integer.toString(mPayloadSize));
        mInstrTest.setRunnerName(".PingTestRunner");
        MetricsCollectingListener collectingListener = new MetricsCollectingListener();
        mInstrTest.run(collectingListener);

        listener.testRunStarted(mRunName, 0);
        listener.testRunEnded(0, collectingListener.mMetrics);
    }

    /**
     * Start PingEndpointActivity, and wait for it to dump the port to logcat
     *
     * @throws AssertionError if endpoint was not started
     * @return the port of the {@link PortParser}
     * @throws DeviceNotAvailableException
     */
    protected int startPingEndpoint(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(
                "am start -n com.android.athome.ping.endpoint/.PingEndpointActivity");
        PortParser portParser = new PortParser();
        for (int i = 0; i < 10 && portParser.getPort() == null; i++) {
            if (i != 0) {
                RunUtil.getDefault().sleep(1 * 1000);
            }
            device.executeShellCommand("logcat -d", portParser);
        }
        Assert.assertNotNull("Failed to start ping endpoint", portParser.getPort());
        return portParser.getPort();
    }

    /**
     * Logcat shell receiver that parses ping endpoint port.
     * <p/>
     * Exposed for unit testing
     */
    static class PortParser extends MultiLineReceiver {

        private Integer mPort = null;
        private Pattern portPattern = Pattern.compile("PING_PORT:\\s(\\d+)$");
        private boolean mCancel = false;

        @Override
        public boolean isCancelled() {
            return mCancel ;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                Matcher portMatcher = portPattern.matcher(line);
                if (portMatcher.find()) {
                    mPort = Integer.parseInt(portMatcher.group(1));
                    mCancel = true;
                    return;
                }
            }
        }

        public Integer getPort() {
            return mPort;
        }
    }

    static class MetricsCollectingListener implements ITestInvocationListener {
        private Map<String, String> mMetrics = new HashMap<String, String>();

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
            mMetrics.putAll(testMetrics);
        }
    }
}
