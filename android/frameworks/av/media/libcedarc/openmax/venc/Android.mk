LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

MOD_TOP=$(LOCAL_PATH)/../..

include $(MOD_TOP)/config.mk

LOCAL_CFLAGS += $(AW_OMX_EXT_CFLAGS)
LOCAL_CFLAGS += -D__OS_ANDROID
LOCAL_CFLAGS += -DTARGET_BOARD_PLATFORM=$(TARGET_BOARD_PLATFORM)
LOCAL_MODULE_TAGS := optional

ifeq ($(TARGET_ARCH_VARIANT), armv7-a-neon)
LOCAL_SRC_FILES:= neon_rgb2yuv.s  omx_venc.cpp omx_tsem.c
else
LOCAL_SRC_FILES:= omx_venc.cpp omx_tsem.c
endif


####################### donot support neon in android N ###########
ifeq ($(NOUGAT_AND_NEWER), yes)
LOCAL_SRC_FILES:= omx_venc.cpp omx_tsem.c
endif


###########################################################

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../omxcore/inc/ \
	$(TOP)/frameworks/native/include/     \
	$(TOP)/frameworks/native/include/media/hardware \
	$(TOP)/frameworks/native/include/media/openmax \
	$(TOP)/frameworks/av/media/libcedarc/include \
	$(MOD_TOP)/memory/include \
	$(MOD_TOP)/include \
	$(MOD_TOP)/base/include \
	$(MOD_TOP)/ve/include \
	$(MOD_TOP)/ \

LOCAL_C_INCLUDES  += $(TOP)/hardware/libhardware/include/hardware/

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    liblog \
    libbinder \
    libui \
    libion

ifeq ($(COMPILE_SO_IN_VENDOR), yes)
LOCAL_C_INCLUDES += frameworks/native/libs/nativebase/include \
                frameworks/native/libs/nativewindow/include \
                frameworks/native/libs/arect/include

LOCAL_SHARED_LIBRARIES += \
	libVE \
    	libMemAdapter \
    	libvencoder \
    	libcdc_base
LOCAL_PROPRIETARY_MODULE := true
else
LOCAL_SHARED_LIBRARIES += \
	libVE \
    	libMemAdapter \
    	libvencoder \
    	libcdc_base
endif

LOCAL_MODULE:= libOmxVenc
include $(BUILD_SHARED_LIBRARY)
