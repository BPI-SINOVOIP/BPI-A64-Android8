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

package android.support.design.testutils;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import java.util.concurrent.CountDownLatch;

/**
 * Activity that keeps track of resume / destroy lifecycle events, as well as of the last
 * instance of itself.
 */
public class RecreatedAppCompatActivity extends AppCompatActivity {
    // These must be cleared after each test using clearState()
    public static RecreatedAppCompatActivity sActivity;
    public static CountDownLatch sResumed;
    public static CountDownLatch sDestroyed;

    public static void clearState() {
        sActivity = null;
        sResumed = null;
        sDestroyed = null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActivity = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sResumed != null) {
            sResumed.countDown();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sDestroyed != null) {
            sDestroyed.countDown();
        }
    }
}
