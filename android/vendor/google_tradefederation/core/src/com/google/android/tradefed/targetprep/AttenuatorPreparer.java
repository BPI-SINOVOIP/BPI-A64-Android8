// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.util.AttenuatorUtil;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a preparer used to initialize the 2.4G wireless attenuator to certain value (0 normally)
 * before the test start. It uses AttenuatorUtil to connect and set the value provided in option
 * You can initialize more then one attenuator, by providing a list of ip addresses in cmd file
*/
@OptionClass(alias = "attenuator")
public class AttenuatorPreparer implements ITargetPreparer {
    @Option(name = "attenuator-value", description = "Initial value for the attenuator")
    private int mInitValue = 0;

    @Option(name = "disable", description = "Disable this preparer")
    private boolean mDisable = true;

    @Option(name = "attenuator-ip", description = "IP address for attenuators, it can be repeated "
            + "as a list")
    private List<String> mIpAddresses = new ArrayList<String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException {
        if (mDisable) {
            CLog.d("AttenuatorPreparer is disabled");
            return;
        }
        for (String ipAddress : mIpAddresses) {
            CLog.v("Initialize attenuator %s to %d at setup", mIpAddresses, mInitValue);
            AttenuatorUtil att = new AttenuatorUtil(ipAddress);
            att.setValue(mInitValue);
        }
    }
}

