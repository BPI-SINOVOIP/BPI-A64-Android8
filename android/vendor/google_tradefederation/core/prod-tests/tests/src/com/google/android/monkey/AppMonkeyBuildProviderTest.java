// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.BuildRetrievalError;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.net.HttpHelper;
import com.android.tradefed.util.net.IHttpHelper;
import com.google.android.tradefed.build.RemoteBuildInfo;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link AppMonkeyBuildProvider}
 */
public class AppMonkeyBuildProviderTest extends TestCase {
    private AppMonkeyBuildProvider mProvider = null;
    private IHttpHelper mMockHttp = null;
    private HttpHelper mRealHttp = new HttpHelper();

    private static final String RESPONSE_TEMPLATE = "{\"status\": \"ok\", \"count\": %d}";
    private static final String RESPONSE_0RUNS = String.format(RESPONSE_TEMPLATE, 0);
    private static final String RESPONSE_20RUNS = String.format(RESPONSE_TEMPLATE, 20);

    private static final String BUILD_FLAVOR = "soju-userdebug";
    private static final String LC_BUILD_INFO = "bid:1234";

    private RemoteBuildInfo mRemoteBuild = null;
    private ITestDevice mMockDevice;

    @SuppressWarnings("unchecked")
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mMockHttp = EasyMock.createMock(IHttpHelper.class);
        EasyMock.expect(mMockHttp.buildUrl((String)EasyMock.anyObject(),
                (MultiMap<String, String>)EasyMock.anyObject())).andStubDelegateTo(mRealHttp);

        mRemoteBuild = RemoteBuildInfo.parseRemoteBuildInfo(LC_BUILD_INFO);

        mProvider = new AppMonkeyBuildProvider() {
            @Override
            public String getBuildFlavor() {
                return BUILD_FLAVOR;
            }

            @Override
            public RemoteBuildInfo queryForBuild() throws BuildRetrievalError {
                return mRemoteBuild;
            }

            @Override
            IHttpHelper getHttpHelper() {
                return mMockHttp;
            }
        };
        mMockDevice = EasyMock.createNiceMock(ITestDevice.class);
    }

    /**
     * Check that the BSBP returns {@code true} (do need more monkey runs) when it receives a
     * {@code true} response (yes, need more runs) from BugService
     */
    public void testRunCount_needMore() throws Exception {
        EasyMock.expect(mMockHttp.doGet((String)EasyMock.anyObject())).andReturn(RESPONSE_0RUNS);
        EasyMock.replay(mMockHttp);

        assertTrue(mProvider.needMoreMonkeyRuns(mRemoteBuild, mMockDevice));
        EasyMock.verify(mMockHttp);
    }

    /**
     * Check that the BSBP returns {@code true} (do need more monkey runs) when it receives a
     * {@code true} response (yes, need more runs) from BugService
     */
    public void testRunCount_allDone() throws Exception {
        EasyMock.expect(mMockHttp.doGet((String)EasyMock.anyObject())).andReturn(RESPONSE_20RUNS);
        EasyMock.replay(mMockHttp);

        assertFalse(mProvider.needMoreMonkeyRuns(mRemoteBuild, mMockDevice));
        EasyMock.verify(mMockHttp);
    }
}
