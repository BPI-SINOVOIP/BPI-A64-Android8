LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE:= rstest-latency

LOCAL_SDK_VERSION := 21
LOCAL_NDK_STL_VARIANT := stlport_static

LOCAL_SRC_FILES:= \
	latency.rs \
	latency.cpp

LOCAL_STATIC_LIBRARIES := \
	libRScpp_static

include frameworks/rs/tests/cpp_api/common.mk
include $(BUILD_EXECUTABLE)
