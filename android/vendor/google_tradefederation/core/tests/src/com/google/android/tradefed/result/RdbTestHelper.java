/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.google.android.tradefed.result;

import com.android.tradefed.util.StreamUtil;

import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * A helper class for unit testing subclasses of {@link AbstractRdbResultReporter}.
 */
public class RdbTestHelper {

    public static final String SUCCESS_BLACKBOX_RESPONSE = "HTTP/1.1 200 OK\n\n";

    void assertData(ByteArrayOutputStream outputStream, String fileName) throws IOException {
        String expectedDataFilePath = String.format("/testdata/%s", fileName);
        String expectedData = sanitizeString(StreamUtil.getStringFromStream(
                getClass().getResourceAsStream(expectedDataFilePath)));
        String actualData = sanitizeString(outputStream.toString());
        // construct a custom error message here instead of using assertEquals because
        // assertEquals will not display the full expected actual strings
        Assert.assertEquals(expectedData, actualData);
    }

    /**
     *  assertData compares the outputStream with the provided files.
     *  asserts at least one file has the same content as the stream.
     *
     *  This logic is here because an XML may have different versions with
     *  different orders of items but exactly the same meaning. In those cases,
     *  any one of all possible validated versions of the XML is okay.
     */
    void assertData(ByteArrayOutputStream outputStream, String[] fileNames)
            throws IOException {
        String actualData = sanitizeString(outputStream.toString());
        String expectedData = "";
        for (int i = 0; i < fileNames.length; i++) {
            String expectedDataFilePath = String.format("/testdata/%s", fileNames[i]);
            expectedData = sanitizeString(StreamUtil.getStringFromStream(
                    getClass().getResourceAsStream(expectedDataFilePath)));
            if (actualData.equals(expectedData)) {
                break;
            }
        }
        Assert.assertEquals(expectedData, actualData);
    }

    String sanitizeString(String output) {
        // ignore whitespace
        output = output.replaceAll("\\s", "");
        return output;
    }
}
