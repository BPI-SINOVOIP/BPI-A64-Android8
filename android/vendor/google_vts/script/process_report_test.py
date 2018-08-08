#!/usr/bin/env python
#
# Copyright 2017 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import os
import unittest
import xml.etree.ElementTree as ET
import zipfile

import process_report

class ReportProcessorTest(unittest.TestCase):
    '''Unit tests for process_report.py
    '''

    def checkOutput(self, file, fingerprint, manufacturer, brand, product, device):
        test_file = None
        zipped = zipfile.ZipFile(file)
        for file_name in zipped.namelist():
            if file_name.endswith(process_report.TEST_RESULT_FILE):
                test_file = zipped.open(file_name)
                break

        self.assertIsNotNone(test_file)

        tree = ET.parse(test_file)
        root = tree.getroot()

        build_elts = [elt for elt in root if elt.tag == process_report.BUILD_TAG]
        self.assertGreater(len(build_elts), 0)

        for build_elt in build_elts:
            self.assertIn(process_report.BUILD_FINGERPRINT_KEY, build_elt.attrib)
            self.assertIn(process_report.MANUFACTURER_KEY, build_elt.attrib)
            self.assertIn(process_report.BRAND_KEY, build_elt.attrib)
            self.assertIn(process_report.PRODUCT_KEY, build_elt.attrib)
            self.assertIn(process_report.DEVICE_KEY, build_elt.attrib)

            self.assertEqual(build_elt.attrib[process_report.BUILD_FINGERPRINT_KEY], fingerprint)
            self.assertEqual(build_elt.attrib[process_report.MANUFACTURER_KEY], manufacturer)
            self.assertEqual(build_elt.attrib[process_report.BRAND_KEY], brand)
            self.assertEqual(build_elt.attrib[process_report.PRODUCT_KEY], product)
            self.assertEqual(build_elt.attrib[process_report.DEVICE_KEY], device)

    def testVtsReportFullySpecified(self):
        '''Test that a VTS report is successfully processed when fingerprint is provided.
        '''
        parser = process_report.ReportProcessor(
            'google',
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'data/vts_result.zip')
        new_report_path = parser.GenerateProcessedReport()
        self.assertIsNotNone(new_report_path)
        self.checkOutput(
            new_report_path,
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'google',
            'google',
            'sailfish',
            'sailfish')
        os.remove(new_report_path)

    def testVtsReportNoFingerprint(self):
        '''Test that a VTS report is successfully processed when fingerprint is not provided.
        '''
        parser = process_report.ReportProcessor(
            'google',
            None,
            'data/vts_result.zip')
        new_report_path = parser.GenerateProcessedReport()
        self.assertIsNotNone(new_report_path)
        self.checkOutput(
            new_report_path,
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'google',
            'google',
            'sailfish',
            'sailfish')
        os.remove(new_report_path)

    def testCtsReport(self):
        '''Test that a CTS report is successfully processed.
        '''
        parser = process_report.ReportProcessor(
            'google',
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'data/cts_result.zip')
        new_report_path = parser.GenerateProcessedReport()
        self.assertIsNotNone(new_report_path)
        self.checkOutput(
            new_report_path,
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'google',
            'google',
            'sailfish',
            'sailfish')
        os.remove(new_report_path)

    def testDoNotProcess(self):
        '''Test that a correct report is not processed.
        '''
        parser = process_report.ReportProcessor(
            'google',
            'google/sailfish/sailfish:8.0.0/OPR1.170623.011/4160747:userdebug/dev-keys',
            'data/donotprocess.zip')
        with self.assertRaises(SystemExit) as context:
            parser.GenerateProcessedReport()
            self.assertEquals(3, context.exception.code)

if __name__ == "__main__":
    unittest.main()