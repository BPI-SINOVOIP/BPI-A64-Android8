// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.DeviceSetup;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.BinaryState;
import com.android.tradefed.util.MultiMap;
import com.google.android.tradefed.device.NcdDeviceRecovery;
import com.google.android.tradefed.device.SettingsUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * An extension of {@link DeviceSetup} that allows setup of additional Google specific properties.
 */
@OptionClass(alias = "google-device-setup")
public class GoogleDeviceSetup extends DeviceSetup {

    // Screen
    @Option(name = "screen-brightness-nits",
            description = "Set the screen brightness to the number of nits. Currently, the value " +
            "200 for N6, N5, N9, N7v2, Tinno, Longcheer, and Seed are supported, all other " +
            "values and devices will throw a TargetSetupException. This option will override " +
            "screen-brightness option")
    private Integer mScreenBrighnessNit = null;

    @Option(name = "wake-double-tap",
            description = "Turn wake on double tap on or off. Only supported on N6")
    private BinaryState mWakeDoubleTap = BinaryState.IGNORE;
    // ON (shamu):  echo on > /sys/bus/i2c/devices/1-004a/tsp
    // OFF (shamu): echo off > /sys/bus/i2c/devices/1-004a/tsp

    // For option `wake-gesture` specified in DeviceSetup, also run the following commands:
    // ON (volantis):  echo 1 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/input/input0/wake_gesture
    // OFF (volantis): echo 0 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/input/input0/wake_gesture

    // Location
    private static final boolean DEFAULT_DISABLE_LOCATION_PROMPT = true;
    @Option(name = "disable-use-location-prompt",
            description = "Disable the 'Use My location for google services' prompt. Useful to " +
            "prevent the dialog from interfering with UI based tests.")
    private boolean mDisableUseLocationPrompt = DEFAULT_DISABLE_LOCATION_PROMPT;
    // true: am start -a com.google.android.gsf.action.SET_USE_LOCATION_FOR_SERVICES --ez disable true

    @Option(name = "location-collection",
            description = "Turn location collection on or off")
    private BinaryState mLocationCollection = BinaryState.IGNORE;
    // ON:  GServices: location:collection_enabled 1
    // OFF: GServices: location:collection_enabled 0

    @Option(name = "location-opt-in",
            description = "Turn location opt in on or off")
    private BinaryState mLocationOptIn = BinaryState.IGNORE;
    // ON:  Google partner: network_location_opt_in 1
    //      Google partner: use_location_for_services 1
    // OFF: Google partner: network_location_opt_in 0
    //      Google partner: use_location_for_services 0

    // Cast
    @Option(name = "cast-broadcast",
            description = "Turn the cast broadcast on or off")
    private BinaryState mCastBroadcast = BinaryState.IGNORE;
    // ON:  GServices: gms:cast:mdns_device_scanner:is_enabled 1
    // OFF: GServices: gms:cast:mdns_device_scanner:is_enabled 0

    // App
    @Option(name = "disable-playstore",
            description = "Disable the Play Store app")
    private boolean mDisablePlaystore = false;
    // pm disable-user com.android.vending

    @Option(name = "disable-volta",
            description = "Disable the Volta app")
    private boolean mDisableVolta = false;
    // pm disable-user com.google.android.volta

    // Test harness
    @Option(name = "setting-cmd-apk-path",
            description = "file system path to the settings cmd apk.")
    private File mSettingCmdApk = null;

    @Option(name = "set-google-partner-setting",
            description = "Change a Google partner setting. Option may be repeated and all " +
            "key/value pairs will be set in order.")
    // Use a Multimap since it is possible for a setting to have multiple values for the same key
    private MultiMap<String, String> mGooglePartnerSettings = new MultiMap<>();

    @Option(name = "set-gservice-setting",
            description = "Override a GService setting. Option may be repeated and all key/value " +
                    "pairs will be set in order.")
    // Use a Multimap since it is possible for a setting to have multiple values for the same key
    protected MultiMap<String, String> mGServiceSettings = new MultiMap<>();

    @Option(name = "force-ncd-reset-during-setup",
            description = "Force a full NCD reset of the device during setup.  All errors during " +
            "this attempt will be ignored.")
    private boolean mForceNcdReset = false;

