// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link MultiChannelAttenuatorUtil} */
@RunWith(JUnit4.class)
public class MultiChannelAttenuatorUtilTest {

    public static final String FAKE_IP = "127.0.0.1";

    @Test
    public void testValidateIncorrectChannelNumber() {
        try {
            new FakeMultiChannelAttenuatorUtil(FAKE_IP, 0);
            Assert.fail("Passing invalid channel should preven't MultiChannelAttenuatorUtil "
                    + "initialization.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testValidateCorrectChannelNumber() {
        try {
            new FakeMultiChannelAttenuatorUtil(FAKE_IP, 1);
        } catch (IllegalArgumentException e){
            Assert.fail("Valid channel should not throw exceptions.");
        }
    }
}
