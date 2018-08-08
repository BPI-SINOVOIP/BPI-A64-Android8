// Copyright 2015 Google Inc.  All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IFileDownloader;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.build.OtaToolsDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo;
import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;
import com.google.android.tradefed.util.ReleaseToolsUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generate an incremental OTA package.
 */
public class IncrementalOtaLaunchControlProvider extends OtaDeviceLaunchControlProvider {

    private static final long OTA_GEN_TIMEOUT = 1000 * 60 * 60;
    private static final int MAX_OTA_GEN_HOLDS = 2;
    private static final String TIMESTAMP_FIELD = "ro.build.date.utc";

    @Option(name = "use-block", description = "whether or not to generate a block-based package")
    private boolean mUseBlock = true;

    @Option(name = "sign-ota-package", description = "whether or not to sign the OTA package")
    private boolean mSignPackage = true;

    @Option(name = "ota-gen-timeout", description = "timeout in ms of releasetools scripts")
    private long mOtaTimeout = OTA_GEN_TIMEOUT;

    @Option(name = "package-key-path", description = "path of key to use for signing the package")
    private String mPackageKeyPath =
            "/google/data/ro/teams/tradefed/testdata/ota-incremental/devkey";

    @Option(name = "oem-prop-path", description = "path to OEM properties file")
    private String mOemPropPath = "";

    @Option(name = "use-verify", description = "whether or not to remount and verify the "
            + "checksums of the files")
    private boolean mVerify = false;

    @Option(
        name = "use-two-step-ota",
        description =
                "whether or not to use a 2-step OTA, where recovery is updated first, so "
                        + "that any changes made to the system partition are done using the new "
                        + "recovery (new kernel, etc.)"
    )
    private boolean mTwoStep = false;

    @Option(name = "ota-tools-flags", description = "A list of flag that should be set for "
            + "ota_from_target_file.py")
    private List<String> mOtaToolsFlags = new ArrayList<String>();

    // A non-Option parameter that swaps the order of the target_files when passed into
    // ota_from_target_files.
    private boolean mSwap = false;
    boolean mDowngrade = false;
    File mFromTargetFilesPath;
    File mToTargetFilesPath;
    private File mOtaToolsFile;
    private File mOtaToolsUnzipDir;
    private File mOtaScript;
    private File mOutputPackage;
    private Path mOemPropFile;

