#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

LIBFUSE_DIR_INCLUDE := \
    $(LOCAL_PATH)/include

mount_source = \
    lib/mount.c \
    lib/mount_util.c
# mount_util.h
iconv_source = lib/modules/iconv.c

LIBFUSE_SRC_FILE := \
    lib/fuse.c \
    lib/fuse_kern_chan.c \
    lib/fuse_loop.c \
    lib/fuse_loop_mt.c \
    lib/fuse_lowlevel.c \
    lib/fuse_mt.c \
    lib/fuse_opt.c \
    lib/fuse_session.c \
    lib/fuse_signals.c \
    lib/buffer.c \
    lib/cuse_lowlevel.c \
    lib/helper.c \
    lib/modules/subdir.c \
    $(iconv_source) \
    $(mount_source)

bindir = $(TARGET_COPY_OUT_VENDOR)/bin

LIBFUSE_CFLAGS += \
    -DFUSERMOUNT_DIR=\"$(bindir)\" \
    -D_FILE_OFFSET_BITS=64 \
    -D_REENTRANT \
    -DFUSE_USE_VERSION=26 \
    -DPACKAGE_VERSION=\"2.9.7\"

include $(CLEAR_VARS)
LOCAL_MODULE := libfuse
LOCAL_SHARED_LIBRARIES := libc libcutils
LOCAL_CFLAGS := $(LIBFUSE_CFLAGS)
LOCAL_SRC_FILES := $(LIBFUSE_SRC_FILE)
LOCAL_C_INCLUDES := $(LIBFUSE_DIR_INCLUDE)
LOCAL_PROPRIETARY_MODULE := true
include $(BUILD_STATIC_LIBRARY)
