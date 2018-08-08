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

package android.support.text.emoji.widget;

import static android.support.text.emoji.util.Emoji.EMOJI_SINGLE_CODEPOINT;
import static android.support.text.emoji.util.EmojiMatcher.hasEmojiCount;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThat;

import android.annotation.TargetApi;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SdkSuppress;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.TestActivity;
import android.support.text.emoji.TestConfigBuilder;
import android.support.text.emoji.test.R;
import android.support.text.emoji.util.TestString;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class EmojiEditTextTest {

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule = new ActivityTestRule<>(
            TestActivity.class);
    private Instrumentation mInstrumentation;

    @BeforeClass
    public static void setupEmojiCompat() {
        EmojiCompat.reset(TestConfigBuilder.config());
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testInflateWithMaxEmojiCount() {
        final TestActivity activity = mActivityRule.getActivity();
        final EmojiEditText editText = activity.findViewById(R.id.editTextWithMaxCount);

        // value set in XML
        assertEquals(5, editText.getMaxEmojiCount());

        // set max emoji count
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                editText.setMaxEmojiCount(1);
            }
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(1, editText.getMaxEmojiCount());
    }

    @Test
    @SdkSuppress(minSdkVersion = 19)
    @TargetApi(19)
    public void testSetMaxCount() {
        final TestActivity activity = mActivityRule.getActivity();
        final EmojiEditText editText = activity.findViewById(R.id.editTextWithMaxCount);

        // set max emoji count to 1 and set text with 2 emojis
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                editText.setMaxEmojiCount(1);
                final String string = new TestString(EMOJI_SINGLE_CODEPOINT).append(
                        EMOJI_SINGLE_CODEPOINT).toString();
                editText.setText(string);
            }
        });
        mInstrumentation.waitForIdleSync();

        assertThat(editText.getText(), hasEmojiCount(1));
    }
}
