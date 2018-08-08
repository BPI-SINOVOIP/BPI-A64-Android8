// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.util;

import com.google.android.tradefed.targetprep.GoogleAccountPreparer;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.io.File;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Unit tests for {@link GoogleAccountPreparer}.
 */
public class GoogleAccountUtilTest extends TestCase {

    private static final String SUCCESS_OUTPUT =
        "INSTRUMENTATION_RESULT: result=SUCCESS\n" +
        "INSTRUMENTATION_RESULT: accountType=com.google\n" +
        "INSTRUMENTATION_RESULT: authtoken=DQAAAI\n" +
        "INSTRUMENTATION_CODE: -1";

    private static final String FAILURE_OUTPUT =
        "INSTRUMENTATION_RESULT: errorCode=8\n" +
        "INSTRUMENTATION_CODE: -1";

    private static final String MISSING_OUTPUT = "";

    private ITestDevice mMockDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("serial").anyTimes();
    }

    /**
     * Test success case for
     * {@link GoogleAccountUtil#addAccountAttempt(ITestDevice, String, String, boolean, boolean)}.
     */
    public void testAddAccount() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).andReturn(
                SUCCESS_OUTPUT);
        EasyMock.replay(mMockDevice);
        assertTrue(GoogleAccountUtil.addAccountAttempt(mMockDevice, "account", "password",
                false, false));
    }

    /**
     * Test failure case for
     * {@link GoogleAccountUtil#addAccountAttempt(ITestDevice, String, String, boolean, boolean)}.
     */
    public void testAddAccount_fail() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).andReturn(
                FAILURE_OUTPUT);
        EasyMock.replay(mMockDevice);
        assertFalse(GoogleAccountUtil.addAccountAttempt(mMockDevice, "account", "password",
                false, false));
    }

    /**
     * {@link GoogleAccountUtil#addAccountAttempt(ITestDevice, String, String, boolean, boolean)}.
     * when no output is returned
     */
    public void testAddAccount_missing() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).andReturn(
                MISSING_OUTPUT);
        EasyMock.replay(mMockDevice);
        assertFalse(GoogleAccountUtil.addAccountAttempt(mMockDevice, "account", "password",
                false, false));
    }

    /**
     * {@link GoogleAccountUtil#installUtil(ITestDevice)}.
     * when the utility is already installed on the device
     */
    public void testInstallUtility_alreadyInstalled() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.installPackage((File)EasyMock.anyObject(),EasyMock.eq(false),
                (String[])EasyMock.anyObject())).andReturn(null);
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject())).andReturn(
                GoogleAccountUtil.ACCOUNT_PKG_NAME);
        EasyMock.replay(mMockDevice);
        assertTrue(GoogleAccountUtil.installUtil(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#installUtil(ITestDevice)}.
     * when the installation is successful
     */
    public void testInstallUtility_success() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.installPackage((File)EasyMock.anyObject(),EasyMock.eq(false)))
                .andReturn(null);
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn("not null").once();
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(GoogleAccountUtil.ACCOUNT_PKG_NAME).once();
        EasyMock.replay(mMockDevice);
        assertTrue(GoogleAccountUtil.installUtil(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#installUtil(ITestDevice)}.
     * when the installation of the utility is not successful
     */
    public void testInstallUtility_fails() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.installPackage((File)EasyMock.anyObject(),EasyMock.eq(false)))
                .andReturn("not null");
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn("").once();
        EasyMock.replay(mMockDevice);
        assertFalse(GoogleAccountUtil.installUtil(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#uninstallUtil(ITestDevice)}.
     * when the utility is correctly removed from the device
     */
    public void testUninstallUtility_success() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.uninstallPackage(GoogleAccountUtil.ACCOUNT_PKG_NAME))
                .andReturn(null);
        EasyMock.replay(mMockDevice);
        assertTrue(GoogleAccountUtil.uninstallUtil(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#uninstallUtil(ITestDevice)}.
     * when the utility is not correctly removed from the device
     */
    public void testUninstallUtility_fails() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.uninstallPackage(GoogleAccountUtil.ACCOUNT_PKG_NAME))
                .andReturn("not null");
        EasyMock.replay(mMockDevice);
        assertFalse(GoogleAccountUtil.uninstallUtil(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#isAccountUtilInstalled(ITestDevice)}.
     * when the utility is currently installed
     */
    public void testIsAccountUtil_currentlyInstalled() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(GoogleAccountUtil.ACCOUNT_PKG_NAME).once();
        EasyMock.replay(mMockDevice);
        assertTrue(GoogleAccountUtil.isAccountUtilInstalled(mMockDevice));
    }

    /**
     * {@link GoogleAccountUtil#isAccountUtilInstalled(ITestDevice)}.
     * when the utility is not currently installed
     */
    public void testIsAccountUtil_currentlyNotInstalled() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.executeShellCommand((String)EasyMock.anyObject()))
                .andReturn(null).once();
        EasyMock.replay(mMockDevice);
        assertFalse(GoogleAccountUtil.isAccountUtilInstalled(mMockDevice));
    }
}
