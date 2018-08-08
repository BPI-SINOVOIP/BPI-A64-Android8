// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.ddmlib.Log;
import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationReceiver;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.ByteArrayList;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RegexTrie;
import com.android.tradefed.util.StreamUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;


/**
 * An {@link IBuildProvider} that fetches work units from a TF-Workbuf backend
 * and returns them for the TF instance to execute
 */
@OptionClass(alias = "workbuf-build")
public class TfWorkbufBuildProvider implements IBuildProvider, IConfigurationReceiver {

    final static private int WORKBUF_PROTOCOL_VERSION = 0x1;

    private BuildAPIHelper mHelp = null;

    @Option(name = "hostname", description = "host name of Workbuffer instance to try",
            mandatory = true)
    private String mServerHost = null;

    @Option(name = "port", description = "port number for Workbuffer instance")
    private int mPort = TfWorkbufProtocol.DEFAULT_PORT;

    @Option(name = "max-fast-forward-count", description = "How many successive times to query " +
            "the workbuf when we receive a target that we're configured to skip.  Note that this " +
            "value is ignored if the workbuf returns no results.")
    private int mMaxFastForwardCount = 0;

    private IConfiguration mConfiguration;

    static final RegexTrie<Boolean> SKIP_TARGETS = new RegexTrie<Boolean>();

    static final RegexTrie<Boolean> DOWNLOAD_FILES = new RegexTrie<Boolean>();

    static {
        // SKIP_TARGETS[target] = <should skip?>

        SKIP_TARGETS.put(false, "GmsCore_x86_debug");
        SKIP_TARGETS.put(false, "Email-eng");
        SKIP_TARGETS.put(false, "NewsWeather-eng");

        // default: skip any targets not explicitly mentioned
        SKIP_TARGETS.put(true, (String) null);


        // DOWNLOAD_FILES[target][filename] = <should download?>
        // Download (and install) any apk that is not in a subdirectory
        final String allRootDirApks = "[^/]+\\.apk";

        // Default GoogleNow behavior
        // only include Velvet, VelvetTests, VelvetTestApp
        DOWNLOAD_FILES.put(true, "GoogleNow(_.*)?", "Velvet(Tests|TestApp)?\\.apk");

        // Default GmsCore behavior
        // only include GmsCore.apk or GmsCoreTests.apk
        DOWNLOAD_FILES.put(true, "GmsCore(_.*)?", "GmsCore(Tests)?\\.apk");

        // Default Email behavior
        // include: EmailGoogle(Tests) Exchange2Google, ExchangeGoogleTests, UnifiedEmail(Tests)
        DOWNLOAD_FILES.put(true, "Email-eng", "EmailGoogle(Tests)?\\.apk");
        DOWNLOAD_FILES.put(true, "Email-eng", "UnifiedEmail(Tests)?\\.apk");
        DOWNLOAD_FILES.put(true, "Email-eng", "Exchange2Google\\.apk");
        DOWNLOAD_FILES.put(true, "Email-eng", "ExchangeGoogleTests\\.apk");

        // Default NewsWeather behavior
        // download GenieWidget(Tests)
        DOWNLOAD_FILES.put(true, "NewsWeather-eng", "GenieWidget(Tests)?\\.apk");

        // Default behavior.  Note that RegexTrie is greedy, so this default should be specified last.
        DOWNLOAD_FILES.put(true, ".*", allRootDirApks);
    }

    @Override
    public void buildNotTested(IBuildInfo info) {
        // ignore
    }

    /**
     * Read a response from an {@link InputStream} into a {@link ByteArrayList}.  {@code input}
     * will be wrapped in a {@link BufferedInputStream} internally, so no need to do so beforehand.
     */
    ByteArrayList readResponse(InputStream input) throws IOException {
        InputStream bufStream = new BufferedInputStream(input);
        // Guesstimating a reasonable default response size; ByteArrayList will expand as needed.
        ByteArrayList response = new ByteArrayList(16384);

        int b = 0;
        CLog.v("Starting the read loop");
        while ((b = bufStream.read()) != -1) {
            response.add((byte) (b & 0xff));
        }
        CLog.v("Got a response of length %d", response.size());

        return response;
    }

