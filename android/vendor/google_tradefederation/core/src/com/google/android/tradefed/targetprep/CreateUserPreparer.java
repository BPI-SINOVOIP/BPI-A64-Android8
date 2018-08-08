// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@link ITargetPreparer} that creates one or more users on the device.
 */
@OptionClass(alias = "create-user")
public class CreateUserPreparer implements ITargetPreparer {

    /** The users that we still need to create, or an empty set if no user creation is pending. */
    @Option(name = "user", description =
            "The name of a user to create on the device. Can be repeated.")
    private Set<String> mUsersToCreate = new HashSet<>();

    /** The users that this class keeps track of, mapped from their user name. */
    private Map<String, Integer> mUsers = new HashMap<>();

    @Option(name = "switch", description = "The name of the user to switch to.")
    private String mUserToSwitchTo;

    @Option(name = "switch-user-timeout", description = "The timeout allowed for the user to "
            + "actually switch after switch-user.", isTimeVal = true)
    private long mSwitchUserTimeout = 10 * 1000;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, DeviceNotAvailableException {

        for (String user : mUsersToCreate) {
            // Remember the resulting user ID for each user we create.
            mUsers.put(user, device.createUser(user));
        }
        mUsersToCreate = new HashSet<>();
        if (mUserToSwitchTo != null && !mUserToSwitchTo.isEmpty()) {
            if (!mUsers.containsKey(mUserToSwitchTo)) {
                throw new IllegalArgumentException("Request to switch to user '"
                        + mUserToSwitchTo + "' but not to create it.");
            }
            boolean res = device.switchUser(mUsers.get(mUserToSwitchTo), mSwitchUserTimeout);
            if (!res) {
                throw new TargetSetupError(String.format("user '%s' did not switch in the given "
                        + "timeout.", mUserToSwitchTo), device.getDeviceDescriptor());
            }
        }
    }

    /** Sets the users to create. Exposed for testing. */
    @VisibleForTesting
    public void setUsersToCreate(Set<String> users) {
        mUsersToCreate = users;
    }

    /**
     * Sets the user to switch to. Exposed for testing.
     */
    @VisibleForTesting
    public void setUserToSwitchTo(String user) {
        mUserToSwitchTo = user;
    }
}
