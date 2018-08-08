// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.buildstats;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Forwards the build statistics to the release dashboard.
 */
public class BuildStatsTest extends DeviceTestCase implements IBuildReceiver {

    private IStatsBuildInfo mStatsBuild;

    @Option(name = "ru-key", description = "name of ru key to report build stats to")
    private String mRuKey = "new-build-image-stats-2";

    @Option(name = "output-file", description = "the file which includes all the stats")
    private String mOutputFile = "build-stats";

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) {
        Map<String, String> metrics = new HashMap<String, String>();
        compileFileSizes(mStatsBuild.getSystemRoot(),
                mStatsBuild.getSystemRoot().getPath(), metrics);
        String allMetricsString = filterMetrics(metrics);
        try (InputStreamSource is = new ByteArrayInputStreamSource(allMetricsString.getBytes())) {
            listener.testLog(mOutputFile, LogDataType.TEXT, is);
        }
        reportMetrics(listener, mRuKey, metrics);
    }

    /**
     * Report run metrics by creating an empty test run to stick them in
     *
     * @param listener the {@link ITestInvocationListener} of test results
     * @param runName the test name
     * @param metrics the {@link Map} that contains metrics for the given test
     */
    void reportMetrics(ITestInvocationListener listener, String runName,
            Map<String, String> metrics) {
        // Create an empty testRun to report the parsed runMetrics
        CLog.d("About to report metrics: %s", metrics);
        listener.testRunStarted(runName, 0);
        listener.testRunEnded(0, metrics);
    }

    /**
     * Iterate through the file and compile the sizes for all files recursively.
     *
     * @param file the {@File} for which to compile the size for.
     * @param rootPath the {@String} of the root path.
     * @param metrics is the map where to store the file sizes keyed by file name.
     * @return total size of given file in bytes.
     */
    long compileFileSizes(File file, String rootPath, Map<String, String> metrics) {
        assertNotNull(file);
        assertNotNull(metrics);
        long totSize = 0;
        String path = file.getPath().replace(rootPath, "");
        if (!path.isEmpty() && path.startsWith(File.separator)) {
            path = path.substring(1);
        }
        if (file.isFile()) {
            long kbLength = file.length() / 1024;
            metrics.put(path, Long.toString(kbLength));
            return file.length();
        } else {
            for (File f : file.listFiles()) {
                totSize += compileFileSizes(f, rootPath, metrics);
            }
            // If the path is empty, we are the root path.
            if (path.isEmpty()) {
                path = "System Image";
            }
            long kbLength = totSize / 1024;
            metrics.put(path, Long.toString(kbLength));
        }
        return totSize;
    }

    /**
     * Filters metric map and only keep top level files and directories with their file sizes.
     * @param metrics Map of metrics to filter.
     * @return allMetrics {@link String} of all metrics
     */
    String filterMetrics(Map<String, String> metrics) {
        StringBuilder allMetrics = new StringBuilder("File, Size(kB)\n");
        for (Iterator<Map.Entry<String, String>> i = metrics.entrySet().iterator(); i.hasNext();) {
            Map.Entry<String, String> entry= i.next();
            allMetrics.append(String.format("%s, %d\n", entry.getKey(),
                    Long.parseLong(entry.getValue())));
            if (entry.getKey().contains("/")) {
                i.remove();
            }
        }
        return allMetrics.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mStatsBuild = (IStatsBuildInfo)buildInfo;
    }
}
