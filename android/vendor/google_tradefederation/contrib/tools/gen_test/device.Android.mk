LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

# Don't include this package in any target
LOCAL_MODULE_TAGS := tests

# When built, explicitly put it in the data partition.
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_DEX_PREOPT := false

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_STATIC_JAVA_LIBRARIES := \
	android-support-test \
	compatibility-device-util \
	platform-test-annotations

LOCAL_SRC_FILES := $(call all-java-files-under, src)

# Tag this module as a :suite: test artifact
LOCAL_COMPATIBILITY_SUITE := :suite:

LOCAL_PACKAGE_NAME := :module:

LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)