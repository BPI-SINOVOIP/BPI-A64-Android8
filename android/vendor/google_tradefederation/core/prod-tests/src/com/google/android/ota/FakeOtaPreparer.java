// Copyright 2016 Google Inc.  All Rights Reserved.

package com.google.android.ota;

import com.android.loganalysis.util.ArrayUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Publishes a fake OTA package to the device using the fake-ota script.
 */
@OptionClass(alias = "fake-ota")
public class FakeOtaPreparer implements ITargetCleaner {

    private static final String OVERRIDE_ACTION =
            "com.google.gservices.intent.action.GSERVICES_OVERRIDE";
    private static final String FAKE_SUCCESS_MESSAGE = "Fake update succeeded.";
    private static final String FAKE_FAILURE_MESSAGE = "Fake update failed.";
    private static final String FAKE_MOBILE_NET_DELAY = "15";
    private static final String FAKE_PREFER_DATA = "1";
    // The "small" package used by the fake-ota script. It is intended only for userdebug builds
    // and will fail to verify on release builds.
    private static final String FAKE_DEFAULT_PKG =
            "http://android.clients.google.com/packages/internal/"
            + "dummy-ota-ab-small.dev.ab39d95e.zip";
    private static final long TIMEOUT = 100000;

    @Option(name = "update-url", description = "the value of Gservices update_url")
    private String mUpdateUrl = null;

    @Option(name = "update-file", description = "the file on the device to be used as the update;"
            + " mutually exclusive with update-url")
    private String mUpdateFile = null;

    @Option(name = "update-priority", description = "the priority of the update")
    private FakeOtaPriority mUpdatePriority = FakeOtaPriority.RECOMMENDED;

    @Option(name = "broadcast-timeout", description = "timeout for Gservices broadcast",
            isTimeVal = true)
    private long mTimeout = TIMEOUT;

    @Option(name = "broadcast-headroom", description = "time to wait for broadcast side effects" +
            " to propagate", isTimeVal = true)
    private long mHeadroom = 30000;

    @Option(name = "update-size", description = "the fake update size text")
    private String mUpdateSize = "small";

    @Option(name = "update-title", description = "the fake update title")
    private String mUpdateTitle = "fake-ota";

    @Option(name = "update-required-setup", description = "whether the update is SuW based")
    private String mUpdateRequiredSetup = "";

    @Option(name = "update-description", description = "the fake update description")
    private String mUpdateDescription = "This is a fake %s OTA update! Flop blop.";

    public enum FakeOtaPriority {
        RECOMMENDED("recommended", "2"),
        MANDATORY("mandatory", "3"),
        AUTOMATIC("automatic", "4");

        private String mPriority;
        private String mNumericUrgency;

        FakeOtaPriority(String priority, String numericUrgency) {
            mPriority = priority;
            mNumericUrgency = numericUrgency;
        }

        @Override
        public String toString() {
            return mPriority;
        }

        public String getNumericUrgency() {
            return mNumericUrgency;
        }
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        runFakeOtaWithArgs(device, getDefaultArgs(device));
        try {
            Thread.sleep(mHeadroom);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        runFakeOtaWithArgs(device,
                  "--esn update_url --esn update_description --esn update_urgency "
                + "--esn update_size --esn update_title --esn update_install_success_message "
                + "--esn update_install_failure_message --esn update_required_setup "
                + "--esn update_download_prefer_data --esn update_mobile_network_delay ");
    }

    protected String getDefaultArgs(ITestDevice device) throws TargetSetupError {
        StringBuilder sb = new StringBuilder();
        if (mUpdateUrl != null && mUpdateFile != null) {
            throw new TargetSetupError("update-url and update-file are mutually exclusive",
                    device.getDeviceDescriptor());
        }
        String url;
        if (mUpdateFile != null) {
            url = "file://" + mUpdateFile;
        } else if (mUpdateUrl != null) {
            url = mUpdateUrl;
        } else {
            url = FAKE_DEFAULT_PKG;
            CLog.i("Using default fake-ota url %s", url);
        }
        sb.append(String.format("-e update_url %s ", url));
        sb.append(String.format("-e update_description \"%s\" ", String.format(
                mUpdateDescription, mUpdatePriority.toString())));
        sb.append(String.format("-e update_urgency \"%s\" ", mUpdatePriority.getNumericUrgency()));
        sb.append(String.format("-e update_required_setup \"%s\" ", mUpdateRequiredSetup));
        sb.append(String.format("-e update_size \"%s\" ", mUpdateSize));
        sb.append(String.format("-e update_title \"%s\" ", mUpdateTitle));
        sb.append(String.format("-e update_install_success_message \"%s\" ", FAKE_SUCCESS_MESSAGE));
        sb.append(String.format("-e update_install_failure_message \"%s\" ", FAKE_FAILURE_MESSAGE));
        sb.append(String.format("-e update_mobile_network_delay \"%s\" ", FAKE_MOBILE_NET_DELAY));
        sb.append(String.format("-e update_download_prefer_data \"%s\" ", FAKE_PREFER_DATA));

        return sb.toString();
    }

    protected void runFakeOtaWithArgs(ITestDevice device, String args)
            throws DeviceNotAvailableException {
        String cmd = "am broadcast -a " + OVERRIDE_ACTION + " ";
        cmd += ArrayUtil.join(" ", args);
        device.executeShellCommand(cmd);
    }
}

