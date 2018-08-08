// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.tradefed.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.result.TestSummary;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link NotifilterResultReporter} */
public class NotifilterResultReporterTest {
    private NotifilterResultReporter mResultReporter = null;
    private ByteArrayOutputStream mRequestStream = new ByteArrayOutputStream();
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    private static final String STOCK_FLAVOR = "full-eng-userdebug";
    private static final String STOCK_BRANCH = "git_master";
    private static final String STOCK_ATTR1 = "attr1";
    private static final String STOCK_ATTR1_VALUE = "value1";
    private static final String STOCK_ATTR2 = "attr2";
    private static final String STOCK_ATTR2_VALUE = "value2";
    private static final String STOCK_ATTR3 = "attr3";
    private static final String STOCK_ATTR3_VALUE = "value3";
    private static final String TEST_TAG = "TEST_TAG";

    @Before
    public void setUp() throws Exception {
        mBuildInfo = new BuildInfo();
        mBuildInfo.setBuildFlavor(STOCK_FLAVOR);
        mBuildInfo.setBuildBranch(STOCK_BRANCH);
        mBuildInfo.addBuildAttribute(STOCK_ATTR1, STOCK_ATTR1_VALUE);
        mBuildInfo.addBuildAttribute(STOCK_ATTR2, STOCK_ATTR2_VALUE);
        mBuildInfo.addBuildAttribute(STOCK_ATTR3, STOCK_ATTR3_VALUE);
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("device", mBuildInfo);
        mContext.setTestTag(TEST_TAG);
        mResultReporter = new NotifilterResultReporter() {
            @Override
            BufferedOutputStream getReportingStream() throws IOException {
                return new BufferedOutputStream(mRequestStream);
            }
        };
    }

    private void assertStubInv(JSONObject inv) throws Exception {
        assertEquals(mContext.getTestTag(), inv.getString(NotifilterProtocol.TESTTAG_KEY));
        assertEquals(STOCK_BRANCH, inv.getString(NotifilterProtocol.BRANCH_KEY));
        assertEquals(STOCK_FLAVOR, inv.getString(NotifilterProtocol.FLAVOR_KEY));
        assertEquals(mBuildInfo.getBuildId(), inv.getString(NotifilterProtocol.BUILDID_KEY));
        assertEquals(mBuildInfo.getBuildId(), inv.getString(NotifilterProtocol.DISP_BUILDID_KEY));
        assertEquals("SUCCESS", inv.getString(NotifilterProtocol.STATUS_KEY));
    }

    private void assertTestCase(JSONObject test, String klass, String method, String status)
            throws Exception {
        assertEquals("", test.getString(NotifilterProtocol.PKGNAME_KEY));
        assertEquals(klass, test.getString(NotifilterProtocol.CLASSNAME_KEY));
        assertEquals(method, test.getString(NotifilterProtocol.METHODNAME_KEY));
        assertEquals(status, test.getString(NotifilterProtocol.STATUS_KEY));
    }

