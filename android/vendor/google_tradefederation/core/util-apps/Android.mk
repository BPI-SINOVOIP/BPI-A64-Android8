# Copyright 2013 Google Inc. All Rights Reserved.

# Include makefiles from all subdirectories
LOCAL_PATH := $(call my-dir)
include $(call all-makefiles-under,$(LOCAL_PATH))
