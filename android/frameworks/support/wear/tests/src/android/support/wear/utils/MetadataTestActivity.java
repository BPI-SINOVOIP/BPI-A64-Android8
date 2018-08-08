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

package android.support.wear.utils;

import android.app.Activity;
import android.os.Bundle;
import android.support.wear.test.R;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class MetadataTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assertTrue(MetadataConstants.isStandalone(this));
        assertTrue(MetadataConstants.isNotificationBridgingEnabled(this));
        assertEquals(R.drawable.preview_face,
                MetadataConstants.getPreviewDrawableResourceId(this, false));
        assertEquals(R.drawable.preview_face_circular,
                MetadataConstants.getPreviewDrawableResourceId(this, true));
    }
}