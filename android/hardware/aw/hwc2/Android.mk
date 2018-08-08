# Copyright (C) 2008 The Android Open Source Project
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


LOCAL_PATH := $(call my-dir)

# HAL module implemenation stored in
# hw/<OVERLAY_HARDWARE_MODULE_ID>.<ro.product.board>.so
include $(CLEAR_VARS)

LOCAL_PROPRIETARY_MODULE := true

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_SHARED_LIBRARIES := liblog libEGL
LOCAL_SRC_FILES :=\
	hwc.cpp \
    layer.cpp \
    de2family/DisplayOpr.cpp \
    hwc_common.cpp \
    other/ion.cpp \
    other/rotate.cpp \
    other/debug.cpp \
    other/memcontrl.cpp \
    threadResouce/hwc_event_thread.cpp \
    threadResouce/hwc_submit_thread.cpp

ifeq ($(USE_IOMMU),true)
	LOCAL_CFLAGS += -DUSE_IOMMU
endif

ifeq ($(TARGET_USES_DE30),true)
	LOCAL_CFLAGS += -DDE_VERSION=30
	LOCAL_CFLAGS += -DGRALLOC_SUNXI_METADATA_BUF
endif

LOCAL_SHARED_LIBRARIES := \
    libutils \
    libEGL \
    libGLESv1_CM \
    liblog \
    libcutils \
    libsync \
    libion \
    libgui \
    libui

ifeq ($(TARGET_PLATFORM),homlet)
	LOCAL_SRC_FILES += other/homlet.cpp
	LOCAL_CFLAGS += -DHOMLET_PLATFORM
	LOCAL_C_INCLUDES += vendor/aw/homlet/hardware/include/display
	LOCAL_SHARED_LIBRARIES += \
	    libbinder \
	    libhwcprivateservice
endif

LOCAL_C_INCLUDES += $(TARGET_HARDWARE_INCLUDE)
LOCAL_C_INCLUDES += system/core/libion/include \
    system/core/libsync/include \
    system/core/libsync \
    system/core/include \
    hardware/libhardware/include/
LOCAL_MODULE := hwcomposer.$(TARGET_BOARD_PLATFORM)
LOCAL_CFLAGS += -DLOG_TAG=\"sunxihwc\" -DTARGET_BOARD_PLATFORM=$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_TAGS := optional
#TARGET_GLOBAL_CFLAGS += -DTARGET_BOARD_PLATFORM=$(TARGET_BOARD_PLATFORM)
include $(BUILD_SHARED_LIBRARY)
