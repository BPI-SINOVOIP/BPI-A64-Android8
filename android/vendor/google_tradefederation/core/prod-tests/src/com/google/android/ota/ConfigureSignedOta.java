// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;

import com.google.android.ota.OtaDownloaderThread.IDownloadListener;

import org.junit.Assert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Utility class for downloading signed ota packages.
 * <p/>
 * Not really a test, but implemented this way to take advantage of other tradefed functionality
 * such as Option, BigStoreDownloader and other util classes.
 */
public class ConfigureSignedOta implements IRemoteTest {

    private static final String[] ALL_BUILD_FLAVORS = {
        "mysid-user", "yakju-user", "nakasi-user", "nakasig-user", "mantaray-user",
        "mictacea-user", "takju-user", "razor-user", "razorg-user", "occam-user",
        "hammerhead-user"
    };

    @Option(name = "build-id", shortName = 'b', description = "the build id to configure. ie IMM76")
    private String mBuildId = null;

    @Option(name = "branch", shortName = 'r', description = "the git branch name.")
    private String mBranch = "git_klp-release";

    // annoyingly, cannot use constants in annotation values so need to repeat ALL_BUILD_FLAVORS
    // here
    @Option(name = "build-flavor", shortName = 'f', description =
            "The build flavor(s) to configure. Defaults to [mysid-user, yakju-user, nakasi-user, "
            + "nakasig-user, mantaray-user, mictacea-user, takju-user, razor-user, razorg-user, "
            + "occam-user")
    private Collection<String> mBuildFlavors = new HashSet<String>();

    @Option(name = "file-path", description = "path to local folder to store downloaded files. "
            + "If unspecified, a local tmp dir will be created and deleted")
    private File mStorageDir = null;

    @Option(name = "incremental", description =
            "flag for controlling if incremental ota packages should be downloaded and pushed."
            + " If false, only the full ota packages will be downloaded/pushed.")
    private boolean mDoIncrementals = true;

    @Option(name = "full-self", description =
            "flag for controlling if full ota packages should be downloaded and pushed, " +
            "and corresponding rules generated for self-update to same build.")
    private boolean mDoFullSelfUpdate = false;

    @Option(name = "full-update", description =
            "flag for controlling if full ota packages should be downloaded and pushed, and " +
            "corresponding rules generated for update.")
    private boolean mDoFullUpdate = false;

    @Option(name = "ota-incremental-pattern", description =
            "The file pattern to use when downloading incremental otas.")
    private String mOtaIncrementalGlob = "signed/*-from-*.zip";

    @Option(name = "full-ota-pattern", description =
            "The file pattern to use when downloading full otas.")
    private String mFullOtaGlob = "signed/*-ota-*.zip";

    @Option(name = "overwrite",
            description = "Overwrite the OTA packages in the backend if they already exist.")
    private boolean mOverwrite = false;


    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        Assert.assertNotNull(mBuildId);
        if (mDoFullSelfUpdate && mDoFullUpdate) {
            CLog.e("Error: Cannot set both --full-self and --full-update");
            return;
        }
        // TODO: verify build id is in correct format
        File storageDir = null;
        File tmpTestingConfig = null;
        try {
            if (mStorageDir == null) {
                storageDir = FileUtil.createTempDir(mBuildId.toString() + "_");
                CLog.i("Using %s as temporary storage dir", storageDir.getAbsolutePath());
            } else {
                storageDir = mStorageDir;
            }
            // write the OTA checkin rules for the packages to a temp file, and when complete
            // send it as a listener log
            tmpTestingConfig = FileUtil.createTempFile("testing", ".config");
            Writer configWriter = new BufferedWriter(new FileWriter(tmpTestingConfig));

            OtaPackagePusher packagePusher = new OtaPackagePusher();
            packagePusher.setOverwrite(mOverwrite);
            OtaRuleGenerator ruleGenerator = new OtaRuleGenerator(mBuildId, packagePusher,
                    configWriter, mDoFullSelfUpdate);
            ruleGenerator.start();
            List<Thread> downloadThreads = downloadFiles(storageDir, ruleGenerator);

            for (Thread t : downloadThreads) {
                joinThread(t);
            }
            // all download threads done, safe to cancel rule generator
            ruleGenerator.cancel();
            joinThread(ruleGenerator);
            packagePusher.join();

            configWriter.close();
            try (FileInputStreamSource source = new FileInputStreamSource(tmpTestingConfig)) {
                listener.testLog("testing.config", LogDataType.TEXT, source);
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            if (mStorageDir == null) {
                FileUtil.recursiveDelete(storageDir);
            }
            FileUtil.deleteFile(tmpTestingConfig);
        }
    }

    private void joinThread(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            CLog.e(e);
        }
    }

    private List<Thread> downloadFiles(File storageDir, IDownloadListener listener) {
        Collection<String> buildFlavors = mBuildFlavors.isEmpty() ? Arrays.asList(ALL_BUILD_FLAVORS)
                : mBuildFlavors;
        List<Thread> threads = new ArrayList<Thread>();
        for (String flavor : buildFlavors) {
            if (mDoFullSelfUpdate || mDoFullUpdate) {
                spawnDownload(threads, mFullOtaGlob, storageDir, flavor, listener);
            }

            if (mDoIncrementals) {
                spawnDownload(threads, mOtaIncrementalGlob, storageDir, flavor, listener);
            }
        }
        return threads;
    }

    private void spawnDownload(List<Thread> threads, String fileGlob, File storageDir,
            String flavor, IDownloadListener listener) {
        try {
            OtaDownloaderThread t = new OtaDownloaderThread(fileGlob, storageDir,
                    flavor, mBranch, mBuildId, listener);
            t.start();
            threads.add(t);
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /** @param buildId */
    void setBuildId(String buildId) {
        mBuildId = buildId;
    }
}
