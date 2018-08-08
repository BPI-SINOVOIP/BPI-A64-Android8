// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.android.tradefed.device;

import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.targetprep.IDeviceFlasher;
import com.android.tradefed.targetprep.SystemUpdaterDeviceFlasher;
import com.android.tradefed.util.RegexTrie;

import com.google.android.tradefed.targetprep.AndroidThingsDeviceFlasher;
import com.google.android.tradefed.targetprep.AngelfishDeviceFlasher;
import com.google.android.tradefed.targetprep.AnglerDeviceFlasher;
import com.google.android.tradefed.targetprep.AnthiasDeviceFlasher;
import com.google.android.tradefed.targetprep.AnthraciteDeviceFlasher;
import com.google.android.tradefed.targetprep.AvkoDeviceFlasher;
import com.google.android.tradefed.targetprep.AyuDeviceFlasher;
import com.google.android.tradefed.targetprep.BassDeviceFlasher;
import com.google.android.tradefed.targetprep.BatLandDeviceFlasher;
import com.google.android.tradefed.targetprep.BluegillDeviceFlasher;
import com.google.android.tradefed.targetprep.BullheadDeviceFlasher;
import com.google.android.tradefed.targetprep.CarpDeviceFlasher;
import com.google.android.tradefed.targetprep.CrespoDeviceFlasher;
import com.google.android.tradefed.targetprep.CrespoSDeviceFlasher;
import com.google.android.tradefed.targetprep.DoradoDeviceFlasher;
import com.google.android.tradefed.targetprep.DoryDeviceFlasher;
import com.google.android.tradefed.targetprep.DragonDeviceFlasher;
import com.google.android.tradefed.targetprep.ElfinDeviceFlasher;
import com.google.android.tradefed.targetprep.FuguDeviceFlasher;
import com.google.android.tradefed.targetprep.GarDeviceFlasher;
import com.google.android.tradefed.targetprep.GlacierDeviceFlasher;
import com.google.android.tradefed.targetprep.GlowlightDeviceFlasher;
import com.google.android.tradefed.targetprep.GordonPeakDeviceFlasher;
import com.google.android.tradefed.targetprep.GrantDeviceFlasher;
import com.google.android.tradefed.targetprep.HammerheadDeviceFlasher;
import com.google.android.tradefed.targetprep.HikeyDeviceFlasher;
import com.google.android.tradefed.targetprep.KoiDeviceFlasher;
import com.google.android.tradefed.targetprep.LenokDeviceFlasher;
import com.google.android.tradefed.targetprep.LionfishDeviceFlasher;
import com.google.android.tradefed.targetprep.MantarayDeviceFlasher;
import com.google.android.tradefed.targetprep.MarFishDeviceFlasher;
import com.google.android.tradefed.targetprep.MinnowDeviceFlasher;
import com.google.android.tradefed.targetprep.MollyDeviceFlasher;
import com.google.android.tradefed.targetprep.Nakasi3gDeviceFlasher;
import com.google.android.tradefed.targetprep.NakasiDeviceFlasher;
import com.google.android.tradefed.targetprep.NemoDeviceFlasher;
import com.google.android.tradefed.targetprep.NexusOneDeviceFlasher;
import com.google.android.tradefed.targetprep.OccamDeviceFlasher;
import com.google.android.tradefed.targetprep.PikeDeviceFlasher;
import com.google.android.tradefed.targetprep.PlatyDeviceFlasher;
import com.google.android.tradefed.targetprep.PrimeCdmaDeviceFlasher;
import com.google.android.tradefed.targetprep.PrimeGsmDeviceFlasher;
import com.google.android.tradefed.targetprep.RazorDeviceFlasher;
import com.google.android.tradefed.targetprep.SapphireDeviceFlasher;
import com.google.android.tradefed.targetprep.SawfishDeviceFlasher;
import com.google.android.tradefed.targetprep.SawsharkDeviceFlasher;
import com.google.android.tradefed.targetprep.SculpinDeviceFlasher;
import com.google.android.tradefed.targetprep.SeedDeviceFlasher;
import com.google.android.tradefed.targetprep.ShamuDeviceFlasher;
import com.google.android.tradefed.targetprep.ShastaDeviceFlasher;
import com.google.android.tradefed.targetprep.ShinerDeviceFlasher;
import com.google.android.tradefed.targetprep.SholesDeviceFlasher;
import com.google.android.tradefed.targetprep.SmeltDeviceFlasher;
import com.google.android.tradefed.targetprep.SparrowDeviceFlasher;
import com.google.android.tradefed.targetprep.SpectraliteDeviceFlasher;
import com.google.android.tradefed.targetprep.SpratDeviceFlasher;
import com.google.android.tradefed.targetprep.SproutDeviceFlasher;
import com.google.android.tradefed.targetprep.StargazerDeviceFlasher;
import com.google.android.tradefed.targetprep.StingrayDeviceFlasher;
import com.google.android.tradefed.targetprep.SturgeonDeviceFlasher;
import com.google.android.tradefed.targetprep.SundialDeviceFlasher;
import com.google.android.tradefed.targetprep.SwiftDeviceFlasher;
import com.google.android.tradefed.targetprep.SwordfishDeviceFlasher;
import com.google.android.tradefed.targetprep.TaimenDeviceFlasher;
import com.google.android.tradefed.targetprep.TetraDeviceFlasher;
import com.google.android.tradefed.targetprep.TungstenDeviceFlasher;
import com.google.android.tradefed.targetprep.VolantisDeviceFlasher;
import com.google.android.tradefed.targetprep.WallskieDeviceFlasher;
import com.google.android.tradefed.targetprep.WolfieDeviceFlasher;
import com.google.android.tradefed.targetprep.WrenDeviceFlasher;

