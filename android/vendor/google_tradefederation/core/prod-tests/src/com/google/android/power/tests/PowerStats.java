package com.google.android.power.tests;

import com.google.android.power.tests.PowerTimestamp.PowerTimestampStatus;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PowerStats extends PowerAnalyzer {

    private static final String ITERATIONS_SEPARATOR = "#";
    protected final Map<String, String> mSchemaRUPair;
    protected final String mRuSuffix;
    protected final String mSchemaSuffix;
    private final List<PowerMeasurement> mPowerMeasurements;

    public PowerStats(ITestInvocationListener listener, ITestDevice testDevice,
            List<PowerMeasurement> powerMeasurements,
            Map<String, String> schemaRUPair, String ruSuffix, String
            schemaSuffix) {
        super(listener, testDevice);
        mSchemaRUPair = schemaRUPair;
        mPowerMeasurements = powerMeasurements;
        mRuSuffix = ruSuffix != null ? ruSuffix : "";
        mSchemaSuffix = schemaSuffix;
    }

    private List<PowerMetric> combineMeasurementsIntoMetrics(List<PowerMeasurement> measurements) {

        // initialize a metric for every defined schema.
        Map<String, PowerMetric> metricsMap = new HashMap<String, PowerMetric>();
        for (String schema : mSchemaRUPair.keySet()) {
            metricsMap.put(schema, new PowerMetric(schema));
        }

        CLog.d("Combining power measurements into power metrics.");
        for (PowerMeasurement measurement : measurements) {
            if (measurement.getStatus() != PowerTimestampStatus.VALID) {
                CLog.d("Ignoring invalid measurement: %s", measurement.toString());
                continue;
            }

            String sanitizedTag = measurement.getTag().split(ITERATIONS_SEPARATOR)[0];
            PowerMetric metric = metricsMap.get(sanitizedTag);
            if (metric == null) {
                CLog.e("No reporting unit was defined for the schema: %s", sanitizedTag);
                continue;
            }

            if (!metric.addMeasurement(measurement)) {
                CLog.d("Ignoring measurement %s because of duration inconsistency: measurement"
                                + " duration %d is too different from pivot duration %d.",
                        measurement.getTag(), measurement.getTimestamp().getDuration(), metric
                                .getPivotDuration());
                continue;
            }

            CLog.d(String.format("Updating metric: %s with measurement: %s", metric.getTag(),
                    measurement.getTag()));
            CLog.d(String.format("The test duration for %s is %d secs", measurement.getTag(),
                    measurement.getTimestamp().getDuration()));
            CLog.d(measurement.toString());
        }

        return new ArrayList<>(metricsMap.values());
    }

    protected abstract void postMetrics(List<PowerMetric> metrics, int decimalPlaces);

    public void run(int decimalPlaces) throws DeviceNotAvailableException {
        List<PowerMetric> metrics = combineMeasurementsIntoMetrics(mPowerMeasurements);
        postMetrics(metrics, decimalPlaces);
    }
}
