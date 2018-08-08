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

package android.support.design.widget;

import static android.support.design.testutils.FloatingActionButtonActions.hideThenShow;
import static android.support.design.testutils.FloatingActionButtonActions.setBackgroundTintColor;
import static android.support.design.testutils.FloatingActionButtonActions.setBackgroundTintList;
import static android.support.design.testutils.FloatingActionButtonActions.setCompatElevation;
import static android.support.design.testutils.FloatingActionButtonActions.setImageResource;
import static android.support.design.testutils.FloatingActionButtonActions.setLayoutGravity;
import static android.support.design.testutils.FloatingActionButtonActions.setSize;
import static android.support.design.testutils.FloatingActionButtonActions.showThenHide;
import static android.support.design.testutils.TestUtilsActions.setClickable;
import static android.support.design.testutils.TestUtilsActions.setEnabled;
import static android.support.design.testutils.TestUtilsActions.setSelected;
import static android.support.design.testutils.TestUtilsMatchers.isPressed;
import static android.support.design.testutils.TestUtilsMatchers.withFabBackgroundFill;
import static android.support.design.testutils.TestUtilsMatchers.withFabContentAreaOnMargins;
import static android.support.design.testutils.TestUtilsMatchers.withFabContentHeight;
import static android.support.design.widget.DesignViewActions.setVisibility;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.design.test.R;
import android.support.design.testutils.TestUtils;
import android.support.test.espresso.action.CoordinatesProvider;
import android.support.test.espresso.action.GeneralSwipeAction;
import android.support.test.espresso.action.Press;
import android.support.test.espresso.action.Swipe;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;
import android.support.v4.content.ContextCompat;
import android.view.Gravity;
import android.view.View;

import org.junit.Test;