    // Deprecated Options
    @Option(name = "enable-airplane-mode",
            description = "deprecated, use option airplane-mode. Turn on airplane mode.")
    @Deprecated
    private boolean mDeprecatedAirplaneMode = false;

    @Option(name = "disable-use-location",
            description = "deprecated, use option disable-use-location-prompt. Disable the 'Use " +
            "my location for google services' prompt. Useful to prevent the dialog from " +
            "interfering with UI based tests.")
    @Deprecated
    private boolean mDeprecatedDisableUseLocation = true;

    @Option(name = "setting",
            description = "deprecated, use option set-system-setting. Change a system " +
            "(non-secure) setting. Format: --setting key value. May be repeated.")
    @Deprecated
    private Map<String, String> mDeprecatedSettings = new HashMap<String, String>();

    @Option(name = "secure-setting",
            description = "deprecated, use option set-secure-setting. Change a secure setting. " +
            "Format: --secure-setting key value. May be repeated.")
    @Deprecated
    private Map<String, String> mDeprecatedSecureSettings = new HashMap<String, String>();

    @Option(name = "google-partner-setting",
            description = "deprecated, use option set-google-partner-setting. Change a google " +
            "partner setting. Format: --setting key value. May be repeated.")
    @Deprecated
    private Map<String, String> mDeprecatedPartnerSettings = new HashMap<String, String>();

    @Option(name = "global-setting",
            description = "deprecated, use option set-global-setting. Change a global setting. " +
            "Format: --setting key value. May be repeated.")
    @Deprecated
    private Map<String, String> mDeprecatedGlobalSettings = new HashMap<String, String>();

    @Option(name = "gservices-setting",
            description = "deprecated, use option set-gservice-setting. Change a GServices " +
            "setting. Format: --gservices-settings key value. May be repeated.")
    @Deprecated
    private Map<String, String> mDeprecatedGServicesSettings = new HashMap<String, String>();

    @Option(name = "default-data-call-subscription",
            description = "deprecated, use option default-sim-data. Set the default data call " +
            "subscription for a multi-SIM device.  Should be 1 for SIM slot 1 or 2 for SIM slot " +
            "2 or unset for a single SIM device.")
    @Deprecated
    private Integer mDeprecatedDefaultDataCallSubscription = null;

    @Option(name = "default-voice-call-subscription",
            description = "deprecated, use option default-sim-voice. Set the default voice call " +
            "subscription for a multi-SIM device.  Should be 1 for SIM slot 1 or 2 for SIM slot " +
            "2 or unset for a single SIM device.")
    @Deprecated
    private Integer mDeprecatedDefaultVoiceCallSubscription = null;

    @Option(name = "default-sms-subscription",
            description = "deprecated, use option default-sim-sms. Set the default SMS " +
            "subscription for a multi-SIM device.  Should be 1 for SIM slot 1 or 2 for SIM slot " +
            "2 or unset for a single SIM device.")
    @Deprecated
    private Integer mDeprecatedDefaultSmsSubscription = null;

    @Option(name = "force-skip-change-settings",
            description = "deprecated, use option force-skip-settings. Force setup not to change " +
            "any settings using settings cmd apk. All settings options and installing settings " +
            "cmd apk will be ignored.")
    @Deprecated
    private boolean mDeprecatedForceSkipChangeSettings = false;

