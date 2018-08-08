//Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.Matchers.any;
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

/** Unit tests for {@link PythonUnitTestlogForwarder}. */
@RunWith(JUnit4.class)
public class PythonUnitTestlogForwarderTest {

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
        PythonUnitTestlogForwarder sut =
                buildSut(
                        biBuilder
                                .withLogContents(
                                        "test_a (a.T) ... ok\n"
                                                + "\n"
                                                + "-----------------------------------"
                                                + "-----------------------------------\n"
                                                + "Ran 1 test in 0.000s\n"
                                                + "\n"
                                                + "OK\n"
                                                + "\n")
                                .build());

        sut.run(listener);

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    @Test
    public void testFailedTestRecognized() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        PythonUnitTestlogForwarder sut =
                buildSut(
                        biBuilder
                                .withLogContents(
                                        "test_b (a.T) ... FAIL\n"
                                                + "\n"
                                                + "==================================="
                                                + "===================================\n"
                                                + "FAIL: test_b (a.T)\n"
                                                + "-----------------------------------"
                                                + "-----------------------------------\n"
                                                + "Traceback (most recent call last):\n"
                                                + "File \"a.py\", line 7, in test_b\n"
                                                + "self.fail()\n"
                                                + "AssertionError: None\n"
                                                + "\n"
                                                + "-----------------------------------"
                                                + "-----------------------------------\n"
                                                + "Ran 1 test in 0.000s\n"
                                                + "\n"
                                                + "FAILED (failures=1)\n")
                                .build());

        sut.run(listener);

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testFailed(any(), any());
        verify(listener, times(1)).testEnded(any(), any());
    }

    @Test(expected = RuntimeException.class)
    public void testStackTraceOfIoExceptionIsCaptured() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        PythonUnitTestlogForwarder sut =
                buildSut(
                        biBuilder
                                .withLogFile("i_dont_exist", new File("/test/never/created"), "1")
                                .build());

        sut.run(listener);
    }

    private PythonUnitTestlogForwarder buildSut(IBuildInfo buildInfo) {
        PythonUnitTestlogForwarder sut = new PythonUnitTestlogForwarder();
        sut.setBuild(buildInfo);
        return sut;
    }
}
