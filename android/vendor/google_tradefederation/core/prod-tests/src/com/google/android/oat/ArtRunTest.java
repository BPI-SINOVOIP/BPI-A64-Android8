// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.android.oat;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.IFileEntry;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Test that runs all the art run-tests on given device.
 */
@OptionClass(alias = "art-run-test")
public class ArtRunTest implements IDeviceTest, IRemoteTest {

    private static final String RUNTEST_TAG = "ArtRunTest";
    private static final String EXPORT_CMD =
            "export ANDROID_DATA=%s && "
                    + "export ANDROID_ADDITIONAL_PUBLIC_LIBRARIES=%s && "
                    + "export DEX_LOCATION=%s && "
                    + "LD_LIBRARY_PATH=|#LIB#| && ";
    private static final String DALVIK_CMD = "dalvikvm|#BITNESS#| "
            + "-Djava.library.path=|#LIB#| "
            + "-Xjnigreflimit:256 -Xcheck:jni |#NDK#| -cp %s Main";
    private static final String RUNTEST_CMD = EXPORT_CMD + DALVIK_CMD;
    private static final String VALGRIND_RUNTEST_CMD = EXPORT_CMD + "valgrind " + DALVIK_CMD;

    static final String DEFAULT_TEST_PATH = "/data/art-run-tests";

    static final String EXPECTED_FILENAME = "expected.txt";
    static final String OUTPUT_FILENAME = "output.txt";

    private ITestDevice mDevice = null;

    @Option(name = "art-run-test-device-path",
            description = "The path on the device where the art run-tests are located.")
    private String mTestDevicePath = DEFAULT_TEST_PATH;

    @Option(name = "base-library-device-path",
            description = "The base path on the device where the libarttestd.so is located.")
    private String mLibBasePath = "/data/nativetest|#64#|/art/|#CPU#|/";

    @Option(
        name = "lib-art-test",
        description = "The name of the libart test to pass to the tests as arguments."
    )
    private String mLibArtTest = "libart.so";

    @Option(
        name = "lib-art-arg",
        description = "The name of the libart arg to pass to the tests as arguments."
    )
    private String mLibArtArg = "arttest";

    @Option(name = "test-timeout", description = "The max time in ms for an art run-test to "
            + "run. Test run will be aborted if any test takes longer.", isTimeVal = true)
    private long mMaxTestTimeMs = 1 * 60 * 1000;

    @Option(name = "test-retries", description = "The max number of retries to do if test fails.")
    private int mTestRetryAttempts = 0;

    @Option(name = "valgrind",
            description = "Whether to run this test with Valgrind enabled or not.")
    private boolean mValgrind = false;

    @Option(name = "force-abi",
            description = "force the specified abi to be used for the test, example: arm64-v8a")
    private String mForceAbi = null;

    private boolean mNdkTranslationNeeded = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Set the max time in ms for a test to run.
     */
    void setMaxTestTimeMs(int timeout) {
        mMaxTestTimeMs = timeout;
    }

