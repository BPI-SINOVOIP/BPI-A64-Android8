# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""This is a client side WebGL performance test.

http://hg.mozilla.org/users/bjacob_mozilla.com/webgl-perf-tests/raw-file/3729e8afac99/index.html

From the sources:
Keep in mind that these tests are not realistic workloads. These are not
benchmarks aiming to compare browser or GPU performance. These are only useful
to catch performance regressions in a given browser and system.
"""

import logging
import os

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.graphics import graphics_utils


class graphics_WebGLPerformance(graphics_utils.GraphicsTest):
    """WebGL performance graphics test."""
    version = 1
    _test_duration_secs = 0
    perf_keyval = {}

    def setup(self):
        self.job.setup_dep(['webgl_perf'])
        self.job.setup_dep(['graphics'])

    def initialize(self):
        super(graphics_WebGLPerformance, self).initialize()

    def cleanup(self):
        super(graphics_WebGLPerformance, self).cleanup()

    def run_performance_test(self, browser, test_url):
        """Runs the performance test from the given url.

        @param browser: The Browser object to run the test with.
        @param test_url: The URL to the performance test site.
        """
        if not utils.wait_for_idle_cpu(60.0, 0.1):
            if not utils.wait_for_idle_cpu(20.0, 0.2):
                raise error.TestFail('Failed: Could not get idle CPU.')

        # Kick off test.
        tab = browser.tabs.New()
        tab.Navigate(test_url)
        tab.Activate()
        tab.WaitForDocumentReadyStateToBeComplete()

        # Wait for test completion.
        tab.WaitForJavaScriptCondition('time_ms_geom_mean > 0.0',
                                       timeout=self._test_duration_secs)

        # Get the geometric mean of individual runtimes.
        time_ms_geom_mean = tab.EvaluateJavaScript('time_ms_geom_mean')
        logging.info('WebGLPerformance: time_ms_geom_mean = %f',
                     time_ms_geom_mean)

        # Output numbers for plotting by harness.
        keyvals = {}
        keyvals['time_ms_geom_mean'] = time_ms_geom_mean
        self.write_perf_keyval(keyvals)
        self.output_perf_value(
            description='time_geom_mean',
            value=time_ms_geom_mean,
            units='ms',
            higher_is_better=False,
            graph='time_geom_mean')
        # Add extra value to the graph distinguishing different boards.
        variant = utils.get_board_with_frequency_and_memory()
        desc = 'time_geom_mean-%s' % variant
        self.output_perf_value(
            description=desc,
            value=time_ms_geom_mean,
            units='ms',
            higher_is_better=False,
            graph='time_geom_mean')

        # Get a copy of the test report.
        test_report = tab.EvaluateJavaScript('test_report')
        results_path = os.path.join(
            self.bindir,
            '../../results/default/graphics_WebGLPerformance/test_report.html')
        f = open(results_path, 'w+')
        f.write(test_report)
        f.close()

        tab.Close()

    @graphics_utils.GraphicsTest.failure_report_decorator('graphics_WebGLPerformance')
    def run_once(self, test_duration_secs=2700, fullscreen=True):
        """Finds a brower with telemetry, and run the test.

        @param test_duration_secs: The test duration in seconds.
        @param fullscreen: Whether to run the test in fullscreen.
        """
        # To avoid 0ms on fast machines like samus the workload was increased.
        # Unfortunately that makes running on slow machines impractical without
        # deviating from upstream too much.
        if utils.get_gpu_family() == 'pinetrail':
            # TODO(ihf): return a TestPass(message) once available.
            logging.warning('Test is too slow to run regularly.')
            return

        self._test_duration_secs = test_duration_secs
        ext_paths = []
        if fullscreen:
            ext_paths.append(
                os.path.join(self.autodir, 'deps', 'graphics',
                             'graphics_test_extension'))

        with chrome.Chrome(logged_in=False,
                           extension_paths=ext_paths,
                           init_network_controller=True) as cr:
            websrc_dir = os.path.join(self.autodir, 'deps', 'webgl_perf', 'src')
            if not cr.browser.platform.SetHTTPServerDirectories(websrc_dir):
                raise error.TestFail('Failed: Unable to start HTTP server')
            test_url = cr.browser.platform.http_server.UrlOf(
                os.path.join(websrc_dir, 'index.html'))
            self.run_performance_test(cr.browser, test_url)
