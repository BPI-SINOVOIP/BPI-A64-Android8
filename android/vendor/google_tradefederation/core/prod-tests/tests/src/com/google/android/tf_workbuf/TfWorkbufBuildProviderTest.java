// Copyright 2014 Google Inc.  All Rights Reserved.
package com.google.android.tf_workbuf;

import com.android.tradefed.build.AppBuildInfo;
import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;
import com.android.tradefed.util.ByteArrayList;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link TfWorkbufBuildProvider}
 */
public class TfWorkbufBuildProviderTest extends TestCase {
    private TfWorkbufBuildProvider mProvider = null;
    private static final String DATA_PATH = "/google/test_data/TfWorkbufResponse.txt";

    @Override
    public void setUp() {
        mProvider = new TfWorkbufBuildProvider();
    }

    private JSONObject getSampleResponse(TfWorkbufBuildProvider provider) throws IOException,
            BuildRetrievalError {
        final InputStream response = getClass().getResourceAsStream(DATA_PATH);
        assertNotNull(response);
        final ByteArrayList responseTxt = provider.readResponse(response);
        assertNotNull(responseTxt);
        final JSONObject responseJson = provider.parseBuildResponse(responseTxt);
        assertNotNull(responseJson);

        return responseJson;
    }

    public void testBackendResponse() throws Exception {
        final JSONObject responseJson = getSampleResponse(mProvider);

        // Check general outer structure
        assertEquals(4, responseJson.length());
        assertTrue(responseJson.has("buildId"));
        assertTrue(responseJson.has("target"));
        assertTrue(responseJson.has("artifacts"));
        assertTrue(responseJson.has("attemptId"));

        // Check specific outer values
        assertEquals("P4164", responseJson.getString("buildId"));
        assertEquals("GoogleNow_prod", responseJson.getString("target"));
        assertEquals("0", responseJson.getString("attemptId"));

        // Artifacts!
        final JSONArray files = responseJson.getJSONArray("artifacts");
        assertEquals(10, files.length());
        for (int i = 0; i < files.length(); ++i) {
            // check general structure of artifact
            final JSONObject file = files.getJSONObject(i);
            assertEquals(5, file.length());
            assertTrue(file.has("lastModifiedTime"));
            assertTrue(file.has("revision"));
            assertTrue(file.has("name"));
            assertTrue(file.has("md5"));
            assertTrue(file.has("size"));
        }

        // Spot check specific values of first and last artifacts
        final JSONObject first = files.getJSONObject(0);
        assertEquals(1391237520408L, first.getLong("lastModifiedTime"));
        assertEquals("rnpP+RCz76lqqSSrLxSSeg==", first.getString("revision"));
        assertEquals("BUILD_INFO", first.getString("name"));
        assertEquals("504087bd89ee6e552be5b307079312c6", first.getString("md5"));
        assertEquals(10958L, first.getLong("size"));

        final JSONObject last = files.getJSONObject(9);
        assertEquals(1391237551671L, last.getLong("lastModifiedTime"));
        assertEquals("aoLrEGdk5cZxUEjbVZAs9A==", last.getString("revision"));
        assertEquals("Velvet.apk", last.getString("name"));
        assertEquals("19e1ed17e5a4a34136512c39d30248e6", last.getString("md5"));
        assertEquals(21564457L, last.getLong("size"));
    }

    /**
     * A Mock for {@link BuildAPIHelper} that makes it easy to see which URIs would have been
     * requested
     */
    private static class FakeBuildAPIHelper extends BuildAPIHelper {
        private final List<String> mFetchRequests;

        FakeBuildAPIHelper(List<String> fetchRequests) {
            mFetchRequests = fetchRequests;
        }

        @Override
        public void setupTransport() {
            // no-op
        }

        /**
         * Use two different mechanisms to log requests
         */
// FIXME: this entire class is probably broken
/*        @Override
        String executeGet(String requestUri) {
            if (mFetchRequests != null) {
                mFetchRequests.add(requestUri);
            }

            return requestUri;
        }
*/
    }

