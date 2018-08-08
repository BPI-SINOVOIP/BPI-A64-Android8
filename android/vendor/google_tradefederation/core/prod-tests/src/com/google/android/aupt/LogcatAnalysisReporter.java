// Copyright 2013 Google Inc. All Rights Reserved.
package com.google.android.aupt;

import com.android.loganalysis.item.LogcatItem;
import com.android.loganalysis.parser.LogcatParser;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.StreamUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *  Result reporter that will parse logcat output of the test and post the number of
 *  java crashes, native crashes and anrs to the release dashboard.
 */
public class LogcatAnalysisReporter implements ITestInvocationListener {

    protected RdbUploader mRdb;
    @Option(name = "java-crashes-key", description = "Reporting unit to post java crashes")
    private String mJavaCrashesKey = "aupt-java-crashes";
    @Option(name = "native-crahes-key", description = "Reporting unit to post native crahes")
    private String mNativeCrashesKey = "aupt-native-crashes";
    @Option(name = "anrs-key", description = "Reporting unit to post anrs")
    private String mAnrsKey = "aupt-anrs";

    public LogcatAnalysisReporter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String dataName, LogDataType dataType, InputStreamSource dataStream) {
        if (!dataName.contains("logcat")) {
            return;
        }

        LogcatParser parser = new LogcatParser();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(dataStream.createInputStream()));
        try {
            LogcatItem logcat = parser.parse(reader);
            mRdb.post(mJavaCrashesKey, logcat.getJavaCrashes().size());
            mRdb.post(mNativeCrashesKey, logcat.getNativeCrashes().size());
            mRdb.post(mAnrsKey, logcat.getAnrs().size());
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            StreamUtil.close(reader);
            StreamUtil.close(mRdb);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invocationStarted(IInvocationContext context) {
        mRdb = new RdbUploader(context);
    }

}
