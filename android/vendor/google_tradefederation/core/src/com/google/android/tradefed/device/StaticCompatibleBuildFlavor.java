// Copyright 2017 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utility to get the compatible known build flavors associated with a product type. like
 * sturgeon and sturgeon_sw for the sturgeon product type. The map is used to match the real device
 * product type and the build product type: Build server allow to create different build product
 * type for the same device type, so to reconciliate where they should go we use a mapping.
 */
public class StaticCompatibleBuildFlavor {
    private static final Map<String, List<String>> MAP = new HashMap<>();

    private static void add(String productType, String... lcFlavors) {
        MAP.put(productType, Arrays.asList(lcFlavors));
    }

    // Underlying hardware is the same, but the build-flavor differs.
    static {
        add("angler", "angler", "aosp_angler", "angler_bootpreopt", "angler_nopreload",
                "angler_asan");
        add("bullhead", "bullhead", "aosp_bullhead");
        add("bowfin", "bowfin", "bowfin_sw");
        add("marlin", "marlin", "marlin_coverage", "aosp_marlin");
        add("sailfish", "sailfish", "sailfish_coverage", "aosp_sailfish");
        add("sawfish", "sawfish", "sawfish_sw");
        add("sawshark", "sawshark", "sawshark_sw", "sawshark_wh");
        add("smelt", "smelt", "smelt_sw");
        add("sparrow", "sparrow", "sparrow_sw");
        add("sturgeon", "sturgeon", "sturgeon_sw");
        add("taimen", "taimen", "taimen_asan_coverage", "aosp_taimen");
        add("walleye", "walleye", "walleye_asan_coverage", "aosp_walleye");
    }

    /**
     * Returns the compatible known build flavors associated with the product type if there are
     * several alternatives.
     */
    public static List<String> getPossibleFlavors(String productType) {
        return MAP.get(productType);
    }
}
