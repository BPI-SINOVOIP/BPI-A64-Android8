LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

BDROID_DIR := $(TOP_DIR)system/bt

LOCAL_SRC_FILES := \
        codec/msbc/sbc.c \
        src/rtk_socket.c \
        src/bt_vendor_rtk.c \
        src/hardware.c \
        src/userial_vendor.c \
        src/upio.c \
        src/bt_list.c \
        src/bt_skbuff.c \
        src/hci_h5.c \
        src/rtk_parse.c \
        src/rtk_btsnoop_net.c

LOCAL_C_INCLUDES += \
        $(LOCAL_PATH)/include \
        $(LOCAL_PATH)/codec/msbc \
        $(BDROID_DIR)/hci/include

LOCAL_SHARED_LIBRARIES := \
        libcutils \
        liblog

ifeq ($(BOARD_HAVE_BLUETOOTH_NAME), rtl8723bs)
LOCAL_CFLAGS += -DRTL_8723BS_BT_USED
endif

ifeq ($(BOARD_HAVE_BLUETOOTH_NAME), rtl8723bs_vq0)
LOCAL_CFLAGS += -DRTL_8723BS_VQ0_BT_USED
endif

LOCAL_MODULE := libbt-vendor
LOCAL_MODULE_TAGS := optional
LOCAL_PROPRIETARY_MODULE := true
LOCAL_MODULE_CLASS := SHARED_LIBRARIES

include $(BUILD_SHARED_LIBRARY)