/**
 * A non-instantiable class to encapsulate proprietary information about devices
 * <p />
 * Currently knows which flasher to use for which device, as well as which Launch Control build
 * flavor.
 */
public class StaticDeviceInfo {
    // Crespo requires a special case, so store some useful constants here
    public static final String CRESPO_PRODUCT_TYPE = "herring";
    public static final String CRESPO_VARIANT = "crespo";
    public static final String CRESPO4G_VARIANT = "crespo4g";

    public static final String TUNGSTEN_PRODUCT = "steelhead";
    public static final String WOLFIE_PRODUCT = "sheepshead";
    public static final String MOLLY_PRODUCT = "molly";
    public static final String FLOUNDER_PRODUCT = "flounder";

    // Ayu is a variant of Koi, so group them together
    public static final String KOI_PRODUCT = "koi";
    public static final String AYU_PRODUCT = "ayu";

    public static final String DORY_PRODUCT = "dory";
    public static final String MINNOW_PRODUCT = "minnow";
    public static final String SPRAT_PRODUCT = "sprat";
    public static final String LENOK_PRODUCT = "lenok";
    public static final String SPROUT_PRODUCT = "sprout";
    public static final String TETRA_PRODUCT = "tetra";
    public static final String ANTHIAS_PRODUCT = "anthias";
    public static final String BASS_PRODUCT = "bass";
    public static final String SEED_PRODUCT = "seed";
    public static final String STURGEON_PRODUCT = "sturgeon";
    public static final String SPARROW_PRODUCT = "sparrow";
    public static final String SMELT_PRODUCT = "smelt";
    public static final String CARP_PRODUCT = "carp";
    public static final String BOWFIN_PRODUCT = "bowfin";
    public static final String WREN_PRODUCT = "wren";
    public static final String NEMO_PRODUCT = "nemo";
    public static final String GRANT_PRODUCT = "grant";
    public static final String GLACIER_PRODUCT = "glacier";
    public static final String PIKE_PRODUCT = "pike";
    public static final String SWORDFISH_PRODUCT = "swordfish";
    public static final String ANGELFISH_PRODUCT = "angelfish";
    public static final String GAR_PRODUCT = "gar";
    public static final String SCULPIN_PRODUCT = "sculpin";
    public static final String SWIFT_PRODUCT = "swift";
    public static final String DORADO_PRODUCT = "dorado";
    public static final String SHASTA_PRODUCT = "shasta";
    public static final String SAWFISH_PRODUCT = "sawfish";
    public static final String SAWSHARK_PRODUCT = "sawshark";
    public static final String ANTHRACITE_PRODUCT = "anthracite";
    public static final String GORDON_PEAK_PRODUCT = "gordon_peak";
    public static final String PLATY_PRODUCT = "platy";
    public static final String LIONFISH_PRODUCT = "lionfish";
    public static final String STARGAZER_PRODUCT = "stargazer";
    public static final String BLUEGILL_PRODUCT = "bluegill";
    public static final String SHINER_PRODUCT = "shiner";
    public static final String GLOWLIGHT_PRODUCT = "glowlight";
    public static final String SPECTRALITE_PRODUCT = "spectralite";


