# Copyright 2007 The Android Open Source Project
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE := preinstalllib
LOCAL_MODULE_STEM := preinstall
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_JAVA_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := preinstall
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := preinstall
LOCAL_REQUIRED_MODULES := preinstalllib
LOCAL_INIT_RC := preinstall.rc
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_PREBUILT)
