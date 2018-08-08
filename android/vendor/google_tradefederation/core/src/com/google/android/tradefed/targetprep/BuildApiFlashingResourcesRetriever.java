// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.IFlashingResourcesRetriever;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import com.google.android.tradefed.build.DeviceBlobFetcher;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * A {@link IFlashingResourcesRetriever} that retrieves auxiliary image files from android build
 * apiary, fallback to nfs if the apiary is not available.
 */
public class BuildApiFlashingResourcesRetriever implements IFlashingResourcesRetriever {
    private final String mDeviceType;
    private final String mKeyFilePath;
    private final String mServiceAccount;

    /**
     * Constructor
     * @param deviceProductType type of the device
     * @param keyFilePath key file path to access android build api
     * @param serviceAccount service account to access android build api
     */
    public BuildApiFlashingResourcesRetriever(
            String deviceProductType, String keyFilePath, String serviceAccount) {
        mDeviceType = deviceProductType;
        mKeyFilePath = keyFilePath;
        mServiceAccount = serviceAccount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File retrieveFile(String imageName, String version) throws TargetSetupError {
        DeviceBlobFetcher fetcher = null;
        File outputFile = null;
        boolean deleteFile = true;

        try {
            fetcher = new DeviceBlobFetcher(new File(mKeyFilePath), mServiceAccount);
        } catch (GeneralSecurityException e) {
            CLog.e("The key file is not valid.");
        } catch (IOException e) {
            CLog.e("Can't read the key file.");
        }

        if (fetcher != null) {
            try {
                outputFile = FileUtil.createTempFile(String.format("%s.%s", imageName, version),
                        ".img");
                CLog.v("Fetch device image file to %s", outputFile.getAbsolutePath());
                fetcher.fetchDeviceBlob(mDeviceType, imageName, version, outputFile);
                deleteFile = false;
                return outputFile;
            } catch (IOException e) {
                CLog.w(String.format(
                        "Can't fetch device image (deviceType: %s, imageName: %s, version: %s)"
                        + " file from android build apiary, falling back to NFS",
                        mDeviceType, imageName, version));
                CLog.w(StreamUtil.getStackTrace(e));
            } catch (JSONException e) {
                CLog.e("The meta data of the device image file is not right");
            } finally {
                if (deleteFile && outputFile != null) {
                    outputFile.delete();
                }
            }
        }

        CLog.v("Try to fetch device image file from android build apiary failed, fallback to nfs");
        NfsFlashingResourcesRetriever nfsRetriever = new NfsFlashingResourcesRetriever(mDeviceType);
        return nfsRetriever.retrieveFile(imageName, version);
    }
}
