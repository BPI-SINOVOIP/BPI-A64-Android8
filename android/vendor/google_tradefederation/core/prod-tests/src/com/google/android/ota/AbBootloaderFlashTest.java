// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.ota;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil2;

import com.google.android.tradefed.build.RemoteBuildInfo.BuildAttributeKey;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An {@IDeviceTest} to confirm that the bootloader on A/B devices is flashed correctly.
 *
 * See b/30635833 for more context. This test assumes that the DUT has had its bootloader flashed
 * to the most recent version. The test unpacks partition images for partitions defined in
 * META/ab_partitions.txt in the build's target_files.zip, then compares them to
 * partition images stored on device at --boot-partition-dir.
 */
@OptionClass(alias = "bootloader-flash")
public class AbBootloaderFlashTest implements IRemoteTest, IBuildReceiver, IDeviceTest {

    private static final String BOOTCTL = "bootctl";
    private static final String BASE_BOOT_PART_DIR = "/dev/block/bootdevice/by-name/";
    private static final int COMPARE_BLOCK = 512;
    private static final String PARTITION_LIST_PATH = "META/ab_partitions.txt";

    @Option(name = "ignore-partition", description = "partitions that won't be checked")
    private List<String> mIgnoredPartitions = new ArrayList<String>();

    @Option(name = "boot-partition-dir", description = "base folder where boot partitions are"
        + "located on disk")
    private String mBootPartitionDir = BASE_BOOT_PART_DIR;

    private IBuildInfo mBuildInfo;
    private ITestDevice mDevice;
    private PartitionSuffix mSuffix;

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        File abPartitionList = null;
        ZipFile targetFilesZip = null;
        BufferedReader partitions = null;
        listener.testRunStarted("bootloader-flash", 1);
        long start = System.currentTimeMillis();
        setupSuffix();
        try {
            targetFilesZip = new ZipFile(
                    mBuildInfo.getFile(BuildAttributeKey.TARGET_FILES.getRemoteValue()));
            abPartitionList = ZipUtil2.extractFileFromZip(targetFilesZip, PARTITION_LIST_PATH);
            partitions = new BufferedReader(
                    new InputStreamReader(new FileInputStream(abPartitionList)));
            String partition;
            while ((partition = partitions.readLine()) != null) {
                if (!mIgnoredPartitions.contains(partition)) {
                    CLog.i("Verifying partition %s", partition);
                    String partitionFileName = getImageFileName(partition);
                    if (targetFilesZip.getEntry(partitionFileName) == null) {
                        String error = String.format(
                                "Could not find partition file %s in target_files",
                                partitionFileName);
                        markFailed(listener, null, error);
                        break;
                    }
                    File partitionFile = null;
                    TestIdentifier testId = new TestIdentifier(getClass().getCanonicalName(),
                            "flash-check-" + partition);
                    listener.testStarted(testId);
                    try {
                        partitionFile = ZipUtil2.extractFileFromZip(targetFilesZip,
                                partitionFileName);
                        boolean foundPartition = false;
                        File pulledPartition = null;
                        try {
                            pulledPartition =
                                    mDevice.pullFile(getOnDiskPartition(partition, mSuffix));
                            if (pulledPartition == null) {
                                CLog.i("No partition %s%s", partition, mSuffix);
                            } else {
                                CLog.i("Checking partition %s%s", partition, mSuffix);
                                foundPartition = true;
                                checkFilesMatch(listener, testId, pulledPartition, partitionFile);
                            }
                        } finally {
                            FileUtil.deleteFile(pulledPartition);
                        }
                        if (!foundPartition) {
                            String error = String.format(
                                    "Didn't find any suffixes for %s", partition);
                            markFailed(listener, testId, error);
                        }
                        listener.testEnded(testId, new HashMap<String, String>());
                    } finally {
                        FileUtil.deleteFile(partitionFile);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            listener.testRunEnded(System.currentTimeMillis() - start,
                    new HashMap<String, String>());
            StreamUtil.close(partitions);
            ZipUtil2.closeZip(targetFilesZip);
            FileUtil.deleteFile(abPartitionList);
        }
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    private String getImageFileName(String partitionName) {
        return "IMAGES/" + partitionName + ".img";
    }

    private String getOnDiskPartition(String partitionName, PartitionSuffix suffix) {
        return mBootPartitionDir + partitionName + suffix;
    }

    private void setupSuffix() throws DeviceNotAvailableException {
        String currentSlotResult = getDevice().executeShellCommand(BOOTCTL + " get-current-slot");
        int currentSlot = Integer.parseInt(currentSlotResult.trim().substring(0, 1));
        if (currentSlot == 0) {
            mSuffix = PartitionSuffix.A;
        } else {
            mSuffix = PartitionSuffix.B;
        }
    }

    private void checkFilesMatch(ITestInvocationListener listener, TestIdentifier testId,
            File fromDevice, File fromZip)
            throws IOException {
        InputStream deviceContents = new FileInputStream(fromDevice);
        InputStream zipContents = new FileInputStream(fromZip);
        byte[] buf1 = new byte[COMPARE_BLOCK];
        byte[] buf2 = new byte[COMPARE_BLOCK];
        int deviceReadCt = 0, zipReadCt = 0;
        try {
            do {
                deviceReadCt = deviceContents.read(buf1, 0, COMPARE_BLOCK);
                zipReadCt = zipContents.read(buf2, 0, COMPARE_BLOCK);
                if (deviceReadCt <= 0 || !Arrays.equals(buf1, buf2)) {
                    String error = String.format("File %s doesn't match file %s; byte mismatch",
                            fromDevice.getName(), fromZip.getName());
                    markFailed(listener, testId, error);
                }
            } while (zipReadCt > 0);
        } finally {
            StreamUtil.close(deviceContents);
            StreamUtil.close(zipContents);
        }
        CLog.i("Found no differences in files %s and %s", fromDevice.getName(), fromZip.getName());
    }

    private void markFailed(ITestInvocationListener listener, TestIdentifier id, String msg) {
        if (id != null) {
            listener.testFailed(id, msg);
            listener.testEnded(id, new HashMap<String, String>());
        }
        listener.testRunFailed(msg);
        throw new AssertionError(msg);
    }

    private enum PartitionSuffix {
        A("_a"),
        B("_b");

        String mSuffix;
        PartitionSuffix(String suffix) {
            mSuffix = suffix;
        }

        @Override
        public String toString() {
            return mSuffix;
        }
    }
}
