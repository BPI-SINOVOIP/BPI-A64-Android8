// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.JUnitXmlParser;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A {@link IRemoteTest} for parsing and reporting the results of a gradle-generated unit test log.
 */
public class GradleTestlogForwarder implements IRemoteTest, IBuildReceiver {

    @Option(name = "testlog-inarchive-regex",
        description = "regex pattern to identify names of logs within a zip archive.",
        importance = Importance.IF_UNSET, mandatory = true)
    private String mTestlogInArchiveRegex = "TEST.*\\.xml";

    private Pattern mTestlogInArchivePattern;

    private IBuildInfo mBuildInfo;

   public GradleTestlogForwarder() {
       mTestlogInArchivePattern = Pattern.compile(mTestlogInArchiveRegex);
   }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        for (VersionedFile vf : mBuildInfo.getFiles()) {
            logFile(vf.getFile(), listener);
        }
    }

    private void logFile(File fileToExport, ITestInvocationListener listener) {
        if (matchesExtension(fileToExport, LogDataType.ZIP)) {
            logZippedXml(fileToExport, listener);
        } else if (matchesExtension(fileToExport, LogDataType.XML)) {
            logXml(fileToExport, listener);
        }
    }

    private boolean matchesExtension(File f, LogDataType ext) {
        return f != null && f.getName().endsWith("." + ext.getFileExt());
    }

    private void logZippedXml(File fileToExport, ITestInvocationListener listener) {
        try {
            JUnitXmlParser jUnitXmlParser = new JUnitXmlParser(listener);
            try (ZipFile zip = new ZipFile(fileToExport)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (mTestlogInArchivePattern.matcher(entry.getName()).find()) {
                        jUnitXmlParser.parse(zip.getInputStream(entry));
                    }
                }
            }
        } catch (java.io.IOException | ParseException e) {
            listener.testFailed(
                new TestIdentifier(
                    fileToExport.getName(), "Unknown Test Method"), StreamUtil.getStackTrace(e));
        }
    }

    private void logXml(File fileToExport, ITestInvocationListener listener) {
        String name = fileToExport.getName();
        String ext = "." + LogDataType.XML.getFileExt();
        if (name.endsWith(ext)) {
            name = name.substring(0, name.length() - ext.length());
        }
        try (FileInputStreamSource inputStream = new FileInputStreamSource(fileToExport)) {
            listener.testLog(name, LogDataType.XML, inputStream);
            new JUnitXmlParser(listener).parse(new FileInputStream(fileToExport));
        } catch (FileNotFoundException | ParseException e) {
            listener.testFailed(
                new TestIdentifier(name, "Unknown Test Method"), StreamUtil.getStackTrace(e));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
