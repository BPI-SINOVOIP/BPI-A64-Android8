package :package:;

import android.platform.test.annotations.Presubmit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TODO: Add JavaDoc
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class :test: implements IAbiReceiver, IBuildReceiver, IDeviceTest {

    public static final String TAG = :test:.class.getSimpleName();

    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = ":app-package:";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = ":app-activity:";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /**
     * A reference to build info.
     */
    private IBuildInfo mBuildInfo;

    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    /**
     * A reference to the ABI under test.
     */
    private IAbi mAbi;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * TODO: Add JavaDoc
     * This test runs on presubmit
     */
    @Presubmit
    @Test
    public void foo() throws Exception {
        Assert.assertNotNull(mDevice);
        // Start the APK and wait for it to complete.
        mDevice.executeShellCommand(START_COMMAND);
    }

    /**
     * TODO: Add JavaDoc
     */
    @Test
    public void bar() throws Exception {
        Assert.assertTrue(true);
    }
}
