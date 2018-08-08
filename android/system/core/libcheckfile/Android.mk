LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= checkfile.cpp

LOCAL_MODULE:=libcheckfile
LOCAL_MODULE_TAGS := optional

LOCAL_SHARED_LIBRARIES+= libcutils liblog


LOCAL_CFLAGS += -DLOG_NDEBUG=0 -O3

include $(BUILD_SHARED_LIBRARY)