    /**
     * A counting lock to prevent excessive entry into ota_from_target_files
     */
    private static Semaphore sGeneratorLock = new Semaphore(MAX_OTA_GEN_HOLDS, true);

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo downloadBuildFiles(RemoteBuildInfo remoteBuild, String testTargetName,
            String buildName, IFileDownloader downloader) throws BuildRetrievalError {
        final String fromTargetFilesName = remoteBuild.getAttribute(BuildAttributeKey.TARGET_FILES);
        final String toTargetFilesName = getTargetRemoteBuild().getAttribute(
                BuildAttributeKey.TARGET_FILES);
        // TODO(jestep): get this from remote build
        final String otatoolsName = String.format("%s-linux-%s/%s/%s",
                getBranch(),
                getBuildFlavor(),
                remoteBuild.getBuildId(),
                "otatools.zip");

        try {
            if (mSwap) {
                mFromTargetFilesPath = downloader.downloadFile(toTargetFilesName);
                mToTargetFilesPath = downloader.downloadFile(fromTargetFilesName);
            } else {
                mFromTargetFilesPath = downloader.downloadFile(fromTargetFilesName);
                mToTargetFilesPath = downloader.downloadFile(toTargetFilesName);
            }
            detectDowngrade();
            mOtaToolsFile = downloader.downloadFile(otatoolsName);
            try {
                mOtaToolsUnzipDir = ZipUtil2.extractZipToTemp(mOtaToolsFile, "otatest");
                mOtaScript = new File(mOtaToolsUnzipDir, "releasetools/ota_from_target_files.py");
                mOtaScript.setExecutable(true);
                new File(mOtaToolsUnzipDir, "bin/imgdiff").setExecutable(true);
                new File(mOtaToolsUnzipDir, "bin/bsdiff").setExecutable(true);
                // A/B files updates need extra files
                grantExecuteIfExists("bin/brillo_update_payload");
                grantExecuteIfExists("bin/delta_generator");
                grantExecuteIfExists("bin/simg2img");
                if (!mOemPropPath.isEmpty()) {
                    // Check if the path is absolute.
                    if (Paths.get(mOemPropPath).isAbsolute()) {
                        mOemPropFile = Paths.get(mOemPropPath);
                    } else {
                        mOemPropFile = Paths.get(mOtaToolsUnzipDir.getAbsolutePath(), mOemPropPath);
                    }
                }
                mOutputPackage = generateOtaPackage();
            } catch (IOException | InterruptedException e) {
                FileUtil.recursiveDelete(mOtaToolsUnzipDir);
                throw new BuildRetrievalError("An error occurred from ota_from_target_files", e);
            }
            try {
                // we may run out of /tmp space if we keep these large files around; delete them now
                FileUtil.deleteFile(mFromTargetFilesPath);
                FileUtil.deleteFile(mToTargetFilesPath);
                // sign the package; ota_from_target_files can do this, but it expects certs and
                // other files to be in specific locations which can't be modified from its
                // command line.
                try {
                    if (mSignPackage) {
                        File signedPackage = ReleaseToolsUtil.signOtaPackage(
                                mOtaToolsUnzipDir, mOutputPackage, OTA_GEN_TIMEOUT);
                        mOutputPackage.delete();
                        mOutputPackage = signedPackage;
                    }
                } catch (IOException e) {
                    throw new BuildRetrievalError("An error occurred while signing an ota package",
                            e);
                }
                String buildId = remoteBuild.getBuildId();
                // Don't download *any* OTA packages
                super.skipDownload(BuildAttributeKey.OTA_PACKAGE);
                getOtaLcProvider().skipDownload(BuildAttributeKey.OTA_PACKAGE);
                if (getIncludeTools()) {
                    OtaToolsDeviceBuildInfo buildInfo = new OtaToolsDeviceBuildInfo(
                            (OtaDeviceBuildInfo) super.downloadBuildFiles(
                                    remoteBuild, testTargetName, buildName, downloader));
                    buildInfo.setOtaPackageFile(mOutputPackage, buildId);
                    CLog.i("Set OTA package to %s",
                            buildInfo.getOtaBuild().getOtaPackageFile().getAbsolutePath());
                    buildInfo.setOtaTools(mOtaToolsUnzipDir);
                    buildInfo.setReportTargetBuild(shouldReportTargetBuild());
                    return buildInfo;
                } else {
                    OtaDeviceBuildInfo buildInfo = (OtaDeviceBuildInfo) super.downloadBuildFiles(
                            remoteBuild, testTargetName, buildName, downloader);
                    buildInfo.getOtaBuild().setOtaPackageFile(mOutputPackage,
                            buildInfo.getOtaBuild().getBuildId());
                    buildInfo.setOtaPackageFile(mOutputPackage, buildId);
                    CLog.i("Set OTA package to %s",
                            buildInfo.getOtaBuild().getOtaPackageFile().getAbsolutePath());
                    buildInfo.setReportTargetBuild(shouldReportTargetBuild());
                    return buildInfo;
                }
            } finally {
                // These aren't safe to delete in cleanUp; if this build provider is used in
                // conjunction with an IShardableTest, they might be deleted before the invocation
                // actually has a chance to start
                FileUtil.deleteFile(mOtaToolsFile);
                if (!getIncludeTools()) {
                    FileUtil.deleteFile(mOtaToolsUnzipDir);
                }
            }
        } catch (BuildRetrievalError e) {
            FileUtil.recursiveDelete(mOtaToolsUnzipDir);
            FileUtil.deleteFile(mOtaToolsFile);
            FileUtil.deleteFile(mOutputPackage);
            throw e;
        } finally {
            FileUtil.deleteFile(mFromTargetFilesPath);
            FileUtil.deleteFile(mToTargetFilesPath);
        }
    }

    /**
     * Detects whether or not the detected pre- and post-builds represent a downgrade. This
     * checks the ro.build.date.utc property in the included build.prop file. If a downgrade
     * is detected, set mDowngrade to true.
     * @throws BuildRetrievalError
     */
    void detectDowngrade() throws BuildRetrievalError {
        ZipFile pre = null;
        ZipFile post = null;
        BufferedReader preReader = null;
        BufferedReader postReader = null;
        try {
            pre = new ZipFile(mFromTargetFilesPath);
            post = new ZipFile(mToTargetFilesPath);
            ZipEntry preEntry = pre.getEntry("SYSTEM/build.prop");
            ZipEntry postEntry = post.getEntry("SYSTEM/build.prop");
            preReader = new BufferedReader(
                    new InputStreamReader(pre.getInputStream(preEntry)));
            postReader = new BufferedReader(
                    new InputStreamReader(post.getInputStream(postEntry)));
            String line;
            String preStamp = null;
            String postStamp = null;
            while ((line = preReader.readLine()) != null) {
                if (line.startsWith(TIMESTAMP_FIELD)) {
                    preStamp = line.split("=")[1];
                    break;
                }
            }
            if (preStamp == null) {
                throw new BuildRetrievalError("Found no pre build timestamp for " + getBuildId());
            }
            while ((line = postReader.readLine()) != null) {
                if (line.startsWith(TIMESTAMP_FIELD)) {
                    postStamp = line.split("=")[1];
                    break;
                }
            }
            if (postStamp == null) {
                throw new BuildRetrievalError("Found no post build timestamp for "
                        + getOtaLcProvider().getBuildId());
            }
            long preDate = Long.parseLong(preStamp);
            long postDate = Long.parseLong(postStamp);
            mDowngrade = postDate < preDate;
        } catch (IOException e) {
            throw new BuildRetrievalError("Error checking for downgrade", e);
        } finally {
            ZipUtil.closeZip(pre);
            ZipUtil.closeZip(post);
            StreamUtil.close(preReader);
            StreamUtil.close(postReader);
        }
    }