    /**
     * A struct to hold data about a device
     */
    private static class DeviceInfo {
        public Class<? extends IDeviceFlasher> flasherClass = null;
        public String lcFlavor = null;


        @SuppressWarnings("unused")
        public DeviceInfo() {
        }

        public DeviceInfo(Class<? extends IDeviceFlasher> klass, String flavor) {
            flasherClass = klass;
            lcFlavor = flavor;
        }
    }

    /**
     * A Trie to map a product type (and possible variant) to a flasher.
     * <p />
     * For retrievals, the first key will be the product type, and the second will be the product
     * variant (which will be the empty string if no variant is reported).  Entries that only care
     * about the product type and not the variant should store the {@code null} wildcard in the
     * second position.
     */
    private static final RegexTrie<DeviceInfo> MAP = new RegexTrie<DeviceInfo>();

    /**
     * A small convenience method to add new entries to the static mapping
     *
     * <p>
     *
     * @param product Should match value retrieved by {@link NativeDevice#getProductType()}. Note
     *     the tradefed console command 'list devices' will display the value in the product column.
     * @param variant Should match value retrieved by {@link NativeDevice#getProductVariant()}. Note
     *     the tradefed console command 'list devices' will display the value in the variant column.
     */
    private static void add(
            String product, String variant, Class<? extends IDeviceFlasher> klass, String flavor) {
        DeviceInfo info = new DeviceInfo(klass, flavor);
        MAP.put(info, product, variant);
    }

