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

import os
import re
import subprocess
import sys

_VIDEO_EXTS = ['.mp4', '.mov', '.avi', '.MOV']

def Analyze(dir_path, ffmpeg_binary_path, fps=30.0):
    """Script for frame-drop detection and statistics calculation.

    The script would scan through all video files in the folder and print out the
    number of frame drops for each video, and the effective frame rate for all
    videos.

    Args:
        dir_path: string, the path of a dir which contains the media files.
        ffmpeg_binary_path: string, the path of ffmpeg binary.
        fps: float, frames per second.

    Returns:
        A string containing a report
    """
    # Change the fps to match the source videos (all videos in the folder should
    # have identical target FPS
    if not ffmpeg_binary_path:
        print "ffmpeg_binary_path not set"
        return ""

    videos = [f for f in os.listdir(dir_path)
              if os.path.splitext(f)[1] in _VIDEO_EXTS]

    thres_30fps = 45.0
    thres = thres_30fps * 30.0 / fps

    frame_cnt_all = 0.0
    frame_drop_cnt_all = 0.0
    report_all = 'Scan ' + dir_path
    for v in videos:
        full_path = os.path.join(dir_path, v)
        command = [ffmpeg_binary_path, '-i', full_path, '-an', '-vf', 'showinfo', '-f',
                   'null', '-']
        ret = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE).communicate()[1]
        rets = ret.split('\n')
        idx = 0
        time_prev = 0
        framedrop_cnt = 0.0
        total_frame_cnt = 0
        dropped_frames = []

        report = ''

        for r in rets:
          head = r.find('[Parsed_showinfo_0')
          if head >= 0:
              r = r[head:]
          else:
              continue
          tokens = re.findall(r'\S+', r.replace(':', ' '))
          if not tokens:
            continue

          if tokens[0] != '[Parsed_showinfo_0' or tokens[3] != 'n':
            continue

          idx = int(tokens[4])
          time = float(tokens[8])
          diff = 1000 * (time - time_prev)
          if diff > thres:
              print 'Curr(s)/Prev(s)/Delta(ms) ', time, time_prev, diff
              dropped_frames.append([idx, diff])
              framedrop_cnt += (diff / (1000.0 / fps) - 1)

          time_prev = time
          total_frame_cnt += 1
        # Add the dropped frame into the total frame count
        # (which is the ideal number of total frames)
        total_frame_cnt += framedrop_cnt
        framedrop_rate = 100.0 * framedrop_cnt / total_frame_cnt
        report = report + '\n' + v + (' total {t:5f} frames. Frame drops: {v} '
                                      '({p:4.2f}%)\n').format(t=total_frame_cnt,
                                                              v=framedrop_cnt,
                                                              p=framedrop_rate)
        for frame in dropped_frames:
          report += '        {v:5d}: {t:5.1f}\n'.format(v=frame[0], t=frame[1])

        frame_cnt_all += total_frame_cnt
        frame_drop_cnt_all += framedrop_cnt
        report_all += report
        framedrop_rate = 100.0 * frame_drop_cnt_all / frame_cnt_all
        effective_framerate = fps * (1.0 - float(frame_drop_cnt_all) / frame_cnt_all)
        report_all += ('\nTotal framedrop: {a}/{b} ({c:4.2f}%). Effective framerate: '
                       '{d}').format(a=frame_drop_cnt_all,
                                     b=frame_cnt_all,
                                     c=framedrop_rate,
                                     d=effective_framerate)

    return report_all
