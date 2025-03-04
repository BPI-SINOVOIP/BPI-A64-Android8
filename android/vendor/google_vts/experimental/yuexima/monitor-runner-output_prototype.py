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

#TODO
#********************** Work in progress prototype **************************

import os
import sys
import time

LOG_DIR = '/tmp'

ERR_ALREADY_DELETED = (
    'Test is finished and latest log has already been deleted by'
    ' vts framework cleanup process, please check the log dir.')


class Monitor:

    last_confirmed_timestamp = -1

    def GetLatestLog(self):
        '''Find the latest vts runner log folder in log directory and return its content.
    
        Args:
            last_time, int, last known log path timestamp
    
        Returns:
            (string, string), returns the latest log file path and log content
        '''
        folders = [
            os.path.join(LOG_DIR, folder_name)
            for folder_name in os.listdir(LOG_DIR)
            if os.path.isdir(os.path.join(LOG_DIR, folder_name)) and
            folder_name.startswith('vts-runner-log')
        ]

        try:
            folders.sort(
                lambda folder1, folder2: int(os.path.getmtime(folder1) - os.path.getmtime(folder2)))
            folder_latest = folders[-1]
            log_path_latest = os.path.join(folder_latest,
                                           os.listdir(folder_latest)[0],
                                           'latest', 'test_run_details.txt')

            time = os.path.getmtime(log_path_latest)
            if time is None or time < self.last_confirmed_timestamp:
                print ERR_ALREADY_DELETED
                return (log_path_latest, None)

            with open(log_path_latest, 'r') as log_latest:
                content = log_latest.read()
                if content is None:
                    print ERR_ALREADY_DELETED
                self.last_confirmed_timestamp = time
                return (log_path_latest, content)
        except Exception as e:
            if self.last_confirmed_timestamp > 0:
                print ERR_ALREADY_DELETED
            return (None, None)

    def StartMonitoring(self, path_only):
        '''Pull the latest vts runner log in a loop, and print out any new contents.
    
        Args:
            path_only: bool, only print out the latest log path in temporary directory.
        '''
        is_first_time = True
        last_log_path = None
        last_text = ''
        last_time = -1
        while True:
            log_path_latest, text_latest = self.GetLatestLog(last_time)

            if path_only:
                print log_path_latest
                return

            if text_latest is None:  # Case: cannot find any runner log
                time.sleep(1)
                continue

            last_time = time_latest

            if last_log_path == log_path_latest:
                text_new = text_latest[len(last_text):]
                last_text = text_latest
                if text_new:  # Case: runner log file changed
                    if is_first_time:
                        is_first_time = False
                        print text_new
                        continue
                    lines = text_new.split('\n')
                    for l in lines:
                        print l
                        time.sleep(0.6 / len(lines))
                else:  # Case: runner log file didn't change
                    time.sleep(1)
            else:  # Case: found a new runner log file
                last_text = ''
                last_log_path = log_path_latest
                print '\n' * 6 + '=' * 24 + 'new file' + '=' * 24 + '\n' * 6
                time.sleep(1)


def PrintUsage():
    print 'A script to read VTS Runner\'s log from temporary directory.'
    print 'Usage:'
    print '  -h, --help: print usage.'
    print '  -p, --path-only: print path to the latest runner file only.'
    print '                   You may pipe the result to vim for searching.'
    print '                   Example: script/monitor-runner-output.py --path-only | xargs gedit'
    print '  -m, --monitor: print VTS runner\'s output in close to real time'
    print '  If no argument is provided, this script will keep pulling the latest log and print it out.'


if __name__ == "__main__":
    argv = sys.argv
    path_only = False
    if len(argv) == 1 or argv[1] == '-h' or argv[1] == '--help':
        PrintUsage()
        exit()
    elif argv[1] == '-p' or argv[1] == '--path-only':
        path_only = True
    elif argv[1] == '-m' or argv[1] == '--monitor':
        path_only = False
    StartMonitoring(path_only)