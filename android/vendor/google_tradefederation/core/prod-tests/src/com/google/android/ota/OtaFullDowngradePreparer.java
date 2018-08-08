// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.ota;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.OtaDeviceBuildInfo;
import com.android.tradefed.build.OtaToolsDeviceBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.android.tradefed.util.ReleaseToolsUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Turn an arbitrary OTA package into a downgrade.
 */
@OptionClass(alias = "full-downgrade")
public class OtaFullDowngradePreparer implements ITargetPreparer {

    private static final Pattern UPDATER_SCRIPT_LINE_PATTERN = Pattern.compile(
            "^\\(!less_than_int\\((?<timestamp>\\d{9,}),.*$");

    @Option(name = "enable", description = "whether or not to run this preparer")
    private boolean mEnable = false;

    long mBaselineTimestamp;
    OtaDeviceBuildInfo mOtaBuildInfo;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (!mEnable) {
            return;
        }
        if (!(buildInfo instanceof OtaDeviceBuildInfo)) {
            throw new TargetSetupError("OtaFullDowngradePreparer requires an OtaDeviceBuildInfo",
                    device.getDeviceDescriptor());
        }
        mOtaBuildInfo = (OtaDeviceBuildInfo) buildInfo;
        // reminder: need to set --files-filter build.prop
        Pattern buildPropPattern = Pattern.compile("build.*\\.prop.*");
        File buildProps = null;
        for (VersionedFile f : buildInfo.getFiles()) {
            Matcher matcher = buildPropPattern.matcher(f.getFile().getName());
            if (matcher.find()) {
                buildProps = f.getFile();
                break;
            }
        }
        if (buildProps == null) {
            throw new TargetSetupError("missing build.prop - did you forget to specify"
                    + "--files-filter build.prop ?",
                    device.getDeviceDescriptor());
        }
        mBaselineTimestamp = Long.parseLong(getTimestampFromBuildProp(device, buildProps));
        ZipFile pkg = null;
        try {
            pkg = new ZipFile(mOtaBuildInfo.getOtaBuild().getOtaPackageFile());
            ZipEntry updaterScript = null;
            if ((updaterScript = pkg.getEntry("META-INF/com/google/android/updater-script"))
                    != null) {
                modifyLegacyPackage(device, pkg, updaterScript);
            }
            else {
                ZipEntry metadata = pkg.getEntry("META-INF/com/android/metadata");
                modifyAbPackage(device, pkg, metadata);
            }
            if (!(mOtaBuildInfo instanceof OtaToolsDeviceBuildInfo)) {
                CLog.w("Cannot sign package; no otatools found "
                        + "(did you mean to specify --include-tools?)");
                return;
            }
            ZipUtil.closeZip(pkg);
            OtaToolsDeviceBuildInfo toolsInfo = (OtaToolsDeviceBuildInfo) mOtaBuildInfo;
            File newPackage = ReleaseToolsUtil.signOtaPackage(toolsInfo.getOtaTools(),
                    mOtaBuildInfo.getOtaBuild().getOtaPackageFile());
            CLog.d("renaming package to %s", toolsInfo.getOtaBuild().getOtaPackageFile());
            newPackage.renameTo(toolsInfo.getOtaBuild().getOtaPackageFile());
        } catch (IOException e) {
            CLog.e("Failed to modify package %s", mOtaBuildInfo.getOtaBuild().getOtaPackageFile());
            throw new TargetSetupError("Couldn't open OTA package",
                    e, device.getDeviceDescriptor());
        } finally {
            ZipUtil.closeZip(pkg);
        }
    }

    void modifyLegacyPackage(ITestDevice device, ZipFile pkg, ZipEntry updaterScript)
            throws TargetSetupError, IOException {
        BufferedReader reader = null;
        ZipOutputStream writer = null;
        FileOutputStream internal = null;
        try {
            // read the existing timestamp from the updater-script
            reader = new BufferedReader(
                    new InputStreamReader(pkg.getInputStream(updaterScript)));
            // the timestamp check is on the first line of the updater script
            String startCheck = reader.readLine();
            // we will need to rewrite the rest of the file later, so save the
            // remaining lines
            List<String> remainingLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                remainingLines.add(line);
            }
            StreamUtil.close(reader);
            // modify the timestamp
            String origTimestamp = getTimestampFromUpdaterScript(device, startCheck);
            String modifiedCheck = startCheck.replace(origTimestamp,
                    Long.toString(mBaselineTimestamp + 1)) + "\n";
            // java.util.zip is terrible at making modifications to existing zip archives, so
            // the most robust approach is to write a new file then rename it.
            File tmp = FileUtil.createTempFile("ota-full-downgrade", ".mod.zip");
            internal = new FileOutputStream(tmp);
            writer = new ZipOutputStream(internal);
            // copy the remaining entries first
            Enumeration<? extends ZipEntry> e = pkg.entries();
            while (e.hasMoreElements()) {
                ZipEntry ent = e.nextElement();
                if (!ent.getName().contains("updater-script")) {
                    writer.putNextEntry(ent);
                    InputStream is = pkg.getInputStream(ent);
                    byte [] buf = new byte[1024];
                    int len;
                    while((len = (is.read(buf))) > 0) {
                        writer.write(buf, 0, len);
                    }
                    is.close();
                    writer.closeEntry();
                }
            }
            // write the modified check to the beginning of the file
            // setting the compressedSize to -1 forces it to be recomputed in the zip central
            // directory
            updaterScript.setCompressedSize(-1);
            writer.putNextEntry(updaterScript);
            writer.write(modifiedCheck.getBytes());
            // assume that putNextEntry opens the file in 'w' mode, so
            // rewrite the rest of the lines too
            for (String remainingLine : remainingLines) {
                writer.write((remainingLine + "\n").getBytes());
            }
            writer.closeEntry();
            writer.finish();
            tmp.renameTo(mOtaBuildInfo.getOtaBuild().getOtaPackageFile());
        } finally {
            StreamUtil.close(internal);
            StreamUtil.close(reader);
            StreamUtil.close(writer);
        }
    }

    void modifyAbPackage(ITestDevice device, ZipFile pkg, ZipEntry metadata)
            throws TargetSetupError, IOException {
        FileOutputStream internal = null;
        BufferedReader reader = null;
        ZipOutputStream writer = null;
        try {
            // read the existing timestamp from the metadata
            reader = new BufferedReader(
                    new InputStreamReader(pkg.getInputStream(metadata)));
            List<String> beforeLines = new ArrayList<>();
            List<String> afterLines = new ArrayList<>();
            String line;
            String timestampLine = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("post-timestamp")) {
                    timestampLine = line;
                    continue;
                }
                if (timestampLine == null) {
                    beforeLines.add(line);
                } else {
                    afterLines.add(line);
                }
            }
            if (timestampLine == null) {
                throw new TargetSetupError("Could not find post-timestamp metadata",
                        device.getDeviceDescriptor());
            }
            StreamUtil.close(reader);
            String origTimestamp = timestampLine.split("=")[1];
            String modifiedTimestampLine = timestampLine.replace(origTimestamp,
                    Long.toString(mBaselineTimestamp + 1));
            // java.util.zip is terrible at making modifications to existing zip archives, so
            // the most robust approach is to write a new file then rename it.
            File tmp = FileUtil.createTempFile("ota-full-downgrade", ".mod.zip");
            internal = new FileOutputStream(tmp);
            writer = new ZipOutputStream(internal);
            writer.setMethod(ZipOutputStream.DEFLATED);
            // copy the remaining entries first
            Enumeration<? extends ZipEntry> e = pkg.entries();
            while (e.hasMoreElements()) {
                ZipEntry ent = e.nextElement();
                if (!ent.getName().contains("metadata")) {
                    CLog.d("copying entry %s", ent.getName());
                    writer.putNextEntry(ent);
                    InputStream is = pkg.getInputStream(ent);
                    byte [] buf = new byte[1024];
                    int len;
                    while((len = (is.read(buf))) > 0) {
                        writer.write(buf, 0, len);
                    }
                    is.close();
                    writer.closeEntry();
                }
            }
            // setting compressedSize to -1 forces the zip to recompute its size
            metadata.setCompressedSize(-1);
            // A/B packages are generated with the metadata file as STORED not DEFLATED,
            // which causes a CRC error
            metadata.setMethod(ZipEntry.DEFLATED);
            writer.putNextEntry(metadata);
            for (String l : beforeLines) {
                writer.write((l + '\n').getBytes());
            }
            writer.write((modifiedTimestampLine + '\n').getBytes());
            for (String l : afterLines) {
                writer.write((l + '\n').getBytes());
            }
            writer.closeEntry();
            writer.finish();
            tmp.renameTo(mOtaBuildInfo.getOtaBuild().getOtaPackageFile());
        } finally {
            StreamUtil.close(internal);
            StreamUtil.close(reader);
            StreamUtil.close(writer);
        }
    }

    String getTimestampFromUpdaterScript(ITestDevice device, String checkLine)
            throws TargetSetupError {
        Matcher m = UPDATER_SCRIPT_LINE_PATTERN.matcher(checkLine);
        if (!m.find()) {
            throw new TargetSetupError("Couldn't find build timestamp",
                    device.getDeviceDescriptor());
        }
        return m.group("timestamp");
    }

    String getTimestampFromBuildProp(ITestDevice device, File buildProps) throws TargetSetupError {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(buildProps));
            String line;
            String timestamp = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ro.build.date.utc")) {
                    timestamp = line.split("=")[1];
                    break;
                }
            }
            if (timestamp == null) {
                throw new TargetSetupError("Didn't find a timestamp in baseline build",
                        device.getDeviceDescriptor());
            }
            return timestamp;
        } catch (IOException e) {
            throw new TargetSetupError("Exception while reading build.prop",
                    e, device.getDeviceDescriptor());
        } finally {
            StreamUtil.close(reader);
        }
    }

}