    private void grantExecuteIfExists(String suffix) {
        File executable = new File(mOtaToolsUnzipDir, suffix);
        if (executable.exists()) {
            executable.setExecutable(true);
        }
    }

    private File generateOtaPackage() throws BuildRetrievalError, IOException,
            InterruptedException {
        CLog.i("Thread %s acquiring package generator lock", Thread.currentThread().getName());
        sGeneratorLock.acquire();
        try {
            mOutputPackage = FileUtil.createTempFile("otatest", "incremental");
            // don't use the default RunUtil or we can't setEnvVariable
            IRunUtil runUtil = new RunUtil();
            // add otatools/bin to the path, to gain access to imgdiff and bsdiff
            String initialPath = System.getenv("PATH");
            String initialLdPath = System.getenv("LD_LIBRARY_PATH");
            String newBinPath = new File(mOtaToolsUnzipDir, "bin").getAbsolutePath();
            String signapkLibPath = new File(mOtaToolsUnzipDir, "lib64").getAbsolutePath();
            CLog.i("Adding %s to PATH", newBinPath);
            runUtil.setEnvVariable("PATH", newBinPath + ":" + initialPath);
            CLog.d("PATH is now %s", newBinPath + ":" + initialPath);
            CLog.i("Adding %s to LD_LIBRARY_PATH", signapkLibPath);
            runUtil.setEnvVariable("LD_LIBRARY_PATH", signapkLibPath + ":" + initialLdPath);
            CLog.d("LD_LIBRARY_PATH is now %s", signapkLibPath + ":" + initialLdPath);
            runUtil.setEnvVariable("PYTHONPATH",
                    new File(mOtaToolsUnzipDir, "releasetools").getAbsolutePath());
            // generate the package
            if (!(new File(mPackageKeyPath + ".pk8").canRead())) {
                throw new BuildRetrievalError("Supplied a nonexistent or unreadable key "
                        + mPackageKeyPath);
            }
            List<String> cmd = ArrayUtil.list(
                    mOtaScript.getAbsolutePath(),
                    "-k", mPackageKeyPath,
                    "-p", mOtaToolsUnzipDir.getAbsolutePath());
            if (mDowngrade) {
                cmd.add("--downgrade");
            }
            if (mUseBlock) {
                cmd.add("--block");
            }
            if (mTwoStep) {
                cmd.add("-2");
            }
            if (mOemPropFile != null) {
                cmd.add("-o");
                cmd.add(mOemPropFile.toString());
            }
            if (mVerify) {
                cmd.add("--verify");
            }
            if (mOtaToolsFlags != null && !mOtaToolsFlags.isEmpty()) {
                for (String flag : mOtaToolsFlags) {
                    cmd.add(String.format("--%s", flag));
                }
            }
            cmd.addAll(Arrays.asList(
                    "-i",
                    mFromTargetFilesPath.getAbsolutePath(),
                    mToTargetFilesPath.getAbsolutePath(),
                    mOutputPackage.getAbsolutePath()));
            CLog.i("Generating OTA package with command %s", ArrayUtil.join(" ", cmd.toArray()));
            CommandResult c = runUtil.runTimedCmd(mOtaTimeout, cmd.toArray(new String[0]));
            try {
                if (c.getStatus() != CommandStatus.SUCCESS) {
                    CLog.e("ota_from_target_files failed due to an error");
                    mOutputPackage.delete();
                    throw new BuildRetrievalError("ota_from_target_files failed");
                }
            } finally {
                CLog.d("ota_from_target_files stdout: %s", c.getStdout());
                CLog.d("ota_from_target_files stderr: %s", c.getStderr());
            }
            return mOutputPackage;
        } finally {
            sGeneratorLock.release();
        }
    }

    void setSwap(boolean swap) {
        mSwap = swap;
    }

    @Override
    public void cleanUp(IBuildInfo buildInfo) {
        super.cleanUp(buildInfo);
        FileUtil.deleteFile(mFromTargetFilesPath);
        FileUtil.deleteFile(mToTargetFilesPath);
        FileUtil.recursiveDelete(mOtaToolsUnzipDir);
    }
}

