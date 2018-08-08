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

import com.google.android.power.tests.PowerTestUsbSwitchProvider;
import com.google.android.utils.usb.switches.DatamationUsbSwitch;
import com.google.android.utils.usb.switches.IUsbSwitch;
import com.google.android.utils.usb.switches.MonsoonUsbSwitch;
import com.google.android.utils.usb.switches.MultiUsbSwitch;
import com.google.android.utils.usb.switches.NcdUsbSwitch;
import com.google.android.utils.usb.switches.TigertailUsbSwitch;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link PowerTestUsbSwitchProvider}. */
@RunWith(JUnit4.class)
public class PowerTestUsbSwitchProviderTest {
    private static final String DEVICE_SERIAL = "serial";

    @Test
    public void testBuildareCanBeUsedAtMostOnce() {
        PowerTestUsbSwitchProvider.Builder builder = PowerTestUsbSwitchProvider.Builder();
        builder.build();

        try {
            builder.build();
            Assert.fail("Second call to build should throw an exception.");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testFindsTigertailSwitch() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .tigertailSerial("12345")
                        .useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        for (IUsbSwitch s : multiSwitch.getSwitchesList())
            System.out.println(s.getClass().toString());
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                TigertailUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsDatamationUsbSwitch() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder().datamationPort("5").build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                DatamationUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsNCDUsbSwitch() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder().ncdPort("10").build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(NcdUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsMonsoonUsbSwitchByMonsoonSerialNumber() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder().monsoonSerial("1234").build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                MonsoonUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsDatamationByLabSetupMap() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .deviceSerial(DEVICE_SERIAL)
                        .labSetupStreamReader(asStream("device:serial datamation:1234"))
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                DatamationUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsNcdByLabSetupMap() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial ncd:1234"))
                        .deviceSerial(DEVICE_SERIAL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(NcdUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsMonsoonUsbSwitchByLabSetupMap() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial monsoon:1234"))
                        .deviceSerial(DEVICE_SERIAL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                MonsoonUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindsTigertailUsbSwitchByMapSetupFile() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial tigertail:1234"))
                        .deviceSerial(DEVICE_SERIAL)
                        .useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                TigertailUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testComplaintsIfTigertailIsNeededButMissingFromMapSetupFile() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial"))
                        .deviceSerial(DEVICE_SERIAL)
                        .useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL)
                        .build();

        try {
            switchProvider.getUsbSwitch();
            Assert.fail("Tigertail was required but never provided. Exception was expected.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testIgnoresTigertailUsbSwitchIfSpecified() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial tigertail:1234"))
                        .deviceSerial(DEVICE_SERIAL)
                        .useTigertail(
                                PowerTestUsbSwitchProvider.UseTigertailOption.DO_NOT_USE_TIGERTAIL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(0, multiSwitch.getSwitchesList().size());
    }

    @Test
    public void testFindsTigertailIfPresentUsbSwitchIfSpecified() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream("device:serial tigertail:1234"))
                        .deviceSerial(DEVICE_SERIAL)
                        .useTigertail(
                                PowerTestUsbSwitchProvider.UseTigertailOption.USE_IF_AVAILABLE)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();
        Assert.assertEquals(1, multiSwitch.getSwitchesList().size());
        Assert.assertEquals(
                TigertailUsbSwitch.class, multiSwitch.getSwitchesList().get(0).getClass());
    }

    @Test
    public void testFindSwitchCombo() {
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL)
                        .monsoonSerial("1234")
                        .tigertailSerial("54321")
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();

        List<Class<?>> types = toTypeList(multiSwitch.getSwitchesList());
        Assert.assertTrue(types.contains(TigertailUsbSwitch.class));
        Assert.assertTrue(types.contains(MonsoonUsbSwitch.class));

        // Assert those are the only two
        Assert.assertEquals(2, multiSwitch.getSwitchesList().size());
    }

    @Test
    public void testFindSwitchComboFromLabSetupMap() {
        String setupMapLine = "device:serial tigertail:1234 monsoon:321 datamation:1 ncd:2";
        PowerTestUsbSwitchProvider switchProvider =
                PowerTestUsbSwitchProvider.Builder()
                        .labSetupStreamReader(asStream(setupMapLine))
                        .deviceSerial(DEVICE_SERIAL)
                        .useTigertail(PowerTestUsbSwitchProvider.UseTigertailOption.USE_TIGERTAIL)
                        .build();

        MultiUsbSwitch multiSwitch = (MultiUsbSwitch) switchProvider.getUsbSwitch();

        List<Class<?>> types = toTypeList(multiSwitch.getSwitchesList());
        Assert.assertTrue(types.contains(TigertailUsbSwitch.class));
        Assert.assertTrue(types.contains(MonsoonUsbSwitch.class));
        Assert.assertTrue(types.contains(DatamationUsbSwitch.class));
        Assert.assertTrue(types.contains(NcdUsbSwitch.class));

        // Assert those are the only four.
        Assert.assertEquals(4, multiSwitch.getSwitchesList().size());
    }

    private List<Class<?>> toTypeList(List<?> list) {
        List<Class<?>> result = new ArrayList<Class<?>>();
        for (Object element : list) {
            result.add(element.getClass());
        }

        return result;
    }

    private InputStreamReader asStream(String... lines) {
        String stringthing = String.join("\n", lines);
        return new InputStreamReader(new ByteArrayInputStream(stringthing.getBytes()));
    }
}
