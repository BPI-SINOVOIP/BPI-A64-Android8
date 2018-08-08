// Copyright 2016 Google Inc. All Rights Reserved.

package com.google.android.tradefed.util;

public class AttenuatorState {

    private final int mLevel;
    private final long mDuration;

    public AttenuatorState(int level, long duration) {
        mLevel = level;
        mDuration = duration;
    }

    public int getLevel() {
        return mLevel;
    }

    public long getDuration() {
        return mDuration;
    }
}
