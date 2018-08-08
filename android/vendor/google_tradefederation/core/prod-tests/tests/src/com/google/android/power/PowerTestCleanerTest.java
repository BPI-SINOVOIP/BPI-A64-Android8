/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.power;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceState;
import com.android.tradefed.util.IRunUtil;
import com.google.android.power.tests.PowerTestCleaner;
import com.google.android.utils.usb.switches.IUsbSwitch;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.FileNotFoundException;

/** Unit tests for {@link PowerTestCleaner}. */
@RunWith(JUnit4.class)
public class PowerTestCleanerTest {

    private TestDeviceState mState = TestDeviceState.NOT_AVAILABLE;
    private boolean mIsAndroidRunning = false;
    private PowerTestCleaner mCleaner;
    private ITestDevice mDevice;
    private IUsbSwitch mUsbSwitch;
    private IBuildInfo mBuildInfo;
    private IRunUtil mRunUtil;

    @Before
    public void setUp() throws FileNotFoundException {
        mDevice = Mockito.mock(ITestDevice.class);
        Mockito.when(mDevice.getDeviceState())
                .thenAnswer(
                        new Answer<TestDeviceState>() {
                            @Override
                            public TestDeviceState answer(InvocationOnMock invocationOnMock)
                                    throws Throwable {
                                return mState;
                            }
                        });

        mRunUtil = Mockito.mock(IRunUtil.class);
        mUsbSwitch = Mockito.mock(IUsbSwitch.class);
        mBuildInfo = Mockito.mock(IBuildInfo.class);
        mCleaner =
                new PowerTestCleaner() {
                    @Override
                    protected IUsbSwitch getUsbSwitch() {
                        return mUsbSwitch;
                    }

                    @Override
                    protected IRunUtil getRunUtil() {
                        return mRunUtil;
                    }

                    @Override
                    protected boolean isAndroidRunning() {
                        return mIsAndroidRunning;
                    }
                };
    }

    @Test
    public void testCleanerDoesNothingIfDeviceIsOnline() throws DeviceNotAvailableException {
        makeDeviceOnline();
        Assert.assertEquals(TestDeviceState.ONLINE, mDevice.getDeviceState());

        mCleaner.tearDown(mDevice, mBuildInfo, null);

        // The USB switch shouldn't be touched since the device is already online.
        Mockito.verifyNoMoreInteractions(mUsbSwitch);
        Assert.assertEquals(TestDeviceState.ONLINE, mDevice.getDeviceState());
    }

    @Test
    public void testPerformsUsbConnect() throws DeviceNotAvailableException {
        Assert.assertEquals(TestDeviceState.NOT_AVAILABLE, mDevice.getDeviceState());
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocationOnMock)
                                    throws Throwable {
                                makeDeviceOnline();
                                return null;
                            }
                        })
                .when(mUsbSwitch)
                .connectUsb();

        mCleaner.tearDown(mDevice, mBuildInfo, null);
        Mockito.verify(mUsbSwitch).connectUsb();
        Assert.assertEquals(TestDeviceState.ONLINE, mDevice.getDeviceState());
    }

    @Test
    public void testPerformsRecoveryCycle() throws DeviceNotAvailableException {
        Mockito.doAnswer(
                        new Answer<Object>() {
                            @Override
                            public Object answer(InvocationOnMock invocationOnMock)
                                    throws Throwable {
                                // mocks the device becoming available after a power cycle.
                                makeDeviceOnline();
                                return null;
                            }
                        })
                .when(mUsbSwitch)
                .powerCycle();

        Assert.assertEquals(TestDeviceState.NOT_AVAILABLE, mDevice.getDeviceState());
        mCleaner.tearDown(mDevice, mBuildInfo, null);

        InOrder inOrder = Mockito.inOrder(mUsbSwitch);
        inOrder.verify(mUsbSwitch).connectUsb();
        inOrder.verify(mUsbSwitch).disconnectUsb();
        inOrder.verify(mUsbSwitch).freeResources();
        inOrder.verify(mUsbSwitch).powerCycle();
        inOrder.verify(mUsbSwitch).connectUsb();
        inOrder.verifyNoMoreInteractions();
        Assert.assertEquals(TestDeviceState.ONLINE, mDevice.getDeviceState());
    }

    private void makeDeviceOnline() {
        mState = TestDeviceState.ONLINE;
        mIsAndroidRunning = true;
    }
}
