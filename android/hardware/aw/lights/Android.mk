# Copyright (C) 2010 The Android Open Source Project
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

$(warning $(TARGET_BOARD_PLATFORM))

LOCAL_PATH := $(call my-dir)

ifneq ($(filter astar,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de)
else
ifneq ($(filter kylin,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de)
else
ifneq ($(filter octopus,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de2)
else
ifneq ($(filter tulip,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de2)
else
ifneq ($(filter venus,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de2)
else
ifneq ($(filter eagle,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de2)
else
ifneq ($(filter neptune,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de3)
else
ifneq ($(filter uranus,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,de3)
else
    $(warning $(TARGET_BOARD_PLATFORM))
endif
endif
endif
endif
endif
endif
endif
endif
include $(CLEAR_VARS)

