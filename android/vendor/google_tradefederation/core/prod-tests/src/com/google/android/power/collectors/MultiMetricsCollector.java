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

package com.google.android.power.collectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link IMetricsCollector} that allows concurrent collectors to be used at once.
 */
public class MultiMetricsCollector implements IMetricsCollector {
    List<IMetricsCollector> mCollectors = new ArrayList<>();

    public void add(IMetricsCollector collector) {
        if (mCollectors.contains(collector)) {
            return;
        }

        mCollectors.add(collector);
    }

    public List<IMetricsCollector> getCollectors() {
        return mCollectors;
    }

    @Override
    public void start() {
        for (IMetricsCollector collector : mCollectors) {
            collector.start();
        }
    }

    @Override
    public void stop() {
        for (IMetricsCollector collector : mCollectors) {
            collector.stop();
        }
    }

    @Override
    public Map<String, Double> getMetrics() {
        Map<String, Double> allMetrics = new HashMap<>();
        Map<String, IMetricsCollector> origins = new HashMap<>();

        StringBuilder failures = new StringBuilder();
        for (IMetricsCollector collector : mCollectors) {
            Map<String, Double> metrics = collector.getMetrics();
            for (String key : metrics.keySet()) {
                if (allMetrics.containsKey(key)) {
                    String message =
                            String.format(
                                    "conflicting metric with name %s reported by %s was also reported by %s",
                                    key,
                                    collector.getClass().toString(),
                                    origins.get(key).getClass().toString());
                    failures.append(message);
                    failures.append(System.lineSeparator());
                    continue;
                }

                allMetrics.put(key, metrics.get(key));
                origins.put(key, collector);
            }
        }

        if (failures.length() > 0) {
            throw new IllegalStateException(failures.toString());
        }

        return allMetrics;
    }

    @Override
    public List<PowerTestLog> getLogs() {
        List<PowerTestLog> allLogs = new ArrayList<>();
        for (IMetricsCollector collector : mCollectors) {
            allLogs.addAll(collector.getLogs());
        }
        return allLogs;
    }
}
