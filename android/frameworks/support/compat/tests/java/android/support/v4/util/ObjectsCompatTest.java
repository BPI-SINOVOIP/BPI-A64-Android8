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

package android.support.v4.util;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for ObjectsCompat
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ObjectsCompatTest {

    @Test
    public void testEquals() throws Exception {
        String a = "aaa";
        String b = "bbb";
        String c = new String(a);
        String n = null;

        assertFalse(ObjectsCompat.equals(a, b));
        assertFalse(ObjectsCompat.equals(a, n));
        assertFalse(ObjectsCompat.equals(n, a));

        assertTrue(ObjectsCompat.equals(n, n));
        assertTrue(ObjectsCompat.equals(a, a));
        assertTrue(ObjectsCompat.equals(a, c));
    }

}
