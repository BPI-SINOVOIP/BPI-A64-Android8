// Copyright 2012 Google Inc. All Rights Reserved.
package com.google.android.tradefed.result;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;

import junit.framework.TestCase;

import org.easymock.Capture;
import org.easymock.EasyMock;

import java.net.URLEncoder;
import java.util.List;

public class LaunchControlResultReporterTest extends TestCase {
    private LaunchControlResultReporter mReporter = null;
    private IHttpHelper mHttp = null;
    private IBuildInfo mBuild = null;

    private static final IHttpHelper REAL_HTTP = new HttpHelper();

    private static final String LC_URL = "http://foo/bar";
    private static final String STOCK_TARGET = "target";
    private static final String STOCK_BID = "12345";
    private static final String STOCK_TEST_TAG = "test_tag";
    private static final String STOCK_LINK = "http://sponge/inv";
    private static final String STOCK_PER_LINK = "http://sponge/inv/stats";
    private static final String STOCK_GENERIC_LINK = "http://sponge/";

    @Override
    public void setUp() throws Exception {
        mHttp = EasyMock.createMock(IHttpHelper.class);

        mBuild = EasyMock.createMock(IBuildInfo.class);
        EasyMock.expect(mBuild.getBuildTargetName()).andStubReturn(STOCK_TARGET);
        EasyMock.expect(mBuild.getBuildId()).andStubReturn(STOCK_BID);
        EasyMock.expect(mBuild.getTestTag()).andStubReturn(STOCK_TEST_TAG);

        mReporter = new LaunchControlResultReporter() {
            @Override
            IHttpHelper getHttpHelper() {
                return mHttp;
            }
            @Override
            String getUrl() {
                return LC_URL;
            }
            @Override
            public IInvocationContext getInvocationContext() {
                IInvocationContext context = new InvocationContext();
                return context;
            }
        };
    }

    /**
     * Verify that posts to report success are generated as expected.
     */
    public void testSuccess_noLinks() throws Exception {
        final String op = "FUNC_TEST_LINK";
        final Capture<MultiMap<String, String>> mapCap =
                new Capture<MultiMap<String, String>>();
        EasyMock.expect(mHttp.buildUrl(EasyMock.eq(LC_URL), EasyMock.capture(mapCap)))
                .andDelegateTo(REAL_HTTP);

        final Capture<String> fullUrlCap = new Capture<String>();
        mHttp.doGetIgnore(EasyMock.capture(fullUrlCap));

        EasyMock.replay(mHttp, mBuild);
        mReporter.reportSuccess(mBuild, null, null, null);
        EasyMock.verify(mHttp, mBuild);

        assertTrue(mapCap.hasCaptured());
        assertTrue(fullUrlCap.hasCaptured());

        checkValues(fullUrlCap.getValue(), mapCap.getValue(),
                "op", op,
                "id", STOCK_TARGET,
                "bid", STOCK_BID,
                "tag", STOCK_TEST_TAG,
                "link", "",
                "per_link", "",
                "generic_link", "");
    }

    /**
     * Verify that posts to report success are generated as expected.
     */
    public void testSuccess_withLinks() throws Exception {
        final String op = "FUNC_TEST_LINK";
        final Capture<MultiMap<String, String>> mapCap =
                new Capture<MultiMap<String, String>>();
        EasyMock.expect(mHttp.buildUrl(EasyMock.eq(LC_URL), EasyMock.capture(mapCap)))
                .andDelegateTo(REAL_HTTP);

        final Capture<String> fullUrlCap = new Capture<String>();
        mHttp.doGetIgnore(EasyMock.capture(fullUrlCap));

        EasyMock.replay(mHttp, mBuild);
        mReporter.reportSuccess(mBuild, STOCK_LINK, STOCK_PER_LINK, STOCK_GENERIC_LINK);
        EasyMock.verify(mHttp, mBuild);

        assertTrue(mapCap.hasCaptured());
        assertTrue(fullUrlCap.hasCaptured());

        checkValues(fullUrlCap.getValue(), mapCap.getValue(),
                "op", op,
                "id", STOCK_TARGET,
                "bid", STOCK_BID,
                "tag", STOCK_TEST_TAG,
                "link", STOCK_LINK,
                "per_link", STOCK_PER_LINK,
                "generic_link", STOCK_GENERIC_LINK);
    }

