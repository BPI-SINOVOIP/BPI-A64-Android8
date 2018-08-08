// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.google.android.tradefed.build.AppWithDeviceLaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * An {@link AppWithDeviceLaunchControlProvider} that only returns a build to test if Monkey Post
 * Service says that more Monkey runs should be started, in which case it immediately resets the
 * build in Launch Control so that the next query will also return that build.
 */
@OptionClass(alias = "appdevicemonkey-build")
public class AppWithDeviceMonkeyBuildProvider extends AppWithDeviceLaunchControlProvider {

    @Option(name = "api-url", description = "url for posting to counting service")
    private String mApiUrl = "http://android-te-lab-27.mtv.corp.google.com:8080/getruncount/";

    @Option(name = "skip-count-check", description =
            "whether to skip the run count check and act like AppWithDeviceLaunchControlProvider")
    private Boolean mSkipCountCheck = false;

    @Option(name = "run-limit", description = "target number of runs")
    private int mRunLimit = 20;

    @Option(name = "prod-type", description = "Product type name as recognized by counting service",
            mandatory = true)
    private String mProdType = null;


    // Cache this so that we don't end up querying LaunchControl twice
    private RemoteBuildInfo mRemoteBuild = null;

    // impromptu RPC API definition
    private static final String PARAM_KEY = "key";
    private static final String PARAM_PRODUCT = "product";
    private static final String PARAM_BUILD_ID = "build-id";
    private static final String PARAM_APP_BUILD_ID = "app-build-id";

    private static final String RESPONSE_STATUS = "status";
    private static final String RESPONSE_COUNT = "count";

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        mRemoteBuild = super.getRemoteBuild();

        if (mRemoteBuild == null) {
            CLog.d("remoteBuild is null; no build to test.");
            return null;

        } else if (needMoreMonkeyRuns(mRemoteBuild)) {
            IBuildInfo build = super.getBuild();
            if (build == null) {
                CLog.w("We want more Monkey runs, but Launch Control reports no build " +
                        "to test.");
            }

            return build;
        }

        CLog.d("needMoreMonkeyRuns returned false; no build to test.");

        return null;
    }

    /**
     * Returns a cached version of the remote build to avoid multiple launch-control queries from
     * this class and the superclass
     */
    @Override
    public RemoteBuildInfo getRemoteBuild() throws BuildRetrievalError {
        return mRemoteBuild;
    }

    boolean needMoreMonkeyRuns(RemoteBuildInfo build) throws BuildRetrievalError {
        if (mSkipCountCheck) return true;

        int nRuns = getRunCount(build);
        if (nRuns < mRunLimit) {
            // THIS IS A HACK: reset build in LaunchControl so that the next invocation will also
            // pick up this build.  It is an unfortunate side-effect that this guarantees extra runs
            // in any case where we have more than one invocation running at the same time.
            resetTestBuild(build.getBuildId());
            return true;
        }
        return false;
    }

    /**
     * Returns the number of runs counted so far, or <code>null</code> if there was an error
     */
    int getRunCount(RemoteBuildInfo build) throws BuildRetrievalError {

        final MultiMap<String, String> params = new MultiMap<String, String>();
        params.put(PARAM_KEY, build.getAttribute("test_tag"));
        params.put(PARAM_PRODUCT, mProdType);
        params.put(PARAM_APP_BUILD_ID, build.getBuildId());
        // FIXME: app-build-id and build-id should be mutually exclusive, since we only count runs
        // FIXME: against one or the other, never both
        if (getDeviceBuildId() != null) {
            params.put(PARAM_BUILD_ID, getDeviceBuildId());
        }

        final IHttpHelper http = getHttpHelper();
        final String url = http.buildUrl(mApiUrl, params);
        try {
            final String jsonResponse = http.doGet(url);
            final JSONObject json = new JSONObject(jsonResponse);
            CLog.v("Got json %s", json);
            if (!"ok".equals(json.get(RESPONSE_STATUS))) {
                throw new BuildRetrievalError(String.format(
                        "Received error while fetching run count: %s", jsonResponse));
            }

            return Integer.parseInt(json.getString(RESPONSE_COUNT));

        } catch (IOException e) {
            throw new BuildRetrievalError("Failed to get run count from count service", e);
        } catch (JSONException e) {
            throw new BuildRetrievalError("Failed to get run count from count service", e);
        } catch (IHttpHelper.DataSizeException e) {
            throw new BuildRetrievalError("Failed to get run count from count service", e);
        }
    }

    /**
     * Return the http helper to use.
     * <p/>
     * Exposed for unit testing
     */
    IHttpHelper getHttpHelper() {
        return new HttpHelper();
    }
}

