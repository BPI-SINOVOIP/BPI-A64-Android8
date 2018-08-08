// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.android.tradefed.targetprep;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.RunUtil;

/**
 * A {@link ITargetPreparer} that configures device with desired hardware performance, such as
 * locking CPU frequencies and GPU frequencies, setting CPU governors and stopping runtime.
 * Please refer to page:
 * https://docs.google.com/a/google.com/document/d/1qu-ZCFjjpT00AhyuDIFEOf5W9nPyZ8E3RRD7numY-xM/edit
 * <p/>
 */
@OptionClass(alias = "hw-performance-setup")
public class HardwarePerformancePreparer implements ITargetPreparer {

    private static final String SCREEN_OFF = "input keyevent 26";

    @Option(name = "cpu-frequency", description = "cpu frequency to be set")
    private String mCpuFrequency = null;

    @Option(name = "cpu-governor", description = "cpu governor to be set")
    private String mCpuGovernor = null;

    @Option(name = "gpu-frequency", description = "gpu frequency to be set")
    private String mGpuFrequency = null;

    @Option(name = "gpu-state", description = "gpu state maps to the frequency to be set")
    private String mGpuState = null;

    @Option(name = "gpu-governor", description = "gpu governor to be set")
    private String mGpuGovernor = null;

    @Option(name = "stop-run-time-screen",
            description = "flag to stop the framework and turn screen off")
    private boolean mStopRuntimeScreenFlag = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
        DeviceNotAvailableException {
        if ((mCpuFrequency == null) && (mGpuFrequency == null) &&
                (mCpuGovernor == null) && !mStopRuntimeScreenFlag) {
            return;
        }

        // lock CPU and GPU performance
        String productType = device.getProductType();
        String productVariant = device.getProductVariant();
        if ("hammerhead".equals(productType) || "mako".equals(productType)
                || "flo".equals(productType)) {
            nexusFourToSevenHardwareSetup(device);
        } else if ("shamu".equals(productType)) {
            nexusSixHardwareSetup(device);
        } else if (productType.equals("manta")) {
            nexusTenHardwareSetup(device);
        } else if (productType.equals("flounder")) {
            volantisHardwareSetup(device);
        } else if (productType.equals("sprout")){
            sproutHardwareSetup(device);
        } else if (productType.equals("fugu")){
            fuguHardwareSetup(device);
        } else if (productType.equals("avko")){
            avkoHardwareSetup(device);
        } else if (productType.equals("molly")) {
            mollyHardwareSetup(device);
        } else if (productType.equals("bullhead")) {
            bullheadHardwareSetup(device);
        } else if (productType.equals("angler")) {
            anglerHardwareSetup(device);
        } else if (productType.equals("dragon")) {
            ryuHardwareSetup(device);
        } else if (productType.equals("seed")){
            seedHardwareSetup(device);
        } else if (productType.equals("marlin") || productType.equals("sailfish")) {
            marsailHardwareSetup(device);
        } else if (productType.equals("gordon_peak")) {
            gordonPeakHardwareSetup(device);
        } else if (productType.equals("qcom") && productVariant.equals("bat")) {
            mojaveHardwareSetup(device);
        } else {
            throw new TargetSetupError(String.format("please create a new performance setup"
                    + "method for product %s", productType), device.getDeviceDescriptor());
        }

        // turn screen off and stop runtime. On N10, "adb shell stop" won't turn the screen off
        if (mStopRuntimeScreenFlag) {
            device.executeShellCommand(SCREEN_OFF);
            RunUtil.getDefault().sleep(5*1000);
            String output = device.executeShellCommand("dumpsys power");
            // check mInteractive after KKWT, check mScreenOn before that.
            if (output == null ||
                    !(output.contains("mInteractive=false") ||
                            output.contains("mScreenOn=false"))) {
                // if no output or screen is still on, throw an exception
                CLog.w("failed to turn screen off during device setup.");
            }
            device.executeShellCommand("stop");
        }
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void gordonPeakHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {

        final int NUM_CPUS = 4;

        //set CPU governor
        if (mCpuGovernor != null) {
            for (int i = 0; i < NUM_CPUS; i++) {
                device.executeShellCommand(
                        String.format(
                                "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor",
                                mCpuGovernor, i));
            }
        }

        //lock CPU to the given frequency, as suggested in b/38273402
        if (mCpuFrequency != null) {
            for (int i = 0; i < NUM_CPUS; i++) {
                device.executeShellCommand(
                        String.format(
                                "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq",
                                mCpuFrequency, i));
                device.executeShellCommand(
                        String.format(
                                "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq",
                                mCpuFrequency, i));
            }
        }

        //lock GPU to the given frequency, as suggested in b/38273402
        if (mGpuFrequency != null) {
            device.executeShellCommand(
                    String.format(
                            "echo -n %s > /sys/kernel/debug/dri/0/i915_max_freq", mGpuFrequency));
            device.executeShellCommand(
                    String.format(
                            "echo -n %s > /sys/kernel/debug/dri/0/i915_min_freq", mGpuFrequency));
        }
    }

    /**
     * @param device
     * @throw DeviceNotAvailableException
     */
    private void mojaveHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {

        //Inspired by benchmark recommendations in b/23629533
        //disable little cores (cpu0, cpu1)
        for (int i = 0; i < 2; i++) {
            device.executeShellCommand(
                    String.format("echo 0 > /sys/devices/system/cpu/cpu%d/online", i));
        }

        //enable big cores (cpu2, cpu3) and lock max frequency by setting performance governor
        for (int i = 2; i < 4; i++) {
            device.executeShellCommand(
                    String.format("echo 1 > /sys/devices/system/cpu/cpu%d/online", i));
            device.executeShellCommand(
                    String.format(
                            "echo performance > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor",
                            i));
        }

        //lock GPUs to the max frequency
        device.executeShellCommand(
                String.format("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor"));
        device.executeShellCommand(String.format("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split"));
        device.executeShellCommand(String.format("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on"));
        device.executeShellCommand(
                String.format("echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer"));
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void fuguHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException, TargetSetupError {

        // stop services that might interfere with performance, thermal service and perf profiler
        if ("1".equals(device.getProperty("persist.service.thermal"))) {
            throw new TargetSetupError("failed to stop thermal service. Please make sure the "
                    + "persist.service.thermal property is set to 0 and reboot to take it effect.",
                    device.getDeviceDescriptor());
        }
        device.executeShellCommand("stop perfprofd");

        // lock CPU speed to the max freq
        if (mCpuFrequency != null) {
            // enable CPUs
            // and set the governor policy of all CPUs to userspace to control the settings manually
            if (mCpuGovernor == null) {
                mCpuGovernor = "userspace";
            }
            final int NUM_CPUS = 4;
            for (int i = 0; i < NUM_CPUS; ++i) {
                device.executeShellCommand(String.format(
                        "echo 1 > /sys/devices/system/cpu/cpu%d/online", i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor",
                        mCpuGovernor, i));
            }

            for (int i = 0; i < NUM_CPUS; ++i) {
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq",
                        mCpuFrequency, i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq",
                        mCpuFrequency, i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed",
                        mCpuFrequency, i));
            }
        }

        // lock GPU speed to the max freq
        if (mGpuGovernor != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > /sys/class/devfreq/dfrgx/governor", mGpuGovernor));
        }
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > /sys/class/devfreq/dfrgx/max_freq", mGpuFrequency));
            device.executeShellCommand(String.format(
                    "echo -n %s > /sys/class/devfreq/dfrgx/min_freq", mGpuFrequency));
        }
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void avkoHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {

        // stop services that might interfere with performance, perf profiler
        device.executeShellCommand("stop perfprofd");

        // lock CPU speed to the max freq
        if (mCpuFrequency != null) {
            // enable CPUs
            // and set the governor policy of all CPUs to userspace to control the settings manually
            if (mCpuGovernor == null) {
                mCpuGovernor = "userspace";
            }
            final int NUM_CPUS = 2;
            for (int i = 0; i < NUM_CPUS; ++i) {
                device.executeShellCommand(String.format(
                        "echo 1 > /sys/devices/system/cpu/cpu%d/online", i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor",
                        mCpuGovernor, i));
            }

            for (int i = 0; i < NUM_CPUS; ++i) {
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq",
                        mCpuFrequency, i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq",
                        mCpuFrequency, i));
                device.executeShellCommand(String.format(
                        "echo -n %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_setspeed",
                        mCpuFrequency, i));
            }
        }

        // Avko always runs GPU at highest possible speed.
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void mollyHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(
                "echo 0 > /sys/devices/system/cpu/cpuquiet/tegra_cpuquiet/enable");
        device.executeShellCommand(
                "echo \"G\" > /sys/kernel/cluster/active");
        device.executeShellCommand("echo 1 >/sys/devices/system/cpu/cpuquiet/tegra_cpuquiet/no_lp");

        for(int i = 0; i < 4; i ++) {
            device.executeShellCommand(
                    String.format("echo 1 > /sys/devices/system/cpu/cpu%d/online", i));
        }

        for(int i = 0; i < 4; i ++) {
            device.executeShellCommand(
                    String.format(
                            "echo %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_min_freq",
                            mCpuFrequency, i));
            device.executeShellCommand(
                    String.format(
                            "echo %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_max_freq",
                            mCpuFrequency, i));
            device.executeShellCommand(
                    String.format(
                            "echo %s > /sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq",
                            mCpuFrequency, i));
        }

        device.executeShellCommand("echo 0 > /sys/devices/platform/host1x/gr3d/enable_3d_scaling");
        device.executeShellCommand(
                String.format("echo %s > /d/clock/floor.c2bus/rate",mGpuFrequency));
        device.executeShellCommand("echo 1 > /d/clock/floor.c2bus/state");
    }

    /**
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void sproutHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";

        if (mCpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n userspace > %s/cpu0/%s", cpuBase, gov));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_max_freq", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_min_freq", mCpuFrequency, cpuBase));
        }
        //TODO(wsmlby): add GPU freq lock.
    }

    /**
     * Hardware setup for N4, N5 and N7.
     */
    private void nexusFourToSevenHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        // disable power collapse
        device.executeShellCommand("stop mpdecision");
        // lock performance on all four CPUs
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";

        if (mCpuGovernor != null) {
            for (int i = 0; i < 4; i++) {
                device.executeShellCommand(String.format(
                        "echo -n 1 > %s/cpu%d/online", cpuBase, i));
                device.executeShellCommand(
                        String.format("echo -n %s > %s/cpu%d/%s", mCpuGovernor, cpuBase, i, gov));
            }
        }
        // check available gpu frequencies by
        // adb shell cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies
        if (mGpuFrequency != null) {
            device.executeShellCommand("echo -n none > /sys/class/kgsl/kgsl-3d0/pwrscale/policy");
            device.executeShellCommand(String.format(
                    "echo -n %s > /sys/class/kgsl/kgsl-3d0/gpuclk", mGpuFrequency));
        }
        if (mCpuFrequency != null) {
            for (int i = 0; i < 4; i++) {
                device.executeShellCommand(String.format(
                        "echo -n userspace > %s/cpu%d/%s", cpuBase, i, gov));
                device.executeShellCommand(String.format("echo -n %s > "
                        + "%s/cpu%d/cpufreq/scaling_max_freq", mCpuFrequency, cpuBase, i));
                device.executeShellCommand(String.format("echo -n %s > "
                        + "%s/cpu%d/cpufreq/scaling_min_freq", mCpuFrequency, cpuBase, i));
                device.executeShellCommand(String.format("echo -n %s > "
                        + "%s/cpu%d/cpufreq/scaling_setspeed", mCpuFrequency, cpuBase, i));
            }
        }
    }

    /**
     * Hardware setup for N6.
     */
    private void nexusSixHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        // disable power collapse
        device.executeShellCommand("stop mpdecision");
        // lock performance on all four CPUs
        String cpuBase = "/sys/devices/system/cpu";

        for (int i = 0; i < 4; i++) {
            device.executeShellCommand(String.format(
                    "echo -n 1 > %s/cpu%d/online", cpuBase, i));
        }
        if (mCpuGovernor != null) {
            for (int i = 0; i < 4; i++) {
                device.executeShellCommand(String.format(
                        "echo %s > %s/cpu%d/cpufreq/scaling_governor", mCpuGovernor, cpuBase, i));
            }
        }
        if (mCpuFrequency != null) {
            for (int i = 0; i < 4; i++) {
                device.executeShellCommand(String.format(
                        "echo %s > %s/cpu%d/cpufreq/scaling_min_freq", mCpuFrequency, cpuBase, i));
            }
        }
        // check available gpu frequencies by
        // adb shell cat /sys/class/kgsl/kgsl-3d0/gpu_available_frequencies
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq", mGpuFrequency));
            device.executeShellCommand("echo -n 0 > /sys/class/kgsl/kgsl-3d0/bus_split");
            device.executeShellCommand("echo -n 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on");
            device.executeShellCommand("echo -n 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer");
        }
    }

    /**
     * Hardware setup for N10
     */
    private void nexusTenHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";

        // on N10, 2 CPUs are always at the same speed, lock cpu0 will lock cpu1 as well
        if (mCpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n userspace > %s/cpu0/%s", cpuBase, gov));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_max_freq", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_min_freq", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_setspeed", mCpuFrequency, cpuBase));
        }

        // lock GPU frequency
        String gpuBase = "/sys/devices/platform/mali.0";
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > %s/dvfs_under_lock", mGpuFrequency, gpuBase));
            device.executeShellCommand(String.format(
                "echo -n %s > %s/dvfs_upper_lock", mGpuFrequency, gpuBase));
            device.executeShellCommand(String.format(
                "echo -n %s > %s/clock", mGpuFrequency, gpuBase));
        }
    }

    private void volantisHardwareSetup(ITestDevice device) throws DeviceNotAvailableException {
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";

        // on Volantis, 2 CPUs are always at the same speed, lock cpu0 will lock cpu1 as well
        if (mCpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n userspace > %s/cpu0/%s", cpuBase, gov));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_max_freq", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_min_freq", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n %s > "
                    + "%s/cpu0/cpufreq/scaling_setspeed", mCpuFrequency, cpuBase));
            device.executeShellCommand(String.format("echo -n 0 > "
                    + "%s/cpuquiet/tegra_cpuquiet/enable", cpuBase));
            device.executeShellCommand(String.format("echo -n 0 > "
                    + "%s/cpuquiet/tegra_cpuquiet/enable", cpuBase));
            device.executeShellCommand("echo -n 1 > /sys/kernel/cluster/immediate");
            device.executeShellCommand("echo -n \"G\" > /sys/kernel/cluster/active");
        }

        // lock GPU frequency
        String gpuBase = "/d/clock/override.gbus";
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > %s/rate", mGpuFrequency, gpuBase));
            device.executeShellCommand(String.format("echo -n 1 > %s/state", gpuBase));
        }
    }

    /**
     * Hardware setup for Bullhead
     */
    private void bullheadHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        device.executeShellCommand("stop perfd");
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";
        // Based on the benchmark recommendations in b/23629533
        if (mCpuGovernor != null) {
            for (int i = 0; i < 4; i++) {
                // Disable little cores (cpu0,cpu1,cpu2,cpu3)
                device.executeShellCommand(String.format(
                        "echo -n 0 > %s/cpu%d/online", cpuBase, i));
            }
            for (int i = 4; i < 6; i++) {
                // Enable only big cores(cpu4,cpu5) and set cpu governor to performance mode
                device.executeShellCommand(String.format(
                        "echo -n 1 > %s/cpu%d/online", cpuBase, i));
                device.executeShellCommand(String.format(
                        "echo -n %s > %s/cpu%d/%s", mCpuGovernor, cpuBase, i, gov));
            }
        }

        device.executeShellCommand(String.format(
                "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor"));
        device.executeShellCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split");
        device.executeShellCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on");
        device.executeShellCommand("echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer");
    }

    /**
     * Hardware setup for Angler
     */
    private void anglerHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        device.executeShellCommand("stop perfd");
        String cpuBase = "/sys/devices/system/cpu";
        String gov = "cpufreq/scaling_governor";
        // Based on the benchmark recommendations in b/23629533
        if (mCpuGovernor != null) {
            for (int i = 0; i < 8; i++) {
                // Enable only two big cores(cpu4 and cpu5) and set to performance mode
                // Disable little cores(cpu0.cpu1,cpu2,cpu3) and two other big cores(cpu6 and cpu7)
                if (i == 4 || i == 5) {
                    device.executeShellCommand(String.format(
                            "echo -n 1 > %s/cpu%d/online", cpuBase, i));
                    device.executeShellCommand(String.format(
                            "echo -n %s > %s/cpu%d/%s", mCpuGovernor, cpuBase, i, gov));
                } else {
                    device.executeShellCommand(String.format(
                            "echo -n 0 > %s/cpu%d/online", cpuBase, i));
                }
            }
        }
        device.executeShellCommand(String.format(
                "echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor"));
        device.executeShellCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split");
        device.executeShellCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on");
        device.executeShellCommand("echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer");
    }

    /**
     * Hardware setup for Ryu
     */
    private void ryuHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        device.executeShellCommand("stop perfd");
        String cpuBase = "/sys/devices/system/cpu";
        // Based on benchmark recommendations in b/24685552
        if (mCpuFrequency != null) {
            for (int cpuIndex = 0; cpuIndex < 4; cpuIndex++) {
                device.executeShellCommand(String.format(
                        "echo -n %s > %s/cpu%d/cpufreq/scaling_max_freq",
                        mCpuFrequency, cpuBase, cpuIndex));
                device.executeShellCommand(String.format(
                        "echo -n %s > %s/cpu%d/cpufreq/scaling_min_freq",
                        mCpuFrequency, cpuBase, cpuIndex));
            }
        }
        if (mGpuState != null) {
            device.executeShellCommand(String.format(
                    "echo %s > /sys/devices/57000000.gpu/pstate", mGpuState));
        }
    }


    /**
     * Hardware setup for Seed
     */
    private void seedHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        device.executeShellCommand("stop perfd");

        String cpuBase = "/sys/devices/system/cpu";
        String cpuFreqPath = "cpufreq/scaling_max_freq";
        String cpuGovPath = "cpufreq/scaling_governor";
        String gpuFreqPath = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";
        String gpuGovPath = "/sys/class/kgsl/kgsl-3d0/devfreq/governor";
        // Based on benchmark recommendations in b/25651043
        if (mCpuGovernor != null) {
            for (int cpuIndex = 0; cpuIndex < 4; cpuIndex++) {
                device.executeShellCommand(String.format(
                        "echo -n %s > %s/cpu%d/%s", mCpuGovernor, cpuBase, cpuIndex, cpuGovPath));
            }
        }
        if (mCpuFrequency != null) {
            for (int cpuIndex = 0; cpuIndex < 4; cpuIndex++) {
                device.executeShellCommand(String.format(
                        "echo -n %s > %s/cpu%d/%s",
                        mCpuFrequency, cpuBase, cpuIndex, cpuFreqPath));
            }
        }

        if (mGpuGovernor != null) {
            device.executeShellCommand(String.format(
                    "echo %s > %s", mGpuGovernor, gpuGovPath));
        }
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format(
                    "echo -n %s > %s", mGpuFrequency, gpuFreqPath));
        }

        device.executeShellCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split");
        device.executeShellCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on");
        device.executeShellCommand("echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer");
    }


    /**
     * Hardware setup for marlin and sailfish
     *
     * @throws DeviceNotAvailableException
     */
    private void marsailHardwareSetup(ITestDevice device)
            throws DeviceNotAvailableException {
        device.executeShellCommand("stop thermal-engine");
        device.executeShellCommand("stop perfd");
        String disableLittleCore = "echo 0 > /sys/devices/system/cpu/cpu%d/online";
        String setCpuGov = "echo %s  > /sys/devices/system/cpu/cpu2/cpufreq/scaling_governor";
        String setCpuFreq = "echo %s > /sys/devices/system/cpu/cpu2/cpufreq/scaling_max_freq";
        String gpuGovPath = "/sys/class/kgsl/kgsl-3d0/devfreq/governor";
        String gpuFreqPath = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";

        // Disable core 0 and 1 in the little cluster
        for (int i = 0; i < 2; i++) {
            device.executeShellCommand(String.format(disableLittleCore, i));
        }

        /*
         *  Note:Changing governor and frequency in one core will be automatically applied to other
         *  cores in the cluster.i.e core 3 changes will be automatically applied to core 4 here.
         */
        if (mCpuGovernor != null) {
            device.executeShellCommand(String.format(setCpuGov, mCpuGovernor));
        }

        if (mCpuFrequency != null) {
            device.executeShellCommand(String.format(setCpuFreq, mCpuFrequency));
        }

        // Set GPU governor
        if (mGpuGovernor != null) {
            device.executeShellCommand(String.format("echo %s > %s", mGpuGovernor, gpuGovPath));
        }

        // Set GPU frequency
        if (mGpuFrequency != null) {
            device.executeShellCommand(String.format("echo -n %s > %s", mGpuFrequency, gpuFreqPath));
        }
    }

}


