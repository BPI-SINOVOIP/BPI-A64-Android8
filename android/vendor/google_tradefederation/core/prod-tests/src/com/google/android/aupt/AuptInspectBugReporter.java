// Copyright 2015 Google Inc. All Rights Reserved.
package com.google.android.aupt;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.loganalysis.item.MemoryHealthItem;
import com.android.loganalysis.parser.MemoryHealthParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.util.StreamUtil;
import com.google.android.tradefed.result.InspectBugResultReporter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extend the InspectBugResultReporter to report Aupt iteration count. */
@OptionClass(alias = "aupt-inspect-bug-reporter")
public class AuptInspectBugReporter extends InspectBugResultReporter {

    @Option(name="tests-per-iteration",
            description="Specifies the number of uiautomator test that are run in each iteration.")
    private int mTestsInIteration = 1;

    @Option(name="extra-attribute",
            description="Addidtional attributes that are posted to the dashboard")
    private Map<String, String> mAttributes = new HashMap<String,String>();

    private static final Pattern MEMORY_HEALTH_PATTERN = Pattern.compile(
            "memory-health_?[0-9]*\\.txt");
    private static final Pattern LINK_PATTERN =
            Pattern.compile("http(.*)\\.([a-z]+)\\.(json|html)");

    private String mReportUrl;
    private String mRunFailureMsg;
    private String mGraphicsStats;

    private TestIdentifier mLastFailedTest;
    private String mLastTrace;

    @Override
    protected void getAttributes(JSONObject attributes) throws JSONException {
        super.getAttributes(attributes);

        int iterations = getNumTotalTests() / mTestsInIteration;
        attributes.put("iterations", iterations);
        attributes.put("device-label", getBuildInfo().getTestTag());
        if (mReportUrl != null) {
            attributes.put("meminfo-report", mReportUrl);
        }
        if (mRunFailureMsg != null) {
            attributes.put("run-failure-msg", mRunFailureMsg);
        }
        for(Map.Entry<String, String> attribute : mAttributes.entrySet()) {
            attributes.put(attribute.getKey(), attribute.getValue());
        }
    }

    @Override
    public void testFailed(TestIdentifier test, String trace) {
        if (mDisable) {
            return;
        }

        // Save the name and trace of the last test.
        // Assumption is that after this method there will be a callback
        // for the saved png screen shot file, when that happens, we'll post the screenshot
        // and this test and the trace, so we can look at it later
        mLastFailedTest = test;
        mLastTrace = trace;
        super.testFailed(test, trace);
    }

    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource source) {
        if (mDisable) {
            return;
        }

        try {
            if ("meminfo.report.url".equals(dataName) && dataType == LogDataType.TEXT) {
                mReportUrl = StreamUtil.getStringFromSource(source);
            } else if ("progress".equals(dataName) && dataType == LogDataType.TEXT) {
                mRunFailureMsg = StreamUtil.getStringFromSource(source);
            }
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource source,
            LogFile file) {
        if (mDisable) {
            return;
        }

        // do not post secondary logs in the primary reporter
        if (!dataName.contains("companion")) {
            super.testLogSaved(dataName, dataType, source, file);
        }
    }

    @Override
    public void postAdditionalLog(String dataName, LogDataType dataType, InputStreamSource source,
            LogFile file) {
        if (mDisable) {
            return;
        }

        Matcher matcher = MEMORY_HEALTH_PATTERN.matcher(dataName);
        if (matcher.matches()) {
            postMemoryHealth(source.createInputStream(), file);
        } else if (dataType == LogDataType.PNG) {
            if (mLastFailedTest != null && mLastTrace != null) {
                postTestFailure(file);
            }
        } else if (dataName.contains("links") && dataType.equals(LogDataType.TEXT)) {
            postLinks(source.createInputStream());
        }

        // We are expecting a call to this method with a png screenshot immediately after
        // a test failure, if that does not happen, then we reset the failed test state, so wrong
        // thing won't get posted.
        mLastFailedTest = null;
        mLastTrace = null;
    }

    private boolean runIdSet() {
        Integer runId = getRunId();
        if (runId == null || runId == -1) {
            CLog.w("Run id is not set, cannot post");
            return false;
        } else {
            return true;
        }
    }

    private void postTestFailure(LogFile file) {
        if (!runIdSet()) return;

        try {
            JSONObject postData = new JSONObject();
            JSONObject failureData = new JSONObject();
            postData.put("run_id", getRunId());
            failureData.put("test", mLastFailedTest.toString());
            failureData.put("trace", mLastTrace);
            failureData.put("url", file.getUrl());
            postData.put(
                    InspectBugResultReporter.PostMethod.ADD_TEST_FAILURE.getPostKey(), failureData);
            post(InspectBugResultReporter.PostMethod.ADD_TEST_FAILURE, postData);
        } catch (JSONException e) {
            CLog.w("Posting test failure failed: %s", e.getMessage());
        }
    }

    private void postMemoryHealth(InputStream stream, LogFile file) {
        if (!runIdSet()) return;

        try {
            JSONObject postData = new JSONObject();
            postData.put("run_id", getRunId());
            MemoryHealthItem item = new MemoryHealthParser().parse(
                    new BufferedReader(new InputStreamReader(stream)));
            postData.put("memory_health", item.toJson());
            post(InspectBugResultReporter.PostMethod.ADD_MEMORY_HEALTH, postData);
        } catch (JSONException|IOException e) {
            CLog.e(e);
        }
    }

    private void postLinks(InputStream stream) {
        if (!runIdSet()) return;

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        try {
            String line = null;

            while ((line = reader.readLine()) != null) {
                JSONObject postData = new JSONObject();
                postData.put("run_id", getRunId());
                Matcher matcher = LINK_PATTERN.matcher(line);

                if(matcher.matches()){
                    postData.put("link", line);
                    postData.put("name", matcher.group(2));
                } else {
                    postData.put("link", line);
                    postData.put("name", line);
                    CLog.w("Link failed to parse in AuptInspectBugReporter: " + line);
                }

                post(InspectBugResultReporter.PostMethod.ADD_LINKS, postData);
            }
        } catch (JSONException|IOException e) {
            CLog.e(e);
        } finally {
            try {
                reader.close();
            } catch (IOException ioex) {
                /* An IOEx here means our stream is already closed or wasn't ever open */
            }
        }
    }

    @Override
    protected Date parseTimestamp(LogFile file) {
        Pattern p = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
        Matcher m = p.matcher(file.getPath());
        if (m.find()) {
            String timestamp = m.group();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            try {
                return fmt.parse(timestamp);
            } catch (ParseException e) {
                CLog.w("Failed to parse timestamp %s", timestamp);
                return new Date();
            }
        }
        return new Date();
    }
}
