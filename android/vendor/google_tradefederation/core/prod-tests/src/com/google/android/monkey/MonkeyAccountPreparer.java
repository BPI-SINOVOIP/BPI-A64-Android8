// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.google.android.tradefed.targetprep.GoogleAccountPreparer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Random;
import java.util.Set;

@OptionClass(alias = "monkey-account")
public class MonkeyAccountPreparer implements ITargetPreparer {
    private final GoogleAccountPreparer mGooglePreparer;

    @Option(name="account-name", description="A format-string for the full name of the Google " +
            "account to configure; should contain exactly one format field to receive a random " +
            "between account-range-start and account-range-end")
    private String mAccountFormat = null;

    @Option(name="account-range-start", description="The start of the range of numbers for the " +
            "random int in the account-name")
    private int mAccountRangeStart = 0;

    @Option(name="account-range-end", description="The end of the range of numbers (exclusive) " +
            "for the random int in the account-name")
    private int mAccountRangeEnd = 100;

    @Option(name="account-blacklist", description="A blacklisted account in the range of " +
            "numbers for the random in in the account-name")
    private Set<Integer> mAccountBlacklist = new HashSet<>();

    @Option(name="account-password", description="the password of the Google account (specified " +
            "in account-name) to configure")
    private String mAccountPassword = null;

    @Option(name="account-sync", description="enable sync on Google account")
    private boolean mAccountSync = false;

    @Option(name = "sync-then-off", description =
            "after adding account, allow for sync then turn sync off, in secs")
    private long mSyncThenOff = 0;

    @Option(name = "datetime-wait-timeout",
            description = "Timeout in ms to wait for correct datetime on device.")
    private long mDatetimeWaitTimeout = -1;

    @Option(name = "force-datetime", description = "Force sync host datetime to device if device "
            + "fails to set datetime automatically.")
    private boolean mForceDatetime = false;

    @Option(name = "disable", description = "Disable the monkey account preparer.")
    private boolean mDisable = false;

    public MonkeyAccountPreparer() {
        mGooglePreparer = getAccountPreparer();
    }

    /**
     * Constructor exposed for unit testing
     */
    MonkeyAccountPreparer(String name, String password, boolean sync) {
        this();
        mAccountFormat = name;
        mAccountPassword = password;
        mAccountSync = sync;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            DeviceNotAvailableException, BuildError {
        if (mDisable) {
            return;
        }

        String account;
        try {
            account = getAccount(mAccountFormat, mAccountRangeStart, mAccountRangeEnd,
                    mAccountBlacklist);
        } catch (IllegalFormatException e) {
            throw new TargetSetupError(e.getMessage(), device.getDeviceDescriptor());
        }

        mGooglePreparer.setAccount(account, mAccountPassword);
        mGooglePreparer.setAccountSync(mAccountSync);
        mGooglePreparer.setSyncThenOff(mSyncThenOff);
        if (mDatetimeWaitTimeout != -1) {
            mGooglePreparer.setDatetimeWaitTimeout(mDatetimeWaitTimeout);
        }
        mGooglePreparer.setForceDatetime(mForceDatetime);
        mGooglePreparer.setUp(device, buildInfo);
    }

    /**
     * Get a random account from the format and skip over blacklisted entries. Exposed for unit
     * testing.
     */
    String getAccount(String format, int startInclusive, int endExclusive,
            Set<Integer> blacklist) throws IllegalArgumentException {
        if (startInclusive >= endExclusive) {
            throw new IllegalArgumentException("Start of range must be less than end");
        }

        List<Integer> accountNumbers = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            if (!blacklist.contains(i)) {
                accountNumbers.add(i);
            }
        }

        if (accountNumbers.isEmpty()) {
            throw new IllegalArgumentException("No accounts can be added due to blacklist");
        }

        int index = (new Random()).nextInt(accountNumbers.size());

        return String.format(format, accountNumbers.get(index));
    }

    /**
     * Create a GoogleAccountPreparer to bottom out into
     * <p />
     * Exposed for unit testing.
     */
    GoogleAccountPreparer getAccountPreparer() {
        return new GoogleAccountPreparer();
    }
}

