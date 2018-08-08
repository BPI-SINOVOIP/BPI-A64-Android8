/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.tradefed.result;

import com.google.android.tradefed.result.StatusTrackingEmailResultReporter.BuildResult;
import com.google.android.tradefed.result.StatusTrackingEmailResultReporter.TestStatusTransition;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.IEmail;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tests for {@link StatusTrackingEmailResultReporter}
 */
public class StatusTrackingEmailResultReporterTest extends TestCase {

    private static final String SOME_TRACE = "some trace";
    private static final TestIdentifier SOME_TEST_ID = new TestIdentifier("className", "testName");
    private static final IBuildInfo BUILD_INFO_1 = new BuildInfo("1", "mybuild");
    private static final IBuildInfo BUILD_INFO_2 = new BuildInfo("2", "mybuild");
    @SuppressWarnings("serial")
    private static final Map<String, String> PRODUCT_X_ATTRS =
            new HashMap<String, String>() {
                {
                    put("foo", "foo for X");
                    put("bar", "bar for X");
                }
            };

    private static final Map<String, String> EMPTY_METRICS = new HashMap<String, String>();

    private IEmail mMockMailer;
    private StatusTrackingEmailResultReporter mEmailReporter;
    private ConcurrentMap<TestIdentifier, ConcurrentMap<Map<String, String>, BuildResult>> mMockHistory;

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockMailer = EasyMock.createMock(IEmail.class);
        mMockHistory = EasyMock.createMock(ConcurrentMap.class);
        mEmailReporter = new StatusTrackingEmailResultReporter(mMockMailer, mMockHistory);
        mEmailReporter.addDestination("foo");
    }

    @SuppressWarnings("serial")
    public void testBadAttrs_throwsException() {
        EasyMock.expect(mMockHistory.get(EasyMock.eq(SOME_TEST_ID))).andReturn(stubHistory());
        EasyMock.replay(mMockMailer, mMockHistory);
        // set attributes absent in fake product X attributes
        mEmailReporter.setProductAttrs(new HashSet<String>(){{add("bazz");}});

        IBuildInfo productXat1 = buildForProduct(BUILD_INFO_1, PRODUCT_X_ATTRS);
        simulateInvocationFor(productXat1, false /* success */);
        try {
            mEmailReporter.processTransitions();
            fail("Should have thrown an exception when instructed to use an invalid subset of "
                    + "build attributes to identify a product");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    public void testEmptyInvocation_NoMailSent() {
        EasyMock.replay(mMockMailer, mMockHistory);
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", BUILD_INFO_1);
        mEmailReporter.invocationStarted(context);
        mEmailReporter.processTransitions();
        assertEquals("Should have detected no transitions", 0, mEmailReporter.getTransitions()
                .size());
        EasyMock.verify(mMockMailer, mMockHistory);
    }

    public void testFirstStatus_NoMailSent() {
        ConcurrentMap<Map<String, String>, BuildResult> emptyProductHistory = stubHistory();
        EasyMock.expect(mMockHistory.get(EasyMock.eq(SOME_TEST_ID))).andReturn(emptyProductHistory);
        EasyMock.replay(mMockMailer, mMockHistory);

        IBuildInfo productXat1 = buildForProduct(BUILD_INFO_1, PRODUCT_X_ATTRS);
        simulateInvocationFor(productXat1, false /* success */);
        mEmailReporter.processTransitions();
        assertEquals("Should have detected no transitions", 0, mEmailReporter.getTransitions()
                .size());
        assertEquals("Should have at least recorded the first test's result as a baseline", 1,
                emptyProductHistory.size());
        EasyMock.verify(mMockMailer, mMockHistory);
    }

    public void testNoStateChanged_NoMailSent() {
        BuildResult prodXFailingAt1 = buildResultForProduct(BUILD_INFO_1, PRODUCT_X_ATTRS,
                TestStatus.FAILURE);
        ConcurrentMap<Map<String, String>, BuildResult> fakeProdXHistory = stubHistory(prodXFailingAt1);
        EasyMock.expect(mMockHistory.get(EasyMock.eq(SOME_TEST_ID))).andReturn(
                fakeProdXHistory);
        EasyMock.replay(mMockMailer, mMockHistory);

        IBuildInfo productXat2 = buildForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS);
        simulateInvocationFor(productXat2, true);
        mEmailReporter.processTransitions();
        assertEquals("Should have detected no transitions", 0, mEmailReporter.getTransitions()
                .size());
        assertEquals("Should have kept latest results",
                buildResultForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS, TestStatus.FAILURE),
                fakeProdXHistory.get(PRODUCT_X_ATTRS));
        EasyMock.verify(mMockMailer, mMockHistory);
    }

    public void testOldBuildStateChanged_NoMailSent() {
        BuildResult prodXHealthyAt2 = buildResultForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS,
                TestStatus.PASSED);
        EasyMock.expect(mMockHistory.get(EasyMock.eq(SOME_TEST_ID))).andReturn(
                stubHistory(prodXHealthyAt2));
        EasyMock.replay(mMockMailer, mMockHistory);

        IBuildInfo productXat2 = buildForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS);
        simulateInvocationFor(productXat2, true);
        mEmailReporter.processTransitions();
        assertEquals("Should have detected no transitions", 0, mEmailReporter.getTransitions()
                .size());
        EasyMock.verify(mMockMailer, mMockHistory);
    }

    public void testStateChanged_MailSent() {
        final BuildResult prodXHealthyAt1 = buildResultForProduct(BUILD_INFO_1, PRODUCT_X_ATTRS,
                TestStatus.PASSED);
        EasyMock.expect(mMockHistory.get(EasyMock.eq(SOME_TEST_ID))).andReturn(
                stubHistory(prodXHealthyAt1));
        EasyMock.replay(mMockMailer, mMockHistory);

        IBuildInfo productXat2 = buildForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS);
        simulateInvocationFor(productXat2, true);
        mEmailReporter.processTransitions();
        final BuildResult prodXFailingAt2 = buildResultForProduct(BUILD_INFO_2, PRODUCT_X_ATTRS,
                TestStatus.FAILURE);
        Set<TestStatusTransition> expectedTransitions = new HashSet<TestStatusTransition>();
        expectedTransitions.add(new TestStatusTransition(SOME_TEST_ID, prodXHealthyAt1,
                prodXFailingAt2));
        assertEquals(expectedTransitions, mEmailReporter.getTransitions());
        EasyMock.verify(mMockMailer, mMockHistory);
    }

    // helpers
    private void simulateInvocationFor(IBuildInfo iBuildInfo, boolean failure) {
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", iBuildInfo);
        mEmailReporter.invocationStarted(context);
        mEmailReporter.testRunStarted("runName", 1);
        mEmailReporter.testStarted(SOME_TEST_ID);
        if (failure) {
            mEmailReporter.testFailed(SOME_TEST_ID, SOME_TRACE);
        }
        mEmailReporter.testEnded(SOME_TEST_ID, null);
        if (failure) {
            mEmailReporter.testRunFailed("error message");
        }
        mEmailReporter.testRunEnded(1, EMPTY_METRICS);
    }

    private IBuildInfo buildForProduct(IBuildInfo buildInfo, Map<String, String> ppties) {
        IBuildInfo buildInfoCopy = buildInfo.clone();
        for (Entry<String, String> ppty : ppties.entrySet()) {
            buildInfoCopy.addBuildAttribute(ppty.getKey(), ppty.getValue());
        }
        return buildInfoCopy;
    }

    private BuildResult buildResultForProduct(IBuildInfo buildInfo, Map<String, String> prodPpties,
            TestStatus status) {
        IBuildInfo buildForProd = buildForProduct(buildInfo, prodPpties);
        TestResult result = new TestResult();
        result.setStatus(status);
        result.setStackTrace(SOME_TRACE);
        return new BuildResult(buildForProd, result);
    }

    /**
     * Creates a stub history of {@link BuildResult}s in reverse, by indexing
     * them by their associated {@link IBuildInfo#getBuildAttributes()}
     */
    private ConcurrentMap<Map<String, String>, BuildResult> stubHistory(BuildResult... results) {
        ConcurrentHashMap<Map<String, String>, BuildResult> history =
            new ConcurrentHashMap<Map<String, String>, BuildResult>();
        for (BuildResult result : results) {
            history.put(result.mBuildInfo.getBuildAttributes(), result);
        }
        return history;
    }
}