    public JSONObject pollForBuildAttempt() throws BuildRetrievalError {
        Socket sock = null;
        InputStream iStream = null;
        OutputStream oStream = null;

        try {
            sock = new Socket(mServerHost, mPort);
            // Push button (err... send request)
            oStream = new BufferedOutputStream(sock.getOutputStream());
            emitHeader(oStream);
            final JSONObject outMsg = new JSONObject();
            outMsg.put("request", "poll");
            send(oStream, outMsg.toString());
            // Note that this _must_ use .shutdownOutput.  Closing oStream directly will
            // close the socket, and prevent us from seeing the response.
            oStream.flush();
            sock.shutdownOutput();

            // Receive bacon (response)
            final ByteArrayList response = readResponse(sock.getInputStream());
            return parseBuildResponse(response);

        } catch (IOException e) {
            throw new BuildRetrievalError("Failed to poll for new build", e);
        } catch (JSONException e) {
            throw new BuildRetrievalError("Failed to generate workbuf request", e);
        } finally {
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException e) {
                    // No need to throw if everything else was successful
                    CLog.w("Encountered exception while closing poll socket: %s", e.getMessage());
                }
            }
        }
    }

    /**
     * Parse the incoming message into a sequence of updates to apply to our datastore.
     */
    JSONObject parseBuildResponse(ByteArrayList input) throws BuildRetrievalError {
        // If we don't have at least four bytes (the size of the header), input is malformed.
        if (input.size() < 4) {
            throw new BuildRetrievalError(String.format("Received malformed input with size %d",
                    input.size()));
        }

        // Use the header to determine which parser to use
        final byte[] headerBytes = new byte[]
                {input.get(0), input.get(1), input.get(2), input.get(3)};
        int header = TfWorkbufProtocol.fourBytesToInt(headerBytes);

        if (header == TfWorkbufProtocol.HEADER_JSON_OBJECT) {
            final String strInput =
                    new String(input.getContents(), 4, input.getContents().length - 4);

            try {
                return new JSONObject(strInput);

            } catch (JSONException e) {
                throw new BuildRetrievalError("Failed to parse workbuf response", e);
            }
        } else {
            throw new BuildRetrievalError(String.format("Didn't recognize header 0x%x (\"%s\")",
                    header, new String(headerBytes)));
        }
    }

    /**
     * Determine whether or not a particular file should be downloaded
     * <p />
     * Exposed for unit testing
     */
    boolean checkShouldDownloadFile(String target, String name) {
        return Boolean.TRUE.equals(DOWNLOAD_FILES.retrieve(target, name));
    }

    /**
     * Determine whether or not a particular target should be skipped
     * <p />
     * Exposed for unit testing
     */
    boolean checkShouldSkipTarget(String target) {
        return Boolean.TRUE.equals(SKIP_TARGETS.retrieve(target));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cleanUp(IBuildInfo build) {
        build.cleanUp();
    }

    /**
     * Poll the WorkBuffer for a work unit to test
     */
    @Override
    public IBuildInfo getBuild() throws BuildRetrievalError {
        CLog.d("Running internal getBuild().  Max fast-forward count is %d.", mMaxFastForwardCount);
        return getBuild(0);
    }

    /**
     * Poll the WorkBuffer for a unit to test.  This internal function is tail-recursive to
     * implement the Fast Forward behavior when we're configured to skip the returned build.
     */
    IBuildInfo getBuild(int iteration) throws BuildRetrievalError {
        if (iteration > mMaxFastForwardCount) {
            CLog.d("Stopped fast-forwarding after %d iterations.", iteration);
            return null;
        }

        final JSONObject attempt = pollForBuildAttempt();
        int fileRejectCount = 0;

        if (attempt.length() == 0) {
            // this will be logged at DEBUG level by TestInvocation
            CLog.v("Received empty response; returning null");
            return null;
        }

        try {
            final String buildId = attempt.getString("buildId");
            final String target = attempt.getString("target");
            final String attemptId = attempt.getString("attemptId");
            // FIXME: plumb the human-version of the build name through from the API
            final AppBuildInfo build = new AppBuildInfo(buildId, target);
            build.setTestTag(target);
            build.addBuildAttribute("attemptId", attemptId);
            build.setBuildBranch("presubmit-builds");

            if (checkShouldSkipTarget(target)) {
                CLog.w("Target %s is in the skip list; skipping build %s/%s/%s", target, target,
                        buildId, attemptId);
                // tail recursion only happens here
                return getBuild(iteration + 1);
            }

            final JSONArray files = attempt.getJSONArray("artifacts");
            for (int i = 0; i < files.length(); ++i) {
                final JSONObject file = files.getJSONObject(i);

                final String name = file.getString("name");
                final String version = file.getString("revision");
                final String md5 = file.getString("md5");

                if (!checkShouldDownloadFile(target, name)) {
                    fileRejectCount++;
                    CLog.v("Not downloading file \"%s\" which doesn't match filter", name);
                    continue;
                }

                final String basename = FileUtil.getBaseName(name);
                final String extension = FileUtil.getExtension(name);
                CLog.v("About to create file b(%s) e(%s)", basename, extension);
                final File destFile = FileUtil.createTempFile(basename, extension);
                final OutputStream destStream = new FileOutputStream(destFile);
                try {
                    getBuildAPIHelper().fetchBuildArtifact(destStream, buildId, target, attemptId,
                            name);
                } finally {
                    StreamUtil.flushAndCloseStream(destStream);
                }

                build.addAppPackageFile(destFile, version);
            }

            if (build.getAppPackageFiles().isEmpty()) {
                // This is an error, because there are no tests to run without installing
                // extra apks
                throw new BuildRetrievalError(String.format(
                        "No AppPackage files were downloaded for build %s/%s/%s.  We rejected %d " +
                        "of the %d files offered by the BuildAPI.", target, buildId, attemptId,
                        fileRejectCount, files.length()));
            }

            return build;

        } catch (IOException e) {
            throw new BuildRetrievalError("Failed to fetch build artifact", e);
        } catch (JSONException e) {
            throw new BuildRetrievalError("Failed to parse build info", e);
        }
    }

    BuildAPIHelper getBuildAPIHelper() throws BuildRetrievalError, IOException {
        if (mHelp == null) {
            mHelp = (BuildAPIHelper) mConfiguration.getConfigurationObject("build-api-helper");
            try {
                mHelp.setupTransport();
            } catch (GeneralSecurityException | NullPointerException e) {
                if (mHelp == null) {
                    CLog.logAndDisplay(Log.LogLevel.ERROR, "BuildAPIHelper object is missing from "
                            + "the Configuration");
                }
                throw new BuildRetrievalError("Failed to connect to Build API", e);
            }
        }

        return mHelp;
    }

    /**
     * The header is a 32-bit section that precedes every message.  It's currently constant, but
     * is present to enable future protocol enhancements, such as compression.
     */
    private void emitHeader(OutputStream stream) throws IOException {
        int header = 0x0;
        header |= WORKBUF_PROTOCOL_VERSION;
        stream.write(TfWorkbufProtocol.intToFourBytes(header));
    }

    /**
     * A helper function to send bytes from a {@see String} into a stream
     */
    private void send(OutputStream stream, String str) throws IOException {
        byte[] bytes = str.getBytes();
        stream.write(bytes, 0, bytes.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConfiguration(IConfiguration configuration) {
        mConfiguration = configuration;
    }
}
