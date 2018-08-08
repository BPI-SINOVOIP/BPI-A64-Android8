package com.google.android.clockwork.targetprep;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;

import com.android.tradefed.util.RunUtil;
import com.google.android.clockwork.PairingUtils;

import java.util.ArrayList;
import java.util.List;

@OptionClass(alias = "md-pairing")
public class PairingMultiTargetPreparer extends ClockworkMultiTargetPreparer {

    @Option(name = "cloud-sync", description = "Enable cloudsync, default is disable")
    protected boolean mCloudsync = false;

    @Option(name = "cw-factory-default", description = "Wipe watch data and reset to default")
    protected boolean mFactoryDefault = false;

    @Option(name = "disable", description = "Disable this preparer, default is enable")
    protected boolean mDisable = false;

    @Option(name = "post-pair-commands",
            description = "Shell command(s) for after pairing, each line should be in the form of:"
                    + "<device name>:<command>"
    )
    private List<String> mPostPairCommands = new ArrayList<>();

    @Option(
        name = "post-pair-sleep",
        description = "Extra time (in seconds) to sleep after pairing."
    )
    private int mPostPairSleep = 0;

    @Override
    public void setUp(IInvocationContext context) throws DeviceNotAvailableException {
        super.setUp(context);

        PairingUtils mUtils = new PairingUtils();
        try {
            if (mDisable) {
                CLog.i("Skipping pairing devices.");
                return;
            }
            if (mUtils.pair(mWatchDevice, mCompanionDevice, mCloudsync, mFactoryDefault, false, 0)
                    < 0) {
                throw new RuntimeException("Pairing failed.");
            }
            for (String cmd : mPostPairCommands) {
                String[] tuple = cmd.split(":");
                String deviceName = tuple[0];
                String command = tuple[1];

                ITestDevice device = mContext.getDevice(deviceName);
                CLog.i("Executing '%s' on device %s: %s", command, deviceName,
                        device.getSerialNumber());
                String output = device.executeShellCommand(command);
                CLog.i("Result of command '%s': %s", command, output);
            }
            CLog.i("Sleep for %d seconds.", mPostPairSleep);
            RunUtil.getDefault().sleep(mPostPairSleep * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
