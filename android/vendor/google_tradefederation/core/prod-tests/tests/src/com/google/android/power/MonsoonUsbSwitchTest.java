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

import com.google.android.utils.IMonsoonController;
import com.google.android.utils.usb.switches.MonsoonUsbSwitch;

import com.android.tradefed.util.IRunUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Unit tests for {@link MonsoonUsbSwitch}. */
@RunWith(JUnit4.class)
public class MonsoonUsbSwitchTest {

    private IRunUtil mRunUtil;
    private MonsoonUsbSwitch mUsbSwitch;
    private IMonsoonController mMonsoonController;

    @Before
    public void setUp() {
        mRunUtil = Mockito.mock(IRunUtil.class);
        mMonsoonController = Mockito.mock(IMonsoonController.class);
        mUsbSwitch =
                new MonsoonUsbSwitch(mMonsoonController) {
                    @Override
                    public IRunUtil getRunUtil() {
                        return mRunUtil;
                    }
                };
    }

    @Test
    public void testPowerCycle() {
        mUsbSwitch.powerCycle();
        InOrder inorder = Mockito.inOrder(mMonsoonController);
        inorder.verify(mMonsoonController).disconnectUsb();
        inorder.verify(mMonsoonController).setMonsoonVoltage(0);
        inorder.verify(mMonsoonController).setMonsoonCurrent(Mockito.anyDouble());
        inorder.verify(mMonsoonController).setMonsoonStartCurrent(Mockito.anyDouble());
        inorder.verify(mMonsoonController).setMonsoonVoltage(Mockito.anyDouble());
        inorder.verify(mMonsoonController).connectUsb();
        Mockito.verifyNoMoreInteractions(mMonsoonController);
    }
}
