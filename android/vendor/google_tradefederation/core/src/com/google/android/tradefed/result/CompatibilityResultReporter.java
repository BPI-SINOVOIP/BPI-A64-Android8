// Copyright 2014 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.LogcatItem;
import com.android.tradefed.config.Option;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Reporter that posts extra data from the Compatibility test.
 */
public class CompatibilityResultReporter extends InspectBugResultReporter {

    private static final String KEY_VERSION = "app_version";
    private static final String FAILURE_PREFIX = "FAILURE:";
    private static final String ERROR_PREFIX = "ERROR:";
    private static final int POSTING_INTERVAL = 1 * 1000;
    private String mTrace;
    private TestIdentifier mTest;
    private List<JSONObject> mSavedResults = new ArrayList<JSONObject>();
    private boolean mAlreadyPosted = false;
    private String mParsedBuildId = null;
    private String mParsedBuildAlias = null;

    @Option(name = "suite-info-file", description = "when specified, load suite information from "
            + "a serialized build info meta data file. Build id will be used as suite instance key "
            + "and build aliasn will be used as run description. This will not override an "
            + "explicitly provided suite-instance-key or description parameters")
    private File mSuiteInfoFile = null;
    /*
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        mTrace = trimTrace(trace);
        super.testFailed(test, mTrace);
    }

    private String trimTrace(String trace) {
        if (trace.startsWith(ERROR_PREFIX)) {
            return trace.replaceFirst(ERROR_PREFIX, "");
        } else {
            return trace.replaceFirst(FAILURE_PREFIX, "");
        }
    }

    protected CompatibilityTestResult parseResultFromLogcatHeader(String rawLogcat)
            throws JSONException {
        if (!rawLogcat.startsWith(CompatibilityTestResult.SEPARATOR)) {
            return null;
        }
        Matcher m = CompatibilityTestResult.REGEX.matcher(rawLogcat);
        if (m.find()) {
            return CompatibilityTestResult.fromJsonString(m.group(1));
        }
        return null;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test, long startTime) {
        super.testStarted(test, startTime);
        mAlreadyPosted = false;
        mTest = test;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, long endTime, Map<String, String> testMetrics) {
        // clear out locally cached test identifier so that it won't interfere with full
        // device_logcat reporting
        mTest = null;
        super.testEnded(test, endTime, testMetrics);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Integer postLog(LogDataType dataType, InputStream source, LogFile log, Date timestamp) {
        if (dataType != LogDataType.LOGCAT || mTest == null) {
            return super.postLog(dataType, source, log, timestamp);
        }
        if (mAlreadyPosted) {
            return null;
        }
        if (getRunId() == null) {
            Integer runId = createRun();
            if (runId == null) {
                CLog.w("Failed to create run id");
                return null;
            }
        }

        try {
            String rawLogcat = StreamUtil.getStringFromStream(source);
            // get the portion between separators and parse json string
            CompatibilityTestResult result = parseResultFromLogcatHeader(rawLogcat);
            if (result == null) {
                CLog.w("JSON data not found in logcat header, using default logging; "
                        + "test: %s", mTest);
                return super.postLog(dataType, source, log, timestamp);
            }
            JSONObject postData = new JSONObject();
            postData.put("run_id", getRunId());
            postData.put("timestamp", timestamp);
            postData.put("filepath", log.getPath());
            postData.put("url", log.getUrl());
            postData.put(CompatibilityTestResult.KEY_STATUS, result.status);
            postData.put(CompatibilityTestResult.KEY_PACKAGE, result.packageName);
            if (result.versionString != null) {
                postData.put(KEY_VERSION, result.versionString);
            }
            if (result.name != null) {
                postData.put(CompatibilityTestResult.KEY_NAME, result.name);
            }
            if (result.rank != null) {
                postData.put(CompatibilityTestResult.KEY_RANK, result.rank);
            }
            if (result.message != null) {
                postData.put(CompatibilityTestResult.KEY_MESSAGE, result.message);
            }
            LogcatItem item = parseLogcat(new ByteArrayInputStream(rawLogcat.getBytes()));
            if (item == null) {
                return null;
            }
            postData.put(PostMethod.ADD_COMPAT_LOG.getPostKey(), item.toJson());

            mSavedResults.add(postData);
            mAlreadyPosted = true;
        } catch (JSONException e) {
            CLog.w("Posting failed: %s", e.getMessage());
        } catch (IOException e) {
            CLog.w("Posting failed: %s", e.getMessage());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        super.invocationEnded(elapsedTime);

        if (getInvocationFailed()) return;

        for (JSONObject json : mSavedResults) {
            post(PostMethod.ADD_COMPAT_LOG, json);
            RunUtil.getDefault().sleep(POSTING_INTERVAL);
        }
    }

    void parseBuildInfoFromFile(File src) {
        try {
            String text = FileUtil.readStringFromFile(src);
            String[] lines = text.split("\n");
            for (String line : lines) {
                String[] fields = line.split("=");
                if ("build_id".equals(fields[0]) && fields.length == 2) {
                    mParsedBuildId = fields[1];
                } else if ("build_alias".equals(fields[0]) && fields.length == 2) {
                    mParsedBuildAlias = fields[1];
                }
            }
        } catch (IOException ioe) {
            CLog.e("Exception while loading suite instance key from file");
            CLog.e(ioe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getSuiteInstanceKey() {
        String key = super.getSuiteInstanceKey();
        if (key == null && mSuiteInfoFile != null) {
            if (mParsedBuildId == null) {
                parseBuildInfoFromFile(mSuiteInfoFile);
            }
            key = mParsedBuildId;
            if (key == null) {
                CLog.e("suite instance key file was specified, but no key was loaded. file: %s",
                        mSuiteInfoFile.getAbsolutePath());
            } else {
                setSuiteInstanceKey(key);
            }
        }
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDescription() {
        String desc = super.getDescription();
        if (desc == null && mSuiteInfoFile != null) {
            if (mParsedBuildAlias == null) {
                parseBuildInfoFromFile(mSuiteInfoFile);
            }
            desc = mParsedBuildAlias;
            if (desc == null) {
                CLog.e("suite instance key file was specified, but no key was loaded. file: %s",
                        mSuiteInfoFile.getAbsolutePath());
            } else {
                setDescription(desc);
            }
        }
        return super.getDescription();
    }
}
