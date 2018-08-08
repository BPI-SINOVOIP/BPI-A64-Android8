
#########################################

LOCAL_PATH:= $(call my-dir)
#include $(all-subdir-makefiles)
include $(CLEAR_VARS)

LOCAL_MODULE := libisp

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    isp.c \
	isp_events/events.c \
    isp_tuning/isp_tuning.c \
    isp_manage/isp_manage.c


LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/include/V4l2Camera \
    $(LOCAL_PATH)/include/device \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/isp_dev \
    $(LOCAL_PATH)/isp_tuning \
    $(LOCAL_PATH)/

LOCAL_STATIC_LIBRARIES += \
    libiniparser \
    libisp_dev

LOCAL_SHARED_LIBRARIES += \
    libisp_ini libcutils liblog

LOCAL_LDFLAGS += \
    $(LOCAL_PATH)/out/libisp_ae.a \
    $(LOCAL_PATH)/out/libisp_af.a \
    $(LOCAL_PATH)/out/libisp_afs.a \
    $(LOCAL_PATH)/out/libisp_awb.a \
    $(LOCAL_PATH)/out/libisp_base.a \
    $(LOCAL_PATH)/out/libisp_gtm.a \
    $(LOCAL_PATH)/out/libisp_iso.a \
    $(LOCAL_PATH)/out/libisp_math.a \
    $(LOCAL_PATH)/out/libisp_md.a \
    $(LOCAL_PATH)/out/libisp_pltm.a \
    $(LOCAL_PATH)/out/libisp_rolloff.a \
    $(LOCAL_PATH)/out/libmatrix.a


include $(BUILD_SHARED_LIBRARY)

#########################################
#LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := server_test

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    isp_test.c

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/include/V4l2Camera \
    $(LOCAL_PATH)/include/device \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/isp_dev \
    $(LOCAL_PATH)/isp_tuning \
    $(LOCAL_PATH)/

LOCAL_SHARED_LIBRARIES := \
    libisp libcutils liblog

include $(BUILD_EXECUTABLE)

#########################################
#LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := vin_test

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    vi_api/vi_api.c \
    vin_isp_test.c

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/include/V4l2Camera \
    $(LOCAL_PATH)/include/device \
    $(LOCAL_PATH)/include \
    $(LOCAL_PATH)/isp_dev \
    $(LOCAL_PATH)/isp_tuning \
    $(LOCAL_PATH)/

LOCAL_SHARED_LIBRARIES := \
    libisp libcutils liblog

include $(BUILD_EXECUTABLE)

include $(call all-makefiles-under,$(LOCAL_PATH))