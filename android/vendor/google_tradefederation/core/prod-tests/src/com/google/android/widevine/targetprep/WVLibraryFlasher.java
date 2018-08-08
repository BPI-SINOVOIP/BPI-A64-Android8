/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 */

package com.google.android.widevine.targetprep;

import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link ITargetPreparer} that attempts to retrieve the latest built
 * Widevine libraries, and install them on the test device.
 *
 * Should be performed *after* a new build is flashed,
 * and *after* DeviceSetup is run (if enabled).
 */

@OptionClass(alias = "wv-library-flasher")
public class WVLibraryFlasher implements ITargetPreparer {
    private static final String LOG_TAG = "WVLibraryFlasher";

    @Option(name="update-file", description=
            "A String specifying a source file and a destination file, separated by '->'." +
            " Can be repeated.")
    private Collection<String> mUpdateList = new LinkedList<String>();

    @Option(name="enforce-update-only", description=
            "If true, fail the invocation if the update-file does not already exist" +
            " on the device. If false, do not enforce the update-only policy.")
    private boolean mEnforceUpdateOnly = true;

    @Option(name="abort-on-failure", description=
            "If true, abort the invocation on any failure. If false, continue if updates fail.")
    private boolean mAbortOnFailure = true;

    private ITestDevice mDevice;

    /**
     * Set abort on failure.  Exposed for testing.
     */
    void setAbortOnFailure(boolean value) {
        mAbortOnFailure = value;
    }

    /**
     * Set Update List.  Exposed for testing.
     */
    void setUpdateList(LinkedList<String> updatelist) {
        mUpdateList = updatelist;
    }

    /**
     * Set enforce update only. Exposed for testing.
     */
    void setEnforceUpdateOnly(boolean value) {
        mEnforceUpdateOnly = value;
    }

    /**
     * Helper method to only throw if mAbortOnFailure is enabled.
     * Callers should behave as if this method may return.
     */
    private void fail(String message) throws TargetSetupError {
        if (mAbortOnFailure) {
            throw new TargetSetupError(message, mDevice.getDeviceDescriptor());
        } else {
            // Log the error and return
            Log.e(LOG_TAG, message);
        }
    }

    /**
     * Helper method to return a file pair from an update-file option that has been checked
     * for basic update eligibility.
     * @throws TargetSetupError
     * @throws DeviceNotAvailableException
     */

    private File[] getEligibleFilePair(ITestDevice device, IBuildInfo buildInfo, String filepair)
            throws TargetSetupError, DeviceNotAvailableException {
        File[] returnvalue = null;
        String[] pair = filepair.split("->");
        if (pair.length != 2){
            fail(String.format("Invalid update-file string: '%s'", filepair));
        } else {
            File src = new File(pair[0]);
            File dest = new File(pair[1]);

            if (!filePairEligibleForUpdate(device, src, dest)) {
                //Maybe our src is defined in the buildInfo
                src = buildInfo.getFile(pair[0]);
                if (!filePairEligibleForUpdate(device, src, dest)) {
                    fail(String.format("Files do not meet requirements for update in update-file" +
                            " string: '%s'", filepair));
                } else {
                    returnvalue = new File[]{src, dest};
                }
            } else {
                returnvalue = new File[]{src, dest};
            }
        }
        return returnvalue;
    }

    /**
     * Helper method to help determine if the file pair are eligible for an update.
     * Requirements:
     * --Both source and destination file already exist (and/or are not null)
     * --The source and destination file should have the same name
     *   (prevents mistakenly placing an L1 library on an L3 device due to improper config update)
     * --Destination file requirements can be overridden by passing false to enforce-update-only
     */
    private Boolean filePairEligibleForUpdate(ITestDevice device, File src, File dst) throws
            DeviceNotAvailableException {
        Boolean eligible = true;
        if (!device.doesFileExist(dst.getAbsolutePath())) {
            Log.d(LOG_TAG, String.format("File '%s' not found on the device.", dst.getAbsolutePath()));
            if (mEnforceUpdateOnly) {
                eligible = false;
            }
        }
        if (src == null) {
            Log.d(LOG_TAG, "Local source file is null.");
            eligible = false;
        }else if (!src.exists()) {
            Log.d(LOG_TAG, String.format("Local source file '%s' does not exist.",
                    src.getAbsolutePath()));
            eligible = false;
        }else if (!src.getName().equals(dst.getName())) {
            Log.d(LOG_TAG, String.format("Source filename '%s' does not match update filename '%s'.",
                    src.getName(), dst.getName()));
            eligible = false;
        }
        return eligible;
    }

