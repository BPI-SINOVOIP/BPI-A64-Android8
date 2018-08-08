//Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.ITestInvocationListener;

import com.google.android.tradefed.util.BuildInfoBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GTestTestlogForwarder}. */
@RunWith(JUnit4.class)
public class GTestTestlogForwarderTest {

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
    public void testTestcasePassed() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        String xmlContent =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<testsuites tests=\"1\" failures=\"0\" disabled=\"0\" errors=\"0\" "
                        + " timestamp=\"2017-03-29T12:37:04\" time=\"4.537\" name=\"AllTests\">\n"
                        + "  <testsuite name=\"TestSuite\" tests=\"1\" failures=\"0\" "
                        + "   disabled=\"0\" errors=\"0\" time=\"0.054\">\n"
                        + "    <testcase name=\"TestCase\" status=\"run\" time=\"0\" "
                        + "     classname=\"ClassName\" />\n"
                        + "  </testsuite>\n"
                        + "</testsuites>\n";
        GTestTestlogForwarder sut =
                buildSut(biBuilder.withLogContents("TEST-SomeUtil", ".xml", xmlContent).build());

        sut.run(listener);

        verify(listener, times(1)).testRunStarted(any(), anyInt());
        verify(listener, times(0)).testRunFailed(any());

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    @Test
    public void testTestcaseFailed() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        String xmlContent =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<testsuites tests=\"1\" failures=\"1\" disabled=\"0\" errors=\"0\" "
                        + " timestamp=\"2017-03-29T12:37:04\" time=\"4.537\" name=\"AllTests\">\n"
                        + "  <testsuite name=\"TestSuite\" tests=\"1\" failures=\"1\" "
                        + "   disabled=\"0\" errors=\"0\" time=\"0.054\">\n"
                        + "    <testcase name=\"TestCase\" status=\"run\" time=\"0\" "
                        + "     classname=\"ClassName\" >\n"
                        + "      <failure message=\"Fake failure message\" type=\"\">\n"
                        + "        <![CDATA[Fake failure xml-cdata]]>\n"
                        + "      </failure>\n"
                        + "    </testcase>\n"
                        + "  </testsuite>\n"
                        + "</testsuites>\n";
        GTestTestlogForwarder sut =
                buildSut(biBuilder.withLogContents("TEST-SomeUtil", ".xml", xmlContent).build());

        sut.run(listener);

        verify(listener, times(1)).testRunStarted(any(), anyInt());
        verify(listener, times(0)).testRunFailed(any());

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testEnded(any(), any());
        verify(listener, times(1)).testFailed(any(), any());
    }

    @Test
    public void testMalformedXml() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        String xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites tests=";
        GTestTestlogForwarder sut =
                buildSut(biBuilder.withLogContents("TEST-SomeUtil", ".xml", xmlContent).build());

        sut.run(listener);

        verify(listener, times(1)).testRunStarted(any(), anyInt());
        verify(listener, times(1)).testRunFailed(any());

        verify(listener, times(0)).testStarted(any());
        verify(listener, times(0)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    private GTestTestlogForwarder buildSut(IBuildInfo buildInfo) {
        GTestTestlogForwarder sut = new GTestTestlogForwarder();
        sut.setBuild(buildInfo);
        return sut;
    }
}
