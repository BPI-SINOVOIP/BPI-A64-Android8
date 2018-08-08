// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.net.HttpMultipartPost;
import com.android.tradefed.util.net.IHttpHelper.DataSizeException;

import java.io.IOException;

/**
 * Result reporter for uploading results to dashboard that expect multipart HTTP post request.
 * This reporter listens to calls to testLog method and uploads the logs if the call happens
 * between calls to testRunStarted and testRunEnded.
 */
public class PageCyclerResultReporter implements ITestInvocationListener {
    @Option(name = "dashboard-post-url", description = "Url to which the results are posted")
    private String mPostUrl =
            "http://android-browser-test.mtv.corp.google.com/perf/post_results.php";

    @Option(name = "tag", description = "Tag to use when uploading results to the dashoard")
    private String mTag = "result";

    @Option(name = "filename",
            description = "Filename to use when uploading results to the dashboard")
    private String mFilename = "load_test_results.txt";

    private IBuildInfo mPrimaryBuildInfo;

    @Override
    public void invocationStarted(IInvocationContext context) {
        mPrimaryBuildInfo = context.getBuildInfos().get(0);
    }

    @Override
    public void testLog(String dataName, LogDataType dataType,
            InputStreamSource dataStream) {
        if (dataName.startsWith("result:")) {
            try {
                doPost(dataName, dataStream);
            } catch (IOException ioe) {
                CLog.e(String.format("Unable to post results: %s",
                                ioe.getMessage()));
                CLog.e(ioe);
            } catch (DataSizeException dse) {
                CLog.e(String.format("Unable to post results: %s",
                        dse.getMessage()));
                CLog.e(dse);
            }
        }
    }

    private void doPost(String dataName, InputStreamSource dataStream)
            throws IOException, DataSizeException {
        String[] parts = dataName.split(":");
        dataName = parts[1];
        String target = mPrimaryBuildInfo.getBuildFlavor();
        if (parts.length == 3) {
            // dataName contains a suffix to indicate webview provider type, suffix it to target
            // name so we report fake device types for SxS comparison purpose
            target = String.format("%s-%s", target, parts[2]);
        }
        HttpMultipartPost post = new HttpMultipartPost(mPostUrl);
        post.addParameter("buildId", mPrimaryBuildInfo.getBuildId());
        post.addParameter("suite", dataName);
        post.addParameter("branch", mPrimaryBuildInfo.getBuildBranch());
        post.addParameter("target", target);
        post.addTextFile(mTag, mFilename, dataStream.createInputStream());
        post.send();
    }
}