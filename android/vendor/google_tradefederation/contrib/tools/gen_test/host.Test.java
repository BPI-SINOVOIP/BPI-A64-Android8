package :package:;

import android.platform.test.annotations.Presubmit;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
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
     * A reference to the build info.
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
    }

    /**
     * TODO: Add JavaDoc
     */
    @Test
    public void bar() throws Exception {
        Assert.assertTrue(true);
    }
}