    /**
     * Helper method to generate an MD5 Message Digest for a file.
     * @throws TargetSetupError
     */
    private byte[] getMD5Digest(File src) throws TargetSetupError {
        BufferedReader br = null;
        MessageDigest checksum = null;
        byte[] returnvalue = null;
        try {
            br = new BufferedReader(new FileReader(src));
            checksum = MessageDigest.getInstance("MD5");
            String currentline;
            while ((currentline = br.readLine()) != null) {
                checksum.update(currentline.getBytes());
            }
            returnvalue = checksum.digest();
        } catch (IOException ioe) {
            fail(ioe.toString());
        } catch (NoSuchAlgorithmException nsae) {
            fail(nsae.toString());
        } finally {
            StreamUtil.close(br);
        }
        if (returnvalue == null) {
            fail(String.format("MD5 calculation failed for file %s", src.getName()));
        }
        return returnvalue;
    }

    /**
     * Helper method to return the WV Version found in the libWVStreamControlAPI library.
     * @throws TargetSetupError
     */
    private String getVersion(File src) throws TargetSetupError {
        BufferedReader br = null;
        //Widevine version number is of the format #.#.#.##### (followed by a space)
        Pattern pattern = Pattern.compile("Version ([0-9]\\.[0-9]\\.[0-9]\\.[0-9]+) ");
        String returnstring = "unknown";
        if (src != null) {
            try {
                br = new BufferedReader(new FileReader(src));
                String currentline;
                while ((currentline = br.readLine()) != null) {
                    Matcher matcher = pattern.matcher(currentline);
                    if (matcher.find()){
                        returnstring = matcher.group(1);
                        break;
                    }
                }
            } catch (IOException e) {
                fail(e.toString());
            } finally {
                StreamUtil.close(br);
            }
        }
        if (returnstring.equals("unknown")) {
            String filename = "NULL";
            if (src != null) {
                filename = src.getName();
            }
            Log.w(LOG_TAG, String.format("Unable to find version in %s library.", filename));
        }
        return returnstring;
    }

    /**
     * Helper method to push the requested source file to the destination on the device.
     * @throws DeviceNotAvailableException
     * @throws TargetSetupError
     */
    private void updateFile(ITestDevice device, File src, File dest) throws TargetSetupError,
            DeviceNotAvailableException {
      Log.d(LOG_TAG, String.format("Attempting to push %s to device", src.getAbsolutePath()));
      if (!device.pushFile(src, dest.getAbsolutePath())) {
           fail(String.format("Failed to push local %s to remote %s", src.getAbsolutePath(),
                   dest.getAbsolutePath()));
       } else {
           File devicefile = device.pullFile(dest.getAbsolutePath());
           if (Arrays.equals(getMD5Digest(devicefile), getMD5Digest(src))) {
               Log.d(LOG_TAG, String.format("Pushing %s to device appears successful",
                       src.getName()));
           } else {
               fail("Hashes do not match after update.");
           }
       }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        mDevice = device;

        // 1. Need root access to update system libraries (most common use case).
        device.remountSystemWritable();

        // 2. Need to split into file pairs, and check whether we have a valid upgrade scenario.
        for (String filepair : mUpdateList) {
            File[] pair = getEligibleFilePair(device, buildInfo, filepair);
            if (pair == null) {
              continue;
            }
            File src = pair[0];
            File dest = pair[1];
            File devicefile = device.pullFile(dest.getAbsolutePath());

            // 3. Should be able to upgrade. In the case these are classic Widevine libraries,
            //    check for version numbers, and log them to help in debugging tests.
            String currentversion = getVersion(devicefile);
            String requestedversion = getVersion(src);
            Log.d(LOG_TAG, String.format("Current Widevine version: %s", currentversion));
            Log.d(LOG_TAG, String.format("Requested Widevine version: %s", requestedversion));

            // 4. Check if the files are already the same, and if not, update the file on the device.
            if ((devicefile != null) && (Arrays.equals(getMD5Digest(devicefile), getMD5Digest(src)))) {
                Log.d(LOG_TAG, String.format("Not updating %s, version on the device is already" +
                        " the same.", src.getName()));
                continue;
            } else {
               updateFile(device, src, dest);
            }
        }

        // 3. Need to reboot the device to load the new libraries.
        device.reboot();
    }
}
