// Copyright 2010 Google Inc. All Rights Reserved.
package com.google.android.tradefed.targetprep;

import com.android.tradefed.targetprep.FlashingResourcesParser;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import java.io.File;

/**
 * Functional tests for {@link FlashingResourcesParser}.
 * <p/>
 * Unfortunately depends on specific file paths so cannot go in open source yet
 */
public class FlashingResourcesParserFuncTest extends TestCase {

    // reuse device image file from DeviceFlasherFuncTest
    private static final String IMG_FILE_PATH = GoogleDeviceFlashPreparerFuncTest.TEST_DATA_ROOT +
            File.separatorChar + GoogleDeviceFlashPreparerFuncTest.IMG_FILE_1;
    private static final String BAD_FILE_PATH = GoogleDeviceFlashPreparerFuncTest.TEST_DATA_ROOT +
            File.separatorChar + "bad-img.zip";

    /**
     * Test that {@link FlashingResourcesParser} parses a valid zip file
     * correctly.
     */
    public void testParseValidImage() throws TargetSetupError {
        File imgFile = new File(IMG_FILE_PATH);
        FlashingResourcesParser retriever = new FlashingResourcesParser(imgFile);
        assertTrue(retriever.getRequiredBoards().contains("marlin"));
        assertTrue(retriever.getRequiredBoards().contains("sailfish"));
        assertEquals("8996-P11101-1605140618", retriever.getRequiredBootloaderVersion());
        assertEquals("8996-011101-1605181809", retriever.getRequiredBasebandVersion());
    }

    /**
     * Test that {@link FlashingResourcesParser#FlashingResourcesParser(File)} when passed a zip
     * file that does not contain android-info.txt.
     */
    public void testFlashingResourcesParser_badZip() {
        File imgFile = new File(BAD_FILE_PATH);
        try {
            new FlashingResourcesParser(imgFile);
            fail("TargetSetupError not thrown");
        } catch (TargetSetupError e) {
            // expected
        }
    }
}
