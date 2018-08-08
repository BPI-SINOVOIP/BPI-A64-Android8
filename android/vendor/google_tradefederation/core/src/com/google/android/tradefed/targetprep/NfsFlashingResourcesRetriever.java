// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.device.StaticDeviceInfo;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * A {@link IFlashingResourcesRetriever} that retrieves auxiliary image files from network storage.
 */
class NfsFlashingResourcesRetriever implements IFlashingResourcesRetriever {
    private static final String DEFAULT_RESOURCE_PATH =
            "/home/android-build/www/flashstation_images/";
    private String mResourcePath = DEFAULT_RESOURCE_PATH;
    private String mBackupResourcePath = "/auto/android-test/device-blobs/";

    private final String mDeviceType;

    /**
     * Creates a {@link NfsFlashingResourcesRetriever}.
     *
     * @param deviceProductType the device product type. Some files to be retrieved contain
     * the device product type name.
     */
    NfsFlashingResourcesRetriever(String deviceProductType) {
        mDeviceType = deviceProductType;
        // TODO: put this logic within the TungstenDeviceFlasher
        if (StaticDeviceInfo.TUNGSTEN_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "phantasm/");
        }
        if (StaticDeviceInfo.WOLFIE_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "wolfie/");
        }
        if (StaticDeviceInfo.MOLLY_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "molly/");
        }
        if (StaticDeviceInfo.FLOUNDER_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "volantis/");
        }
        if (StaticDeviceInfo.SPROUT_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "sprout/");
        }
        if (StaticDeviceInfo.TETRA_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "tetra/");
        }
        if (StaticDeviceInfo.ANTHIAS_PRODUCT.equals(mDeviceType)) {
            setResourcePath(DEFAULT_RESOURCE_PATH + "anthias/");
        }

    }

    /**
     * Set the image resource root path
     * @param resourcePath
     */
    public void setResourcePath(String resourcePath) {
        mResourcePath = resourcePath;
    }

    /**
     * Set the backup image resource root path
     * @param resourcePath
     */
    public void setBackupResourcePath(String resourcePath) {
        mBackupResourcePath = resourcePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File retrieveFile(String imageName, String version) throws TargetSetupError {
        // files can follow two different naming conventions - one with image name and version
        String imageFileVersionName = String.format("%s.%s.img", imageName, version);
        // and the other with device type
        String imageFileVersionTypeName = String.format("%s.%s.%s.img", imageName, mDeviceType,
                version);
        return findAndCopyFile(imageFileVersionName, imageFileVersionTypeName);
    }

    /**
     * Finds and copies a original file from file system.
     * <p/>
     * Looks for a file with one of the given names in the resource path or the backup resource
     * path. Returns a temporary local copy from the first file found.
     *
     * @param fileNames the set of file names to search for
     * @return the temporary local file
     * @throws TargetSetupError if file cannot be found
     */
    private File findAndCopyFile(String... fileNames) throws TargetSetupError {
        // look for all file names in main RESOURCE_PATH first
        for (String fileName : fileNames) {
            File imgFile = new File(mResourcePath + fileName);
            if (imgFile.exists()) {
                return copyFile(imgFile);
            }
        }
        // not found - so look in BACKUP_RESOURCE_PATH
        // and build up a file name string to use for logging
        StringBuilder fileNamesString = new StringBuilder();
        for (String fileName : fileNames) {
            File imgFile = new File(mBackupResourcePath + fileName);
            if (imgFile.exists()) {
                return copyFile(imgFile);
            }
            fileNamesString.append(fileName);
            fileNamesString.append(" ");
        }
        CLog.e("Search path used: %s, %s", mResourcePath, mBackupResourcePath);
        DeviceDescriptor nullDescriptor = null;
        throw new TargetSetupError(String.format("Could not find files %s", fileNamesString),
                nullDescriptor);
    }

    /**
     * Copies the given file to temporary local storage.
     *
     * @param imgFile the file to copy
     * @return the copied temporary file or <code>null</code> if file could not be copied
     * @throws TargetSetupError if file could not be copied
     */
    private File copyFile(File imgFile) throws TargetSetupError {
        File fileCopy = null;
        try {
            final String fileName = imgFile.getName();
            int dotIndex = fileName.lastIndexOf(".");
            String ext = dotIndex == -1 ? "" : fileName.substring(dotIndex, fileName.length());
            fileCopy = FileUtil.createTempFile(fileName, ext);
            FileUtil.copyFile(imgFile, fileCopy);
            return fileCopy;
        } catch (IOException e) {
            if (fileCopy != null) {
                fileCopy.delete();
            }
            throw new TargetSetupError(String.format("Could not copy file %s",
                    imgFile.getAbsolutePath()), e, null);
        }
    }
}
