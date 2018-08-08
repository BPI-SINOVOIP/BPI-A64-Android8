// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.ota.OtaDownloaderThread.IDownloadListener;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;

/**
 * Class that listens for downloaded OTA files, generate the config rules for them, and then
 * notifies its own listeners.
 * <p/>
 * Implemented as a separate thread to control access to the single OTA rule writer
 */
class OtaRuleGenerator extends Thread implements IDownloadListener {

    static interface IRuleListener {
        public void otaRuleGenerated(File otaPackage);
    }

    /**
     * Template for ota rule. Format parameters must be in the following order:
     * <p/>
     * pre-build fingerprint, build id, update package size, description of update,
     * package file name
     */
    private static final String OTA_RULE_TEMPLATE = "\n"
            + "Rule {\n"
            + "  require: \"fingerprint:%s\"\n"
            + "  Set { name: \"update_title\" value: \"%s\" }\n"
            + "  Set { name: \"update_size\" value: \"%s\" }\n"
            + "  Set { name: \"update_urgency\" value: \"3\" }\n"
            + "  Set { name: \"update_watchdog_frequency\" value: \"3600000\" }\n"
            + "  Set { name: \"update_bypass_self_update_check\" value: \"true\" }\n"
            + "  Set {\n"
            + "    name: \"update_description\"\n"
            + "    value: \"<p>%s update</p>\"\n"
            + "  }\n"
            + "  Set {\n"
            + "    name: \"update_url\"\n"
            + "    value: \"http://android.clients.google.com/packages/internal/%s\"\n"
            + "  }\n"
            + "}\n";

    // path of ota metadata file in zip
    private static final String OTA_METADATA_PATH = "META-INF/com/android/metadata";

    private final IRuleListener mListener;

    /** queue that holds packages waiting to be processed */
    private final Queue<File> mPackageQueue;

    private boolean mCancelled = false;

    private final Writer mWriter;

    private final String mBuildId;

    private boolean mSelfUpdate;

    public OtaRuleGenerator(String buildId, IRuleListener listener, Writer ruleWriter,
            boolean selfUpdate)  {
        mListener = listener;
        mPackageQueue = new LinkedList<File>();
        mWriter = ruleWriter;
        mBuildId = buildId;
        mSelfUpdate = selfUpdate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void downloadFinished(File[] files) {
        synchronized (this) {
            for (File file : files) {
                mPackageQueue.add(file);
            }
            notifyAll();
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            while (!mCancelled) {
                File pkg = null;
                while ((pkg = mPackageQueue.poll()) != null) {
                    if (generateConfigForFile(mWriter, pkg)) {
                        mListener.otaRuleGenerated(pkg);
                    }
                }

                try {
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    public synchronized void cancel() {
        mCancelled = true;
        notifyAll();
    }

    private boolean generateConfigForFile(Writer configOutput, File otaFile) {
        try {
            File otaMetadataFile = ZipUtil2.extractFileFromZip(new ZipFile(otaFile),
                    OTA_METADATA_PATH);
            if (otaMetadataFile == null) {
                CLog.e("Couldn't find ota metadata in %s. not a valid ota zip?",
                        otaFile.getName());
                return false;
            }
            Reader propReader = new BufferedReader(new FileReader(otaMetadataFile));
            return generateConfigFromOtaProp(configOutput, otaFile, propReader);
        } catch (IOException e) {
            CLog.e(e);
            return false;
        }
    }

    /**
     * Generates an OTA rule given OTA package metadata
     * <p/>
     * Exposed for unit testing.
     *
     * @param configOutput
     * @param otaFile
     * @param propReader
     * @return
     * @throws IOException
     */
    boolean generateConfigFromOtaProp(Writer configOutput, File otaFile, Reader propReader)
            throws IOException {
        Properties otaProps = new Properties();
        otaProps.load(propReader);
        String postBuild = otaProps.getProperty("post-build");
        String preBuild = otaProps.getProperty("pre-build");
        if (postBuild == null) {
            CLog.e("Couldn't find post-build signature in OTA metadata for %s",
                    otaFile.getName());
            return false;
        }
        String updateDescription = mBuildId;
        if (preBuild == null) {
            if (mSelfUpdate) {
                CLog.i("Detected full update package for %s. Generating self update.",
                        otaFile.getName());
                preBuild = postBuild;
                updateDescription += " self ";
            } else {
                // generate a full update rule
                // assume postBuild is in google/mysid/toro:4.1.1/JRO03O/424425:user/release-keys
                // format
                int buildPrereqIndex = postBuild.indexOf(':');
                if (buildPrereqIndex == -1) {
                    CLog.e("Unexpected format in post-build signature %s in OTA metadata for %s",
                            postBuild, otaFile.getName());
                    return false;
                }
                String buildPrereq = postBuild.substring(0, buildPrereqIndex + 1);
                // adapt 'preBuild' to have both the build prereq, as well as the rule to
                // prevent continual updates. TODO: look for cleaner solution
                preBuild = String.format("%s\"\n" +
                "  require: \"!fingerprint:%s", buildPrereq, postBuild);
            }
        }
        String formattedFileSize = convertSize(otaFile.length());
        String otaRuleString = String.format(OTA_RULE_TEMPLATE, preBuild, mBuildId,
                formattedFileSize, updateDescription, otaFile.getName());
        configOutput.write(otaRuleString);
        return true;
    }

    /**
     * Convert the given file size in bytes to a more readable format
     * @param size
     * @return
     */
    String convertSize(long size) {
        String[] sizeSpecifiers = {
                "", "K", "M", "G"
        };
        for (int i = 0; i < sizeSpecifiers.length; i++) {
            if (size < 1024) {
                return String.format("%d%s", size, sizeSpecifiers[i]);
            }
            size /= 1024;
        }
        throw new IllegalArgumentException(String.format(
                "Passed a file size of %d, I cannot count that high", size));
    }
}