    // TODO: Load this dynamically?
    private static final Map<String, Integer> BRIGHTNESS_FOR_200_NITS = new HashMap<>();
    static {
        BRIGHTNESS_FOR_200_NITS.put("hammerhead", 88);
        BRIGHTNESS_FOR_200_NITS.put("shamu", 203);
        BRIGHTNESS_FOR_200_NITS.put("razor", 112); // Flo
        BRIGHTNESS_FOR_200_NITS.put("razorg", 112); // Deb
        BRIGHTNESS_FOR_200_NITS.put("volantis", 175);
        BRIGHTNESS_FOR_200_NITS.put("volantisg", 175);
        BRIGHTNESS_FOR_200_NITS.put("4560MMX", 155); // Tinno
        BRIGHTNESS_FOR_200_NITS.put("4560MMX_b", 164); // Longcheer
        BRIGHTNESS_FOR_200_NITS.put("AQ4501", 120); // Tinno MicroMax
        BRIGHTNESS_FOR_200_NITS.put("Mi-498", 134); // Sprout Spice
        BRIGHTNESS_FOR_200_NITS.put("l8150", 113); // Seed
        BRIGHTNESS_FOR_200_NITS.put("ctih220", 116); // Seed Cherry
        BRIGHTNESS_FOR_200_NITS.put("angler", 158); // Angler
        BRIGHTNESS_FOR_200_NITS.put("bullhead", 149); // Bullhead
        BRIGHTNESS_FOR_200_NITS.put("ryu", 91); // Ryu
        BRIGHTNESS_FOR_200_NITS.put("sailfish", 131); // Sailfish & friends
        BRIGHTNESS_FOR_200_NITS.put("sailfish_eas", 131);
        BRIGHTNESS_FOR_200_NITS.put("marlin", 147); // Marlin & friends
        BRIGHTNESS_FOR_200_NITS.put("marlin_eas", 147);
        BRIGHTNESS_FOR_200_NITS.put("muskie", 152); // Muskie
        BRIGHTNESS_FOR_200_NITS.put("walleye", 136); // Walleye & friends
        BRIGHTNESS_FOR_200_NITS.put("walleye_clang", 136);
        BRIGHTNESS_FOR_200_NITS.put("taimen", 157); // Taimen & friends
        BRIGHTNESS_FOR_200_NITS.put("taimen_clang", 157);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mDisable) {
            return;
        }

        // Intended to work around hardware/firmware-related bugs that need a full, cold restart
        if (mForceNcdReset) {
            // FIXME: figure out which errors to ignore
            // FIXME: use the global object somehow so that we get the right NCD path.
            String serial = device.getSerialNumber();
            CLog.w("Performing full cold reset on device %s before setup.", serial);
            (new NcdDeviceRecovery()).resetDevice(serial);
            device.waitForDeviceAvailable();
        }

