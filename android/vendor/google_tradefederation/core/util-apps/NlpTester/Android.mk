# Copyright 2013 Google Inc. All Rights Reserved.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)
LOCAL_SDK_VERSION := 8
LOCAL_PACKAGE_NAME := NlpTester

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)
