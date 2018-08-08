// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link GoogleAccountPreparer}.
 */
public class GoogleAccountPreparerTest extends TestCase {

    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private GoogleAccountPreparer mPreparer;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("serial").anyTimes();
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mPreparer = new GoogleAccountPreparer();
    }

    /**
     * No account name provided should result in skipping preparer.
     * @throws Exception
     */
    public void testSkippingPreparer() throws Exception {
        try {
            mPreparer.setUp(mMockDevice, mMockBuildInfo);
            assertNull(mPreparer.getAccountName());
            assertNull(mPreparer.getAccountPassword());
        } catch (Exception e) {
            fail();
        }
    }

    /**
     * An account was provided but no password, should throw a TargetSetupError
     * @throws Exception
     */
    public void testAccountOnlyException() throws Exception {
        try {
            mPreparer.setAccount("test", null);
            mPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail();
        } catch (TargetSetupError tse) {
            assertNotNull(mPreparer.getAccountName());
            assertNull(mPreparer.getAccountPassword());
        }
    }
}
