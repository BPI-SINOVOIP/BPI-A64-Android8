/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.power.tests;

import com.android.tradefed.log.LogUtil.CLog;

/*
* Class that represents the start and end of a power measurement.
* */
public class PowerTimestamp implements Comparable<PowerTimestamp> {

    private final String mTag;
    private PowerTimestampStatus mStatus = PowerTimestampStatus.UNKNWON;
    private String mStatusReason;

    public enum PowerTimestampStatus {
        UNKNWON,
        VALID,
        INVALID
    }

    private Long mStartTime;
    private Long mEndTime;

    public PowerTimestamp(String tag){
        mTag = tag;
    }

    public String getTag() {
        return mTag;
    }

    public void setStartTime(long startTime) {
        mStartTime = startTime;
    }

    public void setEndTime(long endTime) {
        mEndTime = endTime;
    }

    public Long getStartTime() {
        return mStartTime;
    }

    public Long getEndTime() {
        return mEndTime;
    }

    public void setStatus(PowerTimestampStatus status, String reason) {
        if (status == mStatus) {
            return;
        }

        mStatusReason = String.format("Updated status from: %s to %s. Reason: %s",
                mStatus.name(), status.name(), reason);
        mStatus = status;
    }

    public PowerTimestampStatus getStatus() {
        return mStatus;
    }

    public String getStatusReason() {
        return mStatusReason;
    }

    public long getDuration() {
        if (getStartTime() != null && mEndTime != null && mEndTime > mStartTime) {
            return mEndTime - mStartTime;
        }
        CLog.e(String.format("The test duration is invalid. Start time is %d and end time is %d",
                mStartTime, mEndTime));
        return 0;
    }

    /**
     * PowerTimestamps are to be sorted according to their start time.
     *
     * returns  0 if this.getStartTime and other.getStarteTime are the same. returns -1 if
     * this.getStartTime is less than other.getStartTime. returns  1 if this.getStartTime is greater
     * than other.getStartTime.
     *
     * If getStartTime is null, it is treated as the lowest possible number (Integer.MIN_VALUE).
     */
    @Override
    public int compareTo(PowerTimestamp other) {
        if (this == other) {
            return 0;
        }

        if (getStartTime() == null) {
            return -1;
        }

        if (other.getStartTime() == null) {
            return 1;
        }

        return Long.signum(getStartTime() - other.getStartTime());
    }
}