    /**
     * Verify that posts to report success are generated as expected.
     */
    public void testBootError_noLinks() throws Exception {
        final String op = "BOOT_FAIL_TEST";
        final Capture<MultiMap<String, String>> mapCap =
                new Capture<MultiMap<String, String>>();
        EasyMock.expect(mHttp.buildUrl(EasyMock.eq(LC_URL), EasyMock.capture(mapCap)))
                .andDelegateTo(REAL_HTTP);

        final Capture<String> fullUrlCap = new Capture<String>();
        mHttp.doGetIgnore(EasyMock.capture(fullUrlCap));

        EasyMock.replay(mHttp, mBuild);
        mReporter.reportBootError(mBuild, null, null);
        EasyMock.verify(mHttp, mBuild);

        assertTrue(mapCap.hasCaptured());
        assertTrue(fullUrlCap.hasCaptured());

        checkValues(fullUrlCap.getValue(), mapCap.getValue(),
                "op", op,
                "id", STOCK_TARGET,
                "bid", STOCK_BID,
                "tag", STOCK_TEST_TAG,
                "link", "",
                "generic_link", "");
    }

    /**
     * Verify that posts to report success are generated as expected.
     */
    public void testBootError_withLinks() throws Exception {
        final String op = "BOOT_FAIL_TEST";
        final Capture<MultiMap<String, String>> mapCap =
                new Capture<MultiMap<String, String>>();
        EasyMock.expect(mHttp.buildUrl(EasyMock.eq(LC_URL), EasyMock.capture(mapCap)))
                .andDelegateTo(REAL_HTTP);

        final Capture<String> fullUrlCap = new Capture<String>();
        mHttp.doGetIgnore(EasyMock.capture(fullUrlCap));

        EasyMock.replay(mHttp, mBuild);
        mReporter.reportBootError(mBuild, STOCK_LINK, STOCK_GENERIC_LINK);
        EasyMock.verify(mHttp, mBuild);

        assertTrue(mapCap.hasCaptured());
        assertTrue(fullUrlCap.hasCaptured());

        checkValues(fullUrlCap.getValue(), mapCap.getValue(),
                "op", op,
                "id", STOCK_TARGET,
                "bid", STOCK_BID,
                "tag", STOCK_TEST_TAG,
                "link", STOCK_LINK,
                "generic_link", STOCK_GENERIC_LINK);
    }

    /**
     * Verify that the post to report test invocation commencement is generated as expected.
     */
    public void testStartingTest() throws Exception {
        final String op = "NOTIFY-STARTING-TEST";
        final Capture<MultiMap<String, String>> mapCap =
                new Capture<MultiMap<String, String>>();
        EasyMock.expect(mHttp.buildUrl(EasyMock.eq(LC_URL), EasyMock.capture(mapCap)))
                .andDelegateTo(REAL_HTTP);

        final Capture<String> fullUrlCap = new Capture<String>();
        mHttp.doGetIgnore(EasyMock.capture(fullUrlCap));

        EasyMock.replay(mHttp, mBuild);
        mReporter.reportStartingTest(mBuild);
        EasyMock.verify(mHttp, mBuild);

        assertTrue(mapCap.hasCaptured());
        assertTrue(fullUrlCap.hasCaptured());

        checkValues(fullUrlCap.getValue(), mapCap.getValue(),
                "op", op,
                "id", STOCK_TARGET,
                "bid", STOCK_BID,
                "tag", STOCK_TEST_TAG);
    }

    /**
     * Utility function to verify that the specified key/value pairs are set in both the provided
     * url as well as the parameter map
     */
    private void checkValues(String url, MultiMap<String, String> mmap, String... expectations)
            throws Exception {
        if ((expectations.length & 0x1) != 0x0) {
            throw new IllegalArgumentException(String.format("expectations must be a list of " +
                    "matched pairs, and thus must have even length.  Current length is %d",
                    expectations.length));
        }

        for (int i = 0; i < expectations.length; i += 2) {
            final String key = expectations[i];
            final String val = expectations[i+1];
            // Verify the map value
            assertEquals(String.format("Mismatched map value for key '%s'", key),
                    val, getOnlyVal(mmap, key));
            // And verify the value in the url
            assertTrue(String.format(
                    "Failed to find pair '%s' in url '%s'", urlPair(key, val), url),
                    url.contains(urlPair(key, val)));
        }
    }

    private static String urlPair(String key, String val) throws Exception {
        return String.format("%s=%s",
                URLEncoder.encode(key, "UTF-8"), URLEncoder.encode(val, "UTF-8"));
    }

    /**
     * A small utility function to fetch the only value with the provided key.
     */
    private static String getOnlyVal(MultiMap<String, String> mmap, String key) throws Exception {
        assertTrue(String.format("Map is missing key '%s'", key), mmap.containsKey(key));
        List<String> vals = mmap.get(key);
        assertEquals(String.format("Map doesn't have exactly 1 value for key '%s'", key),
                1, vals.size());
        return vals.get(0);
    }
}

