#!/usr/bin/env python
#
# Copyright (C) 2016 The Android Open Source Project
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

"""app_profiler.py: manage the process of profiling an android app.
    It downloads simpleperf on device, uses it to collect samples from
    user's app, and pulls perf.data and needed binaries on host.
"""

from __future__ import print_function
import argparse
import copy
import os
import os.path
import shutil
import subprocess
import sys
import time

from binary_cache_builder import BinaryCacheBuilder
from simpleperf_report_lib import *
from utils import *

class AppProfiler(object):
    """Used to manage the process of profiling an android app.

    There are three steps:
       1. Prepare profiling.
       2. Profile the app.
       3. Collect profiling data.
    """
    def __init__(self, config):
        self.check_config(config)
        self.config = config
        self.adb = AdbHelper()
        self.is_root_device = False
        self.android_version = 0
        self.device_arch = None
        self.app_arch = None
        self.app_pid = None


    def check_config(self, config):
        config_names = ['app_package_name', 'native_lib_dir', 'apk_file_path',
                        'recompile_app', 'launch_activity', 'launch_inst_test',
                        'record_options', 'perf_data_path']
        for name in config_names:
            if name not in config:
                log_exit('config [%s] is missing' % name)
        if not config['app_package_name']:
            log_exit("The package name of the application hasn't been set")
        native_lib_dir = config.get('native_lib_dir')
        if native_lib_dir and not os.path.isdir(native_lib_dir):
            log_exit('[native_lib_dir] "%s" is not a dir' % native_lib_dir)
        apk_file_path = config.get('apk_file_path')
        if apk_file_path and not os.path.isfile(apk_file_path):
            log_exit('[apk_file_path] "%s" is not a file' % apk_file_path)
        if config['recompile_app']:
            if not config['launch_activity'] and not config['launch_inst_test']:
                log_exit('one of launch_activity and launch_inst_test is needed for recompile app')


    def profile(self):
        log_info('prepare profiling')
        self.prepare_profiling()
        log_info('start profiling')
        self.start_and_wait_profiling()
        log_info('collect profiling data')
        self.collect_profiling_data()
        log_info('profiling is finished.')


    def prepare_profiling(self):
        self._get_device_environment()
        self._enable_profiling()
        self._recompile_app()
        self._restart_app()
        self._get_app_environment()
        self._download_simpleperf()
        self._download_native_libs()


    def _get_device_environment(self):
        self.is_root_device = self.adb.switch_to_root()

        # Get android version.
        build_version = self.adb.get_property('ro.build.version.release')
        if build_version:
            if not build_version[0].isdigit():
                c = build_version[0].upper()
                if c < 'L':
                    self.android_version = 0
                else:
                    self.android_version = ord(c) - ord('L') + 5
            else:
                strs = build_version.split('.')
                if strs:
                    self.android_version = int(strs[0])

        # Get device architecture.
        output = self.adb.check_run_and_return_output(['shell', 'uname', '-m'])
        if output.find('aarch64') != -1:
            self.device_arch = 'aarch64'
        elif output.find('arm') != -1:
            self.device_arch = 'arm'
        elif output.find('x86_64') != -1:
            self.device_arch = 'x86_64'
        elif output.find('86') != -1:
            self.device_arch = 'x86'
        else:
            log_fatal('unsupported architecture: %s' % output.strip())


    def _enable_profiling(self):
        self.adb.set_property('security.perf_harden', '0')
        if self.is_root_device:
            # We can enable kernel symbols
            self.adb.run(['shell', 'echo 0 >/proc/sys/kernel/kptr_restrict'])


    def _recompile_app(self):
        if not self.config['recompile_app']:
            return
        if self.android_version == 0:
            log_warning("Can't fully compile an app on android version < L.")
        elif self.android_version == 5 or self.android_version == 6:
            if not self.is_root_device:
                log_warning("Can't fully compile an app on android version < N on non-root devices.")
            elif not self.config['apk_file_path']:
                log_warning("apk file is needed to reinstall the app on android version < N.")
            else:
                flag = '-g' if self.android_version == 6 else '--include-debug-symbols'
                self.adb.set_property('dalvik.vm.dex2oat-flags', flag)
                self.adb.check_run(['install', '-r', self.config['apk_file_path']])
        elif self.android_version >= 7:
            self.adb.set_property('debug.generate-debug-info', 'true')
            self.adb.check_run(['shell', 'cmd', 'package', 'compile', '-f', '-m', 'speed',
                                self.config['app_package_name']])
        else:
            log_fatal('unreachable')


    def _restart_app(self):
        if not self.config['launch_activity'] and not self.config['launch_inst_test']:
            return

        pid = self._find_app_process()
        if pid is not None:
            self.run_in_app_dir(['kill', '-9', str(pid)])
            time.sleep(1)

        if self.config['launch_activity']:
            activity = self.config['app_package_name'] + '/' + self.config['launch_activity']
            result = self.adb.run(['shell', 'am', 'start', '-n', activity])
            if not result:
                log_exit("Can't start activity %s" % activity)
        else:
            runner = self.config['app_package_name'] + '/android.support.test.runner.AndroidJUnitRunner'
            result = self.adb.run(['shell', 'am', 'instrument', '-e', 'class',
                                   self.config['launch_inst_test'], runner])
            if not result:
                log_exit("Can't start instrumentation test  %s" % self.config['launch_inst_test'])

        for i in range(10):
            pid = self._find_app_process()
            if pid is not None:
                return
            time.sleep(1)
            log_info('Wait for the app process for %d seconds' % (i + 1))
        log_exit("Can't find the app process")


    def _find_app_process(self):
        ps_args = ['-e'] if self.android_version >= 8 else []
        result, output = self.adb.run_and_return_output(['shell', 'ps'] + ps_args)
        if not result:
            return None
        output = output.split('\n')
        for line in output:
            strs = line.split()
            if len(strs) > 2 and strs[-1].find(self.config['app_package_name']) != -1:
                return int(strs[1])
        return None


    def _get_app_environment(self):
        self.app_pid = self._find_app_process()
        if self.app_pid is None:
            log_exit("can't find process for app [%s]" % self.config['app_package_name'])
        if self.device_arch in ['aarch64', 'x86_64']:
            output = self.run_in_app_dir(['cat', '/proc/%d/maps' % self.app_pid])
            if output.find('linker64') != -1:
                self.app_arch = self.device_arch
            else:
                self.app_arch = 'arm' if self.device_arch == 'aarch64' else 'x86'
        else:
            self.app_arch = self.device_arch
        log_info('app_arch: %s' % self.app_arch)


    def _download_simpleperf(self):
        simpleperf_binary = get_target_binary_path(self.app_arch, 'simpleperf')
        self.adb.check_run(['push', simpleperf_binary, '/data/local/tmp'])
        self.run_in_app_dir(['cp', '/data/local/tmp/simpleperf', '.'])
        self.run_in_app_dir(['chmod', 'a+x', 'simpleperf'])


    def _download_native_libs(self):
        if not self.config['native_lib_dir']:
            return
        filename_dict = dict()
        for root, _, files in os.walk(self.config['native_lib_dir']):
            for file in files:
                if not file.endswith('.so'):
                    continue
                path = os.path.join(root, file)
                old_path = filename_dict.get(file)
                log_info('app_arch = %s' % self.app_arch)
                if self._is_lib_better(path, old_path):
                    log_info('%s is better than %s' % (path, old_path))
                    filename_dict[file] = path
                else:
                    log_info('%s is worse than %s' % (path, old_path))
        maps = self.run_in_app_dir(['cat', '/proc/%d/maps' % self.app_pid])
        searched_lib = dict()
        for item in maps.split():
            if item.endswith('.so') and searched_lib.get(item) is None:
                searched_lib[item] = True
                # Use '/' as path separator as item comes from android environment.
                filename = item[item.rfind('/') + 1:]
                dirname = item[1:item.rfind('/')]
                path = filename_dict.get(filename)
                if path is None:
                    continue
                self.adb.check_run(['push', path, '/data/local/tmp'])
                self.run_in_app_dir(['mkdir', '-p', dirname])
                self.run_in_app_dir(['cp', '/data/local/tmp/' + filename, dirname])


    def _is_lib_better(self, new_path, old_path):
        """ Return true if new_path is more likely to be used on device. """
        if old_path is None:
            return True
        if self.app_arch == 'arm':
            result1 = new_path.find('armeabi-v7a/') != -1
            result2 = old_path.find('armeabi-v7a') != -1
            if result1 != result2:
                return result1
        arch_dir = 'arm64' if self.app_arch == 'aarch64' else self.app_arch + '/'
        result1 = new_path.find(arch_dir) != -1
        result2 = old_path.find(arch_dir) != -1
        if result1 != result2:
            return result1
        result1 = new_path.find('obj/') != -1
        result2 = old_path.find('obj/') != -1
        if result1 != result2:
            return result1
        return False


    def start_and_wait_profiling(self):
        subproc = None
        returncode = None
        try:
            args = self.get_run_in_app_dir_args([
                './simpleperf', 'record', self.config['record_options'], '-p',
                str(self.app_pid), '--symfs', '.'])
            adb_args = [self.adb.adb_path] + args
            log_debug('run adb cmd: %s' % adb_args)
            subproc = subprocess.Popen(adb_args)
            returncode = subproc.wait()
        except KeyboardInterrupt:
            if subproc:
                self.stop_profiling()
                returncode = 0
        log_debug('run adb cmd: %s [result %s]' % (adb_args, returncode == 0))


    def stop_profiling(self):
        """ Stop profiling by sending SIGINT to simpleperf, and wait until it exits
            to make sure perf.data is completely generated."""
        has_killed = False
        while True:
            (result, _) = self.run_in_app_dir(['pidof', 'simpleperf'], check_result=False)
            if not result:
                break
            if not has_killed:
                has_killed = True
                self.run_in_app_dir(['pkill', '-l', '2', 'simpleperf'], check_result=False)
            time.sleep(1)


    def collect_profiling_data(self):
        self.run_in_app_dir(['cat perf.data | tee /data/local/tmp/perf.data >/dev/null'])
        self.adb.check_run_and_return_output(['pull', '/data/local/tmp/perf.data',
                                              self.config['perf_data_path']])
        if self.config['collect_binaries']:
            config = copy.copy(self.config)
            config['binary_cache_dir'] = 'binary_cache'
            config['symfs_dirs'] = []
            if self.config['native_lib_dir']:
                config['symfs_dirs'].append(self.config['native_lib_dir'])
            binary_cache_builder = BinaryCacheBuilder(config)
            binary_cache_builder.build_binary_cache()


    def run_in_app_dir(self, args, stdout_file=None, check_result=True):
        args = self.get_run_in_app_dir_args(args)
        if check_result:
            return self.adb.check_run_and_return_output(args, stdout_file)
        else:
            return self.adb.run_and_return_output(args, stdout_file)


    def get_run_in_app_dir_args(self, args):
        if self.is_root_device:
            return ['shell', 'cd /data/data/' + self.config['app_package_name'] + ' && ' +
                      (' '.join(args))]
        else:
            return ['shell', 'run-as', self.config['app_package_name']] + args

