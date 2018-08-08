# Copyright 2006 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	UEventFramework.c \
	radio_monitor.c

LOCAL_SHARED_LIBRARIES := \
	liblog \
	libcutils \

LOCAL_CFLAGS += -Wall -Wextra -Werror

LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE:= radio_monitor
LOCAL_INIT_RC := radio_monitor.rc

include $(BUILD_EXECUTABLE)

