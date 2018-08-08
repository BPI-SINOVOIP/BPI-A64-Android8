//Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.testtype.host;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.ITestInvocationListener;
import com.google.android.tradefed.util.BuildInfoBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ArtTestlogForwarder}. */
@RunWith(JUnit4.class)
public class ArtTestlogForwarderTest {
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
    public void testAllSuccesses() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        ArtTestlogForwarder sut =
                buildSut(
                        biBuilder
                                .withLogContents(
                                        "Machine: vpeb2.mtv.corp.google.com\n"
                                                + "EXECUTE: ['runalarm', '-t', '14400', '/bin/bash', '-c', 'make -j49 test-art-host dist']\n"
                                                + "============================================\n"
                                                + "TARGET_PRODUCT=aosp_arm\n"
                                                + "TARGET_BUILD_VARIANT=eng\n"
                                                + "TARGET_2ND_ARCH=\n"
                                                + "============================================\n"
                                                + "[1/1] compile out/soong/.bootstrap/gotestmain/obj/gotestmain.a\n"
                                                + "[  PASSED  ] 2 tests.\n"
                                                + "[ 99% 8613/8615] build test-art-host-gtest-oatdump_test32\n"
                                                + "[==========] Running 14 tests from 1 test case.\n"
                                                + "[----------] Global test environment set-up.\n"
                                                + "[----------] 2 tests from OatDumpTest\n"
                                                + "[ RUN      ] OatDumpTest.TestImage\n"
                                                + "[       OK ] OatDumpTest.TestImage (4232 ms)\n"
                                                + "[ RUN      ] OatDumpTest.TestImageStatic\n"
                                                + "[       OK ] OatDumpTest.TestImageStatic (30000 ms)\n"
                                                + "[----------] 2 tests from OatDumpTest (34232 ms total)\n"
                                                + "\n"
                                                + "[----------] Global test environment tear-down\n"
                                                + "[==========] 2 tests from 1 test case ran. (34232 ms total)\n"
                                                + "[  PASSED  ] 2 tests.\n"
                                                + "[ 99% 8614/8615] build test-art-host-gtest\n"
                                                + "test-art-host-gtest COMPLETE\n"
                                                + "[100% 8615/8615] build test-art-host\n"
                                                + "test-art-host COMPLETE\n"
                                                + "PASSING TESTS\n"
                                                + "test-art-host-gtest-arch_test32\n"
                                                + "test-art-host-gtest-arch_test64\n"
                                                + "NO TESTS SKIPPED\n"
                                                + "NO TESTS FAILED\n"
                                                + "SKIPPED TESTS: \n"
                                                + "make: Leaving directory `/usr/local/google/buildbot/src/googleplex-android/master-art-host\n"
                                                + "[ 99% 1416/1419] host Prebuilt: libopenjdkjvmti_32 (out/host/linux-x86/obj32/lib/libopenjdkjvmti.so)\n"
                                                + "make: Leaving directory `/usr/local/google/buildbot/src/googleplex-android/master-art-host\n"
                                                + "['/usr/local/google/buildbot/src/googleplex-android/master-art-host/art/test/testrunner/testrunner.py', '-j', '110', '-b', '--host', '--verbose']\n"
                                                + "[ 99% 6418/6450 ] test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64 PASS\n"
                                                + "[ 99% 6419/6450 ] test-art-host-run-test-debug-prebuild-optimizing-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64 PASS\n"
                                                + "[ 99% 6420/6450 ] test-art-host-run-test-debug-prebuild-speed-profile-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc32 SKIP\n"
                                                + "test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-530-checker-lse64\n"
                                                + "test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-530-checker-lse32\n")
                                .build());

        sut.run(listener);

        TestIdentifier id1 = new TestIdentifier("gtest-arch_test32", "gtest-arch_test32");
        TestIdentifier id2 = new TestIdentifier("gtest-arch_test64", "gtest-arch_test64");
        verify(listener).testStarted(id1);
        verify(listener).testEnded(eq(id1), any());
        verify(listener).testStarted(id2);
        verify(listener).testEnded(eq(id2), any());
        verify(listener, times(0)).testFailed(any(), any());
    }

    @Test
    public void testAllStatus() throws Exception {
        ITestInvocationListener listener = mock(ITestInvocationListener.class);
        ArtTestlogForwarder sut =
                buildSut(
                        biBuilder
                                .withLogContents(
                                        "Machine: vpeb2.mtv.corp.google.com\n"
                                                + "PASSING TESTS\n"
                                                + "test-art-host-gtest-arch_test32\n"
                                                + "test-art-host-gtest-arch_test64\n"
                                                + "SKIPPED TESTS\n"
                                                + "test-art-host-gtest-dexdiag32\n"
                                                + "FAILING TESTS\n"
                                                + "test-art-host-gtest-dexlayout_test32\n"
                                                + "make: Leaving directory `/usr/local/google/buildbot/src/googleplex-android/master-art-host\n"
                                                + "[ 99% 1416/1419] host Prebuilt: libopenjdkjvmti_32 (out/host/linux-x86/obj32/lib/libopenjdkjvmti.so)\n"
                                                + "make: Leaving directory `/usr/local/google/buildbot/src/googleplex-android/master-art-host\n"
                                                + "['/usr/local/google/buildbot/src/googleplex-android/master-art-host/art/test/testrunner/testrunner.py', '-j', '110', '-b', '--host', '--verbose']\n"
                                                + "[ 99% 6418/6450 ] test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64 PASS\n"
                                                + "[ 99% 6419/6450 ] test-art-host-run-test-debug-prebuild-optimizing-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64 FAIL\n"
                                                + "[ 99% 6420/6450 ] test-art-host-run-test-debug-prebuild-speed-profile-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc32 SKIP\n"
                                                + "test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-530-checker-lse64\n"
                                                + "test-art-host-run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-530-checker-lse32\n")
                                .build());

        sut.run(listener);

        TestIdentifier passing1 = new TestIdentifier("gtest-arch_test32", "gtest-arch_test32");
        TestIdentifier passing2 = new TestIdentifier("gtest-arch_test64", "gtest-arch_test64");
        TestIdentifier passing3 =
                new TestIdentifier(
                        "run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64",
                        "run-test-debug-prebuild-interp-ac-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64");
        TestIdentifier failing1 =
                new TestIdentifier(
                        "run-test-debug-prebuild-optimizing-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64",
                        "run-test-debug-prebuild-optimizing-no-relocate-ntrace-cms-checkjni-picimage-npictest-ndebuggable-no-jvmti-072-precise-gc64");
        TestIdentifier failing2 =
                new TestIdentifier("gtest-dexlayout_test32", "gtest-dexlayout_test32");
        verify(listener).testStarted(passing1);
        verify(listener).testEnded(eq(passing1), any());
        verify(listener).testStarted(passing2);
        verify(listener).testEnded(eq(passing2), any());
        verify(listener).testStarted(passing3);
        verify(listener).testEnded(eq(passing3), any());
        verify(listener).testStarted(failing1);
        verify(listener, times(1)).testFailed(eq(failing1), any());
        verify(listener).testStarted(failing2);
        verify(listener, times(1)).testFailed(eq(failing2), any());
        verify(listener, times(2)).testFailed(any(), any()); // No other failures recorded.
        verify(listener).testEnded(eq(failing1), any());
    }

    private ArtTestlogForwarder buildSut(IBuildInfo buildInfo) {
        ArtTestlogForwarder sut = new ArtTestlogForwarder();
        sut.setBuild(buildInfo);
        return sut;
    }
}
