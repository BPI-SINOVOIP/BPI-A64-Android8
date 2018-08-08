// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.FileInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO(mikewallstedt): Replace this with classes for parsing XML outputs from ART gtests and
// the ART jvm-based tests. As is, this relies on the aggregated result of the ART test runner
// http://cs/aosp-master/art/build/Android.common_test.mk?l=166&rcl=0bdba6c3017f2b3602b1e6fe5c9b8717d800791c
/**
 * A {@link IRemoteTest} for parsing and reporting the results of an ART test log.
 */
public class ArtTestlogForwarder implements IRemoteTest, IBuildReceiver {

    private enum Section {
        UNKNOWN(""),
        PASSING("PASSING TESTS", "NO TESTS PASSED"),
        SKIPPED("SKIPPED TESTS", "NO TESTS SKIPPED"),
        FAILING("FAILING TESTS", "NO TESTS FAILED"),
        FINISHED("ninja: build stopped:");  // Indication of build failure.

        Set<String> markers;

        Section(String... markers) {
            this.markers = ImmutableSet.copyOf(markers);
        }

        boolean lineMatchesAnyMarker(String line) {
            for (String m: markers) {
                if (line.contains(m)) {
                    return true;
                }
            }
            return false;
        }
    }

    private IBuildInfo mBuildInfo;

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
        if (fileToExport != null) {
            try (FileInputStreamSource inputStream = new FileInputStreamSource(fileToExport)) {
                String name = fileToExport.getName();
                String ext = "." + LogDataType.TEXT.getFileExt();
                if (name.endsWith(ext)) {
                    name = name.substring(0, name.length() - ext.length());
                }
                // TODO(mikewallstedt): Update our usage of the Sponge HTTP API, such that these
                // can be attached as test logs (instead of build logs).
                listener.testLog(fileToExport.getName(), LogDataType.TEXT, inputStream);
                parseTestLog(listener, fileToExport);
            }
        }
    }

    @VisibleForTesting
    void parseTestLog(ITestInvocationListener listener, File fileToExport) {
        listener.testRunStarted("art-host-tests", 1);
        String contents = null;
        try {
            contents = StreamUtil.getStringFromStream(new FileInputStream(fileToExport));
        } catch (IOException e) {
            listener.testFailed(
                new TestIdentifier("Unknown", "Unknown"), StreamUtil.getStackTrace(e));
            return;
        }

        String gTestOutputPrefix = "test-art-host-gtest";
        String passRegex = "\\[.*\\]\\s+(test-art-host-run-test-.+) PASS";
        String failRegex = "\\[.*\\]\\s+(test-art-host-run-test-.+) FAIL";
        Pattern passPattern = Pattern.compile(passRegex);
        Pattern failPattern = Pattern.compile(failRegex);

        Section section = Section.UNKNOWN;
        for (String line : contents.split("\n")) {
            switch (section) {
                case UNKNOWN:
                    if (Section.PASSING.lineMatchesAnyMarker(line)) {
                        section = Section.PASSING;
                    } else {
                        Matcher m = passPattern.matcher(line);
                        if (m.find()) {
                            addPassingTest(listener, m.group(1));
                            continue;
                        }
                        m = failPattern.matcher(line);
                        if (m.find()) {
                            addFailingTest(listener, m.group(1), fileToExport.getName());
                            continue;
                        }
                    }
                    break;
                case PASSING:
                    if (Section.SKIPPED.lineMatchesAnyMarker(line)) {
                        section = Section.SKIPPED;
                    } else if (line.startsWith(gTestOutputPrefix)) {
                        addPassingTest(listener, line);
                    }
                    break;
                case SKIPPED:
                    if (Section.FAILING.lineMatchesAnyMarker(line)) {
                        section = Section.FAILING;
                    }
                    break;
                case FAILING:
                    if (line.startsWith(gTestOutputPrefix)) {
                        addFailingTest(listener, line, fileToExport.getName());
                    } else {
                        section = Section.UNKNOWN;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void addPassingTest(ITestInvocationListener listener, String line) {
        TestIdentifier id = getIdFromLine(line);
        listener.testStarted(id);
        listener.testEnded(id, ImmutableMap.of());
    }

    private void addFailingTest(ITestInvocationListener listener, String line, String logName) {
        TestIdentifier id = getIdFromLine(line);
        listener.testStarted(id);
        listener.testFailed(
            id, String.format("Failure detected. Details in Build Logs > %s", logName));
        listener.testEnded(id, ImmutableMap.of());
    }

    private TestIdentifier getIdFromLine(String line) {
        String testName = line.replaceFirst("test-art-host-", "");
        return new TestIdentifier(testName, testName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }
}
