/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.selection;

import static junit.framework.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.SelectionManagers;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SelectionManager_SingleSelectTest {

    private static final List<String> ITEMS = TestData.create(100);

    private SelectionManager mManager;
    private TestSelectionListener mCallback;
    private TestDocumentsAdapter mAdapter;
    private SelectionProbe mSelection;

    @Before
    public void setUp() throws Exception {
        mCallback = new TestSelectionListener();
        mManager = SelectionManagers.createTestInstance(ITEMS, SelectionManager.MODE_SINGLE);
        mManager.addCallback(mCallback);

        mSelection = new SelectionProbe(mManager);
    }

    @Test
    public void testSimpleSelect() {
        mManager.toggleSelection(ITEMS.get(3));
        mManager.toggleSelection(ITEMS.get(4));
        mCallback.assertSelectionChanged();
        mSelection.assertSelection(4);
    }

    @Test
    public void testRangeSelectionNotEstablished() {
        mManager.toggleSelection(ITEMS.get(3));
        mCallback.reset();

        try {
            mManager.snapRangeSelection(10);
            fail("Should have thrown.");
        } catch (Exception expected) {}

        mCallback.assertSelectionUnchanged();
        mSelection.assertSelection(3);
    }
}
