# Copyright 2008 The Android Open Source Project
#
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-proto-files-under, proto)
LOCAL_MODULE := am
LOCAL_PROTOC_OPTIMIZE_TYPE := stream
LOCAL_REQUIRED_MODULES := vendor_am
include $(BUILD_JAVA_LIBRARY)

#
include $(CLEAR_VARS)
LOCAL_MODULE := vendor_am
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_JAVA_LIBRARIES)
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-proto-files-under, proto)
LOCAL_PROTOC_OPTIMIZE_TYPE := stream
include $(BUILD_JAVA_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := am
LOCAL_SRC_FILES := am
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_VENDOR_OPTIONAL_EXECUTABLES)
LOCAL_POST_INSTALL_CMD := $(hide) mkdir -p $(TARGET_ROOT_OUT)/sbin; \
    ln -sf ../vendor/xbin/am $(TARGET_ROOT_OUT)/sbin/am;
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := \
    $(call all-proto-files-under, proto)
LOCAL_MODULE := libinstrumentation
LOCAL_PROTOC_OPTIMIZE_TYPE := full
LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(call intermediates-dir-for,STATIC_LIBRARIES,libinstrumentation,HOST,,,)/proto/$(LOCAL_PATH)/proto
include $(BUILD_HOST_STATIC_LIBRARY)

