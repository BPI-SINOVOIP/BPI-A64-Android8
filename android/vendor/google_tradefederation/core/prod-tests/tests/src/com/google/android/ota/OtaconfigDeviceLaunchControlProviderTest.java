// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.ota;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.android.tradefed.build.RemoteBuildInfo;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OtaconfigDeviceLaunchControlProviderTest {

    OtaconfigDeviceLaunchControlProvider mProvider;
    IncrementalOtaLaunchControlProvider mIncrProvider;
    IRunUtil mMockRunUtil;

    /**
     * Partial mock of IncrementalOtaLCP.
     */
    private class MockIncrementalOtaLaunchControlProvider
            extends IncrementalOtaLaunchControlProvider {
        @Override
        public RemoteBuildInfo queryForBuild() {
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        mProvider = new OtaconfigDeviceLaunchControlProvider();
        mIncrProvider = new MockIncrementalOtaLaunchControlProvider();

        mProvider.setBuildId("2345667");
        mProvider.setBuildFlavor("foo-userdebug");
        mProvider.setBranch("asdf");
        mProvider.setTestTag("foobar");
        mProvider.setBuildFlavor("fjfjfjf");

        mProvider.mIncrementalProvider = mIncrProvider;
        mProvider.setIncremental(true);
        mMockRunUtil = EasyMock.createNiceMock(IRunUtil.class);
        CommandResult result = new CommandResult(CommandStatus.SUCCESS);
        result.setStdout("123545");
        EasyMock.expect(mMockRunUtil.runTimedCmd(EasyMock.anyLong(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject(),
                (String) EasyMock.anyObject())).andReturn(result).anyTimes();

        mProvider.mRunUtil = mMockRunUtil;
    }

    @Test
    public void testArgsPropagated() throws Exception {
        EasyMock.replay(mMockRunUtil);
        mProvider.queryForBuild();
        EasyMock.verify(mMockRunUtil);
        Assert.assertEquals(mProvider.getBuildId(), mIncrProvider.getBuildId());
        Assert.assertEquals(mProvider.getBranch(), mIncrProvider.getBranch());
        Assert.assertEquals(mProvider.getBuildOs(), mIncrProvider.getBuildOs());
        Assert.assertEquals(mProvider.getBuildFlavor(), mIncrProvider.getBuildFlavor());
        Assert.assertEquals(mProvider.getTestTag(), mIncrProvider.getTestTag());
        Assert.assertEquals(mProvider.useOtaBuildAsSource(), mIncrProvider.useOtaBuildAsSource());
        Assert.assertEquals(mProvider.allowDowngrade(), mIncrProvider.allowDowngrade());
        Assert.assertEquals(mProvider.skipTests(), mIncrProvider.skipTests());
        Assert.assertEquals(mProvider.reportTargetBuild(), mIncrProvider.reportTargetBuild());
    }
}
