# Copyright 2013 Google Inc. All Rights Reserved.

LOCAL_PATH := $(call my-dir)

# Only build notifilter if servlet-api-3.0 exists.
ifneq (,$(wildcard tools/external/servlets/Android.mk))

#######################################################
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_IS_HOST_MODULE := true
LOCAL_PREBUILT_EXECUTABLES := run_googlenf.sh

include $(BUILD_MULTI_PREBUILT)

#######################################################
include $(CLEAR_VARS)

LOCAL_MODULE := google-nf
LOCAL_MODULE_TAGS := optional
LOCAL_REQUIRED_MODULES := google-nf-fe google-nf-be
LOCAL_IS_HOST_MODULE := true

include $(BUILD_PHONY_PACKAGE)

#######################################################
include $(CLEAR_VARS)

LOCAL_MODULE := google-nf-tests
LOCAL_MODULE_TAGS := optional
LOCAL_REQUIRED_MODULES := google-nf-fe-tests google-nf-be-tests google-nf-common-tests
LOCAL_IS_HOST_MODULE := true

include $(BUILD_PHONY_PACKAGE)

#######################################################
include $(CLEAR_VARS)

# Create a simple alias to build all the google-nf-related targets
.PHONY: google-nf-all
google-nf-all: google-nf google-nf-tests

########################################################
# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))

endif # ifneq (,$(wildcard tools/external/servlets/Android.mk))
