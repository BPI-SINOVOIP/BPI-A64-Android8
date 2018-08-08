// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice.DeviceState;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.TimeoutException;
import com.android.tradefed.command.ICommandScheduler;
import com.android.tradefed.command.ICommandScheduler.IScheduledInvocationListener;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.GlobalConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceAllocationState;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.FastbootHelper;
import com.android.tradefed.device.FreeDeviceState;
import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.device.IManagedTestDevice;
import com.android.tradefed.device.IMultiDeviceRecovery;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.android.tradefed.util.RunUtil;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** A {@link IMultiDeviceRecovery} which flashes device left in fastboot. */
@OptionClass(alias = "fastboot-recovery")
public class FastbootMultiDeviceRecovery implements IMultiDeviceRecovery {

    @Option(name = "online-timeout", description = "the timeout to wait for the device to be "
            + "online after flashing.", isTimeVal = true)
    private long mOnlineTimeout = 5 * 60 * 1000;

    @Option(name = "recovery-config-name", description = "The configuration to be used on the "
            + "device to recover.", mandatory = true)
    private String mRecoveryConfigName = "google/util/recovery";

    @Option(name = "recovery-test-tag", description = "The test-tag to be used for the recovery "
            + "invocation.", mandatory = true)
    private String mRecoveryTestTag;

    @Option(name = "extra-arg", description = "Extra arguments to be passed to the recovery "
            + "invocation.")
    private List<String> mExtraArgs = new ArrayList<>();

    @Option(
        name = "product-branch-pairing",
        description =
                "Option to specify or replace product to recovery branch pairing. format: "
                        + "<product name>:<recovery_branch>:<flavor> or "
                        + "<product_name>:<recovery_branch>. (flavor is optional)"
    )
    private List<String> mPairingDefinition = new ArrayList<>();

    private String mFastbootPath = "fastboot";
    // The configuration map being static, we only need to update it once per TF instance.
    private boolean mUpdateDone = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFastbootPath(String fastbootPath) {
        mFastbootPath = fastbootPath;
    }

    /**
     * {@InheritDoc}
     */
    @Override
    public void recoverDevices(List<IManagedTestDevice> devices) {
        // We setup the look up table for recovery branch/flavor.
        updateDeviceBranchMap();

        final FastbootHelper fastboot = new FastbootHelper(getRunUtil(), mFastbootPath);
        Set<String> fastbootSerials = fastboot.getDevices();
        // Any device in fastboot/recovery mode and not allocated should be looked at.
        List<ITestDevice> deviceRecoverList = new ArrayList<>();
        for (IManagedTestDevice device : devices) {
            if (device.getAllocationState() != DeviceAllocationState.Allocated) {
                if (fastbootSerials.contains(device.getSerialNumber())) {
                    CLog.logAndDisplay(LogLevel.DEBUG, "Considering: %s for fastboot recovery",
                            device.getSerialNumber());
                    deviceRecoverList.add(device);
                } else if (DeviceState.RECOVERY.equals(device.getIDevice().getState())) {
                    CLog.logAndDisplay(
                            LogLevel.DEBUG,
                            "device: %s is in 'recovery-mode'" + "rebooting it in bootloader.",
                            device.getSerialNumber());
                    try {
                        // FIXME: transition from RECOVERY -> BOOTLOADER is not well handled by TF
                        // We let the device be considered for recovery at next round with an
                        // unchecked reboot instead of using ITestDevice#rebootInBootloader().
                        device.getIDevice().reboot("bootloader");
                    } catch (TimeoutException | AdbCommandRejectedException | IOException e) {
                        CLog.e(
                                "Something went wrong when rebooting from recovery to bootloader "
                                        + "device %s:",
                                device.getSerialNumber());
                        CLog.e(e);
                    }
                }
            }
        }
        // Info in format <branch, build flavor>
        Entry<String, String> info = null;
        for (final ITestDevice device : deviceRecoverList) {
            String serial = device.getSerialNumber();
            // Get the branch from the product type.
            String productType = null;
            try {
                productType = device.getProductType();
                info = sDeviceBranchMap.get(productType);
            } catch (DeviceNotAvailableException e) {
                CLog.e("Device '%s' became unavailable while fetching product type.",
                        device.getSerialNumber());
            }
            if (info == null) {
                CLog.w(
                        "Could not find a recovery branch for device '%s' of type '%s'. Ensure "
                                + "that the device type is configured.",
                        device.getSerialNumber(), productType);
                continue;
            }

            FreeDeviceHandler listener = new FreeDeviceHandler(getDeviceManager());
            List<String> argList = new ArrayList<>();
            argList.add(mRecoveryConfigName);
            argList.add("--branch");
            argList.add(info.getKey());
            if (info.getValue() != null) {
                argList.add("--build-flavor");
                argList.add(info.getValue());
            }
            argList.add("--test-tag");
            argList.add(mRecoveryTestTag);
            argList.add("--online-timeout");
            argList.add(Long.toString(mOnlineTimeout));
            for (String args : mExtraArgs) {
                String[] extraArgs = QuotationAwareTokenizer.tokenizeLine(args);
                if (extraArgs.length != 0) {
                    argList.addAll(Arrays.asList(extraArgs));
                }
            }

            ITestDevice deviceToRecover = getDeviceManager().forceAllocateDevice(serial);
            if (deviceToRecover == null) {
                CLog.e("Fail to force allocate '%s'", serial);
                continue;
            }
            try {
                getCommandScheduler().execCommand(listener, deviceToRecover,
                        argList.toArray(new String[0]));
            } catch (ConfigurationException e) {
                CLog.e("Device multi recovery is misconfigured");
                CLog.e(e);
                // In this case, the device doesn't go through regular de-allocation so we
                // explicitly deallocate.
                getDeviceManager().freeDevice(device, FreeDeviceState.UNAVAILABLE);
                return;
            }
        }
    }

