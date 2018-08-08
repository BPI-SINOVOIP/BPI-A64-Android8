// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.monkey;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.google.android.tradefed.targetprep.GoogleAccountPreparer;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link MonkeyAccountPreparer}
 */
public class MonkeyAccountPreparerTest extends TestCase {
    private MonkeyAccountPreparer mPreparer = null;
    @SuppressWarnings("unused")
    private GoogleAccountPreparer mGooglePreparer = null;

    private String mPrepAccount = null;
    private String mPrepPasswd = null;
    private boolean mSetupCalled = false;

    /**
     * A simple test to verify that MonkeyAccountPreparer substitutes values as expected
     */
    public void testSimple() throws Exception {
        String accountName = "test.account.%02d@gmail.com";
        String accountPasswd = "a password";

        // Create a mock preparer that stores the values it receives
        final GoogleAccountPreparer mockGooglePreparer = new GoogleAccountPreparer() {
            @Override
            public void setAccount(String account, String passwd) {
                mPrepAccount = account;
                mPrepPasswd = passwd;
            }

            @Override
            public void setUp(ITestDevice device, IBuildInfo info) {
                mSetupCalled = true;
            }
        };

        // And a Monkey preparer that calls the mock Google preparer
        mPreparer = new MonkeyAccountPreparer(accountName, accountPasswd, false) {
            @Override
            GoogleAccountPreparer getAccountPreparer() {
                return mockGooglePreparer;
            }
        };

        // Actually run the preparer
        mPreparer.setUp(null, null);

        // And verify results
        String acctPat = "test\\.account\\.\\d{2}@gmail\\.com";
        assertTrue(String.format("Account name '%s' doesn't have expected format '%s'",
                mPrepAccount, acctPat), mPrepAccount.matches(acctPat));
        assertEquals(accountPasswd, mPrepPasswd);
        assertTrue(mSetupCalled);
    }

    /**
     * Test that account can be chosen and skip over blacklist entries.
     */
    public void testPickAccount() {
        mPreparer = new MonkeyAccountPreparer();

        assertEquals("0", mPreparer.getAccount("%d", 0, 1, Collections.<Integer> emptySet()));

        Set<Integer> blacklist = new HashSet<>();
        blacklist.add(0);
        assertEquals("1", mPreparer.getAccount("%d", 0, 2, blacklist));

        blacklist = new HashSet<>();
        blacklist.add(1);
        blacklist.add(2);
        String account = mPreparer.getAccount("%d", 0, 4, blacklist);
        assertTrue("0".equals(account) || "3".equals(account));
        account = mPreparer.getAccount("%d", 0, 4, blacklist);
        assertTrue("0".equals(account) || "3".equals(account));
        account = mPreparer.getAccount("%d", 0, 4, blacklist);
        assertTrue("0".equals(account) || "3".equals(account));
        account = mPreparer.getAccount("%d", 0, 4, blacklist);
        assertTrue("0".equals(account) || "3".equals(account));
        account = mPreparer.getAccount("%d", 0, 4, blacklist);
        assertTrue("0".equals(account) || "3".equals(account));

        try {
            mPreparer.getAccount("%d%d", 0, 1, Collections.<Integer> emptySet());
            fail("Expected an IllegalArgumentException due to invalid format");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            mPreparer.getAccount("%d", 10, 0, Collections.<Integer> emptySet());
            fail("Expected an IllegalArgumentException due to no accounts");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            blacklist = new HashSet<>();
            blacklist.add(0);
            mPreparer.getAccount("%d", 0, 0, blacklist);
            fail("Expected an IllegalArgumentException due to no accounts");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}

