// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.performance;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

public class CaliperPerformanceTestTest extends TestCase {

    private static final String SIMPLE_BENCHMARK_RESULT =
            "RANDOM STUFF AHEAD\r\n" +
            "0% Scenario{vm=app_process, trial=0, benchmark=GetAllReturnsLargeAnnotation} " +
            "7263625.88 ns; σ=66697.31 ns @ 3 trials\n" +
            "6% Scenario{vm=app_process, trial=0, benchmark=GetAllReturnsMarkerAnnotation} " +
            "530013.31 ns; σ=3036.05 ns @ 3 trials\n" +
            "12% Scenario{vm=app_process, trial=0, benchmark=GetAllReturnsNoAnnotation} " +
            "111507.82 ns; σ=1352.67 ns @ 10 trials\n" +
            "RANDOM OTHER STUFF BELOW";

    private static final String MULTI_PARAM_BENCHMARK_RESULT =
            "RANDOM STUFF AHEAD\r\n" +
            "0% Scenario{vm=app_process, trial=0, benchmark=Get, bodySize=0, chunkSize=2048, " +
            "readBufferSize=1024, responseHeaders=MINIMAL, transferEncoding=FIXED_LENGTH} " +
            "1158564.67 ns; σ=18129.50 ns @ 10 trials\n" +
            "8% Scenario{vm=app_process, trial=0, benchmark=Get, bodySize=1024, chunkSize=2048, " +
            "readBufferSize=1024, responseHeaders=MINIMAL, transferEncoding=FIXED_LENGTH} " +
            "35081045.96 ns; σ=15862.47 ns @ 3 trials\n" +
            "17% Scenario{vm=app_process, trial=0, benchmark=Get, bodySize=1048576, " +
            "chunkSize=2048, readBufferSize=1024, responseHeaders=MINIMAL, " +
            "transferEncoding=FIXED_LENGTH} 44733683.30 ns; σ=12520960.96 ns @ 10 trials\n" +
            "RANDOM OTHER STUFF BELOW";

    public void testParseBenchmarkResults() {
        // make sure that our regex parsing for a simple benchmark is working as expected.
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        Map<String, String> metrics = new HashMap<String, String>();
        caliperTest.parseOutput("foo", SIMPLE_BENCHMARK_RESULT, metrics);
        assertEquals("Failed to parse all three metrics", 3, metrics.size());
        assertEquals("Failed to parse benchmark GetAllReturnsLargeAnnotation",
                "7263625.88", metrics.get("foo_GetAllReturnsLargeAnnotation"));
        assertEquals("Failed to parse benchmark GetAllReturnsLargeAnnotation",
                "530013.31", metrics.get("foo_GetAllReturnsMarkerAnnotation"));
        assertEquals("Failed to parse benchmark GetAllReturnsNoAnnotation",
                "111507.82", metrics.get("foo_GetAllReturnsNoAnnotation"));
    }

    public void testParseMultiParamBenchmarkResults() {
        // make sure that our regex parsing for a benchmark which uses params is working as
        // expected.
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        Map<String, String> metrics = new HashMap<String, String>();
        caliperTest.parseOutput("bar", MULTI_PARAM_BENCHMARK_RESULT, metrics);
        assertEquals("Failed to parse all three metrics", 3, metrics.size());
        assertEquals("Failed to parse benchmark Get_0_2048_1024_MINIMAL_FIXED_LENGTH",
                "1158564.67", metrics.get("bar_Get_0_2048_1024_MINIMAL_FIXED_LENGTH"));
        assertEquals("Failed to parse benchmark Get_1024_2048_1024_MINIMAL_FIXED_LENGTH",
                "35081045.96", metrics.get("bar_Get_1024_2048_1024_MINIMAL_FIXED_LENGTH"));
        assertEquals("Failed to parse benchmark Get_1048576_2048_1024_MINIMAL_FIXED_LENGTH",
                "44733683.30", metrics.get("bar_Get_1048576_2048_1024_MINIMAL_FIXED_LENGTH"));
    }

    public void testGenerateRuKey() {
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("foo_GetAllReturnsLargeAnnotation",
                caliperTest.generateKeyFromString("foo", "GetAllReturnsLargeAnnotation"));
    }

    public void testGenerateRuKeyEmptyPrefix() {
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("GetAllReturnsLargeAnnotation",
                caliperTest.generateKeyFromString("", "GetAllReturnsLargeAnnotation"));
    }

    public void testGenerateRuKeyForMultipleParams() {
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("bar_Get_0_2048_1024_MINIMAL_FIXED_LENGTH",
                caliperTest.generateKeyFromString("bar", "Get, bodySize=0, chunkSize=2048, " +
            "readBufferSize=1024, responseHeaders=MINIMAL, transferEncoding=FIXED_LENGTH"));
    }

    public void testGenerateRuKeyForMultipleParamsOutOfOrder() {
        // Test to make sure that if the args are out of order that it will still generate the same
        // key.
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("hah_Get_0_2048_1024_MINIMAL_FIXED_LENGTH",
                caliperTest.generateKeyFromString("hah", "Get, transferEncoding=FIXED_LENGTH, " +
            "responseHeaders=MINIMAL, readBufferSize=1024, bodySize=0, chunkSize=2048"));
    }

    public void testGetClassWithoutPackage() {
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("foo", caliperTest.getClassWithoutPackage("hello.world.foo"));
    }

    public void testGetClassWithoutPackageWithoutPackage() {
        CaliperPerformanceTest caliperTest = new CaliperPerformanceTest();
        assertEquals("foo", caliperTest.getClassWithoutPackage("foo"));
    }
}
