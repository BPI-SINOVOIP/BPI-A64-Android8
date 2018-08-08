LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE_TAGS := tests

# tag this module as a :suite: test artifact
LOCAL_COMPATIBILITY_SUITE := :suite:

LOCAL_MODULE := :module:

LOCAL_JAVA_LIBRARIES := :suite:-tradefed tradefed compatibility-host-util

LOCAL_STATIC_JAVA_LIBRARIES := platform-test-annotations-host

include $(BUILD_HOST_JAVA_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))

