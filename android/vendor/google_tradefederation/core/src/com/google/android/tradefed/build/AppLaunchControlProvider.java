// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.DeviceBuildDescriptor;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildProvider;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A {@link LaunchControlProvider} for {@link AppBuildInfo}
 */
@OptionClass(alias = "app-launch-control")
public class AppLaunchControlProvider extends LaunchControlProvider
        implements IDeviceBuildProvider {

    @Option(name = "app-name-filter", description = "Optional name regex pattern filter(s) for " +
            "apks to retrieve. If set, only apks matching one or more of these filters will be " +
            "retrieved. An exception will be thrown if no files are found matching a given " +
            "pattern.")
    private Collection<String> mAppFilters = new ArrayList<String>();

    @Option(name = "optional-app-name-filter", description = "Similiar to app-name-filter " +
            "but no exception will be thrown if no files are found matching a given " +
            "pattern.")
    private Collection<String> mOptionalAppFilters = new ArrayList<String>();

    @Option(name = "test-tag-by-device", description = "Flag to instruct provider to append a "
            + "device-specific postfix to test tag. This is useful in cases where you want to run "
            + "test tag across multiple hardware targets")
    private boolean mTestTagByDevice = false;

    @Option(name = "test-tag-by-device-sdk", description = "Flag to instruct provider to append a "
            + "device_sdk level pecific postfix to test tag. This is useful in cases where you "
            + "want to run test tag across multiple hardware targets with different builds")
    private boolean mTestTagByDeviceSdk = false;

    @Option(name = "override-device-build-id", description = "the device build id to inject.")
    private String mOverrideDeviceBuildId = null;

    @Option(name = "override-device-build-flavor",
            description = "the device build flavor to inject.")
    private String mOverrideDeviceBuildFlavor = null;

    public void setApkFilters(Collection<String> appFilters) {
        mAppFilters = appFilters;
    }

    public void setOptionalApkFilters(Collection<String> optionalAppFilters) {
        mOptionalAppFilters = optionalAppFilters;
    }

    public void addApkFilter(String appFilter) {
        mAppFilters.add(appFilter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        setTestTagByDevice(device);
        return super.getBuild();
    }

    protected void setTestTagByDevice(ITestDevice device)
            throws BuildRetrievalError, DeviceNotAvailableException {
        if (mTestTagByDevice || mTestTagByDeviceSdk) {
            String productVariant;
            if (device.getIDevice().isEmulator()) {
                if (mOverrideDeviceBuildFlavor == null || mOverrideDeviceBuildId == null) {
                    throw new BuildRetrievalError(
                            "Missing override value for build flavor and id for emulator.");
                }
                productVariant = String.format("%s_%s",
                        mOverrideDeviceBuildFlavor, mOverrideDeviceBuildId);
            } else {
                productVariant =  DeviceBuildDescriptor.generateDeviceProduct(device);
            }
            setTestTag(String.format("%s-%s%s", getTestTag(), productVariant,
                    getSdkTagSuffix(device)));
        }
    }

    private String getSdkTagSuffix(ITestDevice device) throws DeviceNotAvailableException {
        if (mTestTagByDeviceSdk) {
            return Integer.toString(device.getApiLevel());
        }
        return "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        AppBuildInfo localBuild = new AppBuildInfo(remoteBuild.getBuildId(), buildName);
        try {
            downloadApks(remoteBuild, downloader, localBuild);
            downloadAdditionalFiles(remoteBuild, downloader, localBuild);
        } catch (BuildRetrievalError e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            e.setBuildInfo(localBuild);
            throw e;
        } catch (RuntimeException e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            throw e;
        }
        return localBuild;
    }

    private void downloadApks(RemoteBuildInfo remoteBuild, IFileDownloader downloader,
            AppBuildInfo localBuild) throws BuildRetrievalError {
        // expect comma-separated apkPaths
        final String apkPathCSVString = remoteBuild.getAttribute(BuildAttributeKey.APP_APKS);
        String[] apkPaths;
        if (apkPathCSVString.isEmpty()) {
            apkPaths = new String[] {};
        } else {
            apkPaths = apkPathCSVString.split(",");
        }
        Collection<String> mandatoryFilterCopy = new ArrayList<String>(mAppFilters);
        if (mandatoryFilterCopy.size() == 0 && mOptionalAppFilters.size() == 0) {
            // no filters specified! Insert a dummy 'match all' pattern to keep logic simple
            mandatoryFilterCopy.add(".*");
        }
        for (String pattern : mandatoryFilterCopy) {
            boolean foundMatch = false;
            for (String apkPath : apkPaths) {
                if (apkPath.matches(pattern)) {
                    localBuild.addAppPackageFile(downloader.downloadFile(apkPath),
                            remoteBuild.getBuildId());
                    foundMatch = true;
                }
            }
            if (!foundMatch) {
                throw new BuildRetrievalError(String.format(
                        "Could not find file matching pattern '%s'", pattern));
            }
        }

        // now download optional apks
        for (String pattern : mOptionalAppFilters) {
            for (String apkPath : apkPaths) {
                if (apkPath.matches(pattern)) {
                    localBuild.addAppPackageFile(downloader.downloadFile(apkPath),
                            remoteBuild.getBuildId());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    private String getSignedFileRegex() {
        return String.format("^%s(|-x*(h|m)dpi-.*|-mnc|-lmp|-internal)\\.apk$",
                getBuildFlavor());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void convertBuildToSigned(RemoteBuildInfo remoteBuild) throws BuildRetrievalError {
        String signedRegex = getSignedFileRegex();
        final String apkPathCSVString = remoteBuild.getAttribute(BuildAttributeKey.APP_APKS);
        String[] apks;
        if (apkPathCSVString.isEmpty()) {
            apks = new String[] {};
        } else {
            apks = apkPathCSVString.split(",");
        }
        List<String> apksList = new ArrayList<>();
        List<String> filesList = new ArrayList<>();
        for (String apk : apks) {
            String[] apkParts = apk.split("/");
            if (apkParts.length != 3) {
                throw new BuildRetrievalError("Malformatted APK name for signed build");
            }
            String apkFilename = apkParts[2];
            if (apkFilename.matches(signedRegex)) {
                apksList.add(createSignedParameter(apk));
                filesList.add(createSignedParameter(apk) +
                        ".SIGN_INFO");
            } else {
                apksList.add(apk);
            }
        }
        String signedApks = ArrayUtil.join(",", apksList);
        String signedFiles = remoteBuild.getAttribute(BuildAttributeKey.FILES) +
                ArrayUtil.join(",", filesList);
        remoteBuild.addAttribute(BuildAttributeKey.APP_APKS, signedApks);
        remoteBuild.addAttribute(BuildAttributeKey.FILES, signedFiles);
    }
}
