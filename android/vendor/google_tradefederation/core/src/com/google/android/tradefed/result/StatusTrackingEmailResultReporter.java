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

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.EmailResultReporter;
import com.android.tradefed.util.Email;
import com.android.tradefed.util.IEmail;
import com.google.common.base.Objects;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An {@link EmailResultReporter} that tracks tests' status change.
 * <p>
 * This reporter keeps track of {@link TestIdentifier}s' status across
 * invocations (i.e. {@link IBuildInfo#getBuildId() build ID} and products, as
 * uniquely identified by the subset of configurable
 * {@link IBuildInfo#getBuildAttributes() builds' attributes}. It will only send
 * emails out if a test's status has changed.<br>
 * A test status change will only be considered iff:
 * <li>There already existed at least one result (any status) registered for the
 * test (first-time results are ignored)
 * <li>The {@link IBuildInfo#getBuildId() build ID} of the invocation it was
 * detected on is higher than that associated with the last status seen. Note
 * build IDs will be treated as an integer, and this reporter will fail if any
 * can't be treated as such
 */
public class StatusTrackingEmailResultReporter extends EmailResultReporter {

    private static final Comparator<IBuildInfo> INTEGER_BUILD_ID_COMPARATOR =
            new Comparator<IBuildInfo>() {
                @Override
                public int compare(IBuildInfo bInfo1, IBuildInfo bInfo2) {
                    int bId1 = Integer.parseInt(bInfo1.getBuildId());
                    int bId2 = Integer.parseInt(bInfo2.getBuildId());
                    return bId1 - bId2;
                }
            };

    /**
     * Global database that keeps track of all transitions across invocations.<br>
     * Keeps a mapping of {@link TestIdentifier} -> (subset of {@link
     * IBuildInfo#getBuildAttributes() Build Attributes} -> {@link BuildResult})
     */
    private static final ConcurrentMap<
                    TestIdentifier, ConcurrentMap<Map<String, String>, BuildResult>>
            GLOBAL_TESTS_HISTORY =
                    new ConcurrentHashMap<
                            TestIdentifier, ConcurrentMap<Map<String, String>, BuildResult>>();

    @Option(name = "product-attribute", importance = Importance.ALWAYS, shortName = 'p',
            description = "One of many possible IBuildInfo attribures that uniquely identify "
            + "products to track changes for. If you don't specify anything, all build attributes "
            + "will be taken into account")
    private Set<String> mProductAttrs = new HashSet<String>();
    private final ConcurrentMap<TestIdentifier, ConcurrentMap<Map<String, String>, BuildResult>>
            mTestsHistory;
    private final Set<TestStatusTransition> mTransitions = new HashSet<TestStatusTransition>();

    /**
     * Data-object representing a {@link TestResult} at a given
     * {@link IBuildInfo}. These objects are used in the context of
     * {@link TestStatusTransition}s
     */
    static class BuildResult {
        BuildResult(IBuildInfo mBuildInfo, TestResult mTestResult) {
            this.mBuildInfo = mBuildInfo;
            this.mTestResult = mTestResult;
        }
        IBuildInfo mBuildInfo;
        TestResult mTestResult;

        @Override
        public String toString() {
            return mTestResult.getStatus() + " (" + mBuildInfo.getBuildId() + ")";
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mBuildInfo, mTestResult);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            BuildResult other = (BuildResult) obj;
            return Objects.equal(mBuildInfo, other.mBuildInfo) &&
                    Objects.equal(mTestResult, other.mTestResult);
        }
    }

    /**
     * Data-object representing a {@link TestIdentifier}'s transition from one
     * {@link BuildResult} to another
     */
    static class TestStatusTransition {
        TestIdentifier mTest;
        BuildResult mFrom;
        BuildResult mTo;

        public TestStatusTransition(TestIdentifier test, BuildResult from, BuildResult to) {
            mTest = test;
            mFrom = from;
            mTo = to;
        }

        @Override
        public String toString() {
            return mTest + ": " + mFrom + " -> " + mTo;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mFrom == null) ? 0 : mFrom.hashCode());
            result = prime * result + ((mTest == null) ? 0 : mTest.hashCode());
            result = prime * result + ((mTo == null) ? 0 : mTo.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TestStatusTransition other = (TestStatusTransition) obj;
            return Objects.equal(mFrom, other.mFrom) &&
                    Objects.equal(mTest, other.mTest) &&
                    Objects.equal(mTo, other.mTo);
        }
    }

    /**
     * Default constructor
     */
    public StatusTrackingEmailResultReporter() {
        this(new Email(), GLOBAL_TESTS_HISTORY);
    }

    /**
     * Create a {@link StatusTrackingEmailResultReporter} with custom
     * {@link IEmail} and test history map.<br>
     * Package-private for unit testing
     *
     * @param mailer the {@link IEmail}er to use
     * @param testsHistory the history {@link ConcurrentMap} to use as the
     *            global test status database.
     */
    StatusTrackingEmailResultReporter(IEmail mailer,
            ConcurrentMap<TestIdentifier,
                ConcurrentMap<Map<String, String>, BuildResult>> testsHistory) {
        super(mailer);
        mTestsHistory = testsHistory;
    }

    @Override
    public void invocationEnded(long elapsedTime) {
        processTransitions();
        super.invocationEnded(elapsedTime);
    }

    /**
     * Look for and record {@link TestStatus} transitions in all
     * {@link TestIdentifier}s that took place after the invocation this
     * reporter was created for is done.
     */
    void processTransitions() {
        mTransitions.clear();
        for (TestRunResult runRes : getRunResults()) {
            for (Entry<TestIdentifier, TestResult> aResult : runRes.getTestResults().entrySet()) {
                for (IBuildInfo buildInfo : getInvocationContext().getBuildInfos()) {
                    TestIdentifier testIdentifier = aResult.getKey();
                    TestResult reportedResult = aResult.getValue();

                    ConcurrentMap<Map<String, String>, BuildResult> perBuildKindHistory =
                            mTestsHistory.get(testIdentifier);
                    if (perBuildKindHistory == null) {
                        perBuildKindHistory =
                                new ConcurrentHashMap<Map<String, String>, BuildResult>();
                        // in case another reporter added an entry for this kind of
                        // build since we last checked with get()
                        ConcurrentMap<Map<String, String>, BuildResult> newlyAdded = mTestsHistory
                                .putIfAbsent(testIdentifier, perBuildKindHistory);
                        perBuildKindHistory = newlyAdded != null ? newlyAdded : perBuildKindHistory;
                    }

                    Map<String, String> currBuildAttrs = resolveProductAttributes(buildInfo);
                    BuildResult newResult = new BuildResult(buildInfo, reportedResult);

                    BuildResult lastResult = perBuildKindHistory.putIfAbsent(currBuildAttrs,
                            newResult);
                    if (lastResult != null) {
                        if (INTEGER_BUILD_ID_COMPARATOR.compare(lastResult.mBuildInfo,
                                buildInfo) < 0) {
                            perBuildKindHistory.replace(currBuildAttrs, newResult);
                            TestStatus lastKnownStatus = lastResult.mTestResult.getStatus();
                            TestStatus currentStatus = reportedResult.getStatus();
                            if (lastKnownStatus != currentStatus) {
                                mTransitions.add(new TestStatusTransition(testIdentifier,
                                        lastResult, newResult));
                            }
                        }
                    }
                }
            }
        }
        CLog.v("Found " + mTransitions.size() + " test status transitions");
    }

    @Override
    protected String generateEmailSubject() {
        StringBuilder subj = new StringBuilder();
        subj.append(getInvocationContext().getTestTag());
        subj.append(" on branch ");
        for (IBuildInfo buildInfo : getInvocationContext().getBuildInfos()) {
            subj.append("{" + buildInfo.getBuildBranch() + "@" + buildInfo.getBuildId() + "}");
        }
        return subj.toString();
    }

    /**
     * {@inheritDoc} This implementation appends the state transition details
     * that caused an email to be sent at the end of the generic Email body.
     */
    @Override
    protected String generateEmailBody() {
        StringBuilder builder = new StringBuilder();
        builder.append("The following tests changed their status:\n");
        for (TestStatusTransition transition : mTransitions) {
            builder.append("\n").append(transition);
        }

        builder.append("\n\n");
        builder.append("--- details ---");
        builder.append("\n\n");
        builder.append(super.generateEmailBody());

        return builder.toString();
    }

    /**
     * package-private for unit testing
     *
     * @return A copy of the {@link TestStatusTransition}s found
     */
    Set<TestStatusTransition> getTransitions() {
        return new HashSet<TestStatusTransition>(mTransitions);
    }

    /**
     * Indicates whether to an email should be sent based on whether a any
     * {@link TestStatusTransition}s were detected during the last invocation.
     */
    @Override
    protected boolean shouldSendMessage() {
        return mTransitions.size() > 0;
    }

    /**
     * Filters-in only the {@link IBuildInfo#getBuildAttributes() build
     * attributes} specified by {@link #mProductAttrs}
     *
     * @return a subset of the {@link IBuildInfo#getBuildAttributes() build
     *         attributes}
     */
    private Map<String, String> resolveProductAttributes(IBuildInfo buildInfo) {
        Map<String, String> buildAttributes = buildInfo.getBuildAttributes();
        if (getProductAttrs().isEmpty()) {
            return buildAttributes;
        }
        Set<String> attributesPresent = buildAttributes.keySet();
        if (!attributesPresent.containsAll(getProductAttrs())) {
            Set<String> subset = new HashSet<String>(getProductAttrs());
            subset.removeAll(attributesPresent);
            throw new IllegalArgumentException(
                    "The product-identifying attributes " + subset + " are missing from the build "
                            + "attributes: " + attributesPresent);
        }
        Map<String, String> prodAttrValues = new HashMap<String, String>(getProductAttrs().size());
        // Always identify a product at least by branch and flavor
        prodAttrValues.put("branch", buildInfo.getBuildBranch());
        prodAttrValues.put("flavor", buildInfo.getBuildFlavor());
        for (String att : getProductAttrs()) {
            prodAttrValues.put(att, buildAttributes.get(att));
        }
        return prodAttrValues;
    }

    /**
     * exposed for unit testing
     */
    void setProductAttrs(Set<String> mProductAttrs) {
        this.mProductAttrs = mProductAttrs;
    }

    /**
     * exposed for unit testing
     */
    Set<String> getProductAttrs() {
        return mProductAttrs;
    }
}
