// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link AttenuatorController} */
@RunWith(JUnit4.class)
public class AttenuatorControllerTest {

    private static final String FAKE_IP = "127.0.0.1";

    // Using very small delays for test purposes.
    private static final long LONG_DURATION = 250L; // 250ms
    private static final long SHORT_DURATION = LONG_DURATION / 5; // 50ms

    private AttenuatorController mController;

    @After
    public void tearDown() {
        if (mController != null) {
            mController.stop();
        }
    }

    @Test
    public void testAttenuationsArePerformedInOrder() throws InterruptedException {
        IRunUtil mMockRunUtil = Mockito.mock(RunUtil.class);
        FakeAttenuatorUtil attUtil = new FakeAttenuatorUtil(0);
        List<Integer> levels = Arrays.asList(1, 3, 2);
        List<Long> durations = Arrays.asList(LONG_DURATION, LONG_DURATION, LONG_DURATION);
        mController =
                new AttenuatorController(attUtil, buildStates(levels, durations)) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        Assert.assertEquals(
                "Number of attenuation levels set should be 0 before starting",
                0,
                attUtil.getValuesSet().size());
        mController.run();
        // wait for attenuations to be performed.
        mController.join(LONG_DURATION);
        Mockito.verify(mMockRunUtil, Mockito.times(3)).sleep(Mockito.anyLong());
        Assert.assertEquals(1, attUtil.getValuesSet().get(0).intValue());
        Assert.assertEquals(3, attUtil.getValuesSet().get(1).intValue());
        Assert.assertEquals(2, attUtil.getValuesSet().get(2).intValue());
    }

    @Test
    public void testAttenuationsRepeat() throws InterruptedException {
        IRunUtil mMockRunUtil = Mockito.mock(RunUtil.class);
        FakeAttenuatorUtil attUtil = new FakeAttenuatorUtil(0);
        List<Integer> levels = Arrays.asList(0, 4);
        List<Long> durations = Arrays.asList(SHORT_DURATION, SHORT_DURATION);
        mController =
                new AttenuatorController(attUtil, buildStates(levels, durations)) {
                    @Override
                    IRunUtil getRunUtil() {
                        return mMockRunUtil;
                    }
                };
        mController.setGradualStepSize(1);
        mController.setRepeat(true);
        Assert.assertEquals(
                "Number of attenuation levels set should be 0 before starting",
                0,
                attUtil.getValuesSet().size());
        mController.run();
        // wait for attenuations to be performed.
        mController.join(LONG_DURATION);
        Mockito.verify(mMockRunUtil, Mockito.atLeastOnce()).sleep(Mockito.anyLong());
        Assert.assertEquals(0, attUtil.getValuesSet().get(0).intValue());
        Assert.assertEquals(4, attUtil.getValuesSet().get(1).intValue());
        Assert.assertEquals(0, attUtil.getValuesSet().get(2).intValue());
        Assert.assertEquals(4, attUtil.getValuesSet().get(3).intValue());
    }

    private List<AttenuatorState> buildStates(List<Integer> levels,
            List<Long> durations) {
        List<AttenuatorState> states = new ArrayList<>();

        for (int i = 0; i < levels.size(); i++) {
            states.add(new AttenuatorState(levels.get(i), durations.get(i)));
        }

        return states;
    }

    private class FakeAttenuatorUtil extends AttenuatorUtil {
        private int mInternalValue = 0;
        private List<Integer> mValuesSet = new ArrayList<>();

        public List<Integer> getValuesSet(){
            return mValuesSet;
        }


        public FakeAttenuatorUtil(int initialValue) {
            super(FAKE_IP);
            mInternalValue = initialValue;
        }

        @Override
        protected void connect () throws IOException {
            return;
        }

        @Override
        protected void disconnect() {
            return;
        }

        @Override
        public boolean setValue(int value){
            mValuesSet.add(value);
            mInternalValue = value;
            return true;
        }

        @Override
        public int getValue(){
            return mInternalValue;
        }
    }
}