    /**
     * Make sure that our apk download filtering is working as expected
     */
    public void testFileDownloadFilter() throws Exception {
        // Make sure that GoogleNow filtering is working, for any GoogleNow_.* target
        assertTrue(mProvider.checkShouldDownloadFile("GoogleNow_prod", "Velvet.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_prod", "GoogleHome.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_prod", "Launcher3.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_prod",
                "bundled_Velvet/Velvet.apk"));

        assertTrue(mProvider.checkShouldDownloadFile("GoogleNow_dev", "Velvet.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_dev", "GoogleHome.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_dev", "Launcher3.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GoogleNow_dev",
                "bundled_Velvet/Velvet.apk"));

        // Make sure that GmsCore filtering is working as expected, for any GmsCore_.* target
        assertTrue(mProvider.checkShouldDownloadFile("GmsCore_debug", "GmsCore.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("GmsCore_debug", "GmsCoreTests.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GmsCore_debug", "GmsCore-hdpi.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GmsCore_debug", "GmsCoreApiTests.apk"));

        assertTrue(mProvider.checkShouldDownloadFile("GmsCore", "GmsCore.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("GmsCore", "GmsCoreTests.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GmsCore", "GmsCore-hdpi.apk"));
        assertFalse(mProvider.checkShouldDownloadFile("GmsCore", "GmsCoreApiTests.apk"));

        // Make sure that we limit the special behavior to =="GmsCore" or .startsWith("GmsCore_")
        assertTrue(mProvider.checkShouldDownloadFile("GmsCore2", "GmsCore-hdpi.apk"));

        // Email
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "EmailGoogle.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "EmailGoogleTests.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "UnifiedEmail.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "UnifiedEmailTests.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "Exchange2Google.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("Email-eng", "ExchangeGoogleTests.apk"));

        // NewsWeather
        assertTrue(mProvider.checkShouldDownloadFile("NewsWeather-eng", "GenieWidget.apk"));
        assertTrue(mProvider.checkShouldDownloadFile("NewsWeather-eng", "GenieWidgetTests.apk"));
    }

    /**
     * Make sure that we can specify which targets we do or do not want to run.
     */
    public void testTargetSkipFilter() {
        // disabled
        assertTrue(mProvider.checkShouldSkipTarget("GmsCore_debug"));
        assertTrue(mProvider.checkShouldSkipTarget("GmsCore"));
        assertTrue(mProvider.checkShouldSkipTarget("GoogleNow_prod"));

        // enabled
        assertFalse(mProvider.checkShouldSkipTarget("GmsCore_x86_debug"));
        assertFalse(mProvider.checkShouldSkipTarget("Email-eng"));
        assertFalse(mProvider.checkShouldSkipTarget("NewsWeather-eng"));
    }

    /**
     * Make sure that we handle an empty response correctly
     */
    public void testEmptyResponse() throws Exception {
        // Make sure parsing works as expected
        final ByteArrayList input = new ByteArrayList(6);
        input.addAll("\u0000\u0000\u0000\u0001{}".getBytes());
        final JSONObject jsResponse = mProvider.parseBuildResponse(input);
        assertEquals("Empty response parsed as non-empty jsResponse", 0, jsResponse.length());

        // And make sure we turn that parsed result into a null IBuildInfo
        final TfWorkbufBuildProvider mockProvider = new TfWorkbufBuildProvider() {
            @Override
            public JSONObject pollForBuildAttempt() {
                return jsResponse;
            }
        };
        assertNull("Result from an empty response wasn't a null build!",
                mockProvider.getBuild());
    }

    /**
     * Make sure that we throw if we encounter a build with no downloaded files
     */
    public void testNoFilesDownloaded() throws Exception {
        final String expectedMsg = "No AppPackage files were downloaded.  We rejected 10 of the " +
                "10 files offered by the BuildAPI.";

        final TfWorkbufBuildProvider mockProvider = new TfWorkbufBuildProvider() {
            @Override
            public JSONObject pollForBuildAttempt() throws BuildRetrievalError {
                try {
                    // Pull static data and return it
                    return getSampleResponse(this);
                } catch (IOException e) {
                    throw new BuildRetrievalError("IOException", e);
                }
            }

            /**
             * Don't skip anything.
             */
            @Override
            boolean checkShouldSkipTarget(String target) {
                return false;
            }

            /**
             * Reject all files.  This should cause the Provider to throw in #getBuild()
             */
            @Override
            boolean checkShouldDownloadFile(String target, String name) {
                return false;
            }
        };

        try {
            final IBuildInfo build = mockProvider.getBuild();
            fail(String.format("No BuildRetrievalError thrown for build with no files downloaded!" +
                    " Build was \"%s\".", build));
        } catch (BuildRetrievalError e) {
            assertEquals(expectedMsg, e.getMessage());
        }
    }

    public void disabled_testFetchArtifacts() throws Exception {
        final List<String> fetchUrls = new ArrayList<String>(10);
        final TfWorkbufBuildProvider provider =
                new TfWorkbufBuildProvider() {
                    @Override
                    public JSONObject pollForBuildAttempt() throws BuildRetrievalError {
                        try {
                            // Pull static data and return it
                            return getSampleResponse(this);
                        } catch (IOException e) {
                            throw new BuildRetrievalError("IOException", e);
                        }
                    }

                    @Override
                    BuildAPIHelper getBuildAPIHelper() throws BuildRetrievalError, IOException {
                        return new FakeBuildAPIHelper(null);
                    }
                };

        // Set up expectations
        // Remember that the FakeBuildAPIHelper returns the _request URI_ as the _file contents_
        final String uriPat = "https://www.googleapis.com/android/internal/build/v1/builds/" +
                "pending/P4164/GoogleNow_prod/attempts/0/artifacts/%s/?alt=media";

        // "BUILD_INFO" and "COPIED" should be filtered out
        final String[] files = {"GoogleHome.apk", "HotwordServiceDemo.apk", "Launcher3.apk",
                "MemoryLeak.apk", "NowDevUtils.apk", "RecognitionServiceDemo.apk",
                "SpeechLibraryDemo.apk", "Velvet.apk"};

        IBuildInfo build = provider.getBuild();
        try {
            assertNotNull(build);
            assertTrue(build instanceof AppBuildInfo);
            // to appease the static checker
            if (!(build instanceof AppBuildInfo)) return;

            AppBuildInfo appBuild = (AppBuildInfo) build;
            List<VersionedFile> appFiles = appBuild.getAppPackageFiles();
            assertEquals(files.length, appFiles.size());
            for (int i = 0; i < files.length; ++i) {
                // Remember that the FakeBuildAPIHelper returns the _request URI_ as the _file contents_
                final String expected = String.format(uriPat, files[i]);
                final File obsFile = appFiles.get(i).getFile();
                final String observed = FileUtil.readStringFromFile(obsFile);
                assertEquals(expected, observed);
            }
        } finally {
            if (build != null) {
                build.cleanUp();
            }
        }
    }
}
