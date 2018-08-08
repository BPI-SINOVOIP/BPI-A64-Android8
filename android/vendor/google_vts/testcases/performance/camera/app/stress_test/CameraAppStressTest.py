#!/usr/bin/env python
#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import logging
import os

from vts.runners.host import asserts
from vts.runners.host import base_test
from vts.runners.host import const
from vts.runners.host import keys
from vts.runners.host import test_runner
from vts.testcases.performance.camera.app.stress_test import FrameDropRate
from vts.utils.python.controllers import android_device

import shutil
import tempfile

class VtsAppCameraStressTest(base_test.BaseTestClass):
    """A stress test of Google camera app."""

    _TESTS = [
        {"apk": "CameraAppStressTest.apk",
         "opt": "-e videoCount 7 "
                "-e screenshotOnFailure true "
                "-e timeout_msec 21600000 "
                "-e class com.android.camera.stress.CameraStressTest#testBackCameraHFR240FPSVideoCaptureLongDuration "
                "com.android.camera2/android.test.InstrumentationTestRunner"},
        ]
    CAMERA_DATA_DIR_ON_TARGET = "/sdcard/DCIM/Camera"
    RESULT_PREFIX_TEST = "Total framedrop: "

    def setUpClass(self):
        self.getUserParams(
            req_param_names=[keys.ConfigKeys.IKEY_FFMPEG_BINARY_PATH])
        self._ffmpeg_binary_path = getattr(self, keys.ConfigKeys.IKEY_FFMPEG_BINARY_PATH)
        logging.info("ffmpeg_binary_path: %s", self._ffmpeg_binary_path)
        self.dut = self.registerController(android_device)[0]

    def setUpTest(self):
        self._tmpdir = tempfile.mkdtemp()

    def tearDownTest(self):
        shutil.rmtree(self._tmpdir)

    def testFrameDropRate(self):
        """Runs all test cases listed in self._TESTS."""
        for test in self._TESTS:
            logging.info("Run %s", test["apk"])
            self.dut.adb.shell(
                "rm -rf %s/*" % self.CAMERA_DATA_DIR_ON_TARGET)
            self.dut.adb.shell(
                "am instrument -w -r %s" % (test["opt"]))
            self.dut.adb.pull("%s %s" % (self.CAMERA_DATA_DIR_ON_TARGET,
                                         self._tmpdir))
            report = FrameDropRate.Analyze(
                os.path.join(self._tmpdir, "Camera"),
                ffmpeg_binary_path=self._ffmpeg_binary_path)
            logging.info("Frame drop rate report: %s", report)
            for line in report.split("\n"):
                if line.startswith(self.RESULT_PREFIX_TEST):
                    droped_frames, all_frames = line.replace(
                        self.RESULT_PREFIX_TEST, "").split()[0].split("/")
                    fdr = float(droped_frames) / float(all_frames) * 100
                    logging.info("fdr %s", fdr)
                    break
            asserts.assertLess(fdr, 0.4)


if __name__ == "__main__":
    test_runner.main()