    /**
     * Returns a {@link IRunUtil} instance.
     * Exposed for testing.
     */
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }

    /**
     * Returns a {@link IDeviceManager} instance.
     * Exposed for testing.
     */
    IDeviceManager getDeviceManager() {
        return GlobalConfiguration.getInstance().getDeviceManager();
    }

    /**
     * Returns a {@link ICommandScheduler} instance.
     * Exposed for testing.
     */
    ICommandScheduler getCommandScheduler() {
        return GlobalConfiguration.getInstance().getCommandScheduler();
    }

    /**
     * Handler to free up the device once the invocation completes
     */
    private class FreeDeviceHandler implements IScheduledInvocationListener {

        private final IDeviceManager mDeviceManager;

        FreeDeviceHandler(IDeviceManager deviceManager) {
            mDeviceManager = deviceManager;
        }

        @Override
        public void invocationComplete(IInvocationContext context,
                Map<ITestDevice, FreeDeviceState> devicesStates) {
            for (ITestDevice device : context.getDevices()) {
                mDeviceManager.freeDevice(device, devicesStates.get(device));
                if (device instanceof IManagedTestDevice) {
                    // This quite an important setting so we do make sure it's reset.
                    ((IManagedTestDevice)device).setFastbootPath(mDeviceManager.getFastbootPath());
                }
            }
        }
    }

    /**
     * Convenience helper to create the entries.
     * Flavor can be null to express the fact that it is not required.
     */
    private static SimpleImmutableEntry<String, String> createEntry(String branch, String flavor) {
        return new AbstractMap.SimpleImmutableEntry<>(branch, flavor);
    }

    /**
     * Helper to setup the table with the additional option input. This is pretty safe since {@link
     * IMultiDeviceRecovery} is a global configuration object and usually only one exists.
     */
    private void updateDeviceBranchMap() {
        if (!mUpdateDone && !mPairingDefinition.isEmpty()) {
            for (String pairing : mPairingDefinition) {
                Entry<String, String> res = null;
                String[] data = pairing.split(":");
                if (data.length == 2) {
                    res = sDeviceBranchMap.put(data[0], createEntry(data[1], null));
                } else if (data.length == 3) {
                    res = sDeviceBranchMap.put(data[0], createEntry(data[1], data[2]));
                } else {
                    CLog.e("invalid pairing specified for --product-branch-pairing: '%s'", pairing);
                }
                if (res != null) {
                    CLog.w(
                            "Original entry for product '%s' in recovery map is overriden by "
                                    + "option.",
                            data[0]);
                }
            }
        }
        mUpdateDone = true;
    }

    /**
     * Map of device product name and the associated branch that should be used. Ensure that the
     * branch is as stable as possible and contains a build for the device.
     */
    private static Map<String, Entry<String, String>> sDeviceBranchMap = new HashMap<>();
    static {
        sDeviceBranchMap.put("angler",     createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("bullhead",   createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("elfin",      createEntry("git_oc-tv-release", null));
        sDeviceBranchMap.put("fugu",       createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("hammerhead", createEntry("git_mnc-release", "hammerhead-userdebug"));
        sDeviceBranchMap.put("marlin",     createEntry("git_nyc-dr1-release", null));
        sDeviceBranchMap.put("dragon", createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("sailfish",   createEntry("git_nyc-dr1-release", null));
        sDeviceBranchMap.put("seed",       createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("shamu",      createEntry("git_nyc-release", null));
        sDeviceBranchMap.put("flounder",   createEntry("git_nyc-release", "volantis-userdebug"));
        sDeviceBranchMap.put("nemo",
                createEntry("partner-feldspar-release", "nemo-userdebug"));
        sDeviceBranchMap.put("angelfish",
                createEntry("partner-feldspar-release", "angelfish-userdebug"));
        sDeviceBranchMap.put("sturgeon",
                createEntry("partner-feldspar-release", "sturgeon-userdebug"));
        sDeviceBranchMap.put("swordfish",
                createEntry("partner-feldspar-release", "swordfish-userdebug"));
        sDeviceBranchMap.put("grant", createEntry("partner-feldspar-release", "grant-userdebug"));
        sDeviceBranchMap.put("bass", createEntry("partner-feldspar-release", "bass-userdebug"));
        sDeviceBranchMap.put("dory",
                createEntry("partner-emerald-dr-release", "platina-userdebug"));
        sDeviceBranchMap.put("sprat",
                createEntry("partner-emerald-dr-release", "sprat-userdebug"));
        sDeviceBranchMap.put("muskie", createEntry("git_oc-dr1-release", "muskie-userdebug"));
        sDeviceBranchMap.put("walleye", createEntry("git_oc-dr1-release", "walleye-userdebug"));
        sDeviceBranchMap.put("taimen", createEntry("git_oc-dr1-release", "taimen-userdebug"));

        // AndroidThings devices
        sDeviceBranchMap.put("edison", createEntry("git_nyc-iot-release", null));
        sDeviceBranchMap.put("imx6ul", createEntry("git_nyc-iot-release", null));
        sDeviceBranchMap.put("imx7d", createEntry("git_nyc-iot-release", null));
        sDeviceBranchMap.put("joule", createEntry("git_nyc-iot-release", null));

    }
}
