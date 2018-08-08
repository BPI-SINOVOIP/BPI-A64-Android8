// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.clockwork;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link WeedTestRunner} */
@RunWith(JUnit4.class)
public class WeedTestRunnerTest {

    private static final String TEST_JAR = "Test_Jar";
    private static final String ADB_PATH = "adb";
    private static final String FAKE_SERIAL_WATCH = "FAKE_WATCH";
    private static final String FAKE_SERIAL_COMPANION = "FAKE_COMPANION";

    private ITestDevice mMockWatchDevice;
    private ITestDevice mMockCompanionDevice;

    @Before
    public void setUp() throws Exception {
        mMockWatchDevice = EasyMock.createMock(ITestDevice.class);
        mMockCompanionDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockWatchDevice.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard,watch");
        EasyMock.expect(mMockCompanionDevice.getProperty("ro.build.characteristics"))
                .andReturn("nosdcard");
        EasyMock.expect(mMockWatchDevice.getSerialNumber()).andReturn(FAKE_SERIAL_WATCH);
        EasyMock.expect(mMockCompanionDevice.getSerialNumber()).andReturn(FAKE_SERIAL_COMPANION);
    }

    @Test
    public void test_NoExtraArgs() throws Exception {
        WeedTestRunner weedTestRunner = new WeedTestRunner();
        OptionSetter setter = new OptionSetter(weedTestRunner);
        setter.setOptionValue("test-jar-path", TEST_JAR);
        setter.setOptionValue("adb-path", ADB_PATH);

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch", mMockWatchDevice);
        context.addDeviceBuildInfo("watch", null);
        context.addAllocatedDevice("companion", mMockCompanionDevice);
        context.addDeviceBuildInfo("companion", null);

        EasyMock.replay(mMockWatchDevice, mMockCompanionDevice);
        weedTestRunner.setDeviceInfos(context.getDeviceBuildMap());
        List<String> ret = weedTestRunner.constructCommand(TEST_JAR);
        EasyMock.verify(mMockWatchDevice, mMockCompanionDevice);

        String correctString =
                String.format(
                        "java -jar %s --watchId %s --androidPhoneId %s --adbPath %s",
                        TEST_JAR, FAKE_SERIAL_WATCH, FAKE_SERIAL_COMPANION, ADB_PATH);
        List<String> correct = Arrays.asList(correctString.split(" "));
        Assert.assertEquals(correct, ret);
    }

    @Test
    public void test_ExtraArgs() throws Exception {
        WeedTestRunner weedTestRunner = new WeedTestRunner();
        OptionSetter setter = new OptionSetter(weedTestRunner);
        setter.setOptionValue("test-jar-path", TEST_JAR);
        setter.setOptionValue("adb-path", ADB_PATH);
        setter.setOptionValue("extra-args", "keyonly", "");
        setter.setOptionValue("extra-args", "key", "value");

        IInvocationContext context = new InvocationContext();
        context.addAllocatedDevice("watch", mMockWatchDevice);
        context.addDeviceBuildInfo("watch", null);
        context.addAllocatedDevice("companion", mMockCompanionDevice);
        context.addDeviceBuildInfo("companion", null);

        EasyMock.replay(mMockWatchDevice, mMockCompanionDevice);
        weedTestRunner.setDeviceInfos(context.getDeviceBuildMap());
        List<String> ret = weedTestRunner.constructCommand(TEST_JAR);

        String correctString =
                String.format(
                        "java -jar %s --watchId %s --androidPhoneId %s --adbPath %s --keyonly  --key value",
                        TEST_JAR, FAKE_SERIAL_WATCH, FAKE_SERIAL_COMPANION, ADB_PATH);
        List<String> correct = Arrays.asList(correctString.split(" "));
        Assert.assertEquals(correct, ret);
    }
}
