package com.google.android.tradefed.device;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceOptions;

import java.io.File;

/**
 * Container for {@link ITestDevice} {@link Option}s from Cloud Android devices.
 */
public class GceAvdTestDeviceOptions extends TestDeviceOptions {

    @Option(name = "gce-boot-timeout",
            description = "timeout to wait in ms for GCE to be online.",
            isTimeVal = true)
    private long mGceCmdTimeout = 20 * 60 * 1000; // 20 minutes.

    @Option(name = "gce-driver-path", description = "path of the binary to launch GCE devices")
    private String mAvdDriverBinary = "/google/data/ro/projects/android/treehugger/acloud/acloud";

    @Option(name = "gce-driver-config-path",
            description = "path of the config to use to launch GCE devices.")
    private String mAvdConfigFile =
            "/google/data/ro/projects/android/treehugger/acloud/treehugger_postsubmit.config";

    @Option(
        name = "gce-private-key-path",
        description = "path to the ssh key private key location."
    )
    private File mSshPrivateKeyPath =
            new File("/google/data/ro/teams/tradefed/configs/gce-ssh/id_rsa");

    @Option(name = "gce-driver-log-level", description = "Log level for gce driver")
    private LogLevel mGceDriverLogLevel = LogLevel.DEBUG;

    @Option(name = "gce-account", description = "email account to use with GCE driver.")
    private String mGceAccount = null;

    @Option(name = "max-gce-attempt", description = "Maximum number of attempts to start Gce "
            + "before throwing an exception.")
    private int mGceMaxAttempt = 1;

    /**
     * Return the Gce Avd timeout for the instance to come online.
     */
    public long getGceCmdTimeout() {
        return mGceCmdTimeout;
    }

    /**
     * Set the Gce Avd timeout for the instance to come online.
     */
    public void setGceCmdTimeout(long mGceCmdTimeout) {
        this.mGceCmdTimeout = mGceCmdTimeout;
    }

    /**
     * Return the path to the binary to start the Gce Avd instance.
     */
    public String getAvdDriverBinary() {
        return mAvdDriverBinary;
    }

    /**
     * Set the path to the binary to start the Gce Avd instance.
     */
    public void setAvdDriverBinary(String mAvdDriverBinary) {
        this.mAvdDriverBinary = mAvdDriverBinary;
    }

    /**
     * Return the path of the Gce Avd config file to start the instance.
     */
    public String getAvdConfigFile() {
        return mAvdConfigFile;
    }

    /**
     * Set the path of the Gce Avd config file to start the instance.
     */
    public void setAvdConfigFile(String mAvdConfigFile) {
        this.mAvdConfigFile = mAvdConfigFile;
    }

    /**
     * Return the path of the ssh key to use for operations with the Gce Avd instance.
     */
    public File getSshPrivateKeyPath() {
        return mSshPrivateKeyPath;
    }

    /**
     * Set the path of the ssh key to use for operations with the Gce Avd instance.
     */
    public void setSshPrivateKeyPath(File mSshPrivateKeyPath) {
        this.mSshPrivateKeyPath = mSshPrivateKeyPath;
    }

    /**
     * Return the log level of the Gce Avd driver.
     */
    public LogLevel getGceDriverLogLevel() {
        return mGceDriverLogLevel;
    }

    /**
     * Set the log level of the Gce Avd driver.
     */
    public void setGceDriverLogLevel(LogLevel mGceDriverLogLevel) {
        this.mGceDriverLogLevel = mGceDriverLogLevel;
    }

    /**
     * Return the gce email account to use with the driver
     */
    public String getGceAccount() {
        return mGceAccount;
    }

    /**
     * Return the max number of attempts to start a gce device
     */
    public int getGceMaxAttempt() {
        if (mGceMaxAttempt < 1) {
            throw new RuntimeException("--max-gce-attempt cannot be bellow 1 attempt.");
        }
        return mGceMaxAttempt;
    }

    /**
     * Set the max number of attempts to start a gce device
     */
    public void setGceMaxAttempt(int gceMaxAttempt) {
        mGceMaxAttempt = gceMaxAttempt;
    }
}