    /** A simple test to ensure expected output is generated for test run with no tests. */
    @Test
    public void testEmptyGeneration() throws Exception {
        mResultReporter.invocationStarted(mContext);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(0, runs.length());
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single passed test.
     */
    @Test
    public void testSinglePass() throws Exception {
        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(1, runs.length());
        final JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo", "PASS");
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single passed test.
     */
    @Test
    public void testSinglePass_withSummary() throws Exception {
        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = new ArrayList<TestSummary>(1);
        final String url = "http://foo.com/bar?bat=quux";
        summaryList.add(new TestSummary(url));

        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(url, inv.getString(NotifilterProtocol.SUMMARY_KEY));
        assertEquals(1, runs.length());
        final JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo", "PASS");
    }

    @Test
    public void testMultipleTests() throws Exception {
        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = new ArrayList<TestSummary>(1);
        TestIdentifier testId;
        final String url = "http://foo.com/bar?bat=quux";
        summaryList.add(new TestSummary(url));

        mResultReporter.invocationStarted(mContext);
        for (int i = 0; i < 3; ++i) {
            testId = new TestIdentifier("FooTest", "testFoo" + Integer.toString(i));
            mResultReporter.testRunStarted("testrun" + Integer.toString(i), 1);
            mResultReporter.testStarted(testId);
            mResultReporter.testEnded(testId, emptyMap);
            mResultReporter.testRunEnded(2, emptyMap);
        }

        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(url, inv.getString(NotifilterProtocol.SUMMARY_KEY));
        assertEquals(3, runs.length());
        for (int i = 0; i < 3; ++i) {
            final JSONArray cases = runs.getJSONArray(i);
            assertEquals(1, cases.length());
            assertTestCase(cases.getJSONObject(0), "FooTest", String.format("testFoo%d", i),
                    "PASS");
        }
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single incomplete
     * test.
     */
    @Test
    public void testIncompleteTest() throws Exception {
        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        // test never finishes
        // mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        //System.err.println(output);
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(0, runs.length());
    }

    /**
     * A simple test to ensure expected output is generated for test run with a single incomplete
     * test among multiple tests
     */
    @Test
    public void testMultipleIncompleteTests() throws Exception {
        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = new ArrayList<TestSummary>(1);
        TestIdentifier testId;
        final String url = "http://foo.com/bar?bat=quux";
        summaryList.add(new TestSummary(url));

        mResultReporter.invocationStarted(mContext);
        for (int i = 0; i < 3; ++i) {
            testId = new TestIdentifier("FooTest", "testFoo" + Integer.toString(i));
            mResultReporter.testRunStarted("testrun" + Integer.toString(i), 1);
            mResultReporter.testStarted(testId);
            if (i != 1) {
                mResultReporter.testEnded(testId, emptyMap);
            }
            mResultReporter.testRunEnded(2, emptyMap);
        }

        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        // We expect testFoo1 to be incomplete, and thus we don't expect testFoo1 to show up in
        // the output.

        final String output = getOutput();
        //System.out.format("got some output <<END\n%s\nEND\n", output);
        final JSONObject inv = new JSONObject(output);
        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertStubInv(inv);
        assertEquals(url, inv.getString(NotifilterProtocol.SUMMARY_KEY));
        assertEquals(2, runs.length());

        // expect the first testcase
        JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo0" /* first */, "PASS");

        // expect the third testcase
        cases = runs.getJSONArray(1);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo2" /* third */, "PASS");
    }

    /** Make sure that sending a basic Extras works as expected */
    @Test
    public void testExtras_basic() throws Exception {
        final OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("include-extra", STOCK_ATTR1);

        final Map<String, String> emptyMap = Collections.emptyMap();
        final List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        assertStubInv(inv);
        final JSONObject extras = inv.getJSONObject(NotifilterProtocol.EXTRAS_KEY);
        assertEquals(1, extras.length());
        assertEquals(STOCK_ATTR1_VALUE, extras.getString(STOCK_ATTR1));

        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertEquals(1, runs.length());
        final JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo", "PASS");
    }

    /**
     * Ensure that nothing is posted when an Extra is specified that doesn't exist in the {@link
     * IBuildInfo}
     */
    @Test
    public void testExtras_missing() throws Exception {
        OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("include-extra", "key that doesn't exist");

        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        assertTrue(output.isEmpty());
    }

    /** Make sure that sending Extras and Ngextras in the same invocation works as expected */
    @Test
    public void testExtrasAndNgextras() throws Exception {
        final OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("include-extra", STOCK_ATTR1);
        option.setOptionValue("include-ng-extra", STOCK_ATTR2);

        final Map<String, String> emptyMap = Collections.emptyMap();
        final List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        assertStubInv(inv);
        final JSONObject extras = inv.getJSONObject(NotifilterProtocol.EXTRAS_KEY);
        assertEquals(1, extras.length());
        assertEquals(STOCK_ATTR1_VALUE, extras.getString(STOCK_ATTR1));

        final JSONObject ngextras = inv.getJSONObject(NotifilterProtocol.NGEXTRAS_KEY);
        assertEquals(1, ngextras.length());
        assertEquals(STOCK_ATTR2_VALUE, ngextras.getString(STOCK_ATTR2));

        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertEquals(1, runs.length());
        final JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo", "PASS");
    }

    /** Make sure that that Ngextras are optional as expected. */
    @Test
    public void testNgextras_missing() throws Exception {
        final OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("include-extra", STOCK_ATTR1);
        option.setOptionValue("include-ng-extra", "key doesn't exist");
        option.setOptionValue("include-ng-extra", STOCK_ATTR2);

        final Map<String, String> emptyMap = Collections.emptyMap();
        final List<TestSummary> summaryList = Collections.emptyList();
        final TestIdentifier testId = new TestIdentifier("FooTest", "testFoo");
        mResultReporter.invocationStarted(mContext);
        mResultReporter.testRunStarted("testrun", 1);
        mResultReporter.testStarted(testId);
        mResultReporter.testEnded(testId, emptyMap);
        mResultReporter.testRunEnded(2, emptyMap);
        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        final JSONObject inv = new JSONObject(output);
        assertStubInv(inv);
        final JSONObject extras = inv.getJSONObject(NotifilterProtocol.EXTRAS_KEY);
        assertEquals(1, extras.length());
        assertEquals(STOCK_ATTR1_VALUE, extras.getString(STOCK_ATTR1));

        final JSONObject ngextras = inv.getJSONObject(NotifilterProtocol.NGEXTRAS_KEY);
        assertEquals(1, ngextras.length());
        assertEquals(STOCK_ATTR2_VALUE, ngextras.getString(STOCK_ATTR2));

        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertEquals(1, runs.length());
        final JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo", "PASS");
    }

    /**
     * Make sure that everything works all together. This method is used to generate _input_ for the
     * parser unit tests
     */
    @Test
    public void testEverything() throws Exception {
        final OptionSetter option = new OptionSetter(mResultReporter);
        option.setOptionValue("include-extra", STOCK_ATTR1);
        option.setOptionValue("include-ng-extra", "key doesn't exist");
        option.setOptionValue("include-ng-extra", STOCK_ATTR2);
        option.setOptionValue("include-ng-extra", STOCK_ATTR3);

        Map<String, String> emptyMap = Collections.emptyMap();
        List<TestSummary> summaryList = new ArrayList<TestSummary>(1);
        TestIdentifier testId;
        final String url = "http://foo.com/bar?bat=quux";
        summaryList.add(new TestSummary(url));

        mResultReporter.invocationStarted(mContext);
        for (int i = 0; i < 3; ++i) {
            testId = new TestIdentifier("FooTest", "testFoo" + Integer.toString(i));
            mResultReporter.testRunStarted("testrun" + Integer.toString(i), 1);
            mResultReporter.testStarted(testId);
            if (i != 1) {
                mResultReporter.testEnded(testId, emptyMap);
            }
            mResultReporter.testRunEnded(2, emptyMap);
        }

        mResultReporter.putSummary(summaryList);
        mResultReporter.invocationEnded(1);

        final String output = getOutput();
        System.out.println(output);
        final JSONObject inv = new JSONObject(output);
        assertStubInv(inv);
        final JSONObject extras = inv.getJSONObject(NotifilterProtocol.EXTRAS_KEY);
        assertEquals(1, extras.length());
        assertEquals(STOCK_ATTR1_VALUE, extras.getString(STOCK_ATTR1));

        final JSONObject ngextras = inv.getJSONObject(NotifilterProtocol.NGEXTRAS_KEY);
        assertEquals(2, ngextras.length());
        assertEquals(STOCK_ATTR2_VALUE, ngextras.getString(STOCK_ATTR2));
        assertEquals(STOCK_ATTR3_VALUE, ngextras.getString(STOCK_ATTR3));

        final JSONArray runs = inv.getJSONArray(NotifilterProtocol.RUNS_KEY);
        assertEquals(url, inv.getString(NotifilterProtocol.SUMMARY_KEY));
        assertEquals(2, runs.length());

        // expect the first testcase
        JSONArray cases = runs.getJSONArray(0);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo0" /* first */, "PASS");

        // expect the third testcase
        cases = runs.getJSONArray(1);
        assertEquals(1, cases.length());
        assertTestCase(cases.getJSONObject(0), "FooTest", "testFoo2" /* third */, "PASS");
    }

    private String getOutput() throws Exception {
        final byte[] bytes = mRequestStream.toByteArray();
        int header = NotifilterProtocol.fourBytesToInt(Arrays.copyOfRange(bytes, 0, 4));
        assertEquals(
                String.format("Received unexpected header 0x%x", header),
                NotifilterProtocol.HEADER_JSON,
                header,
                0.001d);
        String output = new String(bytes, 4, bytes.length - 4);

        return output;
    }
}
