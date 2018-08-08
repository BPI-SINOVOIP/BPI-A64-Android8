// Copyright 2013 Google Inc. All Rights Reserved.

package com.google.android.nlptester;

import android.content.Context;
import android.location.LocationManager;
import android.test.AndroidTestCase;

public class NlpTester extends AndroidTestCase {

    /**
     * Tests for presence of {@link LocationManager.NETWORK_PROVIDER}.
     */
    public void testNlp() throws Exception {
         LocationManager locationManager = (LocationManager) getContext().getSystemService(
                 Context.LOCATION_SERVICE);
         assertNotNull(locationManager);

         assertTrue("network provider not found",
                 locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER));
    }
}
