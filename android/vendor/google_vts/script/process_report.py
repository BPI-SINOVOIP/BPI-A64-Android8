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

import argparse
import os
import xml.etree.ElementTree as ET
import zipfile


BUILD_TAG = 'Build'
TEST_RESULT_FILE = 'test_result.xml'
VENDOR_FINGERPRINT = 'build_vendor_fingerprint'
VENDOR_MANUFACTURER = 'build_vendor_manufacturer'
VENDOR_MODEL = 'build_vendor_model'
ZIP_EXT = '.zip'
ZIP_SUFFIX = '_processed'

BRAND_KEY = 'build_brand'
DEVICE_KEY = 'build_device'
PRODUCT_KEY = 'build_product'
MANUFACTURER_KEY = 'build_manufacturer'
BUILD_FINGERPRINT_KEY = 'build_fingerprint'

class ReportProcessor(object):
    '''Process the compatibility report from a VTS run.

    Attributes:
        _manufacturer: the device manufacturer
        _fingerprint: the device build fingerprint
        _file: the path to compatiblity report
    '''
    def __init__(self, manufacturer, fingerprint, file):
        self._manufacturer = manufacturer
        self._fingerprint = fingerprint
        self._file = file

    def _ParseFingerprint(self, fingerprint):
        '''Parse the brand, product, and device from a build fingerprint.

        Args:
            fingerprint The build fingerprint string.

        Returns:
            A tuple containing the device brand, product, and device.
        '''
        parts = fingerprint.split('/')
        if len(parts) < 3:
            raise ValueError('Invalid fingerprint %s' % str(fingerprint))
        brand = parts[0]
        product = parts[1]
        device = parts[2].split(':')[0]
        return brand, product, device

    def GenerateProcessedReport(self):
        '''Read the compatibility report and output a processed version.

        Returns:
            The path to the processed compatibility report.
        '''
        basename, ext = os.path.splitext(self._file)
        if not ext == ZIP_EXT:
            print 'ERROR: Invalid zip file specified.'
            exit(3)
        zipped = zipfile.ZipFile(self._file)

        test_file = None
        for file_name in zipped.namelist():
            if file_name.endswith(TEST_RESULT_FILE):
                test_file = zipped.open(file_name)
                break

        if test_file is None:
            print 'ERROR: Could not find test_result.xml in the specified zip.'
            exit(3)

        header = test_file.readline()
        tree = ET.parse(test_file)
        root = tree.getroot()

        build_elts = [elt for elt in root if elt.tag == BUILD_TAG]
        for build_elt in build_elts:
            if (
                VENDOR_FINGERPRINT in build_elt.attrib and
                build_elt.attrib[VENDOR_FINGERPRINT] != 'null' and
                VENDOR_MANUFACTURER in build_elt.attrib and
                build_elt.attrib[VENDOR_MANUFACTURER] != 'null' and
                VENDOR_MODEL in build_elt.attrib and
                build_elt.attrib[VENDOR_MODEL] != 'null'
            ):
                print (
                    'Required properties provided in original report. '
                    'Please submit the original zip to APFE.'
                )
                exit(3)

            if VENDOR_FINGERPRINT in build_elt.attrib:
                vendor_fpt = build_elt.attrib[VENDOR_FINGERPRINT]
                if self._fingerprint is None:
                    self._fingerprint = vendor_fpt
                elif vendor_fpt != self._fingerprint:
                    print 'ERROR: vendor_build_fingerprint does not match.'
                    exit(3)

            try:
                brand, product, device = self._ParseFingerprint(self._fingerprint)
            except ValueError as e:
                print 'ERROR: Could not parse build fingerprint.'
                exit(3)

            if BRAND_KEY in build_elt.attrib:
                build_elt.attrib[BRAND_KEY] = brand
            if DEVICE_KEY in build_elt.attrib:
                build_elt.attrib[DEVICE_KEY] = device
            if PRODUCT_KEY in build_elt.attrib:
                build_elt.attrib[PRODUCT_KEY] = product
            if MANUFACTURER_KEY in build_elt.attrib:
                build_elt.attrib[MANUFACTURER_KEY] = self._manufacturer
            if BUILD_FINGERPRINT_KEY in build_elt.attrib:
                build_elt.attrib[BUILD_FINGERPRINT_KEY] = self._fingerprint

        processed_name = os.path.join(basename + ZIP_SUFFIX + ext)

        with zipfile.ZipFile(self._file, 'r') as input_file:
            with zipfile.ZipFile(processed_name, 'w') as output_file:
                for file_name in input_file.namelist():
                    if file_name.endswith(TEST_RESULT_FILE):
                        output_file.writestr(file_name, header + ET.tostring(root))
                    else:
                        output_file.writestr(file_name, input_file.read(file_name))
        return processed_name


if __name__ == '__main__':
    print (
        'Note: Post-processing VTS reports is not needed if the device has required sysprops.\n'
        'To skip post-processing, please cherry-pick the change below to device source:\n\n'
        'https://android-review.googlesource.com/#/c/platform/build/+/504857\n\n'
    )
    parser = argparse.ArgumentParser(description='Prepare VTS report.')
    parser.add_argument('manufacturer', help='The device manufacturer.')
    parser.add_argument('-fingerprint', help='The device build fingerprint.')
    parser.add_argument('report_zip', help='The path to the zip file containing a VTS result.')
    args = parser.parse_args()
    parser = ReportProcessor(
        args.manufacturer,
        args.fingerprint,
        args.report_zip)
    new_report_path = parser.GenerateProcessedReport()
    print "Processed report saved to: " + new_report_path
