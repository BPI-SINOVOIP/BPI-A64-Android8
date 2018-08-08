# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_PATH:= $(call my-dir)

# Library of tools classes for creating / interpreting time zone distros.
include $(CLEAR_VARS)
LOCAL_MODULE := time_zone_distro_tools
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src/main)
LOCAL_JAVACFLAGS := -encoding UTF-8
LOCAL_JAVA_LIBRARIES := core-oj core-libart time_zone_distro
LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_STATIC_JAVA_LIBRARY)

# Library of tools classes for create / interpreting time zone distros.
# Used when generating distros and in host-side tests.
include $(CLEAR_VARS)
LOCAL_MODULE := time_zone_distro_tools-host
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src/main)
LOCAL_JAVACFLAGS := -encoding UTF-8
LOCAL_JAVA_LIBRARIES := time_zone_distro-host
LOCAL_ADDITIONAL_DEPENDENCIES := $(LOCAL_PATH)/Android.mk
include $(BUILD_HOST_JAVA_LIBRARY)
