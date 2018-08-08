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

import com.google.android.utils.MonsoonController;

import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.File;

/** Unit tests for {@link MonsoonController}. */
@RunWith(JUnit4.class)
public class MonsoonControllerTest {

    private static final String MOCK_EXECUTABLE_PATH = "/mock/path";
    private static final String MOCK_SERIAL_PORT = "/mock/serial/port";

    private static final String MOCK_SERIAL_NUMBER = "1234";
    private static final String MOCK_SPURIUS_SERIAL_NUMBER = "9999";

    private IRunUtil mRunUtil;
    private MonsoonController mMonsoonController;

    @Before
    public void setUp() {
        mRunUtil = Mockito.mock(IRunUtil.class);
        mMonsoonController =
                new MonsoonController(MOCK_EXECUTABLE_PATH, MOCK_SERIAL_NUMBER) {
                    @Override
                    public IRunUtil getRunUtil() {
                        return mRunUtil;
                    }

                    @Override
                    public File[] getProbableSerialPorts() {
                        return new File[] {new File(MOCK_SERIAL_PORT)};
                    }
                };
    }

    @Test
    public void testGetValidSerialPort() {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        // mocking answer from the expected serial number.
        result.setStdout(String.format("serialNumber: %s", MOCK_SERIAL_NUMBER));

        Mockito.when(
                        mRunUtil.runTimedCmd(
                                Mockito.anyLong(),
                                Mockito.eq(MOCK_EXECUTABLE_PATH),
                                Mockito.eq("--device"),
                                Mockito.eq(MOCK_SERIAL_PORT),
                                Mockito.eq("--status")))
                .thenReturn(result);

        // Assert it retrieves the right serial port.
        Assert.assertEquals(MOCK_SERIAL_PORT, mMonsoonController.getMonsoonSerialPort());

        Mockito.verify(mRunUtil)
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(MOCK_EXECUTABLE_PATH),
                        Mockito.eq("--device"),
                        Mockito.eq(MOCK_SERIAL_PORT),
                        Mockito.eq("--status"));
    }

    @Test
    public void testGetInvalidSerialPort() {
        CommandResult result = new CommandResult();
        result.setStatus(CommandStatus.SUCCESS);
        // mocking answer from a different serial number.
        result.setStdout(String.format("serialNumber: %s", MOCK_SPURIUS_SERIAL_NUMBER));

        Mockito.when(
                        mRunUtil.runTimedCmd(
                                Mockito.anyLong(),
                                Mockito.eq(MOCK_EXECUTABLE_PATH),
                                Mockito.eq("--device"),
                                Mockito.anyString(),
                                Mockito.eq("--status")))
                .thenReturn(result);

        // serial port should be not found since all the answers were from unexpected monsoons'
        // serials.
        Assert.assertNull(mMonsoonController.getMonsoonSerialPort());

        Mockito.verify(mRunUtil)
                .runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq(MOCK_EXECUTABLE_PATH),
                        Mockito.eq("--device"),
                        Mockito.anyString(),
                        Mockito.eq("--status"));
    }

    @Test
    public void testKillsProcess() {
        String pid = "123";
        CommandResult monsoonStatusResult = new CommandResult();
        monsoonStatusResult.setStatus(CommandStatus.SUCCESS);
        // mocking answer from the expected serial number.
        monsoonStatusResult.setStdout(String.format("serialNumber: %s", MOCK_SERIAL_NUMBER));
        Mockito.when(
                        mRunUtil.runTimedCmd(
                                Mockito.anyLong(),
                                Mockito.eq(MOCK_EXECUTABLE_PATH),
                                Mockito.eq("--device"),
                                Mockito.anyString(),
                                Mockito.eq("--status")))
                .thenReturn(monsoonStatusResult);

        CommandResult lsofResult = new CommandResult();
        lsofResult.setStatus(CommandStatus.SUCCESS);
        lsofResult.setStdout(pid);
        Mockito.when(
                        mRunUtil.runTimedCmd(
                                Mockito.anyLong(),
                                Mockito.eq("lsof"),
                                Mockito.eq("-t"),
                                Mockito.eq(MOCK_SERIAL_PORT)))
                .thenReturn(lsofResult);
        mMonsoonController.freeResources();

        Mockito.verify(mRunUtil)
                .runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("kill"), Mockito.eq("-2"), Mockito.eq(pid));
    }
}
