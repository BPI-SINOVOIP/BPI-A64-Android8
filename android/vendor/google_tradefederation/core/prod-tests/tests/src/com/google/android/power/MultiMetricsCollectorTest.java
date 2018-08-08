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

import com.google.android.power.collectors.IMetricsCollector;
import com.google.android.power.collectors.MultiMetricsCollector;
import com.google.android.power.collectors.PowerTestLog;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link MultiMetricsCollector}. */
@RunWith(JUnit4.class)
public class MultiMetricsCollectorTest {

    @Test
    public void testTheSameCollectorIsNotAddedTwice() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector1);

        Assert.assertEquals(multiMetricsCollector.getCollectors().size(), 1);
    }

    @Test
    public void testJoinsListsOfLogs() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);
        IMetricsCollector collector2 = Mockito.mock(IMetricsCollector.class);

        Mockito.when(collector1.getLogs())
                .thenReturn(Arrays.asList(new PowerTestLog(null, null, null)));
        Mockito.when(collector2.getLogs())
                .thenReturn(
                        Arrays.asList(
                                new PowerTestLog(null, null, null),
                                new PowerTestLog(null, null, null)));

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector2);

        Assert.assertEquals(multiMetricsCollector.getLogs().size(), 3);
    }

    @Test
    public void testJoinsMetrics() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);
        IMetricsCollector collector2 = Mockito.mock(IMetricsCollector.class);

        Map<String, Double> metrics1 = new HashMap<>();
        metrics1.put("METRIC1", 1d);
        metrics1.put("METRIC2", 2d);

        Map<String, Double> metrics2 = new HashMap<>();
        metrics2.put("METRIC3", 3d);

        Mockito.when(collector1.getMetrics()).thenReturn(metrics1);
        Mockito.when(collector2.getMetrics()).thenReturn(metrics2);

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector2);

        Assert.assertEquals(multiMetricsCollector.getMetrics().size(), 3);
    }

    @Test
    public void testFailureIfThereAreMetricsWithConflictingNames() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);
        IMetricsCollector collector2 = Mockito.mock(IMetricsCollector.class);

        Map<String, Double> metrics1 = new HashMap<>();
        metrics1.put("CONFLICTING", 1d);
        metrics1.put("NON_CONFLICTING", 2d);

        Map<String, Double> metrics2 = new HashMap<>();
        metrics2.put("CONFLICTING", 3d);
        metrics2.put("NON_CONFLICTING", 4d);

        Mockito.when(collector1.getMetrics()).thenReturn(metrics1);
        Mockito.when(collector2.getMetrics()).thenReturn(metrics2);

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector2);

        try {
            multiMetricsCollector.getMetrics();
            Assert.fail("There were conflicting metrics and a failure should have been thrown.");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testCallsAllStarts() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);
        IMetricsCollector collector2 = Mockito.mock(IMetricsCollector.class);

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector2);

        multiMetricsCollector.start();

        Mockito.verify(collector1).start();
        Mockito.verify(collector2).start();
        Mockito.verifyNoMoreInteractions(collector1, collector2);
    }

    @Test
    public void testCallsAllStops() {
        MultiMetricsCollector multiMetricsCollector = new MultiMetricsCollector();
        IMetricsCollector collector1 = Mockito.mock(IMetricsCollector.class);
        IMetricsCollector collector2 = Mockito.mock(IMetricsCollector.class);

        multiMetricsCollector.add(collector1);
        multiMetricsCollector.add(collector2);

        multiMetricsCollector.stop();

        Mockito.verify(collector1).stop();
        Mockito.verify(collector2).stop();
        Mockito.verifyNoMoreInteractions(collector1, collector2);
    }
}
