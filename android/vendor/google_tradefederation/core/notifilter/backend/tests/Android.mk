# Copyright 2013 Google Inc. All Rights Reserved.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_MODULE := google-nf-be-tests
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := google-nf-be notifilter-be-tests
LOCAL_STATIC_JAVA_LIBRARIES := easymock junit-host

include $(BUILD_HOST_JAVA_LIBRARY)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
