// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;

public class AttenuatorController implements ISignalStrengthController {
    private final AttenuatorUtil mAtt;
    private final List<AttenuatorState> mAttenuatorStates;

    private boolean mRepeat = false;
    private Throwable mUnexpectedException = null;
    private int mGradualStepSize = 1;
    private long mGradualStepWait = 1000;
    private boolean mMakeGradualAttenuations = false;

    /**
     * @param attenuatorUtil The utility class to control an attenuator.
     * @param states Sequence of attenuator states which describes at what level and for how long
     * each one should be set.
     */
    public AttenuatorController(AttenuatorUtil attenuatorUtil, List<AttenuatorState> states) {
        mAtt = attenuatorUtil;
        validatePatternArguments(states);
        mAttenuatorStates = states;
    }

    /**
     * @param ip The attenuator ip.
     * @param channel If it is a multi channel attenuator then specify the channel number. If it is
     * not a multi channel attenuator pass null.
     * @param states Sequence of attenuator states which describes at what level and for how long
     * each one should be set.
     */
    public AttenuatorController(String ip, Integer channel, List<AttenuatorState> states) {
        this(getAttenuatorUtil(ip, channel), states);
    }

    /**
     * @param ip The attenuator ip.
     * @param port Port to be used for communication
     * @param channel If it is a multi channel attenuator then specify the channel number. If it is
     *     not a multi channel attenuator pass null.
     * @param states Sequence of attenuator states which describes at what level and for how long
     *     each one should be set.
     */
    public AttenuatorController(
            String ip, Integer port, Integer channel, List<AttenuatorState> states) {
        this(getAttenuatorUtil(ip, port, channel), states);
    }

    private static AttenuatorUtil getAttenuatorUtil(String ip, Integer port, Integer channel) {
        AttenuatorUtil attenuator;
        if (channel == null) {
            attenuator = new AttenuatorUtil(ip, port);
        } else {
            attenuator = new MultiChannelAttenuatorUtil(ip, port, channel);
        }
        return attenuator;
    }

    private static AttenuatorUtil getAttenuatorUtil(String ip, Integer channel) {
        AttenuatorUtil attenuator;
        if (channel == null) {
            attenuator = new AttenuatorUtil(ip);
        } else {
            attenuator = new MultiChannelAttenuatorUtil(ip, channel);
        }
        return attenuator;
    }

    /**
     * @param repeat Parameter that indicates if the pattern should be repeated or stopped after the
     * first iteration.
     */
    public void setRepeat(boolean repeat) {
        mRepeat = repeat;
    }

    /**
     * @param makeGradualAttenuations Whether or not the attenuation sequences will be made in
     * gradual changes instead of steady jumps. The default value is false.
     */
    public void setGradualAttenuations(boolean makeGradualAttenuations) {
        mMakeGradualAttenuations = makeGradualAttenuations;
    }

    /**
     * @param stepSize How many attenuation levels the controller will skip while doing gradual
     * gradual changes of attenuation levels. The default falue is 1.
     */
    public void setGradualStepSize(int stepSize) {
        mGradualStepSize = stepSize;
    }

    /**
     * @param wait Number of seconds the controller will hold an attenuation level before stepping
     * into the next one while doing gradual changes of attenuation levels. The default value is
     * 1000 ms (1 second).
     */
    public void setGradualStepWait(long wait) {
        mGradualStepWait = wait;
    }

    private void validatePatternArguments(List<AttenuatorState> states) {
        if (states == null) {
            throw new IllegalArgumentException("List of attenuatior states can not be null.");
        }

        for (AttenuatorState state : states) {
            if (state.getLevel() < 0 || state.getLevel() > 95) {
                String message = String.format("Attenuation levels must be between 0 and 95. %d "
                        + "found.", state.getLevel());
                throw new IllegalArgumentException(message);
            }

            if (state.getDuration() < 0) {
                String message = String.format("Durations for attenuation levels must be "
                        + "greater than zero. %d found.", state.getDuration());
                throw new IllegalArgumentException(message);
            }
        }
    }

    private Thread mThread =
            new Thread() {
                @Override
                public void run() {
                    printoutPattern();
                    do {
                        performAttenuations();
                    } while (mRepeat);
                    CLog.d("Completed attenuation pattern on %s.", mAtt.getIdentifier());
                }
            };

