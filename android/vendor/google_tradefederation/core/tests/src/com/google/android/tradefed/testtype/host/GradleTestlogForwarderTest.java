//Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.android.tradefed.util.BuildInfoBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

/** Unit tests for {@link GradleTestlogForwarder}. */
@RunWith(JUnit4.class)
public class GradleTestlogForwarderTest {

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
    public void testXml() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        GradleTestlogForwarder sut = buildSut(
            biBuilder.withLogContents(
                "TEST-SomeUtil", ".xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<testsuite name=\"com.android.SomeUtilTest\"\n"
                    + "    tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\">\n"
                    + "  <properties/>\n"
                    + "  <testcase name=\"testOne\" classname=\"com.android.SomeUtilTest\"/>\n"
                    + "  <system-out><![CDATA[]]></system-out>\n"
                    + "  <system-err><![CDATA[]]></system-err>\n"
                    + "</testsuite>\n"
            ).build());

        sut.run(listener);

        verify(listener, times(1)).testStarted(any());
        verify(listener, times(1)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    @Test
    public void testMalformedXml() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        GradleTestlogForwarder sut = buildSut(
            biBuilder.withLogContents(
                "TEST-SomeUtil", ".xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<testsuite name="
            ).build());

        sut.run(listener);

        // Test should fail without propagating the exception.
        verify(listener, times(0)).testStarted(any());
        verify(listener, times(0)).testEnded(any(), any());
        verify(listener, times(1)).testFailed(any(), contains("ParseException"));
    }

    @Test
    public void testXmlExtractedFromZip() throws Exception {
        File tempDir = FileUtil.createTempDir("gradle_test");
        File testLog = FileUtil.createTempFile("TEST-SomeUtil", ".xml", tempDir);
        FileUtil.writeToFile(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<testsuite name=\"com.android.SomeUtilTest\"\n"
            + "    tests=\"2\" skipped=\"0\" failures=\"0\" errors=\"0\">\n"
            + "  <properties/>\n"
            + "  <testcase name=\"testOne\" classname=\"com.android.SomeUtilTest\" time=\"0.1\"/>\n"
            + "  <testcase name=\"testTwo\" classname=\"com.android.SomeUtilTest\" time=\"1.2\"/>\n"
            + "  <system-out><![CDATA[]]></system-out>\n"
            + "  <system-err><![CDATA[]]></system-err>\n"
            + "</testsuite>\n", testLog);
        FileUtil.createTempFile("other", ".txt", tempDir);
        File zip = ZipUtil.createZip(tempDir);
        FileUtil.recursiveDelete(tempDir);

        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        GradleTestlogForwarder sut = buildSut(
            biBuilder.withLogFile("testZip", zip, "1").build());

        sut.run(listener);

        verify(listener, times(2)).testStarted(any());
        verify(listener, times(2)).testEnded(any(), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    private GradleTestlogForwarder buildSut(IBuildInfo buildInfo) {
        GradleTestlogForwarder sut = new GradleTestlogForwarder();
        sut.setBuild(buildInfo);
        return sut;
    }

}
