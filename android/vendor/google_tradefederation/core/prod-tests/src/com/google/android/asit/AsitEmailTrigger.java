// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.asit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * A "Test" that acts as a trigger to send ASIT status reminder email when a new build is ready
 *
 * The "test" will be invoked when a new build is ready, and do an HTTP GET on rdb server, which
 * then sends out the nag mails about what tests are lagging behind.
 */
public class AsitEmailTrigger implements IRemoteTest, IBuildReceiver {

    private static final String OK_RESPONSE = "OK";

    private IBuildInfo mBuildInfo;
    @Option(name = "release-dashboard-trigger", description =
            "url trigger for sending ASIT reminder emails.")
    private String mRdbTriggerUrl = "http://android-rdb.corp.google.com/asit-email/";

    @Option(name = "product-group",
            description = "the group of products that should be checked for test status;"
                          + " corresponds to the group field of product config on dashboard",
            importance = Importance.ALWAYS)
    private String mProductGroup = null;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        String urlString = getTriggerUrl(mProductGroup, mBuildInfo.getBuildId());
        CLog.i("Triggering ASIT reminder email with URL: " + urlString);
        IHttpHelper httpHelper = new HttpHelper();
        String serverResponse = null;
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = httpHelper.createConnection(url, "GET", null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            StreamUtil.copyStreams(conn.getInputStream(), baos);
            serverResponse = baos.toString();
        } catch (MalformedURLException e) {
            CLog.e(e);
        } catch (IOException e) {
            CLog.e(e);
        }
        Assert.assertEquals(OK_RESPONSE, serverResponse);
    }

    protected String getTriggerUrl(String productGroup, String buildId) {
        // check if the build id is a number
        try {
            Integer.parseInt(buildId);
        } catch (NumberFormatException nfe) {
            throw new RuntimeException(buildId + " is not a valid number", nfe);
        }
        if (!mRdbTriggerUrl.endsWith("/")) {
            mRdbTriggerUrl = mRdbTriggerUrl + "/";
        }
        return String.format("%s%s/%s/", mRdbTriggerUrl, mProductGroup, buildId);
    }
}
