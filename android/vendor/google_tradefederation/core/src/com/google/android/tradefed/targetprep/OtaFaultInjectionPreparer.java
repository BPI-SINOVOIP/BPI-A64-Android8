// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.build.OtaToolsDeviceBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;
import com.google.android.tradefed.util.ReleaseToolsUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * An {@link ITargetPreparer} that adds fault injection configuration to an OTA package ZIP.
 */
public class OtaFaultInjectionPreparer implements ITargetPreparer {

    protected static final String CFG_BASE = ".libotafault";

    @Option(name = "read-fault-file", description = "the filename to trigger a read fault")
    protected String mReadFaultFile = null;

    @Option(name = "write-fault-file", description = "the filename to trigger a write fault")
    protected String mWriteFaultFile = null;

    @Option(name = "fsync-fault-file", description = "the filename to trigger a fsync fault")
    protected String mFsyncFaultFile = null;

    @Option(name = "hit-cache", description = "whether or not to hit /cache/saved.file instead of "
            + "the targeted file")
    protected boolean mShouldHitCache = false;

    @Option(name = "sign-timeout", description = "timeout for signing the edited package")
    private long mSignTimeoutMs = 1000 * 60 * 5;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (!(buildInfo instanceof IDeviceBuildInfo)) {
            throw new TargetSetupError("OtaFaultInjectionPreparer must receive an "
                    + "IDeviceBuildInfo", device.getDeviceDescriptor());
        }
        IDeviceBuildInfo deviceBuild = (IDeviceBuildInfo) buildInfo;
        File pkgFile = deviceBuild.getOtaPackageFile();
        // if this pkgFile doesn't exist, this is a full update. Try to grab the package from
        // the target build.
        if (pkgFile == null || !pkgFile.exists()) {
            if (deviceBuild instanceof OtaDeviceBuildInfo) {
                pkgFile = ((OtaDeviceBuildInfo) deviceBuild).getOtaBuild().getOtaPackageFile();
            } else {
                throw new TargetSetupError("Received a BuildInfo which is not OtaDeviceBuildInfo,"
                        + " but no OTA package was included", device.getDeviceDescriptor());
            }
        }
        CLog.i("Adding fault injection to %s", pkgFile);
        ZipOutputStream otaPackageStream = null;
        ZipInputStream otaPackageReader = null;
        ZipFile oldPkg = null;
        File changedPackage = null;
        File tmpOtaFaultDir = null;
        try {
            tmpOtaFaultDir = FileUtil.createTempDir("otafault");
            changedPackage = ZipUtil.createZip(tmpOtaFaultDir);
            otaPackageStream = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(changedPackage)));
            otaPackageReader = new ZipInputStream(new BufferedInputStream(new FileInputStream(
                    pkgFile)));
            // copy old zip material to otaPackageStream to emulate appending
            // since Java's zip implementation doesn't support appending
            oldPkg = new ZipFile(pkgFile);
            ZipEntry ent;
            while ((ent = otaPackageReader.getNextEntry()) != null) {
                CLog.i("Copying zip entry %s", ent.getName());
                otaPackageStream.putNextEntry(ent);
                StreamUtil.copyStreams(otaPackageReader, otaPackageStream);
                otaPackageStream.closeEntry();
                otaPackageReader.closeEntry();
            }
            otaPackageStream.flush();
            if (mReadFaultFile != null) {
                addToConfig(otaPackageStream, "READ", mReadFaultFile);
            }
            if (mWriteFaultFile != null) {
                addToConfig(otaPackageStream, "WRITE", mWriteFaultFile);
            }
            if (mFsyncFaultFile != null) {
                addToConfig(otaPackageStream, "FSYNC", mFsyncFaultFile);
            }
            if (mShouldHitCache) {
                addToConfig(otaPackageStream, "CACHE", "");
            }
            if (buildInfo instanceof OtaToolsDeviceBuildInfo) {
                OtaToolsDeviceBuildInfo toolsInfo = (OtaToolsDeviceBuildInfo)buildInfo;
                // Zipfile needs to be closed before it can be signed, or signapk can't acquire
                // a file descriptor
                StreamUtil.close(otaPackageStream);
                File newPackage = ReleaseToolsUtil.signOtaPackage(
                        toolsInfo.getOtaTools(), changedPackage, mSignTimeoutMs);
                toolsInfo.setOtaPackageFile(newPackage, deviceBuild.getOtaPackageVersion());
            } else {
                // a generic IDeviceBuildInfo won't allow us to overwrite the otaPackageFile with
                // setOtaPackageFile. So instead we can just rename changedPackage to pkgFile.
                changedPackage.renameTo(pkgFile);
                CLog.w("Can't re-sign package, buildInfo didn't contain any otatools!");
            }
        } catch (IOException e) {
            FileUtil.recursiveDelete(changedPackage);
            throw new TargetSetupError("Could not add config files to OTA zip", e,
                    device.getDeviceDescriptor());
        } finally {
            ZipUtil.closeZip(oldPkg);
            FileUtil.recursiveDelete(tmpOtaFaultDir);
            StreamUtil.close(otaPackageReader);
            StreamUtil.close(otaPackageStream);
        }
    }

    private void addToConfig(ZipOutputStream packageStream, String ioType, String targetFile)
            throws IOException {
        File cfgFile = new File(ioType);
        try {
            FileWriter cfgWriter = new FileWriter(cfgFile);
            cfgWriter.write(targetFile, 0, targetFile.length());
            cfgWriter.close();
            ZipUtil.addToZip(packageStream, cfgFile,
                    ArrayUtil.list(CFG_BASE, "/"));
        } finally {
            cfgFile.delete();
        }
    }

}
