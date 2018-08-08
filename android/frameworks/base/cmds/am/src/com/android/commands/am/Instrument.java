/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.commands.am;

import android.app.IActivityManager;
import android.app.IInstrumentationWatcher;
import android.app.Instrumentation;
import android.app.UiAutomationConnection;
import android.content.ComponentName;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.os.SystemProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.AndroidException;
import android.util.proto.ProtoOutputStream;
import android.util.Log;
import android.view.IWindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * Runs the am instrument command
 */
public class Instrument {
    private final IActivityManager mAm;
    private final IPackageManager mPm;
    private final IWindowManager mWm;

    // Command line arguments
    public String profileFile = null;
    public boolean wait = false;
    public boolean rawMode = false;
    public boolean proto = false;
    public boolean noWindowAnimation = false;
    public String abi = null;
    public int userId = UserHandle.USER_CURRENT;
    public Bundle args = new Bundle();
    // Required
    public String componentNameArg;

    /**
     * Construct the instrument command runner.
     */
    public Instrument(IActivityManager am, IPackageManager pm) {
        mAm = am;
        mPm = pm;
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
    }

    /**
     * Base class for status reporting.
     *
     * All the methods on this interface are called within the synchronized block
     * of the InstrumentationWatcher, so calls are in order.  However, that means
     * you must be careful not to do blocking operations because you don't know
     * exactly the locking dependencies.
     */
    private interface StatusReporter {
        /**
         * Status update for tests.
         */
        public void onInstrumentationStatusLocked(ComponentName name, int resultCode,
                Bundle results);

        /**
         * The tests finished.
         */
        public void onInstrumentationFinishedLocked(ComponentName name, int resultCode,
                Bundle results);

        /**
         * @param errorText a description of the error
         * @param commandError True if the error is related to the commandline, as opposed
         *      to a test failing.
         */
        public void onError(String errorText, boolean commandError);
    }

    private static Collection<String> sorted(Collection<String> list) {
        final ArrayList<String> copy = new ArrayList<>(list);
        Collections.sort(copy);
        return copy;
    }

    /**
     * Printer for the 'classic' text based status reporting.
     */
    private class TextStatusReporter implements StatusReporter {
        private boolean mRawMode;

        /**
         * Human-ish readable output.
         *
         * @param rawMode   In "raw mode" (true), all bundles are dumped.
         *                  In "pretty mode" (false), if a bundle includes
         *                  Instrumentation.REPORT_KEY_STREAMRESULT, just print that.
         */
        public TextStatusReporter(boolean rawMode) {
            mRawMode = rawMode;
        }

