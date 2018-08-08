// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.DeviceBuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.IDeviceBuildProvider;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.IFlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;
import com.google.android.tradefed.device.StaticDeviceInfo;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link LaunchControlProvider} for {@link IDeviceBuildInfo}
 */
@OptionClass(alias = "device-launch-control")
public class DeviceLaunchControlProvider extends LaunchControlProvider implements
        IDeviceBuildProvider {
    private final static String MKBOOTIMG_NAME = "mkbootimg";
    private final static String RAMDISK_NAME = "ramdisk.img";
    private final static String BOOTLOADER_FILTER = "bootloader\\.img";
    private final static String BASEBAND_FILTER = "radio\\.img";

    @Option(name = "download-build-file", description = "Files to download from the build " +
            "server.  May be repeated.")
    private Collection<BuildAttributeKey> mDownloadKeys =
            new HashSet<BuildAttributeKey>(ArrayUtil.list(
                    BuildAttributeKey.DEVICE_IMAGE, BuildAttributeKey.TESTS_ZIP,
                    BuildAttributeKey.BOOTLOADER, BuildAttributeKey.BASEBAND));

    @Option(name = "test-zip-file-filter",
            description = "Regex to specify the test zip to download. " +
            "If set, the first test zip matching this filter will be downloaded " +
            "instead of the default test zip.")
    private String mTestZipFileFilter = null;

    @Option(name = "skip-download", description = "Skip download of given file. May be repeated.")
    private Collection<BuildAttributeKey> mSkipDownloadKeys = new HashSet<BuildAttributeKey>();

    @Option(name = "skip-if-same", description = "Do not proceed with invocation if device is " +
        "already running requested build")
    private boolean mSkipIfSameBuild = false;

    @Option(name = "bootstrap-build-info", description = "When set, attempt to bootstrap build "
            + "info from device, then fallback to provided branch and build information; using "
            + "this option will implicitly use NOTIFY-TEST-CL or QUERY-LATEST-CL as query type")
    private boolean mBootstrapBuildInfo = false;

    @Option(name = "bootloader-filter",
            description = "Regex to specify the bootloader image to download. " +
            "If set, the first file in files matching this filter will be downloaded "
            + "and used as bootloader image.")
    private String mBootloaderFilter = BOOTLOADER_FILTER;

    @Option(name = "baseband-filter",
            description = "Regex to specify the baseband image to download. " +
            "If set, the first file in files matching this filter will be downloaded "
            + "and used as baseband image.")
    private String mBasebandFilter = BASEBAND_FILTER;

    @Option(name = "files-filter",
            description = "Regex to specify extra files to download. " +
            "If set, the first file in files matching each filter will be downloaded. " +
            "May be repeated.")
    private List<String> mFilePattern = new ArrayList<>();

    /** stores the current device build's id */
    private String mCurrentBuildId = null;

    String fetchTestsZipPath(RemoteBuildInfo remoteBuild) {
        if (mTestZipFileFilter == null || mTestZipFileFilter.isEmpty()) {
            return remoteBuild.getAttribute(BuildAttributeKey.TESTS_ZIP);
        }
        // if mTestZipFileFilter was specified, search through all files to locate the new
        // test zip file.
        final String filesPathCsvString = remoteBuild.getAttribute(BuildAttributeKey.FILES);
        if (filesPathCsvString == null) {
            return null;
        }
        String[] filePaths;
        if (filesPathCsvString.isEmpty()) {
            filePaths = new String[] {};
        } else {
            filePaths = filesPathCsvString.split(",");
        }
        for (String filePath : filePaths) {
            if (filePath.matches(mTestZipFileFilter)) {
                CLog.d("Found matching test zip file %s", filePath);
                return filePath;
            }
        }
        CLog.w("Did not find matching test zip file for regex %s", mTestZipFileFilter);
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void convertBuildToSigned(RemoteBuildInfo remoteBuild)
            throws BuildRetrievalError {
        // TODO: check that a signed build exists, error if there's no signed build
        replaceParamWithSigned(remoteBuild, BuildAttributeKey.DEVICE_IMAGE);
        replaceParamWithSigned(remoteBuild, BuildAttributeKey.TARGET_FILES);
        replaceParamWithSigned(remoteBuild, BuildAttributeKey.OTA_PACKAGE);
    }

    @Override
    protected IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        String buildId = remoteBuild.getBuildId();
        IDeviceBuildInfo localBuild = new DeviceBuildInfo(buildId, buildName);
        localBuild.setBuildFlavor(getBuildFlavor());
        localBuild.setBuildBranch(getBranch());
        localBuild.setTestTag(testTargetName);
        try {
            final String deviceImgPath = remoteBuild.getAttribute(BuildAttributeKey.DEVICE_IMAGE);
            if (shouldDownload(BuildAttributeKey.DEVICE_IMAGE)) {
                if (deviceImgPath == null) {
                    throw new BuildRetrievalError("Device image path (updater) not found in"
                            + " RemoteBuildInfo.");
                }
                localBuild.setDeviceImageFile(downloader.downloadFile(deviceImgPath), buildId);
            }
            final String userdataPath = remoteBuild.getAttribute(BuildAttributeKey.USER_DATA);
            if (userdataPath != null && shouldDownload(BuildAttributeKey.USER_DATA)) {
                localBuild.setUserDataImageFile(downloader.downloadFile(userdataPath), buildId);
            }

            final String testsZipPath = fetchTestsZipPath(remoteBuild);
            if (testsZipPath != null && shouldDownload(BuildAttributeKey.TESTS_ZIP)) {
                File testsZip = null;
                try {
                    testsZip = downloader.downloadFile(testsZipPath);
                    File testsDir = null;
                    try {
                        testsDir = extractZip(testsZip);
                    } catch (IOException e) {
                        // clear cache if we can't unzip the downloaded file.
                        deleteCacheEntry(testsZipPath);
                        throw e;
                    }
                    localBuild.setTestsDir(testsDir, buildId);
                } finally {
                    FileUtil.deleteFile(testsZip);
                }
            }

            final String otaPackagePath = remoteBuild.getAttribute(BuildAttributeKey.OTA_PACKAGE);
            if (otaPackagePath != null && shouldDownload(BuildAttributeKey.OTA_PACKAGE)) {
                localBuild.setOtaPackageFile(downloader.downloadFile(otaPackagePath), buildId);
            }

            // TODO: Get mkbootimg path from remoteBuild instead of constructing it.
            final String mkbootimgPath = createPathForImage(
                    buildName, remoteBuild.getBuildId(), MKBOOTIMG_NAME);
            if (shouldDownload(BuildAttributeKey.MKBOOTIMG)) {
                try {
                    localBuild.setMkbootimgFile(downloader.downloadFile(mkbootimgPath), buildId);
                } catch (BuildRetrievalError e) {
                    // Do nothing
                    CLog.i("mkbootimg not found on build server. Ignoring.");
                }
            }

            // TODO: Get ramdisk path from remoteBuild instead of constructing it.
            final String ramdiskPath = createPathForImage(
                    buildName, remoteBuild.getBuildId(), RAMDISK_NAME);
            if (shouldDownload(BuildAttributeKey.RAMDISK)) {
                try {
                    localBuild.setFile("ramdisk", downloader.downloadFile(ramdiskPath), buildId);
                } catch (BuildRetrievalError e) {
                    // Do nothing
                    CLog.i("ramdisk.img not found on build server. Ignoring.");
                }
            }

            String bootloaderVersion = buildId;
            String basebandVersion = buildId;
            if (shouldDownload(BuildAttributeKey.DEVICE_IMAGE)) {
                try {
                    IFlashingResourcesParser flashingResourcesParser = new FlashingResourcesParser(
                            localBuild.getDeviceImageFile());
                    bootloaderVersion = flashingResourcesParser.getRequiredBootloaderVersion();
                    basebandVersion = flashingResourcesParser.getRequiredBasebandVersion();
                } catch (TargetSetupError e) {
                    // delete the image from the cache
                    deleteCacheEntry(deviceImgPath);
                    throw new BuildRetrievalError(
                            "Failed to get image info from android-info.txt", e);
                }
            }
            // If the build artifact does not have bootloader or baseband images,
            // the flash resource retriever will try to download it from build api.
            if (shouldDownload(BuildAttributeKey.BOOTLOADER)) {
                final String bootloaderPath = getPathForImage(remoteBuild, mBootloaderFilter);
                if (bootloaderPath == null) {
                    CLog.d("There is no %s in files", mBootloaderFilter);
                } else {
                    try {
                        localBuild.setBootloaderImageFile(downloader.downloadFile(bootloaderPath),
                                bootloaderVersion);
                    } catch (BuildRetrievalError e) {
                        CLog.w("%s not found on build server. Ignoring.", mBootloaderFilter);
                    }
                }
            }

            if (shouldDownload(BuildAttributeKey.BASEBAND)) {
                final String basebandPath = getPathForImage(remoteBuild, mBasebandFilter);
                if (basebandPath == null) {
                    CLog.d("There is no %s in files", mBasebandFilter);
                } else {
                    try {
                        localBuild.setBasebandImage(downloader.downloadFile(basebandPath),
                                basebandVersion);
                    } catch (BuildRetrievalError e) {
                        CLog.w("%s not found on build server. Ignoring.", mBasebandFilter);
                    }
                }
            }

            if (!mFilePattern.isEmpty()) {
                for (String pattern : mFilePattern) {
                    final String filePath = getPathForImage(remoteBuild, pattern);
                    if (filePath == null) {
                        CLog.d("There is no '%s' in files", pattern);
                    } else {
                        try {
                            File downloadedFile = downloader.downloadFile(filePath);
                            localBuild.setFile(downloadedFile.getName(), downloadedFile, "");
                        } catch (BuildRetrievalError e) {
                            CLog.w("%s not found on build server. Ignoring.", mBasebandFilter);
                        }
                    }
                }
            }
        } catch (BuildRetrievalError e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            e.setBuildInfo(localBuild);
            throw e;
        } catch (IOException e) {
            localBuild.cleanUp();
            BuildRetrievalError be = new BuildRetrievalError(
                    String.format("IO error (%s) when retrieving device build", e.toString()), e);
            be.setBuildInfo(localBuild);
            throw be;
        } catch (RuntimeException e) {
            // one or more packages failed to download - clean up any successfully downloaded files
            localBuild.cleanUp();
            throw e;
        }
        return localBuild;
    }

    /**
     * Help method to create remote path for images.
     * @param buildName
     * @param buildId
     * @param imageName
     * @return remote path
     */
    private String createPathForImage(String buildName, String buildId, String imageName) {
        return String.format("%s/%s/%s", buildName, buildId, imageName);
    }

    /**
     * Help method to get remote path for images from RemoteBuildInfo
     * @param buildInfo a RemoteBuildInfo
     * @param pattern the pattern of the remote path for the image
     * @return the remote path
     */
    String getPathForImage(RemoteBuildInfo buildInfo, String pattern) {
        String filesString = buildInfo.getAttribute(BuildAttributeKey.FILES);
        if (filesString == null) {
            return null;
        }
        Pattern p = Pattern.compile(pattern);
        String[] fileStrings;
        if (filesString.isEmpty()) {
            fileStrings = new String[] {};
        } else {
            fileStrings = filesString.split(",");
        }
        for (String fileString: fileStrings) {
            Matcher matcher = p.matcher(fileString);
            if (matcher.find()) {
                return fileString;
            }
        }
        return null;
    }

    /**
     * Exposed for unit testing
     */
    Collection<BuildAttributeKey> getDownloadKeys() {
        return mDownloadKeys;
    }

    /**
     * Exposed for unit testing
     */
    Collection<BuildAttributeKey> getSkipDownloadKeys() {
        return mSkipDownloadKeys;
    }

    /**
     * Require downloading a file with a given {@link BuildAttributeKey}.  Note that exclusions
     * preempt inclusions, so if a file is in the skip list, it won't be downloaded, even if it's
     * mentioned here.
     */
    public void addDownloadKey(BuildAttributeKey attrKey) {
        mDownloadKeys.add(attrKey);
    }

    /**
     * Skip downloading a file with a given {@link BuildAttributeKey}.
     */
    public void skipDownload(BuildAttributeKey attrKey) {
        mSkipDownloadKeys.add(attrKey);
    }

    /**
     * Return true if file corresponding to given {@link BuildAttributeKey} should be downloaded
     * <p />
     * Exposed for unit testing
     */
    boolean shouldDownload(BuildAttributeKey attrKey) {
        return mDownloadKeys.contains(attrKey) && !mSkipDownloadKeys.contains(attrKey);
    }

    /**
     * Extract the given zip file to a local dir.
     * <p/>
     * Exposed so unit tests can mock
     *
     * @param testsZip
     * @return the {@link File} referencing the zip output.
     * @throws IOException
     */
    File extractZip(File testsZip) throws IOException {
        File testsDir = null;
        try (ZipFile zip = new ZipFile(testsZip)) {
            testsDir = FileUtil.createTempDir("tests-zip_");
            ZipUtil2.extractZip(zip, testsDir);
        } catch (IOException e) {
            FileUtil.recursiveDelete(testsDir);
            throw e;
        }
        return testsDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo info) {
        info.cleanUp();
    }

    /**
     * Gets build id, flavor from device and fetches branch information from build server
     * @param device
     */
    protected void resolveBuildInfoFromDevice(ITestDevice device)
            throws DeviceNotAvailableException, BuildRetrievalError {
        String buildIdProp = device.getBuildId();
        if (Pattern.matches("^\\d+$", buildIdProp)) {
            Long.parseLong(buildIdProp);
            final MultiMap<String, String> params = new MultiMap<>();
            params.put(QUERY_TYPE_PARAM, QueryType.GET_BUILD_DETAILS.getRemoteValue());
            params.put(BUILD_ID_PARAM, buildIdProp);
            RemoteBuildInfo rbi = queryForBuild(params);
            String branch = rbi.getAttribute(BuildAttributeKey.BRANCH);
            if (branch == null) {
                throw new BuildRetrievalError("cannot get branch info for build " + buildIdProp);
            }
            setBranch(branch);
            setBuildId(buildIdProp);
            // overriding query type since we are testing a particular build id
            setQueryType(QueryType.NOTIFY_TEST_BUILD);
            String flavor = device.getBuildFlavor();
            if (flavor != null) {
                setBuildFlavor(flavor);
            }
            CLog.i("Using info from device branch: %s build id: %s", branch, buildIdProp);
        } else {
            if (getBranch() == null) {
                // if device does not have a numerical build id, suggesting locally generated build
                // we then at least need branch parameter to decide where to get the build artifact
                throw new BuildRetrievalError(String.format(
                        "\"%s\" is not from build server, please use --branch <branch name>",
                        buildIdProp));
            } else {
                // override query param in case it's something else
                setQueryType(QueryType.QUERY_LATEST_BUILD);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild(ITestDevice device) throws BuildRetrievalError,
            DeviceNotAvailableException {
        if (mBootstrapBuildInfo) {
            resolveBuildInfoFromDevice(device);
        }
        if (getBuildFlavor() == null) {
            String product = device.getProductType();
            String variant = device.getProductVariant();
            String flavor = StaticDeviceInfo.getDefaultLcFlavor(product, variant);
            if (flavor == null) {
                throw new BuildRetrievalError(String.format(
                        "Could not determine build flavor for device %s, %s:%s",
                        device.getSerialNumber(), product, variant));
            } else {
                String buildType = device.getProperty("ro.build.type");
                if (buildType == null) {
                    CLog.i("Unknown build type for device %s. Assuming userdebug",
                            device.getSerialNumber());
                    buildType = "userdebug";
                }
                flavor = String.format("%s-%s", flavor, buildType);
                CLog.i("Using build flavor %s for device %s, %s:%s", flavor,
                        device.getSerialNumber(), product, variant);
                setBuildFlavor(flavor);
            }
        }
        if (mSkipIfSameBuild) {
            // only query device's build id if necessary, because this can result in an adb query
            // to device
            mCurrentBuildId = device.getBuildId();
        }
        return getBuild();
    }

    @Override
    public RemoteBuildInfo getRemoteBuild() throws BuildRetrievalError {
        RemoteBuildInfo remoteBuild = super.getRemoteBuild();
        if (mSkipIfSameBuild && remoteBuild != null &&
                remoteBuild.getBuildId().equals(mCurrentBuildId)) {
            CLog.d("Skipping retrieval of build %s, since device is already running that build",
                    mCurrentBuildId);
            return null;

        }
        return remoteBuild;
    }

    /**
     * Exposed for testing
     * @param bootstrapBuildInfo
     */
    public void setBootstrapBuildInfo(boolean bootstrapBuildInfo) {
        mBootstrapBuildInfo = bootstrapBuildInfo;
    }
}