public class FloatingActionButtonTest
        extends BaseInstrumentationTestCase<FloatingActionButtonActivity> {

    public FloatingActionButtonTest() {
        super(FloatingActionButtonActivity.class);
    }

    @SmallTest
    @Test
    public void testDefaultBackgroundTint() {
        final int colorAccent = TestUtils.getThemeAttrColor(
                mActivityTestRule.getActivity(), R.attr.colorAccent);
        onView(withId(R.id.fab_standard))
                .check(matches(withFabBackgroundFill(colorAccent)));
    }

    @SmallTest
    @Test
    public void testSetTintOnDefaultBackgroundTint() {
        onView(withId(R.id.fab_standard))
                .perform(setBackgroundTintColor(Color.GREEN))
                .check(matches(withFabBackgroundFill(Color.GREEN)));
    }

    @SmallTest
    @Test
    public void testDeclaredBackgroundTint() {
        onView(withId(R.id.fab_tint))
                .check(matches(withFabBackgroundFill(Color.MAGENTA)));
    }

    @SmallTest
    @Test
    public void testSetTintOnDeclaredBackgroundTint() {
        onView(withId(R.id.fab_tint))
                .perform(setBackgroundTintColor(Color.GREEN))
                .check(matches(withFabBackgroundFill(Color.GREEN)));
    }

    @SmallTest
    @Test
    public void testSetStatefulTintAcrossStateChanges() {
        final Activity activity = mActivityTestRule.getActivity();

        final ColorStateList tint = ContextCompat.getColorStateList(activity, R.color.fab_tint);
        final int normal = ContextCompat.getColor(activity, R.color.sand_default);
        final int notSelected = ContextCompat.getColor(activity, R.color.sand_disabled);

        // First set the background tint list to the ColorStateList
        onView(withId(R.id.fab_standard))
                .perform(setBackgroundTintList(tint));

        // Assert that the background is tinted correctly across state changes
        onView(withId(R.id.fab_standard))
                .perform(setSelected(true))
                .check(matches(withFabBackgroundFill(normal)))
                .perform(setSelected(false))
                .check(matches(withFabBackgroundFill(notSelected)))
                .perform(setSelected(true))
                .check(matches(withFabBackgroundFill(normal)));
    }

    @SmallTest
    @Test
    public void testDeclaredStatefulTintAcrossStateChanges() {
        final Activity activity = mActivityTestRule.getActivity();
        final int normal = ContextCompat.getColor(activity, R.color.sand_default);
        final int disabled = ContextCompat.getColor(activity, R.color.sand_disabled);

        // Assert that the background is tinted correctly across state changes
        onView(withId(R.id.fab_state_tint))
                .perform(setSelected(true))
                .check(matches(withFabBackgroundFill(normal)))
                .perform(setSelected(false))
                .check(matches(withFabBackgroundFill(disabled)));
    }

    @SmallTest
    @Test
    public void setVectorDrawableSrc() {
        onView(withId(R.id.fab_standard))
                .perform(setImageResource(R.drawable.vector_icon));
    }

    @SmallTest
    @Test
    public void testSetMiniSize() {
        final int miniSize = mActivityTestRule.getActivity().getResources()
                .getDimensionPixelSize(R.dimen.fab_mini_height);

        onView(withId(R.id.fab_standard))
                .perform(setSize(FloatingActionButton.SIZE_MINI))
                .check(matches(withFabContentHeight(miniSize)));
    }

    @SmallTest
    @Test
    public void testSetSizeToggle() {
        final int miniSize = mActivityTestRule.getActivity().getResources()
                .getDimensionPixelSize(R.dimen.fab_mini_height);
        final int normalSize = mActivityTestRule.getActivity().getResources()
                .getDimensionPixelSize(R.dimen.fab_normal_height);

        onView(withId(R.id.fab_standard))
                .perform(setSize(FloatingActionButton.SIZE_MINI))
                .check(matches(withFabContentHeight(miniSize)));

        onView(withId(R.id.fab_standard))
                .perform(setSize(FloatingActionButton.SIZE_NORMAL))
                .check(matches(withFabContentHeight(normalSize)));
    }

    @SmallTest
    @Test
    public void testOffset() {
        onView(withId(R.id.fab_standard))
                .perform(setLayoutGravity(Gravity.LEFT | Gravity.TOP))
                .check(matches(withFabContentAreaOnMargins(Gravity.LEFT | Gravity.TOP)));

        onView(withId(R.id.fab_standard))
                .perform(setLayoutGravity(Gravity.RIGHT | Gravity.BOTTOM))
                .check(matches(withFabContentAreaOnMargins(Gravity.RIGHT | Gravity.BOTTOM)));
    }

    @SmallTest
    @Test
    public void testHideShow() {
        onView(withId(R.id.fab_standard))
                .perform(setVisibility(View.VISIBLE))
                .perform(hideThenShow(FloatingActionButtonImpl.SHOW_HIDE_ANIM_DURATION))
                .check(matches(isDisplayed()));
    }

    @MediumTest
    @Test
    public void testShowHide() {
        onView(withId(R.id.fab_standard))
                .perform(setVisibility(View.GONE))
                .perform(showThenHide(FloatingActionButtonImpl.SHOW_HIDE_ANIM_DURATION))
                .check(matches(not(isDisplayed())));
    }

    @LargeTest
    @Test
    public void testClickableTouchAndDragOffView() {
        onView(withId(R.id.fab_standard))
                .perform(setClickable(true))
                .perform(new GeneralSwipeAction(
                        Swipe.SLOW,
                        new CoordinatesProvider() {
                            @Override
                            public float[] calculateCoordinates(View view) {
                                // Create coordinators that in the center of the FAB's content area
                                final FloatingActionButton fab = (FloatingActionButton) view;

                                final int[] xy = new int[2];
                                fab.getLocationOnScreen(xy);
                                final Rect rect = new Rect();
                                fab.getContentRect(rect);

                                return new float[] {
                                        xy[0] + rect.centerX(),
                                        xy[1] + rect.centerY()
                                };
                            }
                        },
                        new CoordinatesProvider() {
                            @Override
                            public float[] calculateCoordinates(View view) {
                                // Create coordinators that in the center horizontally, but well
                                // below the view vertically (by 50% of the height)
                                final int[] xy = new int[2];
                                view.getLocationOnScreen(xy);

                                return new float[]{
                                        xy[0] + (view.getWidth() / 2f),
                                        xy[1] + (view.getHeight() * 1.5f)
                                };
                            }
                        },
                        Press.FINGER))
                .check(matches(not(isPressed())));
    }

    @MediumTest
    @Test
    public void testOnClickListener() {
        final View.OnClickListener listener = mock(View.OnClickListener.class);
        final View view = mActivityTestRule.getActivity().findViewById(R.id.fab_standard);
        view.setOnClickListener(listener);

        // Click on the fab
        onView(withId(R.id.fab_standard)).perform(click());

        // And verify that the listener was invoked once
        verify(listener, times(1)).onClick(view);
    }

    @SmallTest
    @Test
    public void testSetCompatElevation() {
        onView(withId(R.id.fab_standard))
                .perform(setEnabled(false))
                .perform(setCompatElevation(0));

        onView(withId(R.id.fab_standard))
                .perform(setEnabled(true))
                .perform(setCompatElevation(8));
    }
}
