// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.common.collect.Sets;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/** Unit tests for {@link CreateUserPreparer}. */
public class CreateUserPreparerTest extends TestCase {

    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private CreateUserPreparer mPreparer;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mPreparer = new CreateUserPreparer();
    }

    public void testCreateSingleUser() throws DeviceNotAvailableException, TargetSetupError {
        mPreparer.setUsersToCreate(Sets.newHashSet("foo-user"));
        mPreparer.setUserToSwitchTo("");
        EasyMock.expect(mMockDevice.createUser("foo-user")).andReturn(10);

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mPreparer.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice, mMockBuildInfo);
    }

    public void testCreateMultipleUsers() throws DeviceNotAvailableException, TargetSetupError {
        mPreparer.setUsersToCreate(Sets.newHashSet("foo-user", "bar-user", "baz-user"));
        EasyMock.expect(mMockDevice.createUser("foo-user")).andReturn(10);
        EasyMock.expect(mMockDevice.createUser("bar-user")).andReturn(11);
        EasyMock.expect(mMockDevice.createUser("baz-user")).andReturn(12);

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mPreparer.setUp(mMockDevice, mMockBuildInfo);

        EasyMock.verify(mMockDevice, mMockBuildInfo);
    }

    public void testSwitchUser() throws DeviceNotAvailableException, TargetSetupError {
        final int userId = 10;
        mPreparer.setUsersToCreate(Sets.newHashSet("foo-user"));
        mPreparer.setUserToSwitchTo("foo-user");
        EasyMock.expect(mMockDevice.createUser("foo-user")).andReturn(userId);
        EasyMock.expect(mMockDevice.switchUser(EasyMock.eq(userId), EasyMock.anyLong()))
                .andReturn(true);
        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mPreparer.setUp(mMockDevice, mMockBuildInfo);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
    }

    public void testSwitchUser_timeout() throws DeviceNotAvailableException {
        final int userId = 10;
        mPreparer.setUsersToCreate(Sets.newHashSet("foo-user"));
        mPreparer.setUserToSwitchTo("foo-user");
        EasyMock.expect(mMockDevice.createUser("foo-user")).andReturn(userId);
        EasyMock.expect(mMockDevice.switchUser(EasyMock.eq(userId), EasyMock.anyLong()))
                .andReturn(false);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andStubReturn(null);
        EasyMock.replay(mMockDevice, mMockBuildInfo);
        try {
            mPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (TargetSetupError e) {
            // expected
        }
        EasyMock.verify(mMockDevice, mMockBuildInfo);
    }

    public void testSwitchUser_userNotCreated() throws DeviceNotAvailableException,
            TargetSetupError {
        final int userId = 10;
        mPreparer.setUsersToCreate(Sets.newHashSet("foo-user"));
        mPreparer.setUserToSwitchTo("foo-user2");
        EasyMock.expect(mMockDevice.createUser("foo-user")).andReturn(userId);
        EasyMock.replay(mMockDevice, mMockBuildInfo);
        try {
            mPreparer.setUp(mMockDevice, mMockBuildInfo);
            fail("Should have thrown an exception.");
        } catch (IllegalArgumentException e) {
            // expected
        }
        EasyMock.verify(mMockDevice, mMockBuildInfo);
    }
}
