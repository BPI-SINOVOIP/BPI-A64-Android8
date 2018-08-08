/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.power.collectors;

import com.android.tradefed.result.LogDataType;
import java.io.File;

/**
 * A wrapper for all the information that {@link com.android.tradefed.log.ITestLogger} requires to
 * store log files.
 */
public class PowerTestLog {
    private final LogDataType mDataType;
    private final File mFile;
    private final String mName;

    public PowerTestLog(File file, String name, LogDataType dataType) {
        mName = name;
        mFile = file;
        mDataType = dataType;
    }

    public LogDataType getDataType() {
        return mDataType;
    }

    public String getName() {
        return mName;
    }

    public File getLogFile() {
        return mFile;
    }
}
