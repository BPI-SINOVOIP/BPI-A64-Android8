// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.google.android.tradefed.util.GoogleAccountUtil;

import com.android.ddmlib.Log;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Functional tests for {@link GoogleAccountPreparer}.
 * <p/>
 * Assumes the AddAccount utility is installed already on device under test, and device has
 * 'adb root' enabled, and has network connectivity.
 */
public class GoogleAccountPreparerFuncTest extends DeviceTestCase {
    private static final String LOG_TAG = "GoogleAccountPreparerFuncTest";

    private static final String ACCOUNT_NAME = "tf-func-test@gmail.com";
    // its a bit distasteful to include a password here, but not dangerous since this is a dummy
    // test account
    private static final String ACCOUNT_PASSWORD = "thisisatest";

    /**
     * Test successful account creation.
     * @throws TargetSetupError
     */
    public void testAddAccount() throws DeviceNotAvailableException, TargetSetupError {
        Log.i(LOG_TAG, "testAddAccount");
        GoogleAccountUtil.installUtil(getDevice());
        GoogleAccountPreparer preparer = new GoogleAccountPreparer();
        preparer.setAccount(ACCOUNT_NAME, ACCOUNT_PASSWORD);
        preparer.setAccountSync(false);
        preparer.setWaitForCheckin(false);
        assertTrue(preparer.addAccount(getDevice()));
        assertTrue(GoogleAccountUtil.removeAllAccountAttempt(getDevice()));
    }

    /**
     * Test adding account which does not exist.
     *
     * @throws TargetSetupError
     */
    public void testAddAccount_missing() throws Exception {
        Log.i(LOG_TAG, "testAddAccount_missing");
        GoogleAccountPreparer preparer = new GoogleAccountPreparer();
        OptionSetter setter = new OptionSetter(preparer);
        setter.setOptionValue("max-attempts", "1");
        preparer.setAccount("idontexist@blah.com", "blah");
        preparer.setAccountSync(false);
        preparer.setWaitForCheckin(false);
        assertFalse(preparer.addAccount(getDevice()));
    }
}
