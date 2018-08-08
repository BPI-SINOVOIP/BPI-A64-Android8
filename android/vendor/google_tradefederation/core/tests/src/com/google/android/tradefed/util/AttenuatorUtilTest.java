// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link AttenuatorUtil}.
 */
public class AttenuatorUtilTest extends TestCase {

    private static final String IPADDRESS = "127.0.0.10";
    private static final String UNKNOWN_HOST = "MY_host";
    private static final int ZERO_VALUE = 0;
    private static final int MAX_VALUE = 95;
    private static final int WRONG_VALUE = 100;
    private static final int STEP_VALUE = 2;
    private static final int WAITTIME = 50;

    private FakeAttenuatorUtil mMockAttenuator;
    private AttenuatorUtil mWrongIPAttenuator;
    private AttenuatorUtil mWrongHostAttenuator;
    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockAttenuator = new FakeAttenuatorUtil();
        mWrongIPAttenuator = new AttenuatorUtil(IPADDRESS);
        mWrongHostAttenuator = new AttenuatorUtil(UNKNOWN_HOST);
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#connect()}.
     */
    public void testConnect() {
        assertEquals("Not able to connect to wrong IP", -1, mWrongIPAttenuator.getValue());
        assertEquals("Not able to connect to wrong host", -1, mWrongHostAttenuator.getValue());
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#setValue(int value)}.
     */
    public void testSetValue() {
        assertTrue("Set zero value", mMockAttenuator.setValue(ZERO_VALUE));
        assertTrue("Set max value", mMockAttenuator.setValue(MAX_VALUE));
        assertFalse("Set max value", mMockAttenuator.setValue(WRONG_VALUE));
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#getValue()}.
     */
    public void testGetValue() {
        assertTrue("Set zero value", mMockAttenuator.setValue(ZERO_VALUE));
        assertEquals("Get zero value", ZERO_VALUE, mMockAttenuator.getValue());
        assertTrue("Set max value", mMockAttenuator.setValue(MAX_VALUE));
        assertEquals("Get max value", MAX_VALUE, mMockAttenuator.getValue());
        assertFalse("Set wrong value", mMockAttenuator.setValue(WRONG_VALUE));
        assertEquals("Get wrong value", MAX_VALUE, mMockAttenuator.getValue());
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#progressivelySetAttValue(int startValue, int endValue, int steps,
     *          long waitTime)}.
     */
    public void testStepValue() {
        assertTrue("Set step values", mMockAttenuator.progressivelySetAttValue(ZERO_VALUE,
                MAX_VALUE, STEP_VALUE, WAITTIME));
        assertEquals("Get step value", MAX_VALUE, mMockAttenuator.getValue());
    }


    /**
     * Test cases for
     * {@link AttenuatorUtil#progressivelySetAttValue(int endValue, int stepSize, long waitTime)}.
     *
     * Verifies intermediate values are set correctly in order.
     */
    public void testGradualStepValues(){
        mMockAttenuator.setValue(0);
        mMockAttenuator.clearValuesSet();
        assertEquals(0, mMockAttenuator.getValuesSet().size());

        int targetValue = 5;
        int stepSize = 1;
        int waitTime = 20;

        assertEquals("Couldn't set progressive values",
                mMockAttenuator.progressivelySetAttValue(targetValue, stepSize, waitTime), true);

        assertEquals(1, mMockAttenuator.getValuesSet().get(0).intValue());
        assertEquals(2, mMockAttenuator.getValuesSet().get(1).intValue());
        assertEquals(3, mMockAttenuator.getValuesSet().get(2).intValue());
        assertEquals(4, mMockAttenuator.getValuesSet().get(3).intValue());
        assertEquals(5, mMockAttenuator.getValuesSet().get(4).intValue());
        assertEquals(5, mMockAttenuator.getValuesSet().size());
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#progressivelySetAttValue(int endValue, int stepSize, long waitTime)}.
     *
     * Verifies intermediate values are set correctly in reverse order.
     */
    public void testGradualStepValuesReverseOrder(){
        mMockAttenuator.setValue(5);
        mMockAttenuator.clearValuesSet();
        assertEquals(0, mMockAttenuator.getValuesSet().size());

        int targetVAlue = 0;
        int stepSize = 1;
        int waitTime = 20;

        assertEquals("Couldn't set progressive values",
                mMockAttenuator.progressivelySetAttValue(targetVAlue, stepSize, waitTime), true);

        assertEquals(4, mMockAttenuator.getValuesSet().get(0).intValue());
        assertEquals(3, mMockAttenuator.getValuesSet().get(1).intValue());
        assertEquals(2, mMockAttenuator.getValuesSet().get(2).intValue());
        assertEquals(1, mMockAttenuator.getValuesSet().get(3).intValue());
        assertEquals(0, mMockAttenuator.getValuesSet().get(4).intValue());
        assertEquals(5, mMockAttenuator.getValuesSet().size());
    }

    /**
     * Test cases for
     * {@link AttenuatorUtil#progressivelySetAttValue(int endValue, int steps, long waitTime)}.
     *
     * Verifies correctness on non-divisibility cases.
     */
    public void testGradualStepPartialJumps() {
        mMockAttenuator.setValue(0);
        mMockAttenuator.clearValuesSet();
        assertEquals(0, mMockAttenuator.getValuesSet().size());

        int targetValue = 5;
        int stepSize = 4;
        int waitTime = 20;

        assertTrue("Couldn't set progressive values",
                mMockAttenuator.progressivelySetAttValue(targetValue, stepSize, waitTime));

        assertEquals(4, mMockAttenuator.getValuesSet().get(0).intValue());
        assertEquals(5, mMockAttenuator.getValuesSet().get(1).intValue());
        assertEquals(2, mMockAttenuator.getValuesSet().size());
    }
}
