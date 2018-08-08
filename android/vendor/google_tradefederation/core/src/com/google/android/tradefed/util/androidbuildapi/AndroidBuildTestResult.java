// Copyright 2015 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util.androidbuildapi;

import com.android.tradefed.build.IBuildInfo;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Container object to store the test result to post to the Android Build API.
 */
public class AndroidBuildTestResult {
    public static final String STATUS_IN_PROGRESS = "inProgress";
    public static final String STATUS_PASS = "completePass";
    public static final String STATUS_FAIL = "completeFail";
    public static final String STATUS_ERROR = "error";

    private Long mId = null;
    private String mTestTag;
    private String mStatus;
    private String mSummary;
    private String mBuildType;
    private String mBuildId;
    private String mTarget;
    private String mAttemptId;

    public AndroidBuildTestResult() {
    }

    public AndroidBuildTestResult(String testTag, String buildType,
           String buildId, String target, String attemptId) {
        mTestTag = testTag;
        mBuildType = buildType;
        mAttemptId = attemptId;
        mBuildId = buildId;
        mTarget = target;
    }

    public AndroidBuildTestResult(IBuildInfo build) {
        this(build.getTestTag(),
                build.getBuildAttributes().get("build_type"),
                build.getBuildId(),
                build.getBuildFlavor(),
                build.getBuildAttributes().get("build_attempt_id"));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject data = new JSONObject();
        data.putOpt("id", mId);
        data.putOpt("testTag", mTestTag);
        data.putOpt("status", mStatus);
        data.putOpt("summary", mSummary);
        return data;
    }

    public Long id() {
        return mId;
    }

    public void setId(Long id) {
        mId = id;
    }

    public String status() {
        return mStatus;
    }

    public void setStatus(String status) {
        mStatus = status;
    }

    public String summary() {
        return mSummary;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public String testTag() {
        return mTestTag;
    }

    public void setTestTag(String testTag) {
        this.mTestTag = testTag;
    }

    public String buildType() {
        return mBuildType;
    }

    public void setBuildType(String buildType) {
        this.mBuildType = buildType;
    }

    public String buildId() {
        return mBuildId;
    }

    public void setBuildId(String buildId) {
        this.mBuildId = buildId;
    }

    public String target() {
        return mTarget;
    }

    public void setTarget(String target) {
        this.mTarget = target;
    }

    public String attemptId() {
        return mAttemptId;
    }

    public void setAttemptId(String attemptId) {
        this.mAttemptId = attemptId;
    }

}