    static {
        // Google TV
        add("berlin", null, SystemUpdaterDeviceFlasher.class, null);
        add("ka", null, SystemUpdaterDeviceFlasher.class, null);
        add("tatung4", null, SystemUpdaterDeviceFlasher.class, null);

        // sapphire
        add("sapphire", null, SapphireDeviceFlasher.class, "opal");

        // sholes
        add("sholes", null, SholesDeviceFlasher.class, "voles");

        // passion
        add("mahimahi", null, NexusOneDeviceFlasher.class, "passion");

        // crespo
        add(CRESPO_PRODUCT_TYPE, CRESPO_VARIANT, CrespoDeviceFlasher.class, "soju");
        add(CRESPO_PRODUCT_TYPE, CRESPO4G_VARIANT, CrespoSDeviceFlasher.class, "sojus");

        // stingray/wingray
        for (String type : StingrayDeviceFlasher.STINGRAY_PRODUCT_TYPES) {
            // Don't even try to specify a default LC flavor for stingray/wingray
            add(type, null, StingrayDeviceFlasher.class, null);
        }

        // prime
        add("tuna", "maguro( (16|32)GB)?", PrimeGsmDeviceFlasher.class, "yakju");
        add("tuna", "toro( (16|32)GB)?", PrimeCdmaDeviceFlasher.class, "mysid");

        // tungsten in adb
        add(TUNGSTEN_PRODUCT, "phantasm", TungstenDeviceFlasher.class, "tungsten");
        // tungsten in fastboot - no variant
        add(TUNGSTEN_PRODUCT, null, TungstenDeviceFlasher.class, "tungsten");

        // wolfie in adb
        add(WOLFIE_PRODUCT, "wolfie", WolfieDeviceFlasher.class, "wolfie");
        // wolfie in fastboot - no variant
        add(WOLFIE_PRODUCT, null, WolfieDeviceFlasher.class, "wolfie");

        // molly in adb
        add(MOLLY_PRODUCT, "molly", MollyDeviceFlasher.class, "molly");
        // molly in fastboot - no variant
        add(MOLLY_PRODUCT, null, MollyDeviceFlasher.class, "molly");

        // nakasi
        add("grouper", "grouper", NakasiDeviceFlasher.class, "nakasi");
        // nakasi in fastboot - no variant
        add("grouper", null, NakasiDeviceFlasher.class, "nakasi");

        // nakasi 3g in userspace
        add("grouper", "tilapia", Nakasi3gDeviceFlasher.class, "nakasig");
        // nakasi 3g in fastboot
        // FIXME: this should be grouper:tilapia
        add("tilapia", null, Nakasi3gDeviceFlasher.class, "nakasig");

        // mantaray
        add("manta", null, MantarayDeviceFlasher.class, "mantaray");

        // occam
        add("mako", "mako", OccamDeviceFlasher.class, "occam");
        // occam in fastboot - no variant
        add("mako", null, OccamDeviceFlasher.class, "occam");

        // razor
        add("flo", "flo", RazorDeviceFlasher.class, "razor");
        add("flo", "deb", RazorDeviceFlasher.class, "razorg");

        // hammerhead
        // variants in fastboot can have extra postfixes, so just match any hammerhead variant
        add("hammerhead", "hammerhead.*", HammerheadDeviceFlasher.class, "hammerhead");

        // dory in adb
        add(DORY_PRODUCT, DORY_PRODUCT, DoryDeviceFlasher.class, "platina");
        // dory in fastboot - variants have extra postfixes, match any dory variant
        add(DORY_PRODUCT, "dory.*", DoryDeviceFlasher.class, "platina");

        // sprat
        add(SPRAT_PRODUCT, null, SpratDeviceFlasher.class, SPRAT_PRODUCT);

        // minnow
        add(MINNOW_PRODUCT, MINNOW_PRODUCT, MinnowDeviceFlasher.class, "metallica");

        // volantis
        add("flounder", null, VolantisDeviceFlasher.class, "volantis");
        // no special radio flashing required for volantis lte, since radio image is bundled inside
        // system image & update (if needed) is handled after device boots into userspace
        add("flounder", "flounder_lte", VolantisDeviceFlasher.class, "volantisg");

        // sprout
        add(SPROUT_PRODUCT, null, SproutDeviceFlasher.class, SPROUT_PRODUCT);
        add(SPROUT_PRODUCT, "4560MMX_sprout", SproutDeviceFlasher.class, SPROUT_PRODUCT);

        // shamu
        add("shamu", null, ShamuDeviceFlasher.class, "shamu");

        // lenok
        add(LENOK_PRODUCT, null, LenokDeviceFlasher.class, LENOK_PRODUCT);

        // tetra
        add(TETRA_PRODUCT, null, TetraDeviceFlasher.class, TETRA_PRODUCT);

        // fugu
        add("fugu", null, FuguDeviceFlasher.class, "fugu");

        // anthias
        add(ANTHIAS_PRODUCT, null, AnthiasDeviceFlasher.class, ANTHIAS_PRODUCT);

        // bass
        add(BASS_PRODUCT, null, BassDeviceFlasher.class, BASS_PRODUCT);

        // seed
        add(SEED_PRODUCT, null, SeedDeviceFlasher.class, "seed_l8150");
        add(SEED_PRODUCT, "l8150_sprout", SeedDeviceFlasher.class, "seed_l8150");

        // sturgeon
        add(STURGEON_PRODUCT, null, SturgeonDeviceFlasher.class, STURGEON_PRODUCT);

        // sparrow
        add(SPARROW_PRODUCT, SPARROW_PRODUCT, SparrowDeviceFlasher.class, SPARROW_PRODUCT);

        // angler
        add("angler", null, AnglerDeviceFlasher.class, "angler");

        // bullhead
        add("bullhead", null, BullheadDeviceFlasher.class, "bullhead");

        // dragon
        add("dragon", null, DragonDeviceFlasher.class, "ryu");

        // carp
        add(CARP_PRODUCT, CARP_PRODUCT, CarpDeviceFlasher.class, CARP_PRODUCT);

        // smelt
        add(SMELT_PRODUCT, SMELT_PRODUCT, SmeltDeviceFlasher.class, SMELT_PRODUCT);

        // bowfin
        add(BOWFIN_PRODUCT, CARP_PRODUCT, CarpDeviceFlasher.class, BOWFIN_PRODUCT);

        // wren
        add(WREN_PRODUCT, WREN_PRODUCT, WrenDeviceFlasher.class, WREN_PRODUCT);

        //nemo
        add(NEMO_PRODUCT, null, NemoDeviceFlasher.class, NEMO_PRODUCT);

        //grant
        add(GRANT_PRODUCT, GRANT_PRODUCT, GrantDeviceFlasher.class, GRANT_PRODUCT);

        //glacier
        add(GLACIER_PRODUCT, GLACIER_PRODUCT, GlacierDeviceFlasher.class, GLACIER_PRODUCT);

        //koi
        add(KOI_PRODUCT, KOI_PRODUCT, KoiDeviceFlasher.class, KOI_PRODUCT);

        //pike
        add(PIKE_PRODUCT, PIKE_PRODUCT, PikeDeviceFlasher.class, PIKE_PRODUCT);

        // swordfish
        add(SWORDFISH_PRODUCT, null, SwordfishDeviceFlasher.class, SWORDFISH_PRODUCT);

        // avko
        add("avko", null, AvkoDeviceFlasher.class, "avko");

        // sailfish
        add("sailfish", null, MarFishDeviceFlasher.class, "sailfish");

        // marlin
        add("marlin", null, MarFishDeviceFlasher.class, "marlin");

        // angelfish
        add(ANGELFISH_PRODUCT, null, AngelfishDeviceFlasher.class, ANGELFISH_PRODUCT);

        // gar
        add(GAR_PRODUCT, GAR_PRODUCT, GarDeviceFlasher.class, GAR_PRODUCT);

        // sculpin
        add(SCULPIN_PRODUCT, SCULPIN_PRODUCT, SculpinDeviceFlasher.class, SCULPIN_PRODUCT);

        // Swift
        add(SWIFT_PRODUCT, SWIFT_PRODUCT, SwiftDeviceFlasher.class, SWIFT_PRODUCT);

        // IOT Devices (Brillo)
        add("edison", "edison", AndroidThingsDeviceFlasher.class, "iot_edison");
        add("imx6ul", "imx6ul_aquila", AndroidThingsDeviceFlasher.class, "iot_imx6ul_aquila");
        add("imx6ul", "imx6ul_iopb", AndroidThingsDeviceFlasher.class, "iot_imx6ul_iopb");
        add("imx6ul", "imx6ul_pico", AndroidThingsDeviceFlasher.class, "iot_imx6ul_pico");
        add("imx7d", "imx7d_pico", AndroidThingsDeviceFlasher.class, "iot_imx7d_pico");
        add("joule", "joule", AndroidThingsDeviceFlasher.class, "iot_joule");

        // hikey
        add("hikey", null, HikeyDeviceFlasher.class, "hikey");

        // Dorado
        add(DORADO_PRODUCT, null, DoradoDeviceFlasher.class, DORADO_PRODUCT);

        // Shasta
        add(SHASTA_PRODUCT, null, ShastaDeviceFlasher.class, SHASTA_PRODUCT);

        // Sawfish
        add(SAWFISH_PRODUCT, null, SawfishDeviceFlasher.class, SAWFISH_PRODUCT);

        // Sawshark
        add(SAWSHARK_PRODUCT, null, SawsharkDeviceFlasher.class, SAWSHARK_PRODUCT);

        // Anthracite
        add(ANTHRACITE_PRODUCT, null, AnthraciteDeviceFlasher.class, ANTHRACITE_PRODUCT);

        // Ayu
        add(AYU_PRODUCT, AYU_PRODUCT, AyuDeviceFlasher.class, AYU_PRODUCT);

        // gordonpeak
        add(GORDON_PEAK_PRODUCT, null, GordonPeakDeviceFlasher.class, GORDON_PEAK_PRODUCT);

        // Platy
        add(PLATY_PRODUCT, null, PlatyDeviceFlasher.class, PLATY_PRODUCT);

        // Lionfish
        add(LIONFISH_PRODUCT, null, LionfishDeviceFlasher.class, LIONFISH_PRODUCT);

        // Sundial
        add("sundial", null, SundialDeviceFlasher.class, "sundial");

        // Stargazer
        add(STARGAZER_PRODUCT, null, StargazerDeviceFlasher.class, STARGAZER_PRODUCT);

        // Bluegill
        add(BLUEGILL_PRODUCT, null, BluegillDeviceFlasher.class, BLUEGILL_PRODUCT);

        // Shiner
        add(SHINER_PRODUCT, null, ShinerDeviceFlasher.class, SHINER_PRODUCT);

        // Spectralite
        add(SPECTRALITE_PRODUCT, null, SpectraliteDeviceFlasher.class, SPECTRALITE_PRODUCT);

        // Glowlight
        add(GLOWLIGHT_PRODUCT, null, GlowlightDeviceFlasher.class, GLOWLIGHT_PRODUCT);

        // muskie, walleye
        add("muskie", null, WallskieDeviceFlasher.class, "muskie");
        add("walleye", null, WallskieDeviceFlasher.class, "walleye");

        //bat_land (Qualcomm) device for auto.
        add("qcom", "bat", BatLandDeviceFlasher.class, "bat_land");

        add("taimen", null, TaimenDeviceFlasher.class, "taimen");

        // elfin
        add("elfin", null, ElfinDeviceFlasher.class, "elfin");
    }

