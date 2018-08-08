// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InvocationStatus;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.util.Alarm;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reports test results to the Notifilter test failure notification service
 */
@OptionClass(alias = "notifilter")
public class NotifilterResultReporter extends CollectingTestListener
        implements ITestSummaryListener {

    public static final String DISPLAY_BUILD_ID_KEY = NotifilterProtocol.DISP_BUILDID_KEY;

    private String mSummaryUrl = "";
    private InvocationStatus mStatus = InvocationStatus.SUCCESS;
    private Socket mSocket = null;

    @Option(name = "disable", description = "Disables posting to notifilter.")
    private boolean mSkipReporting = false;

    @Option(name = "notifilter-server", description = "Server where Notifilter daemon is running.")
    private String mNotifilterServer = "android-notifilter.corp.google.com";

    @Option(name = "notifilter-port",
            description = "Port number where the Notifilter daemon is listening.")
    private int mNotifilterPort = 54321;

    @Option(name = "sock-timeout", description = "network operation timeout, in msecs")
    private int mSockTimeout = 30 * 1000;

    @Option(name = "include-extra", description = "Specify the BuildInfo key of a value to " +
            "include that MUST be used to distinguish otherwise-identical tests.  Tests with " +
            "distinct values will be considered distinct tests, even if they might otherwise be " +
            "equivalent.  If the specified values are not found in the BuildInfo, THE RESULTS " +
            "WILL NOT BE REPORTED TO NOTIFILTER.  This is to avoid trampling on results from " +
            "unrelated tests.")
    private List<String> mExtras = new ArrayList<String>();

    @Option(name = "include-ng-extra", description = "Specify the BuildInfo key of " +
            "[N]on-[G]rouping values to include.  These WILL NOT be used to distinguish " +
            "otherwise-identical tests.  These values are solely included as ancillary " +
            "information.  If the specified values are not found in the BuildInfo, a warning " +
            "will be logged, but results will still be posted.")
    private List<String> mNgextras = new ArrayList<String>();

    private boolean mInterrupted = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        // By convention, only store the first summary that we see as the summary URL.
        if (summaries.isEmpty()) {
           return;
        }

        mSummaryUrl = summaries.get(0).getSummary().getString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        if (cause instanceof BuildError) {
            mStatus = InvocationStatus.BUILD_ERROR;
        } else {
            mStatus = InvocationStatus.FAILED;
        }
        mStatus.setThrowable(cause);
    }

    /**
     * Create a new socket to talk to the backend
     * Exposed for testing
     */
    BufferedOutputStream getReportingStream() throws IOException {
        mSocket = new Socket(mNotifilterServer, mNotifilterPort);
        return new BufferedOutputStream(mSocket.getOutputStream());
    }

    /**
     * The header is a 32-bit section that precedes every message.  It's currently constant, but is
     * present to enable future protocol enhancements, such as compression.
     */
    private void emitHeader(OutputStream stream) throws IOException {
        int header = 0x0;
        header |= NotifilterProtocol.HEADER_JSON;
        stream.write(NotifilterProtocol.intToFourBytes(header));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (mSkipReporting) {
            CLog.v("Disabled Notifilter! NOT posting %s invocation to Notifilter backend.",
                    mStatus.toString());
            return;
        }
        super.invocationEnded(elapsedTime);
        if (mInterrupted) {
            CLog.v("Notifilter was inhibited because of InvocationInterrupted! "
                    + "NOT posting to Notifilter backend.");
            return;
        }
        CLog.v("About to report %s invocation to Notifilter backend.", mStatus.toString());
        OutputStream oStream = null;
        final Alarm alarm = new Alarm(mSockTimeout);
        try {
            final Collection<TestRunResult> runs = getRunResults();
            oStream = getReportingStream();

            emitHeader(oStream);
            JSONObject inv = serializeInvJson();
            JSONArray jruns = new JSONArray();
            final Iterator<TestRunResult> iter = runs.iterator();
            while (iter.hasNext()) {
                final TestRunResult run = iter.next();
                final JSONArray jrun = serializeRunJson(run);
                if (jrun.length() == 0) continue;
                jruns.put(jrun);
                // FIXME: why doesn't inv.append() work?
            }
            inv.put(NotifilterProtocol.RUNS_KEY, jruns);

            // Start watching for socket timeouts
            alarm.addSocket(mSocket);
            alarm.start();
            send(oStream, inv.toString());

            CLog.i("Successfully reported invocation results to Notifilter backend.");
        } catch (IOException e) {
            if (alarm.didAlarmFire()) {
                CLog.e("Connection timed out!");
            }
            CLog.e("Failed to submit results to Notifilter daemon: %s", e.getMessage());
        } catch (JSONException e) {
            CLog.e("Failed to serialize results: %s", e.getMessage());
        } finally {
            StreamUtil.flushAndCloseStream(oStream);
            // Disable alarm
            alarm.interrupt();
            try {
                // allow some times for the alarm to terminate.
                alarm.join(2000);
            } catch (InterruptedException e) {
                CLog.e("Alarm did not join after 2000ms.");
                return;
            }
        }
    }

    /**
     * Serialize the invocation.
     *
     * @return A {@link JSONObject} containing the serialization of the invocation status
     */
    private JSONObject serializeInvJson() throws JSONException {
        final JSONObject inv = new JSONObject();
        // some string identifying the test cycle, like "LargeSuite"
        inv.put(NotifilterProtocol.TESTTAG_KEY, getInvocationContext().getTestTag());
        // some string identifying the build, like soju-userdebug
        inv.put(NotifilterProtocol.FLAVOR_KEY, getPrimaryBuildInfo().getBuildFlavor());
        // the branch, "git_master"
        inv.put(NotifilterProtocol.BRANCH_KEY, getPrimaryBuildInfo().getBuildBranch());
        // a build ID
        inv.put(NotifilterProtocol.BUILDID_KEY, getPrimaryBuildInfo().getBuildId());

        if (mStatus == InvocationStatus.SUCCESS) {
            inv.put(NotifilterProtocol.STATUS_KEY, "SUCCESS");
        } else {
            if (mStatus == InvocationStatus.BUILD_ERROR) {
                inv.put(NotifilterProtocol.STATUS_KEY, "BUILD_ERROR");
            } else {
                inv.put(NotifilterProtocol.STATUS_KEY, "FAILED");
            }
            final Throwable t = mStatus.getThrowable();
            if (t != null) {
                inv.put(NotifilterProtocol.STACK_KEY,
                        StreamUtil.getStackTrace(mStatus.getThrowable()));
            }
        }
        inv.put(NotifilterProtocol.SUMMARY_KEY, mSummaryUrl);

        final Map<String, String> attr = getPrimaryBuildInfo().getBuildAttributes();
        if (attr.containsKey(DISPLAY_BUILD_ID_KEY)) {
            inv.put(NotifilterProtocol.DISP_BUILDID_KEY, attr.get(DISPLAY_BUILD_ID_KEY));
        } else {
            // fall back to displaying the standard version number
            inv.put(NotifilterProtocol.DISP_BUILDID_KEY, getPrimaryBuildInfo().getBuildId());
        }

        if (!mExtras.isEmpty()) {
            final JSONObject extras = new JSONObject();
            for (String key : mExtras) {
                if (attr.containsKey(key)) {
                    extras.put(key, attr.get(key));
                } else {
                    // mExtras attributes are mandatory, since they form part of the database key
                    throw new JSONException(String.format(
                            "BuildInfo did not include mandatory extra attribute \"%s\"", key));
                }
            }
            inv.put(NotifilterProtocol.EXTRAS_KEY, extras);
        }

        if (!mNgextras.isEmpty()) {
            final JSONObject ngextras = new JSONObject();
            for (String key : mNgextras) {
                if (attr.containsKey(key)) {
                    ngextras.put(key, attr.get(key));
                } else {
                    // mNgextras attributes are optional
                    CLog.w(String.format("BuildInfo did not include ngextra attribute \"%s\"",
                            key));
                }
            }
            inv.put(NotifilterProtocol.NGEXTRAS_KEY, ngextras);
        }

        return inv;
    }

    /**
     * A method to serialize the provided {@link TestRunResult}
     *
     * @param run The {@link TestRunResult} to serialize
     * @return A {@link JSONArray} containing the serialization of {@code run}
     */
    private JSONArray serializeRunJson(TestRunResult run) throws JSONException {
        Map<TestIdentifier, TestResult> results = run.getTestResults();
        Iterator<Map.Entry<TestIdentifier, TestResult>> iter = results.entrySet().iterator();
        JSONArray jresults = new JSONArray();
        while (iter.hasNext()) {
            Map.Entry<TestIdentifier, TestResult> result = iter.next();
            TestStatus status = result.getValue().getStatus();
            if (status != TestStatus.ASSUMPTION_FAILURE && status != TestStatus.FAILURE &&
                    status != TestStatus.PASSED) {
                // Skip unknown statuses
                continue;
            }

            jresults.put(serializeResultJson(result));
        }
        return jresults;
    }

    /**
     * A method to serialize the provided single result (usually an individual test method run)
     * from inside of a {@link TestRunResult}.
     *
     * @param resultEntry The {@link java.util.Map.Entry} to serialize
     * @return A {@link JSONObject} containing the serialization of {@code run}
     */
    private JSONObject serializeResultJson(Map.Entry<TestIdentifier, TestResult> resultEntry)
            throws JSONException {
        JSONObject json = new JSONObject();

        TestIdentifier id = resultEntry.getKey();
        TestResult result = resultEntry.getValue();

        json.put(NotifilterProtocol.METHODNAME_KEY, id.getTestName());
        int classSplit = id.getClassName().lastIndexOf('.');
        if (classSplit <= 0) {
            // There is no package name.  This can happen with native tests, for instance.
            json.put(NotifilterProtocol.PKGNAME_KEY, "");
            json.put(NotifilterProtocol.CLASSNAME_KEY, id.getClassName());
        } else {
            json.put(NotifilterProtocol.PKGNAME_KEY, id.getClassName().substring(0, classSplit));
            json.put(NotifilterProtocol.CLASSNAME_KEY, id.getClassName().substring(classSplit));
        }

        TestStatus status = result.getStatus();
        String statusStr = "UNKNOWN";
        if (status == TestStatus.ASSUMPTION_FAILURE || status == TestStatus.FAILURE) {
            statusStr = "FAIL";
        } else if (status == TestStatus.PASSED) {
            statusStr = "PASS";
        }
        json.put(NotifilterProtocol.STATUS_KEY, statusStr);
        json.put(NotifilterProtocol.STACK_KEY, result.getStackTrace());

        return json;
    }

    /**
     * A small helper function to avoid null values
     */
    @SuppressWarnings("unused")
    private String def(String value, String fallback) {
        if (value != null) {
            return value;
        } else {
            return fallback;
        }
    }

    /**
     * A helper function to send bytes from a {@link String} into a stream
     */
    private void send(OutputStream stream, String str) throws IOException {
        byte[] bytes = str.getBytes();
        stream.write(bytes, 0, bytes.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationInterrupted() {
        mInterrupted = true;
    }
}
