LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

MOD_TOP=$(LOCAL_PATH)/../..
include $(MOD_TOP)/config.mk

LOCAL_CFLAGS += $(AW_OMX_EXT_CFLAGS)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES:= transform_color_format.c
LOCAL_SRC_FILES += omx_deinterlace.cpp

ifeq ($(NEW_DISPLAY), yes)
    LOCAL_SRC_FILES += omx_vdec_newDisplay.cpp
else
    LOCAL_SRC_FILES += omx_vdec.cpp
endif

LOCAL_CFLAGS += -DTARGET_BOARD_PLATFORM=$(TARGET_BOARD_PLATFORM)

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../omxcore/inc/ \
	$(TOP)/frameworks/native/include/     \
	$(TOP)/frameworks/native/include/media/hardware \
	$(TOP)/frameworks/av/media/libcedarc/include \
	$(TOP)/hardware/libhardware/include/hardware/ \
	$(TOP)/frameworks/av/media/libstagefright/foundation/include \
	$(MOD_TOP)/memory/include \
	$(MOD_TOP)/include \
	$(MOD_TOP)/base/include \
	$(MOD_TOP)/ve/include \
	$(MOD_TOP)/

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	liblog \
	libbinder \
	libui       \
	libdl \
	libion        \
	
ifeq ($(COMPILE_SO_IN_VENDOR), yes)
LOCAL_C_INCLUDES += frameworks/native/libs/nativebase/include \
                frameworks/native/libs/nativewindow/include \
                frameworks/native/libs/arect/include

LOCAL_SHARED_LIBRARIES += \
	libVE \
        libvdecoder \
        libvideoengine \
        libMemAdapter \
        libcdc_base
# build so in verdor
LOCAL_PROPRIETARY_MODULE := true
else

LOCAL_SHARED_LIBRARIES += \
        libVE \
        libvdecoder \
        libvideoengine \
        libMemAdapter \
        libcdc_base
endif

#libvdecoder
LOCAL_MODULE:= libOmxVdec

include $(BUILD_SHARED_LIBRARY)
