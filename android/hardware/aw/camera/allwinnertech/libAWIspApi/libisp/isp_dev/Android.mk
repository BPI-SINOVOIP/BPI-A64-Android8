#
# 1. Set the path and clear environment
#   TARGET_PATH := $(call my-dir)
#   include $(ENV_CLEAR)
#
# 2. Set the source files and headers files
#   TARGET_SRC := xxx_1.c xxx_2.c
#   TARGET_INc := xxx_1.h xxx_2.h
#
# 3. Set the output target
#   TARGET_MODULE := xxx
#
# 4. Include the main makefile
#   include $(BUILD_BIN)
#
# Before include the build makefile, you can set the compilaion
# flags, e.g. TARGET_ASFLAGS TARGET_CFLAGS TARGET_CPPFLAGS
#

#TARGET_PATH :=$(call my-dir)

#########################################
#include $(ENV_CLEAR)
#SRC_TAGS := \
#   video
#TARGET_SRC := $(call all-c-files-under, $(SRC_TAGS))
#TARGET_SRC += \
#   isp_v4l2_helper.c \
#   isp_stats.c \
#   isp_dev.c \
#   isp_subdev.c \
#   media.c
#TARGET_INC := \
#   $(TARGET_PATH)/ \
#   $(TARGET_PATH)/video \
#   $(TARGET_PATH)/../include/V4l2Camera \
#   $(TARGET_PATH)/../include/device
#TARGET_CFLAGS := -fPIC -rdynamic -Wall -Wno-unused-variable
#TARGET_MODULE := libisp_dev
#include $(BUILD_STATIC_LIB)

#########################################
#include $(ENV_CLEAR)
#SRC_TAGS := \
#   video
#TARGET_SRC := $(call all-c-files-under, $(SRC_TAGS))
#TARGET_SRC += \
#   isp_v4l2_helper.c \
#   isp_stats.c \
#   isp_dev.c \
#   isp_subdev.c \
#   media.c
#TARGET_INC := \
#   $(TARGET_PATH)/ \
#   $(TARGET_PATH)/video \
#   $(TARGET_PATH)/../include/V4l2Camera \
#   $(TARGET_PATH)/../include/device
#TARGET_CFLAGS := -fPIC -rdynamic -Wall -Wno-unused-variable
#TARGET_MODULE := libisp_dev
#include $(BUILD_SHARED_LIB)

#########################################
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := libisp_dev

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    isp_v4l2_helper.c \
    isp_stats.c \
    isp_dev.c \
    isp_subdev.c \
    media.c \
    video/video.c

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/ \
    $(LOCAL_PATH)/video \
    $(LOCAL_PATH)/../include/V4l2Camera \
    $(LOCAL_PATH)/../include/device

include $(BUILD_STATIC_LIBRARY)
