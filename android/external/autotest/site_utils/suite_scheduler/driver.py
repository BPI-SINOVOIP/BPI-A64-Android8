# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import contextlib
import logging
import time
from multiprocessing import pool

import base_event, board_enumerator, build_event, deduping_scheduler
import error
import task, timed_event

import common
from autotest_lib.client.common_lib import utils
from autotest_lib.server import utils

try:
    from chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock


POOL_SIZE = 32

BOARD_WHITELIST_SECTION = 'board_lists'
PRE_SECTIONS = [BOARD_WHITELIST_SECTION]

class Driver(object):
    """Implements the main loop of the suite_scheduler.

    @var EVENT_CLASSES: list of the event classes Driver supports.
    @var _LOOP_INTERVAL_SECONDS: seconds to wait between loop iterations.

    @var _scheduler: a DedupingScheduler, used to schedule jobs with the AFE.
    @var _enumerator: a BoardEnumerator, used to list plaforms known to
                      the AFE
    @var _events: dict of BaseEvents to be handled each time through main loop.
    """

    EVENT_CLASSES = [timed_event.Nightly, timed_event.Weekly,
                     build_event.NewBuild]
    _LOOP_INTERVAL_SECONDS = 5 * 60

    # Cache for known ChromeOS boards. The cache helps to avoid unnecessary
    # repeated calls to Launch Control API.
    _cros_boards = set()

    def __init__(self, scheduler, enumerator, is_sanity=False):
        """Constructor

        @param scheduler: an instance of deduping_scheduler.DedupingScheduler.
        @param enumerator: an instance of board_enumerator.BoardEnumerator.
        @param is_sanity: Set to True if the driver is created for sanity check.
                          Default is set to False.
        """
        self._scheduler = scheduler
        self._enumerator = enumerator
        task.TotMilestoneManager.is_sanity = is_sanity


    def RereadAndReprocessConfig(self, config, mv):
        """Re-read config, re-populate self._events and recreate task lists.

        @param config: an instance of ForgivingConfigParser.
        @param mv: an instance of ManifestVersions.
        """
        config.reread()
        new_events = self._CreateEventsWithTasks(config, mv)
        for keyword, event in self._events.iteritems():
            event.Merge(new_events[keyword])


    def SetUpEventsAndTasks(self, config, mv):
        """Populate self._events and create task lists from config.

        @param config: an instance of ForgivingConfigParser.
        @param mv: an instance of ManifestVersions.
        """
        self._events = self._CreateEventsWithTasks(config, mv)


    def _ReadBoardWhitelist(self, config):
        """Read board whitelist from config and save as dict.

        @param config: an instance of ForgivingConfigParser.
        """
        board_lists = {}
        if BOARD_WHITELIST_SECTION not in config.sections():
            return board_lists

        for option in config.options(BOARD_WHITELIST_SECTION):
            if option in board_lists:
                raise error.MalformedConfigEntry(
                        'Board list name must be unique.')
            else:
                board_lists[option] = config.getstring(
                        BOARD_WHITELIST_SECTION, option)

        return board_lists


    def _CreateEventsWithTasks(self, config, mv):
        """Create task lists from config, and assign to newly-minted events.

        Calling multiple times should start afresh each time.

        @param config: an instance of ForgivingConfigParser.
        @param mv: an instance of ManifestVersions.
        """
        events = {}
        for klass in self.EVENT_CLASSES:
            events[klass.KEYWORD] = klass.CreateFromConfig(config, mv)

        tasks = self.TasksFromConfig(config)
        for keyword, task_list in tasks.iteritems():
            if keyword in events:
                events[keyword].tasks = task_list
            else:
                logging.warning('%s, is an unknown keyword.', keyword)
        return events


    def TasksFromConfig(self, config):
        """Generate a dict of {event_keyword: [tasks]} mappings from |config|.

        For each section in |config| that encodes a Task, instantiate a Task
        object.  Determine the event that Task is supposed to run_on and
        append the object to a list associated with the appropriate event
        keyword.  Return a dictionary of these keyword: list of task mappings.

        @param config: a ForgivingConfigParser containing tasks to be parsed.
        @return dict of {event_keyword: [tasks]} mappings.
        @raise MalformedConfigEntry on a task parsing error.
        """
        board_lists = self._ReadBoardWhitelist(config)
        tasks = {}
        for section in config.sections():
            if (not base_event.HonoredSection(section) and
                section not in PRE_SECTIONS):
                try:
                    keyword, new_task = task.Task.CreateFromConfigSection(
                            config, section, board_lists=board_lists)
                except error.MalformedConfigEntry as e:
                    logging.warning('%s is malformed: %s', section, str(e))
                    continue
                tasks.setdefault(keyword, []).append(new_task)
        return tasks


    def RunForever(self, config, mv):
        """Main loop of the scheduler.  Runs til the process is killed.

        @param config: an instance of ForgivingConfigParser.
        @param mv: an instance of manifest_versions.ManifestVersions.
        """
        for event in self._events.itervalues():
            event.Prepare()
        while True:
            try:
                self.HandleEventsOnce(mv)
            except board_enumerator.EnumeratorException as e:
                logging.warning('Failed to enumerate boards: %r', e)
            mv.Update()
            task.TotMilestoneManager().refresh()
            time.sleep(self._LOOP_INTERVAL_SECONDS)
            self.RereadAndReprocessConfig(config, mv)
            metrics.Counter('chromeos/autotest/suite_scheduler/'
                            'handle_events_tick').increment()


    @staticmethod
    def HandleBoard(inputs):
        """Handle event based on given inputs.

        @param inputs: A dictionary of the arguments needed to handle an event.
            Keys include:
            scheduler: a DedupingScheduler, used to schedule jobs with the AFE.
            event: An event object to be handled.
            board: Name of the board.
        """
        scheduler = inputs['scheduler']
        event = inputs['event']
        board = inputs['board']

        # Try to get builds from LaunchControl first. If failed, the board could
        # be ChromeOS. Use the cache Driver._cros_boards to avoid unnecessary
        # repeated call to LaunchControl API.
        launch_control_builds = None
        if board not in Driver._cros_boards:
            launch_control_builds = event.GetLaunchControlBuildsForBoard(board)
        if launch_control_builds:
            event.Handle(scheduler, branch_builds=None, board=board,
                         launch_control_builds=launch_control_builds)
        else:
            branch_builds = event.GetBranchBuildsForBoard(board)
            if branch_builds:
                Driver._cros_boards.add(board)
                logging.info('Found ChromeOS build for board %s. This should '
                             'be a ChromeOS board.', board)
            event.Handle(scheduler, branch_builds, board)
        logging.info('Finished handling %s event for board %s', event.keyword,
                     board)

    @metrics.SecondsTimerDecorator('chromeos/autotest/suite_scheduler/'
                                   'handle_events_once_duration')
    def HandleEventsOnce(self, mv):
        """One turn through the loop.  Separated out for unit testing.

        @param mv: an instance of manifest_versions.ManifestVersions.
        @raise EnumeratorException if we can't enumerate any supported boards.
        """
        boards = self._enumerator.Enumerate()
        logging.info('%d boards currently in the lab: %r', len(boards), boards)
        thread_pool = pool.ThreadPool(POOL_SIZE)
        with contextlib.closing(thread_pool):
            for e in self._events.itervalues():
                if not e.ShouldHandle():
                    continue
                # Reset the value of delay_minutes, as this is the beginning of
                # handling an event for all boards.
                self._scheduler.delay_minutes = 0
                self._scheduler.delay_minutes_interval = (
                        deduping_scheduler.DELAY_MINUTES_INTERVAL)
                logging.info('Handling %s event for %d boards', e.keyword,
                             len(boards))
                args = []
                for board in boards:
                    args.append({'scheduler': self._scheduler,
                                 'event': e,
                                 'board': board})
                thread_pool.map(self.HandleBoard, args)
                logging.info('Finished handling %s event for %d boards',
                             e.keyword, len(boards))
                e.UpdateCriteria()


    def ForceEventsOnceForBuild(self, keywords, build_name,
                                os_type=task.OS_TYPE_CROS):
        """Force events with provided keywords to happen, with given build.

        @param keywords: iterable of event keywords to force
        @param build_name: instead of looking up builds to test, test this one.
        @param os_type: Type of the OS to test, default to cros.
        """
        branch_builds = None
        launch_control_builds = None
        if os_type == task.OS_TYPE_CROS:
            board, type, milestone, manifest = utils.ParseBuildName(build_name)
            branch_builds = {task.PickBranchName(type, milestone): [build_name]}
            logging.info('Testing build R%s-%s on %s', milestone, manifest,
                         board)
        else:
            logging.info('Build is not a ChromeOS build, try to parse as a '
                         'Launch Control build.')
            _,target,_ = utils.parse_launch_control_build(build_name)
            board = utils.parse_launch_control_target(target)[0]
            # Translate board name in build target to the actual board name.
            board = utils.ANDROID_TARGET_TO_BOARD_MAP.get(board, board)
            launch_control_builds = [build_name]
            logging.info('Testing Launch Control build %s on %s', build_name,
                         board)

        for e in self._events.itervalues():
            if e.keyword in keywords:
                e.Handle(self._scheduler, branch_builds, board, force=True,
                         launch_control_builds=launch_control_builds)