    // Make non-instantiable
    private StaticDeviceInfo() {
    }

    /**
     * Method to fetch the flasher class to use for a given product type/variant
     */
    public static Class<? extends IDeviceFlasher> getFlasherClass(String type, String variant) {
        if (type == null) {
            return null;
        }
        if (variant == null) {
            // We protect RegexTrie from NPE by replacing with empty string. This still allow to
            // match against 'null' when no specific variant is expected.
            variant = "";
        }

        String[] key = new String[]{type, variant};
        DeviceInfo info = MAP.retrieve(key);

        if (info == null) {
            return null;
        } else {
            return info.flasherClass;
        }
    }

    /**
     * Method to fetch the default LaunchControl build flavor for a given product type/variant
     *
     * @return the build flavor or <code>null</code> if not found
     */
    public static String getDefaultLcFlavor(String type, String variant) {
        if (type == null) {
            return null;
        }
        if (variant == null) {
            variant = "";
        }

        String[] key = new String[]{type, variant};
        DeviceInfo info = MAP.retrieve(key);

        if (info == null) {
            return null;
        } else {
            return info.lcFlavor;
        }
    }

    /**
     * Special case for herring: determine the variant by examining the bootloader string
     */
    public static String getCrespoVariantFromBootloader(String bootloader) {
        if (bootloader == null) {
            return "(error: unknown bootloader version)";
        } else if (bootloader.startsWith("D720")) {
            // "D720" is CDMA Crespo 4g
            return CRESPO4G_VARIANT;
        } else if (bootloader.startsWith("I902")) {
            // "I902" is GSM Crespo
            return CRESPO_VARIANT;
        } else {
            return String.format("(error: unknown bootloader version '%s'.)", bootloader);
        }
    }
}