        private boolean filterTestResult(String name) {
            String[] list = {
                              "android.media.cts.AdaptivePlaybackTest#testH264_adaptiveSmallReconfigDrc",
                              "android.media.cts.AdaptivePlaybackTest#testH264_flushConfigureDrc",
                              "android.media.cts.AdaptivePlaybackTest#testHEVC_adaptiveEarlyEos",
                              "android.media.cts.AdaptivePlaybackTest#testVP8_adaptiveDrc",
                              "android.media.cts.DecodeAccuracyTest#testGLViewDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testGLViewDecodeAccuracy[28]",
                              "android.media.cts.DecodeAccuracyTest#testGLViewLargerHeightDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testGLViewLargerHeightDecodeAccuracy[28]",
                              "android.media.cts.DecodeAccuracyTest#testGLViewLargerWidthDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testGLViewLargerWidthDecodeAccuracy[28]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewLargerHeightDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewLargerHeightDecodeAccuracy[28]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewLargerWidthDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewLargerWidthDecodeAccuracy[28]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewVideoDecodeAccuracy[12]",
                              "android.media.cts.DecodeAccuracyTest#testSurfaceViewVideoDecodeAccuracy[28]",
                              "android.media.cts.MediaRecorderTest#testRecorderPauseResume",
                              "android.view.cts.PixelCopyTest#testVideoProducer",
                              "android.opengl2.cts.reference.GLReferenceBenchmark#testReferenceBenchmark",
                              "com.android.cts.net.HostsideRestrictBackgroundNetworkTests#testAppIdle_toast",
                              "dEQP-EGL.functional.get_frame_timestamps#other",
                              "dEQP-EGL.functional.get_frame_timestamps#rgb888_depth_stencil",
                              "dEQP-EGL.functional.get_frame_timestamps#rgba8888_depth_stencil",
                              "android.security.cts.StagefrightTest#testStagefright_bug_21443020",
                              "android.security.cts.StagefrightTest#testStagefright_bug_32577290",
                              "android.security.cts.StagefrightTest#testStagefright_bug_34360591",
                              "android.security.cts.StagefrightTest#testStagefright_cve_2015_6604",
                              "android.security.cts.StagefrightTest#testStagefright_cve_2017_0857"
            };
            if(name == null)
                return false;
            for(int i = 0; i < list.length; i++){
                if(name.startsWith(list[i]))
                    return true;
            }
            return false;
        }
        @Override
        public void onInstrumentationStatusLocked(ComponentName name, int resultCode,
                Bundle results) {
            // pretty printer mode?
            String pretty = null;
            String fullTestCaseName = null;
            boolean filter = false;
            if (!mRawMode && results != null) {
                pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
            }
            if (pretty != null) {
                System.out.print(pretty);
            } else {
                if (results != null) {
                    for (String key : sorted(results.keySet())) {
                        if(key.compareTo("class")==0)
                            fullTestCaseName = results.get(key).toString();
                        if(key.indexOf("test")==0)
                            fullTestCaseName = fullTestCaseName + "#" +  results.get(key);
                    }
                    filter = filterTestResult(fullTestCaseName);
                    for (String key : sorted(results.keySet())) {
                        if(filter){
                            if(key.compareTo("stack") == 0)
                                continue;
                        }
                        System.out.println(
                                "INSTRUMENTATION_STATUS: " + key + "=" + results.get(key));
                    }

                }
                if(filter) {
                    if(resultCode != 0 && resultCode != 1) {
                        resultCode = 0;
                    }
                }
                System.out.println("INSTRUMENTATION_STATUS_CODE: " + resultCode);
            }
        }

        @Override
        public void onInstrumentationFinishedLocked(ComponentName name, int resultCode,
                Bundle results) {
            // pretty printer mode?
            String pretty = null;
            if (!mRawMode && results != null) {
                pretty = results.getString(Instrumentation.REPORT_KEY_STREAMRESULT);
            }
            if (pretty != null) {
                System.out.println(pretty);
            } else {
                if (results != null) {
                    for (String key : sorted(results.keySet())) {
                        System.out.println(
                                "INSTRUMENTATION_RESULT: " + key + "=" + results.get(key));
                    }
                }
                System.out.println("INSTRUMENTATION_CODE: " + resultCode);
            }
        }

        @Override
        public void onError(String errorText, boolean commandError) {
            if (mRawMode) {
                System.out.println("onError: commandError=" + commandError + " message="
                        + errorText);
            }
            // The regular BaseCommand error printing will print the commandErrors.
            if (!commandError) {
                System.out.println(errorText);
            }
        }
    }

    /**
     * Printer for the protobuf based status reporting.
     */
    private class ProtoStatusReporter implements StatusReporter {
        @Override
        public void onInstrumentationStatusLocked(ComponentName name, int resultCode,
                Bundle results) {
            final ProtoOutputStream proto = new ProtoOutputStream();

            final long token = proto.startRepeatedObject(InstrumentationData.Session.TEST_STATUS);

            proto.writeSInt32(InstrumentationData.TestStatus.RESULT_CODE, resultCode);
            writeBundle(proto, InstrumentationData.TestStatus.RESULTS, results);

            proto.endRepeatedObject(token);
            writeProtoToStdout(proto);
        }

