// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.memory;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.NameMangleListener;
import com.android.tradefed.testtype.InstrumentationTest;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryUsageTest extends InstrumentationTest {

    @Option(name = "apps", shortName = 'a', description = "List of apps to start on the device",
            importance = Importance.ALWAYS, mandatory = true)
    private Map<String, String> mApps;

    @Option(name = "procs", description = "List of persistent proc to observe",
            importance = Importance.ALWAYS)
    private Map<String, String> mProcs;

    public MemoryUsageTest() {
        mApps = new HashMap<String, String>();
        mProcs = new HashMap<String, String>();
    }

    @Override
    public void run(ITestInvocationListener listener)
            throws DeviceNotAvailableException {

        syncEmail();
        getDevice().reboot();

        setPackageName("com.android.tests.memoryusage");
        setRunnerName(".MemoryUsageInstrumentation");

        List<String> appsKey = new ArrayList<String>();
        for (Map.Entry<String, String> entry : mApps.entrySet()) {
            appsKey.add(entry.getKey() + "^" + entry.getValue());
        }

        addInstrumentationArg("apps", "\""
                + Joiner.on("|").join(appsKey) + "\"");
        appsKey.clear();
        if (mProcs != null) {
            for (Map.Entry<String, String> entry : mProcs.entrySet()) {
                appsKey.add(entry.getKey() + "^" + entry.getValue());
            }
            addInstrumentationArg("persistent", "\""
                    + Joiner.on("|").join(appsKey) + "\"");
        }
        RenameProxy proxy = new RenameProxy(listener);
        super.run(proxy);
    }

    private void syncEmail() {
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Override
    public boolean isResumable() {
        return false;
    }

    private class RenameProxy extends NameMangleListener {
        private static final String RESULT_KEY = "memory_usage";
        private static final String CLASS_NAME = "com.android.tests.memoryusage.MemoryUsageTest";

        public RenameProxy(ITestInvocationListener listener) {
            super(listener);
        }

        @Override
        protected TestIdentifier mangleTestId(TestIdentifier test) {
            return new TestIdentifier(test.getClassName().replace(CLASS_NAME, RESULT_KEY),
                    test.getTestName());
        }
    }
}
