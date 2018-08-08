
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UnbundledAppSetupTest {
    private IInvocationContext mContext;

    @Before
    public void setUp() {
        mContext = new InvocationContext();
    }

    @Test
    public void validation_failsOnEmptyContext() {
        UnbundledAppSetup ub = new UnbundledAppSetup();
        try {
            ub.setUp(mContext);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } catch (BuildError e) {
            Assert.fail("Unexpected BuildError.");
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Unexpected DeviceNotAvailableException");
        }
    }

    @Test
    public void validation_failsOnMissingDeviceLabel() {
        UnbundledAppSetup ub = new UnbundledAppSetup();
        try {
            mContext.addDeviceBuildInfo("random_invalid_device_label", new BuildInfo());
            mContext.addDeviceBuildInfo("ub_app", new BuildInfo());
            ub.setUp(mContext);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } catch (BuildError e) {
            Assert.fail("Unexpected BuildError.");
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Unexpected DeviceNotAvailableException");
        }
    }

    @Test
    public void validation_failsOnMissingUbAppLabel() {
        UnbundledAppSetup ub = new UnbundledAppSetup();
        try {
            mContext.addDeviceBuildInfo("device", new BuildInfo());
            mContext.addDeviceBuildInfo("invalid_app", new BuildInfo());
            ub.setUp(mContext);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } catch (BuildError e) {
            Assert.fail("Unexpected BuildError.");
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Unexpected DeviceNotAvailableException");
        }
    }

    @Test
    public void validation_failsOnInvalidNumberOfBuilds() {
        UnbundledAppSetup ub = new UnbundledAppSetup();
        try {
            mContext.addDeviceBuildInfo("device", new BuildInfo());
            mContext.addDeviceBuildInfo("ub_app", new BuildInfo());
            mContext.addDeviceBuildInfo("another_buildInfo", new BuildInfo());
            ub.setUp(mContext);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } catch (BuildError e) {
            Assert.fail("Unexpected BuildError.");
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Unexpected DeviceNotAvailableException");
        }
    }

    @Test
    public void validation_failsOnInvalidAppBuildInfo() {
        UnbundledAppSetup ub = new UnbundledAppSetup();
        try {
            mContext.addDeviceBuildInfo("device", new BuildInfo());
            mContext.addDeviceBuildInfo("ub_app", new BuildInfo());
            ub.setUp(mContext);
            Assert.fail("Expected TargetSetupError");
        } catch (TargetSetupError e) {
            // expected.
        } catch (BuildError e) {
            Assert.fail("Unexpected BuildError.");
        } catch (DeviceNotAvailableException e) {
            Assert.fail("Unexpected DeviceNotAvailableException");
        }
    }
}