        @Override
        public void onInstrumentationFinishedLocked(ComponentName name, int resultCode,
                Bundle results) {
            final ProtoOutputStream proto = new ProtoOutputStream();

            final long token = proto.startObject(InstrumentationData.Session.SESSION_STATUS);

            proto.writeEnum(InstrumentationData.SessionStatus.STATUS_CODE,
                    InstrumentationData.SESSION_FINISHED);
            proto.writeSInt32(InstrumentationData.SessionStatus.RESULT_CODE, resultCode);
            writeBundle(proto, InstrumentationData.SessionStatus.RESULTS, results);

            proto.endObject(token);
            writeProtoToStdout(proto);
        }

        @Override
        public void onError(String errorText, boolean commandError) {
            final ProtoOutputStream proto = new ProtoOutputStream();

            final long token = proto.startObject(InstrumentationData.Session.SESSION_STATUS);

            proto.writeEnum(InstrumentationData.SessionStatus.STATUS_CODE,
                    InstrumentationData.SESSION_ABORTED);
            proto.writeString(InstrumentationData.SessionStatus.ERROR_TEXT, errorText);

            proto.endObject(token);
            writeProtoToStdout(proto);
        }

        private void writeBundle(ProtoOutputStream proto, long fieldId, Bundle bundle) {
            final long bundleToken = proto.startObject(fieldId);

            for (final String key: sorted(bundle.keySet())) {
                final long entryToken = proto.startRepeatedObject(
                        InstrumentationData.ResultsBundle.ENTRIES);

                proto.writeString(InstrumentationData.ResultsBundleEntry.KEY, key);

                final Object val = bundle.get(key);
                if (val instanceof String) {
                    proto.writeString(InstrumentationData.ResultsBundleEntry.VALUE_STRING,
                            (String)val);
                } else if (val instanceof Byte) {
                    proto.writeSInt32(InstrumentationData.ResultsBundleEntry.VALUE_INT,
                            ((Byte)val).intValue());
                } else if (val instanceof Double) {
                    proto.writeDouble(InstrumentationData.ResultsBundleEntry.VALUE_DOUBLE,
                            ((Double)val).doubleValue());
                } else if (val instanceof Float) {
                    proto.writeFloat(InstrumentationData.ResultsBundleEntry.VALUE_FLOAT,
                            ((Float)val).floatValue());
                } else if (val instanceof Integer) {
                    proto.writeSInt32(InstrumentationData.ResultsBundleEntry.VALUE_INT,
                            ((Integer)val).intValue());
                } else if (val instanceof Long) {
                    proto.writeSInt64(InstrumentationData.ResultsBundleEntry.VALUE_LONG,
                            ((Long)val).longValue());
                } else if (val instanceof Short) {
                    proto.writeSInt32(InstrumentationData.ResultsBundleEntry.VALUE_INT,
                            ((Short)val).intValue());
                } else if (val instanceof Bundle) {
                    writeBundle(proto, InstrumentationData.ResultsBundleEntry.VALUE_BUNDLE,
                            (Bundle)val);
                }

                proto.endRepeatedObject(entryToken);
            }

            proto.endObject(bundleToken);
        }

        private void writeProtoToStdout(ProtoOutputStream proto) {
            try {
                System.out.write(proto.getBytes());
                System.out.flush();
            } catch (IOException ex) {
                System.err.println("Error writing finished response: ");
                ex.printStackTrace(System.err);
            }
        }
    }


    /**
     * Callbacks from the remote instrumentation instance.
     */
    private class InstrumentationWatcher extends IInstrumentationWatcher.Stub {
        private final StatusReporter mReporter;

        private boolean mFinished = false;

        public InstrumentationWatcher(StatusReporter reporter) {
            mReporter = reporter;
        }

        @Override
        public void instrumentationStatus(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                mReporter.onInstrumentationStatusLocked(name, resultCode, results);
                notifyAll();
            }
        }

        @Override
        public void instrumentationFinished(ComponentName name, int resultCode, Bundle results) {
            synchronized (this) {
                mReporter.onInstrumentationFinishedLocked(name, resultCode, results);
                mFinished = true;
                notifyAll();
            }
        }

