# Copyright 2013 Google Inc. All Rights Reserved.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_SDK_VERSION := 11
LOCAL_PACKAGE_NAME := GoogleAccountUtil
LOCAL_CERTIFICATE := vendor/unbundled_google/libraries/certs/app

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)