        super.setUp(device, buildInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processDeprecatedOptions(ITestDevice device) throws TargetSetupError {
        super.processDeprecatedOptions(device);

        if (mDeprecatedAirplaneMode /* default false */) {
            if (!BinaryState.IGNORE.equals(mAirplaneMode)) {
                throw new TargetSetupError("Deprecated option enable-airplane-mode conflicts " +
                        "with option airplane-mode", device.getDeviceDescriptor());
            }
            mAirplaneMode = BinaryState.ON;
        }

        if (mDeprecatedDisableUseLocation != DEFAULT_DISABLE_LOCATION_PROMPT) {
            if (mDisableUseLocationPrompt != DEFAULT_DISABLE_LOCATION_PROMPT) {
                throw new TargetSetupError("Deprecated option enable-airplane-mode conflicts " +
                        "with option airplane-mode", device.getDeviceDescriptor());
            }
            mDisableUseLocationPrompt = mDeprecatedDisableUseLocation;
        }

        if (!mDeprecatedSettings.isEmpty()) {
            if (!mSystemSettings.isEmpty()) {
                throw new TargetSetupError("Deprecated option setting conflicts with option " +
                        "set-system-setting", device.getDeviceDescriptor());
            }
            mSystemSettings.putAll(mDeprecatedSettings);
        }

        if (!mDeprecatedSecureSettings.isEmpty()) {
            if (!mSecureSettings.isEmpty()) {
                throw new TargetSetupError("Deprecated option secure-setting conflicts with " +
                        "option set-secure-setting", device.getDeviceDescriptor());
            }
            mSecureSettings.putAll(mDeprecatedSecureSettings);
        }

        if (!mDeprecatedPartnerSettings.isEmpty()) {
            if (!mGooglePartnerSettings.isEmpty()) {
                throw new TargetSetupError("Deprecated option google-partner-setting conflicts " +
                        "with option set-google-partner-setting", device.getDeviceDescriptor());
            }
            mGooglePartnerSettings.putAll(mDeprecatedPartnerSettings);
        }

        if (!mDeprecatedGlobalSettings.isEmpty()) {
            if (!mGlobalSettings.isEmpty()) {
                throw new TargetSetupError("Deprecated option global-setting conflicts with " +
                        "option set-global-setting", device.getDeviceDescriptor());
            }
            mGlobalSettings.putAll(mDeprecatedGlobalSettings);
            System.out.println(mGlobalSettings);
        }

        if (!mDeprecatedGServicesSettings.isEmpty()) {
            if (!mGServiceSettings.isEmpty()) {
                throw new TargetSetupError("Deprecated option gservices-setting conflicts with " +
                        "option set-gservice-setting", device.getDeviceDescriptor());
            }
            mGServiceSettings.putAll(mDeprecatedGServicesSettings);
        }

        if (mDeprecatedDefaultDataCallSubscription != null) {
            if (mDefaultSimData != null) {
                throw new TargetSetupError("Deprecated option default-data-call-subscription " +
                        "conflicts with option default-sim-data", device.getDeviceDescriptor());
            }
            mDefaultSimData = mDeprecatedDefaultDataCallSubscription;
        }

        if (mDeprecatedDefaultVoiceCallSubscription != null) {
            if (mDefaultSimVoice != null) {
                throw new TargetSetupError("Deprecated option default-voice-call-subscription " +
                        "conflicts with option default-sim-voice", device.getDeviceDescriptor());
            }
            mDefaultSimVoice = mDeprecatedDefaultVoiceCallSubscription;
        }

        if (mDeprecatedDefaultSmsSubscription != null) {
            if (mDefaultSimSms != null) {
                throw new TargetSetupError("Deprecated option default-sms-subscription conflicts " +
                        "with option default-sim-sms", device.getDeviceDescriptor());
            }
            mDefaultSimSms = mDeprecatedDefaultSmsSubscription;
        }

        if (mDeprecatedForceSkipChangeSettings /* default false */) {
            if (mForceSkipSettings /* default false */) {
                throw new TargetSetupError("Deprecated option force-skip-change-settings " +
                        "conflicts with option force-skip-settings", device.getDeviceDescriptor());
            }
            mForceSkipSettings = mDeprecatedForceSkipChangeSettings;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processOptions(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        super.processOptions(device);

        if (mScreenBrighnessNit != null && BinaryState.ON.equals(mScreenAdaptiveBrightness)) {
            throw new TargetSetupError("Option screen-brightness-nits cannot be set when " +
                    "screen-adaptive-brightness is set to ON", device.getDeviceDescriptor());
        }

        // If screen-brightness is already set, it will be overriden by brightnessFor200Nit
        if (mScreenBrighnessNit != null) {
            if (mScreenBrighnessNit == 200) {
                mSystemSettings.put("screen_brightness",
                        Integer.toString(getBrightnessFor200Nit(device)));
            } else {
                throw new TargetSetupError("Option screen-brightness-nit must be 200",
                        device.getDeviceDescriptor());
            }
        }

        if (!BinaryState.IGNORE.equals(mWakeDoubleTap)) {
            if ("shamu".equals(getProductName(device))) {
                setCommandForBinaryState(mWakeDoubleTap, mRunCommandAfterSettings,
                        "echo on > /sys/bus/i2c/devices/1-004a/tsp",
                        "echo off > /sys/bus/i2c/devices/1-004a/tsp");
            } else {
                throw new TargetSetupError("wake-double-tap only supported on shamu",
                        device.getDeviceDescriptor());
            }
        }

        if (!BinaryState.IGNORE.equals(mWakeGesture)) {
            if ("volantis".equals(getProductName(device)) ||
                    "volantisg".equals(getProductName(device))) {
                setCommandForBinaryState(mWakeGesture, mRunCommandAfterSettings,
                        "echo 1 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/" +
                        "input/input0/wake_gesture",
                        "echo 0 > /sys/devices/platform/spi-tegra114.2/spi_master/spi2/spi2.0/" +
                        "input/input0/wake_gesture");
            }
        }

        if (mDisableUseLocationPrompt) {
            if (device.getApiLevel() < 19) {
                mRunCommandAfterSettings.add(
                        "am start -a com.google.android.gsf.action.SET_USE_LOCATION_FOR_SERVICES "
                                + "--ez disable true");
            }
        }

        setSettingForBinaryState(mLocationCollection, mGServiceSettings,
                "location:collection_enabled", "1", "0");

        setSettingForBinaryState(mLocationOptIn, mGooglePartnerSettings,
                "network_location_opt_in", "1", "0");
        setSettingForBinaryState(mLocationOptIn, mGooglePartnerSettings,
                "use_location_for_services", "1", "0");

        setSettingForBinaryState(mCastBroadcast, mGServiceSettings,
                "gms:cast:mdns_device_scanner:is_enabled", "true", "false");

        if (mDisablePlaystore) {
            mRunCommandAfterSettings.add("pm disable-user com.android.vending");
        }

        if (mDisableVolta) {
            mRunCommandAfterSettings.add("pm disable-user com.google.android.volta");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changeSettings(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        if (mForceSkipSettings) {
            CLog.d("Skipping settings due to force-skip-setttings");
            return;
        }

        if (mSystemSettings.isEmpty() && mSecureSettings.isEmpty() && mGlobalSettings.isEmpty() &&
                mGooglePartnerSettings.isEmpty() && mGServiceSettings.isEmpty() &&
                BinaryState.IGNORE.equals(mAirplaneMode)) {
            CLog.d("No settings to change");
            return;
        }

        SettingsUtil settingsUtil = new SettingsUtil(device, mSettingCmdApk);
        settingsUtil.installSettingCmdApk();

        // Special case airplane mode since it needs to be set before other connectivity settings
        // For example, it is possible to enable airplane mode and then turn wifi on
        String command = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state %s";
        switch (mAirplaneMode) {
            case ON:
                CLog.d("Changing global setting airplane_mode_on to 1");
                settingsUtil.changeGlobalSetting("airplane_mode_on", "1");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "true"));
                }
                break;
            case OFF:
                CLog.d("Changing global setting airplane_mode_on to 0");
                settingsUtil.changeGlobalSetting("airplane_mode_on", "0");
                if (!mForceSkipRunCommands) {
                    device.executeShellCommand(String.format(command, "false"));
                }
                break;
            case IGNORE:
                // No-op
                break;
        }

        for (String key : mSystemSettings.keySet()) {
            for (String value : mSystemSettings.get(key)) {
                CLog.d("Changing system setting %s to %s", key, value);
                settingsUtil.changeSetting(key, value);
            }
        }
        for (String key : mSecureSettings.keySet()) {
            for (String value : mSecureSettings.get(key)) {
                CLog.d("Changing secure setting %s to %s", key, value);
                settingsUtil.changeSecureSetting(key, value);
            }
        }
        for (String key : mGlobalSettings.keySet()) {
            for (String value : mGlobalSettings.get(key)) {
                CLog.d("Changing global setting %s to %s", key, value);
                settingsUtil.changeGlobalSetting(key, value);
            }
        }
        for (String key : mGooglePartnerSettings.keySet()) {
            for (String value : mGooglePartnerSettings.get(key)) {
                CLog.d("Changing Google partner setting %s to %s", key, value);
                settingsUtil.changeGooglePartnerSetting(key, value);
            }
        }
        for (String key : mGServiceSettings.keySet()) {
            for (String value : mGServiceSettings.get(key)) {
                CLog.d("Overriding GService setting %s to %s", key, value);
                settingsUtil.overrideGServices(key, value);
            }
        }
        settingsUtil.uninstallSettingCmd();
    }

    /**
     * Helper method to get the calibrated brightness for the products supporting 200 nits.
     *
     * @param device The {@link ITestDevice}
     * @return the brightness value which maps to 200 nits.
     * @throws DeviceNotAvailableException if the device was not available
     * @throws TargetSetupError if there is no calibration data for 200 nits
     */
    private int getBrightnessFor200Nit(ITestDevice device) throws DeviceNotAvailableException,
            TargetSetupError {
        String product = getProductName(device);
        Integer brightness = BRIGHTNESS_FOR_200_NITS.get(product);
        if (brightness == null) {
            throw new TargetSetupError(String.format("Setting screen to 200 nits not supported " +
                    "for product %s", product), device.getDeviceDescriptor());
        }
        return brightness;
    }

    /**
    * Helper method to return the product name
    *
    * @return the product name of the device or null if it's not set.
    * @throws DeviceNotAvailableException if the device was not available
    */
   private String getProductName(ITestDevice device) throws DeviceNotAvailableException {
       String product = device.getProperty("ro.product.name");
       if (product == null || product.isEmpty()) {
           return null;
       }
       return product;
   }

    /**
     * Exposed for unit testing
     */
    @Override
    protected void setAirplaneMode(BinaryState airplaneMode) {
        mAirplaneMode = airplaneMode;
    }

    /**
     * Exposed for unit testing
     */
    void setWakeDoubleTap(BinaryState wakeDoubleTap) {
        mWakeDoubleTap = wakeDoubleTap;
    }

    /**
     * Exposed for unit testing
     */
    @Override
    protected void setWakeGesture(BinaryState wakeGesture) {
        mWakeGesture = wakeGesture;
    }

    /**
     * Exposed for unit testing
     */
    void setDisableUseLocationPrompt(boolean disableUseLocationPrompt) {
        mDisableUseLocationPrompt = disableUseLocationPrompt;
    }

    /**
     * Exposed for unit testing
     */
    void setLocationCollection(BinaryState locationCollection) {
        mLocationCollection = locationCollection;
    }

    /**
     * Exposed for unit testing
     */
    void setLocationOptIn(BinaryState locationOptIn) {
        mLocationOptIn = locationOptIn;
    }

    /**
     * Exposed for unit testing
     */
    void setCastBroadcast(BinaryState castBroadcast) {
        mCastBroadcast = castBroadcast;
    }

    /**
     * Exposed for unit testing
     */
    @Override
    protected void setDefaultSimData(Integer defaultSimData) {
        mDefaultSimData = defaultSimData;
    }

    /**
     * Exposed for unit testing
     */
    @Override
    protected void setDefaultSimVoice(Integer defaultSimVoice) {
        mDefaultSimVoice = defaultSimVoice;
    }

    /**
     * Exposed for unit testing
     */
    @Override
    protected void setDefaultSimSms(Integer defaultSimSms) {
        mDefaultSimSms = defaultSimSms;
    }

    /**
     * Exposed for unit testing
     */
    void setDisablePlaystore(boolean disable) {
        mDisablePlaystore = disable;
    }

    /**
     * Exposed for unit testing
     */
    void setDisableVolta(boolean disable) {
        mDisableVolta = disable;
    }

    /**
     * Exposed for unit testing
     */
    void setSystemSetting(String key, String value) {
        mSystemSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    void setSecureSetting(String key, String value) {
        mSecureSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    void setGlobalSetting(String key, String value) {
        mGlobalSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    void setGooglePartnerSetting(String key, String value) {
        mGooglePartnerSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    void setGserviceSetting(String key, String value) {
        mGServiceSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedAirplaneMode(boolean airplaneMode) {
        mDeprecatedAirplaneMode = airplaneMode;
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    void setDeprecatedDisableUseLocation(boolean disableUseLocation) {
        mDeprecatedDisableUseLocation = disableUseLocation;
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedSetting(String key, String value) {
        mDeprecatedSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedSecureSetting(String key, String value) {
        mDeprecatedSecureSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedPartnerSetting(String key, String value) {
        mDeprecatedPartnerSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedGlobalSetting(String key, String value) {
        mDeprecatedGlobalSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedGServicesSetting(String key, String value) {
        mDeprecatedGServicesSettings.put(key, value);
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedDefaultDataCallSubscription(int subscription) {
        mDeprecatedDefaultDataCallSubscription = subscription;
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedDefaultVoiceCallSubscription(int subscription) {
        mDeprecatedDefaultVoiceCallSubscription = subscription;
    }

    /**
     * Exposed for unit testing
     */
    @Deprecated
    public void setDeprecatedDefaultSmsSubscription(int subscription) {
        mDeprecatedDefaultSmsSubscription = subscription;
    }
}
