//Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.ITestInvocationListener;
import com.google.android.tradefed.util.BuildInfoBuilder;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RobolectricTestlogForwarder}. */
@RunWith(JUnit4.class)
public class RobolectricTestlogForwarderTest {

    private BuildInfoBuilder biBuilder;

    @Before
    public void setUp() {
        biBuilder = new BuildInfoBuilder();
    }

    @After
    public void tearDown() {
        biBuilder.cleanUp();
    }

    @Test
    public void testSuccessfulTestRecognized() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        RobolectricTestlogForwarder sut = buildSut(
            biBuilder.withLogContents("Running com.android.foo.BarTest\n"
                + "JUnit version 4.12\n"
                + ".W/Activity: Some random warning\n"
                + "...............\n"
                + "Time: 35.035\n"
                + "OK (245 tests)\n").build());

        sut.run(listener);

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    @Test
    public void testFailedTestRecognized() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        RobolectricTestlogForwarder sut = buildSut(
                biBuilder.withLogContents("Running com.android.foo.BarTest\n"
                        + "JUnit version 4.12\n"
                        + ".E/Activity: Some random error\n"
                        + "...............\n"
                        + "Time: 26.108\n"
                        + "There was 1 failure:\n"
                        + "1) StuffShouldWork(com.android.foo.BarTest)\n"
                        + "org.mockito.exceptions.verification.TooLittleActualInvocations:\n"
                        + "\tat com.android.foo.BarTest.StuffShouldWork(BarTest.java:10)\n"
                        + "\tat org.junit.runner.JUnitCore.runMain(JUnitCore.java:77)\n"
                        + "\tat org.junit.runner.JUnitCore.main(JUnitCore.java:36)\n"
                        + "\n"
                        + "FAILURES!!!\n"
                        + "Tests run: 5,  Failures: 1\n").build());

        sut.run(listener);

        verify(listener, times(1)).testStarted(any());
        // TODO(mikewallstedt): Parse exceptions from the log, and verify that here.
        verify(listener, times(1)).testFailed(any(), any());
        verify(listener, times(1)).testEnded(any(), any());
    }

    @Test
    public void testElapsedTimeCapturedForSuccessfulTest() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        RobolectricTestlogForwarder sut = buildSut(
                biBuilder.withLogContents("Running com.android.foo.BarTest\n"
                        + "Time: 35.035\n"
                        + "OK (245 tests)\n").build());

        sut.run(listener);

        verify(listener, times(1)).testRunEnded(eq(35035L), any());
    }

    @Test
    public void testElapsedTimeCapturedForFailedTest() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        RobolectricTestlogForwarder sut = buildSut(
                biBuilder.withLogContents("Running com.android.foo.BarTest\n"
                        + "JUnit version 4.12\n"
                        + "Time: 26.108\n"
                        + "There was 1 failure:\n"
                        + "1) StuffShouldWork(com.android.foo.BarTest)\n"
                        + "org.mockito.exceptions.verification.TooLittleActualInvocations:\n"
                        + "\tat com.android.foo.BarTest.StuffShouldWork(BarTest.java:10)\n"
                        + "\tat org.junit.runner.JUnitCore.runMain(JUnitCore.java:77)\n"
                        + "\tat org.junit.runner.JUnitCore.main(JUnitCore.java:36)\n"
                        + "\n"
                        + "FAILURES!!!\n"
                        + "Tests run: 5,  Failures: 1\n").build());

        sut.run(listener);

        verify(listener, times(1)).testRunEnded(eq(26108L), any());
    }

    @Test
    public void testStackTraceOfIoExceptionIsCaptured() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        RobolectricTestlogForwarder sut = buildSut(
                biBuilder.withLogFile(
                    "i_dont_exist", new File("/test/never/created"), "1").build());

        sut.run(listener);

        verify(listener, times(1)).testFailed(
                any(), contains("java.io.FileNotFoundException: /test/never/created"));
    }

    private RobolectricTestlogForwarder buildSut(IBuildInfo buildInfo) {
        RobolectricTestlogForwarder sut = new RobolectricTestlogForwarder();
        sut.setBuild(buildInfo);
        return sut;
    }

}