    private void performAttenuations() {
        for (int i = 0; i < mAttenuatorStates.size(); i++) {
            int currentLevel = mAtt.getValue();
            int nextLevel = mAttenuatorStates.get(i).getLevel();

            long start = System.currentTimeMillis();
            if (mMakeGradualAttenuations) {
                CLog.d("Gradually changing attenuations from %d to %d", currentLevel,
                        nextLevel);
                long delay = getValidGradualStepDelay(currentLevel, nextLevel,
                        mAttenuatorStates.get(i).getDuration());

                setAttenuationLevelGradualy(nextLevel, delay);
            } else {
                setAttenuationLevel(mAttenuatorStates.get(i).getLevel());
            }
            long stop = System.currentTimeMillis();
            long remaining = Math.max(0, mAttenuatorStates.get(i).getDuration() - (stop - start));

            CLog.d("Staying at attenuation level %d for %d milliseconds on %s.",
                    mAttenuatorStates.get(i).getLevel(), remaining, mAtt.getIdentifier());
            getRunUtil().sleep(remaining);
        }
    }

    private long getValidGradualStepDelay(int startLevel, int endLevel, long duration) {
        int numSteps = (int) Math.ceil((double)Math.abs(startLevel - endLevel) / mGradualStepSize);

        // If there is enough time between attenuation levels to perform gradual steps each at
        // lasting for mGradualStepWait ms
        if (duration / mGradualStepWait >= numSteps) {
            return mGradualStepWait;
        }

        return duration / numSteps;
    }

    public int getAttenuationLevel() {
        return mAtt.getValue();
    }

    private Thread.UncaughtExceptionHandler mHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            CLog.e("Unexpected exception occurred while performing attenuations pattern on %s",
                    mAtt.getIdentifier());
            CLog.e(throwable);
            mUnexpectedException = throwable;
        }
    };

    @Override
    public void run() {
        mThread.setUncaughtExceptionHandler(mHandler);
        mThread.setName("AttenuatorControler");
        mThread.setDaemon(true);
        mThread.start();
    }

    private void printoutPattern() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Starting attenuation pattern for %s.", mAtt.getIdentifier()));
        builder.append(System.getProperty("line.separator"));
        if (mRepeat) {
            builder.append(String.format("The following pattern will be repeated indefinitely on "
                    + "%s.", mAtt.getIdentifier()));
            builder.append(System.getProperty("line.separator"));
        } else {
            builder.append(String.format("The following pattern will be run only once on %s.",
                    mAtt.getIdentifier()));
            builder.append(System.getProperty("line.separator"));
        }
        for (int i = 0; i < mAttenuatorStates.size(); i++) {
            builder.append(
                    String.format("Attenuation level: %d; Duration: %d milliseconds", mAttenuatorStates
                            .get(i).getLevel(), mAttenuatorStates.get(i).getDuration()));
            builder.append(System.getProperty("line.separator"));
        }
        CLog.d(builder.toString());
    }

    @Override
    public void stop() {
        setRepeat(false);
        if (mUnexpectedException != null) {
            String message = String
                    .format("An unexpected exception was thrown while performing the "
                            + "attenuation pattern on %s.", mAtt.getIdentifier());
            throw new RuntimeException(message, mUnexpectedException);
        }

        if (mThread.isAlive()) {
            mThread.interrupt();
        }

        // Sets attenuation level to 0 so the signal is available for the next test's setup.
        setAttenuationLevel(0);
    }

    private void setAttenuationLevel(int level) {
        if (!mAtt.setValue(level)) {
            throw new IllegalStateException(
                    String.format("Couldn't set attenuator level to %d on %s.", 0,
                            mAtt.getIdentifier()));
        }

        CLog.d("Attenuator level set to %d on %s.", level, mAtt.getIdentifier());
    }

    private void setAttenuationLevelGradualy(int level, long delay) {
        CLog.d("Starting attenuator level gradual change to level %d on %s.", level,
                mAtt.getIdentifier());
        if(!mAtt.progressivelySetAttValue(level, mGradualStepSize, delay)){
            throw new IllegalStateException(
                    String.format("Couldn't set attenuator level gradualy to %d on %s.", 0,
                            mAtt.getIdentifier()));
        }

        CLog.d("Attenuator level gradually set to %d on %s.", level, mAtt.getIdentifier());
    }


    public int getCurrentLevel() {
        return mAtt.getValue();
    }

    /** {@link Thread#join(long)} on the attenuation thread. */
    public void join(long waitJoinTime) throws InterruptedException {
        mThread.join(waitJoinTime);
    }

    /** Get the default runutil. */
    @VisibleForTesting
    IRunUtil getRunUtil() {
        return RunUtil.getDefault();
    }
}
