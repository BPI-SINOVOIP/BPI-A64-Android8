// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep.clockwork;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.targetprep.companion.CompanionAwarePreparer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares the companion to be used for pairing with primary
 * <p>
 * Currently the preparer installs dependent apks from a location with specified order
 */
@OptionClass(alias = "clockwork-companion-preparer")
public class ClockworkCompanionPreparer extends CompanionAwarePreparer {

    @Option(name = "sideload-apks-path",
            description = "path to the folder containing apks to sideload; all apks will be "
                    + "installed in order as returned by filesystem, unless an explicit list is "
                    + "provided by apk-name-filter parameters")
    private String mSideloadApksPath = null;

    @Option(name = "apk-name-filter",
            description = "regex pattern to match filenames of apks to be installed, in order of "
                    + "filter parameters provided; if specified, sideload-apks-path parameter "
                    + "is required")
    private List<String> mApkNameFilter = new ArrayList<String>();

    @Option(name = "reboot-companion", description = "reboots companion device before preparation")
    private boolean mRebootCompanion = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        ITestDevice companionDevice = getCompanion(device);
        buildInfo.addBuildAttribute("companion_device_serial", companionDevice.getSerialNumber());
        if (mRebootCompanion) {
            companionDevice.reboot();
            companionDevice.waitForDeviceAvailable();
        }
        installApks(companionDevice);
    }

    private void installApks(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {
        if (mSideloadApksPath != null) {
            File base = new File(mSideloadApksPath);
            if (!base.isDirectory()) {
                throw new TargetSetupError("invalid path provided via sideload-apks-path",
                        device.getDeviceDescriptor());
            }
            List<File> apks = getApksToInstall(base.listFiles(), mApkNameFilter);
            for (File apk : apks) {
                device.installPackage(apk, true);
            }
        }
    }

    /**
     * Filters list of files in apk repo folder against list of file name patterns
     *
     * @param fileArray
     * @param patterns
     * @return the filtered list of apk files to install
     */
    List<File> getApksToInstall(File[] fileArray, List<String> patterns) {
        // use a LinkedList since we need to modify list on the fly
        List<File> files = new LinkedList<File>(Arrays.asList(fileArray));
        // process list of apks
        List<Pattern> apkNamePatterns = new ArrayList<Pattern>();
        for (String pattern : patterns) {
            apkNamePatterns.add(Pattern.compile(pattern));
        }
        if (apkNamePatterns.isEmpty()) {
            apkNamePatterns.add(Pattern.compile("^.+\\.apk$"));
        }
        List<File> apks = new ArrayList<File>();
        // loop based on the pattern supplied, rather than files listed, because the order of
        // the file name patterns need to be guaranteed
        for (Pattern p : apkNamePatterns) {
            Iterator<File> itr = files.iterator();
            while (itr.hasNext()) {
                File f = itr.next();
                Matcher m = p.matcher(f.getName());
                if (m.find()) {
                    apks.add(f);
                    // only match each apk once
                    itr.remove();
                }
            }
        }
        return apks;
    }
}
