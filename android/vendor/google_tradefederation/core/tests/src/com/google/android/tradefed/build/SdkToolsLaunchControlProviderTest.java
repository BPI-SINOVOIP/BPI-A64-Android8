// Copyright 2017 Google Inc. All Rights Reserved.

package com.google.android.tradefed.build;

import com.android.tradefed.build.BuildRetrievalError;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit Tests for {@link SdkToolsLaunchControlProvider} */
@RunWith(JUnit4.class)
public class SdkToolsLaunchControlProviderTest {
    /**
     * Test {@link SdkToolsLaunchControlProvider#downloadToolsZipFile(IFileDownloader, String,
     * String)}. Empty string should not match an empty commaSeparatedFiles.
     */
    @Test(expected = BuildRetrievalError.class)
    public void testdownloadToolsZipFile() throws Exception {
        SdkToolsLaunchControlProvider stlcp = new SdkToolsLaunchControlProvider();
        stlcp.downloadToolsZipFile(null, "", "");
    }
}
