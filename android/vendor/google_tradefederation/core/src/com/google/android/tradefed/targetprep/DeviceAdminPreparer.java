package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link ITargetPreparer} that configures a Device Admin policies in preparation for running
 * tests, as well as during test environment clean up. After setting the Device Admin policy on
 * the device, a reboot is performed to ensure the changes are picked up. Requires adb root.
 * <p/>
 */
@OptionClass(alias = "device-admin")
public class DeviceAdminPreparer implements ITargetPreparer, ITargetCleaner {
    private static final String DEVICE_ADMIN_PATH = "/data/system/device_policies.xml";

    @Option(name = "max-attempts",
            description = "Maximum number of attempts to push the policy data to the device.")
    private int mMaxAttempts = 5;

    @Option(name = "setup-policy-path", description = "The policy data path used for setup.")
    private String mSetupPolicyPath = null;

    @Option(name = "teardown-policy-path", description = "The policy data path used for tear down.")
    private String mTeardownPolicyPath = null;

    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        if (mDisable) return;
        if (mSetupPolicyPath != null) {
            updateDevicePolicy(device, mSetupPolicyPath);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        if (mDisable) return;
        if (mTeardownPolicyPath != null) {
            try {
                updateDevicePolicy(device, mTeardownPolicyPath);
            } catch (TargetSetupError tse) {
                throw new DeviceNotAvailableException(tse.toString(), device.getSerialNumber());
            }
        }
    }

    /**
     * Updates the device policy xml on the device, then restarts to allow changes
     * to be picked up.
     * @param device
     * @param policyPath
     * @throws DeviceNotAvailableException
     * @throws IllegalStateException
     */
    public void updateDevicePolicy(ITestDevice device, String policyPath)
            throws TargetSetupError, DeviceNotAvailableException, IllegalStateException {
        CLog.i(String.format("pushing device policies to device from %s", policyPath));
        InputStream policyData = null;
        try {
            policyData = getInputStreamFromPolicyDataPath(policyPath);

            if (policyData != null) {
                String data = StreamUtil.getStringFromStream(policyData);

                boolean pushSuccess = false;
                for (int i = 0; i < mMaxAttempts; i++) {
                    pushSuccess = device.pushString(data, DEVICE_ADMIN_PATH);

                    CLog.i(String.format(
                            "attempt #%d pushing policy to device, status = %b ", i, pushSuccess));
                    if (pushSuccess) {
                        // need to reboot for this to take effect
                        device.reboot();
                        break;
                    }
                }
            } else {
                throw new TargetSetupError(String.format("Could not find %s from tradefed "
                        + "classpath", policyPath), device.getDeviceDescriptor());
            }
        } catch (IOException e) {
            // StreamUtil.getStringFromStream failure
            throw new TargetSetupError(e.toString(), device.getDeviceDescriptor());
        } finally {
            StreamUtil.close(policyData);
        }
    }

    /**
     * Gets the InputStream object from the policy data path.
     * @return The policy data InputStream object
     */
    private InputStream getInputStreamFromPolicyDataPath(String policyPath) {
        if (policyPath == null) {
            throw new IllegalStateException("policy data path can't be null");
        }

        return this.getClass().getResourceAsStream(policyPath);
    }
}