def main():
    parser = argparse.ArgumentParser(
        description=
"""Profile an android app. See configurations in app_profiler.config.""")
    parser.add_argument('--config', default='app_profiler.config', help=
"""Set configuration file. Default is app_profiler.config. The configurations
can be overridden by options in cmdline.""")
    parser.add_argument('-p', '--package_name', help=
"""The package name of the profiled Android app.""")
    parser.add_argument('-lib', '--native_lib_dir', help=
"""Path to find debug version of native shared libraries used in the app.""")
    parser.add_argument('-nc', '--skip_recompile', action='store_true', help=
"""By default we recompile java bytecode to native instructions to profile java
code. It takes some time. You can skip it if the code has been compiled or you
don't need to profile java code.""")
    parser.add_argument('--apk', help=
"""Apk file of the profiled app, used on Android version <= M, which needs to
reinstall the app to recompile it.""")
    parser.add_argument('-a', '--activity', help=
"""Start an activity before profiling. It can be used to profile the startup
time of an activity. Default is .MainActivity.""")
    parser.add_argument('-t', '--test', help=
"""Start an instrumentation test before profiling. It can be used to profile
an instrumentation test.""")
    parser.add_argument('-r', '--record_options', help=
"""Set options for `simpleperf record` command. Default is "-e cpu-cycles:u -f 4000 -g --duration 10".""")
    parser.add_argument('-o', '--perf_data_path', help=
"""The path to store profiling data. Default is perf.data.""")
    parser.add_argument('-nb', '--skip_collect_binaries', action='store_true', help=
"""By default we collect binaries used in profiling data from device to
binary_cache directory. It can be used to annotate source code. This option skips it.""")
    args = parser.parse_args()
    config = load_config(args.config)
    if args.package_name:
        config['app_package_name'] = args.package_name
    if args.native_lib_dir:
        config['native_lib_dir'] = args.native_lib_dir
    if args.skip_recompile:
        config['recompile_app'] = False
    if args.apk:
        config['apk'] = args.apk
    if args.activity:
        config['launch_activity'] = args.activity
        config['launch_inst_test'] = None
    if args.test:
        config['launch_inst_test'] = args.test
        config['launch_activity'] = None
    if args.record_options:
        config['record_options'] = args.record_options
    if args.perf_data_path:
        config['perf_data_path'] = args.perf_data_path
    if args.skip_collect_binaries:
        config['collect_binaries'] = False

    profiler = AppProfiler(config)
    profiler.profile()

if __name__ == '__main__':
    main()