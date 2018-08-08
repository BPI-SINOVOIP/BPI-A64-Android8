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

package com.google.android.power;

import com.google.android.power.tests.LabSetupInfoExtractor;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LabSetupInfoExtractor}. */
@RunWith(JUnit4.class)
public class LabSetupInfoExtractorTests {

    @Test
    public void testSerialIsFound() {
        InputStreamReader stream = asStream("", "  ", "device:serial monsoon:1234", "");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertTrue(extractor.deviceSerialExists("serial"));
        Assert.assertFalse(extractor.deviceSerialExists("serial2"));
    }

    @Test
    public void testIgnoreWhiteLines() {
        InputStreamReader stream = asStream("", "  ", "device:serial monsoon:1234", "");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals("1234", extractor.extractMonsoonSerialNo("serial"));
    }

    @Test
    public void testIgnoreCommentedLines() {
        InputStreamReader stream = asStream("#device:serial monsoon:1234");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals(null, extractor.extractMonsoonSerialNo("serial"));
        Assert.assertFalse(extractor.deviceSerialExists("serial"));
    }

    @Test
    public void testIgnoreComments() {
        InputStreamReader stream =
                asStream("device:serial monsoon:1234 #this comment should be ignored.");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals("1234", extractor.extractMonsoonSerialNo("serial"));
    }

    @Test
    public void ignoreLeadingAndTrailingWhiteSpaces() {
        InputStreamReader stream = asStream("  device:serial monsoon:1234  ");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals("1234", extractor.extractMonsoonSerialNo("serial"));
    }

    @Test
    public void extractTigertailSerial() {
        InputStreamReader stream = asStream("device:serial tigertail:4321 monsoon:1234 #comment");
        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals("1234", extractor.extractMonsoonSerialNo("serial"));
        Assert.assertEquals("4321", extractor.extractTigertailSerialNo("serial"));

        stream = asStream("device:serial monsoon:1234  tigertail:4321");
        extractor = new LabSetupInfoExtractor(stream);
        Assert.assertEquals("1234", extractor.extractMonsoonSerialNo("serial"));
        Assert.assertEquals("4321", extractor.extractTigertailSerialNo("serial"));
    }

    @Test
    public void extractDevicesByHost() {
        InputStreamReader stream =
                asStream(
                        "host:dummy1 device:serial1",
                        "host:dummy1 device:serial2",
                        "host:dummy1 device:serial3",
                        "host:dummy2 device:serial4");

        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        List<String> devices = extractor.getDevicesByHost("dummy1");
        Assert.assertEquals(3, devices.size());
        Assert.assertTrue(
                "serial1 should be in list of devices for host dummy1",
                devices.contains("serial1"));
        Assert.assertTrue(
                "serial2 should be in list of devices for host dummy1",
                devices.contains("serial2"));
        Assert.assertTrue(
                "serial3 should be in list of devices for host dummy1",
                devices.contains("serial3"));
    }

    @Test
    public void nonExistentHostHasZeroDevices() {
        InputStreamReader stream =
                asStream(
                        "host:dummy1 device:serial1",
                        "host:dummy1 device:serial2",
                        "host:dummy1 device:serial3",
                        "device:serial4");

        LabSetupInfoExtractor extractor = new LabSetupInfoExtractor(stream);
        List<String> devices = extractor.getDevicesByHost("non-existent");
        Assert.assertEquals(0, devices.size());
    }

    private InputStreamReader asStream(String... lines) {
        String stringthing = String.join("\n", lines);
        return new InputStreamReader(new ByteArrayInputStream(stringthing.getBytes()));
    }
}
