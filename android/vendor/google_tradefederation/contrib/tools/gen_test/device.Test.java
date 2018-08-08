package :package:;

import android.app.Activity;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityInstrumentationTestCase2;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * TODO: Add JavaDoc
 */
@RunWith(AndroidJUnit4.class)
public class :test: {

    public static final String TAG = :test:.class.getSimpleName();

    @Rule
    public ActivityTestRule<:activity:> mActivityRule =
        new ActivityTestRule(:activity:.class);

    /**
     * TODO: Add JavaDoc
     * This test runs on presubmit
     */
    @Presubmit
    @Test
    public void foo() throws Exception {
        Assert.assertNotNull(mActivityRule.getActivity());
    }

    /**
     * TODO: Add JavaDoc
     */
    @Test
    public void bar() throws Exception {
        Assert.assertTrue(true);
    }
}
