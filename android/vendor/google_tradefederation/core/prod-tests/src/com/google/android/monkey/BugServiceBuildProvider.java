// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.command.FatalHostError;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.android.tradefed.util.net.XmlRpcHelper;
import com.google.android.tradefed.build.DeviceLaunchControlProvider;
import com.google.android.tradefed.build.RemoteBuildInfo;

import org.kxml2.io.KXmlSerializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * A {@link DeviceLaunchControlProvider} that only returns a build to test if BugService says that
 * more Monkey runs should be started.
 */
@OptionClass(alias = "monkey-build")
public class BugServiceBuildProvider extends DeviceLaunchControlProvider {

    // FIXME: configurable
    @Option(name = "bugservice-url", description = "url for posting to BugService")
    private String mBugServiceUrl = "http://android-test.corp.google.com:8388/";

    @Option(name = "skip-bugservice", description =
            "whether to skip the bugservice check and act like LaunchControlProvider")
    private Boolean mSkipBugService = false;

    /** Numerical directory used in output directory hierarchy */
    @Option(name = "subdir-number", description = "The numeric report dir to use.",
            importance = Importance.ALWAYS)
    private Integer mSubdirNumber = 0;

    // FIXME: share with DeviceSelectionOptions
    @Option(name = "prod-type", description = "The device type to send to BugService in the query.",
            importance = Importance.IF_UNSET)
    private String mProdType = null;

    @Option(name="kill-tf-when-limit-reached", description =
            "When set, TF will be forcibly shut down when BugService responds that the Monkey " +
            "run limit has been reached.")
    private Boolean mThrowFatalErrorWhenDone = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        if (!mSkipBugService && mProdType == null) {
            throw new IllegalArgumentException("Product type was not specified.");
        }

        RemoteBuildInfo remoteBuild = queryForBuild();

        if (remoteBuild == null) {
            CLog.d("remoteBuild is null; no build to test.");
            return null;
        } else if (mSkipBugService || needMoreMonkeyRuns(remoteBuild)) {
            IBuildInfo build = super.getBuild();
            if (build == null) {
                CLog.w("BugService wants more Monkey runs, but Launch Control reports no build " +
                        "to test.");
            }

            return build;
        }

        CLog.d("needMoreMonkeyRuns returned false; no build to test.");
        if (mThrowFatalErrorWhenDone) {
            // We've reached the monkey run.  Lights out, party's over...
            throw new FatalHostError("Reached Monkey run limit.  Goodbye, cruel world.");
        }

        return null;
    }

    String getFlavorName() {
        String fullName = getBuildFlavor();
        int hyphenIdx = fullName.indexOf("-");
        if (hyphenIdx <= 0) {
            CLog.w("Can't split unrecognized build flavor name format '%s'", fullName);
            return fullName;
        } else {
            return fullName.substring(0, hyphenIdx);
        }
    }

    boolean needMoreMonkeyRuns(RemoteBuildInfo build) throws BuildRetrievalError {
        String buildId = build.getBuildId();
        //String flavor = getFlavorName();
        return needMoreMonkeyRuns(buildId, mProdType);
    }

    /**
     * Queries BugService to determine if more Monkey runs are needed
     */
    boolean needMoreMonkeyRuns(String buildId, String buildFlavor) throws BuildRetrievalError {
        if (buildId.equals(RemoteBuildInfo.UNKNOWN_BUILD_ID)) {
            // FIXME exception
            return false;
        }

        HttpURLConnection bugConn = null;
        try {
            bugConn = getBugServiceConnection();
        } catch (IOException e) {
            CLog.w("Caught IOException while trying to connect to BugService: %s", e.getMessage());

            // Stop running monkey until we can talk to BugService for coordination
            return false;
        }

        OutputStream bugOut = null;
        InputStream bugIn = null;
        try {
            bugOut = getOutputStream(bugConn);

            KXmlSerializer serializer = new KXmlSerializer();
            String namespace = null;
            serializer.setOutput(bugOut, "UTF-8");
            serializer.startDocument("UTF-8", null);
            serializer.setFeature(
                "http://xmlpull.org/v1/doc/features.html#indent-output", true);

            //XmlRpcHelper.writeOpenMethodCall(serializer, namespace, "AtMonkeyRunLimit");
            XmlRpcHelper.writeOpenMethodCall(serializer, namespace, "NeedMoreMonkeyRuns");
            XmlRpcHelper.writeFullMethodArg(serializer, namespace, "string", buildId);
            // subdirectory (lets BugService keep track of independent runs for the same build)
            XmlRpcHelper.writeFullMethodArg(serializer, namespace, "i4",
                    Integer.toString(mSubdirNumber));
            // build flavor
            XmlRpcHelper.writeFullMethodArg(serializer, namespace, "string", buildFlavor);
            XmlRpcHelper.writeCloseMethodCall(serializer, namespace);

            serializer.endDocument();
            bugOut.flush();

            List<String> response = XmlRpcHelper.parseResponseTuple(getInputStream(bugConn));
            if (response.size() != 2 || !"boolean".equals(response.get(0))) {
                throw new BuildRetrievalError(String.format(
                        "Couldn't understand response parameters %s from bugservice.", response));
            }

            // evaluate if BugService says we need more monkey runs
            return XmlRpcHelper.TRUE_VAL.equals(response.get(1));
        } catch (IOException e) {
            CLog.w("Caught IOException while talking to BugService: %s", e.getMessage());
            return false;
        } finally {
            StreamUtil.close(bugOut);
            StreamUtil.close(bugIn);
        }
    }

    HttpURLConnection getBugServiceConnection() throws IOException {
        return getHttpHelper().createXmlConnection(new URL(mBugServiceUrl), "POST");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore.  Ideally, LaunchControl and BugService would be able to coordinate in some way,
        // but in practice, neither knows about the other.
    }

    /**
     * Creates an {@link OutputStream} to send xmlrpc data to
     * <p/>
     * Exposed for unit testing.
     *
     * @return the {@link OutputStream}
     * @throws IOException if failed to create OutputStream
     */
    OutputStream getOutputStream(HttpURLConnection httpConnection) throws IOException {
        return httpConnection.getOutputStream();
    }

    /**
     * Returns an input stream through which the caller can retrieve the server's response
     * <p/>
     * Exposed for unit testing.
     *
     * @return InputStream to fetch server response
     */
    InputStream getInputStream(HttpURLConnection httpConnection) throws IOException {
        return httpConnection.getInputStream();
    }

    /**
     * Return the http helper to use.
     * <p/>
     * Exposed for unit testing
     */
    IHttpHelper getHttpHelper() {
        return new HttpHelper();
    }

    /**
     * Set the value for {@code mThrowFatalErrorWhenDone}.
     * <p/>
     * Exposed for unit testing
     */
    void setThrowFatalErrorWhenDone(boolean value) {
        mThrowFatalErrorWhenDone = value;
    }

    /**
     * Set the value for {@code mProdType}.
     * <p/>
     * Exposed for unit testing
     */
    void setProdType(String type) {
        mProdType = type;
    }
}