    /**
     * Run a single art run-test
     *
     * @param rootEntry {@link IFileEntry} folder of the test to run
     * @param listener {@link ITestInvocationListener} listener for test
     * @param abi the abi to run the test on.
     * @throws DeviceNotAvailableException
     */
    void runArtTest(IFileEntry rootEntry, ITestRunListener listener, String abi)
            throws DeviceNotAvailableException {
        // If this is not a directory return.
        if (!rootEntry.isDirectory()) {
            return;
        }

        String testName = rootEntry.getName();
        TestIdentifier testId = new TestIdentifier(String.format("%s_%s", RUNTEST_TAG, abi),
                testName);
        listener.testStarted(testId);
        try {
            // Determine if there is a jar file inside the directory.
            IFileEntry testFile = rootEntry.findChild(testName + ".jar");
            String output = null;
            // If there are no test file, maybe it was an intentional omission
            // due to an expected
            // build failure. There should be an output.txt file in the
            // directory.
            if (testFile == null) {
                // Special case for 000-nop test
                if (testName.equals("000-nop")) {
                    output = "Blort.";
                } else {
                    IFileEntry buildOutput = rootEntry.findChild(OUTPUT_FILENAME);
                    if (buildOutput != null) {
                        output = readFromFile(buildOutput);
                    } else {
                        listener.testFailed(testId,
                                "No jar file to run and no build output to compare.");
                        return;
                    }
                }
            } else {
                // Create art-cache directory and run the test.
                File artCache = new File(rootEntry.getFullPath(), "dalvik-cache");
                if (!mDevice.doesFileExist(artCache.getAbsolutePath())) {
                    mDevice.executeShellCommand(String.format("mkdir %s",
                            artCache.getAbsolutePath()));
                }
                String runtest_cmd = String.format("%s %s", RUNTEST_CMD, mLibArtArg);
                if (mValgrind) {
                    runtest_cmd = String.format("%s %s", VALGRIND_RUNTEST_CMD, mLibArtArg);
                }
                String cmd =
                        String.format(
                                runtest_cmd,
                                rootEntry.getFullPath(),
                                mLibArtTest,
                                rootEntry.getFullPath(),
                                testFile.getFullEscapedPath());
                // libartd.so is in the pushed libraries, we add system/lib for dependencies based
                // on the abi bitness.
                if ("64".equals(AbiUtils.getBitness(abi))) {
                    String lib = mLibBasePath.replace("|#64#|", "64");
                    cmd = cmd.replace("|#LIB#|", lib + ":/system/lib64:/data/lib64/");
                } else {
                    String lib = mLibBasePath.replace("|#64#|", "");
                    cmd = cmd.replace("|#LIB#|", lib + ":/system/lib:/data/lib/");
                }
                cmd = cmd.replace("|#CPU#|", AbiUtils.getArchForAbi(abi));
                cmd = cmd.replace("|#BITNESS#|", AbiUtils.getBitness(abi));


                // If we are in a case where ndk would be needed, and arm running.
                if (mNdkTranslationNeeded &&
                        AbiUtils.getArchForAbi(abi).startsWith(AbiUtils.BASE_ARCH_ARM)) {
                    cmd = cmd.replace("|#NDK#|", "-XX:NativeBridge=libndk_translation.so");
                } else {
                    cmd = cmd.replace("|#NDK#|", "");
                }

                CLog.d("About to run run-test command: %s", cmd);
                CollectingOutputReceiver receiver = new CollectingOutputReceiver();
                mDevice.executeShellCommand(cmd, receiver, mMaxTestTimeMs, TimeUnit.MILLISECONDS,
                        mTestRetryAttempts);
                output = receiver.getOutput().trim();
                mDevice.executeShellCommand(String.format("rm -r %s", artCache.getAbsolutePath()));
                CLog.v("%s on %s returned %s", cmd, mDevice.getSerialNumber(), output);
            }

            if (output != null) {
                IFileEntry expectedFile = rootEntry.findChild(EXPECTED_FILENAME);
                if (expectedFile == null) {
                    CLog.e("Failed to get expected file for %s.", testName);
                    listener.testFailed(testId, "No expected file for test.");
                    return;
                }
                String expected = readFromFile(expectedFile);
                if (output.equals(expected)) {
                    CLog.i("%s PASSED", testName);
                } else {
                    String error = String.format("'%s' instead of '%s'", output, expected);
                    CLog.i("%s FAILED: %s", testName, error);
                    listener.testFailed(testId, error);
                }
            } else {
                listener.testFailed(testId, "No output received to compared.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            listener.testFailed(testId, e.toString());
        } finally {
            Map<String, String> emptyMap = Collections.emptyMap();
            listener.testEnded(testId, emptyMap);
        }
    }

    /**
     * Read file to a string, if file does not exist, return null.
     *
     * @param file {@link IFileEntry} the file to read from.
     * @return (@link String} content of the file.
     * @throws IOException
     * @throws DeviceNotAvailableException
     */
    private String readFromFile(IFileEntry file) throws IOException, DeviceNotAvailableException {
        if (file == null) {
            return null;
        }
        String filepath = file.getFullEscapedPath();
        File localFile = mDevice.pullFile(filepath);
        if (localFile == null) {
            return null;
        }
        try {
            String output = FileUtil.readStringFromFile(localFile);
            return output.trim();
        } finally {
            FileUtil.deleteFile(localFile);
        }
    }

    /**
     * Executes all art run-tests in a folder. Each test is assumed to sit in its own folder.
     *
     * @param rootEntry The root folder to begin searching for art run-test tests
     * @param listener the {@link ITestRunListener}
     * @throws DeviceNotAvailableException
     */
    void doRunAllTestsInSubdirectory(IFileEntry rootEntry, ITestRunListener listener)
            throws DeviceNotAvailableException {
        if (!rootEntry.isDirectory()) {
            return;
        }

        List<String> abiList = new ArrayList<>();
        String abiListProperty = mDevice.getProperty("ro.product.cpu.abilist");
        if (abiListProperty != null) {
            abiList.addAll(AbiUtils.parseAbiListFromProperty(abiListProperty));
        }

        boolean arm = false;
        boolean x86 = false;
        for (String abi : abiList) {
            if (AbiUtils.getArchForAbi(abi).startsWith(AbiUtils.BASE_ARCH_ARM)) {
                arm = true;
            }
            if (AbiUtils.getArchForAbi(abi).startsWith(AbiUtils.BASE_ARCH_X86)) {
                x86 = true;
            }
        }
        // In a case where the device contains both arm and x86, arm will need ndk translation
        // to execute.
        mNdkTranslationNeeded = arm && x86;

        for (String abi : abiList) {
            if (mForceAbi != null && !mForceAbi.equals(abi)) {
                CLog.i("Abi:'%s' is not matching the force-abi:'%s'", abi, mForceAbi);
                continue;
            }
            List<IFileEntry> listTests = new ArrayList<>();
            listTests.addAll(rootEntry.getChildren(false));
            // Ensure same order of tests
            Collections.sort(listTests, new Comparator<IFileEntry>() {
                @Override
                public int compare(IFileEntry o1, IFileEntry o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            listener.testRunStarted(String.format("%s_%s", RUNTEST_TAG, abi), listTests.size());
            for (IFileEntry childEntry : listTests) {
                runArtTest(childEntry, listener, abi);
            }
            Map<String, String> emptyMap = Collections.emptyMap();
            listener.testRunEnded(0, emptyMap);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (mDevice == null) {
            throw new IllegalArgumentException("Device has not been set");
        }
        IFileEntry TestDirectory = mDevice.getFileEntry(mTestDevicePath);
        if (TestDirectory == null) {
            CLog.w("Could not find art run-tests directory %s in %s!",
                    mTestDevicePath, mDevice.getSerialNumber());
            return;
        }
        doRunAllTestsInSubdirectory(TestDirectory, listener);
    }
}