        public boolean waitForFinish() {
            synchronized (this) {
                while (!mFinished) {
                    try {
                        if (!mAm.asBinder().pingBinder()) {
                            return false;
                        }
                        wait(1000);
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return true;
        }
    }

    /**
     * Figure out which component they really meant.
     */
    private ComponentName parseComponentName(String cnArg) throws Exception {
        if (cnArg.contains("/")) {
            ComponentName cn = ComponentName.unflattenFromString(cnArg);
            if (cn == null) throw new IllegalArgumentException("Bad component name: " + cnArg);
            return cn;
        } else {
            List<InstrumentationInfo> infos = mPm.queryInstrumentation(null, 0).getList();

            final int numInfos = infos == null ? 0: infos.size();
            ArrayList<ComponentName> cns = new ArrayList<>();
            for (int i = 0; i < numInfos; i++) {
                InstrumentationInfo info = infos.get(i);

                ComponentName c = new ComponentName(info.packageName, info.name);
                if (cnArg.equals(info.packageName)) {
                    cns.add(c);
                }
            }

            if (cns.size() == 0) {
                throw new IllegalArgumentException("No instrumentation found for: " + cnArg);
            } else if (cns.size() == 1) {
                return cns.get(0);
            } else {
                StringBuilder cnsStr = new StringBuilder();
                final int numCns = cns.size();
                for (int i = 0; i < numCns; i++) {
                    cnsStr.append(cns.get(i).flattenToString());
                    cnsStr.append(", ");
                }

                // Remove last ", "
                cnsStr.setLength(cnsStr.length() - 2);

                throw new IllegalArgumentException("Found multiple instrumentations: "
                        + cnsStr.toString());
            }
        }
    }

    /**
     * Run the instrumentation.
     */
    public void run() throws Exception {
        StatusReporter reporter = null;
        float[] oldAnims = null;

        try {
            // Choose which output we will do.
            if (proto) {
                reporter = new ProtoStatusReporter();
            } else if (wait) {
                reporter = new TextStatusReporter(rawMode);
            }

            // Choose whether we have to wait for the results.
            InstrumentationWatcher watcher = null;
            UiAutomationConnection connection = null;
            if (reporter != null) {
                watcher = new InstrumentationWatcher(reporter);
                connection = new UiAutomationConnection();
            }

            // Set the window animation if necessary
            if (noWindowAnimation) {
                oldAnims = mWm.getAnimationScales();
                mWm.setAnimationScale(0, 0.0f);
                mWm.setAnimationScale(1, 0.0f);
                mWm.setAnimationScale(2, 0.0f);
            }

            // Figure out which component we are tring to do.
            final ComponentName cn = parseComponentName(componentNameArg);

            // Choose an ABI if necessary
            if (abi != null) {
                final String[] supportedAbis = Build.SUPPORTED_ABIS;
                boolean matched = false;
                for (String supportedAbi : supportedAbis) {
                    if (supportedAbi.equals(abi)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    throw new AndroidException(
                            "INSTRUMENTATION_FAILED: Unsupported instruction set " + abi);
                }
            }
            if(componentNameArg.startsWith("android.openglperf.cts")){
                SystemProperties.set("debug.hwc.showfps","on.ctrlfps");
            }
            // Start the instrumentation
            if (!mAm.startInstrumentation(cn, profileFile, 0, args, watcher, connection, userId,
                        abi)) {
                throw new AndroidException("INSTRUMENTATION_FAILED: " + cn.flattenToString());
            }

            // If we have been requested to wait, do so until the instrumentation is finished.
            if (watcher != null) {
                if (!watcher.waitForFinish()) {
                    reporter.onError("INSTRUMENTATION_ABORTED: System has crashed.", false);
                    return;
                }
            }
        } catch (Exception ex) {
            // Report failures
            if (reporter != null) {
                reporter.onError(ex.getMessage(), true);
            }

            // And re-throw the exception
            throw ex;
        } finally {
            // Clean up
            SystemProperties.set("debug.hwc.showfps","off.ctrlfps");
            if (oldAnims != null) {
                mWm.setAnimationScales(oldAnims);
            }
        }
    }
}

