# Copyright 2006 The Android Open Source Project
LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
ifdef WIFI_VENDOR_NAME
LOCAL_CFLAGS += -DWIFI_VENDOR_NAME=\"$(WIFI_VENDOR_NAME)\"
else
ifdef BOARD_WIFI_VENDOR
LOCAL_CFLAGS += -DWIFI_VENDOR_NAME=\"$(BOARD_WIFI_VENDOR)\"
endif
endif

ifdef WIFI_MODULE_NAME
LOCAL_CFLAGS += -DWIFI_MODULE_NAME=\"$(WIFI_MODULE_NAME)\"
else
ifdef BOARD_USR_WIFI
LOCAL_CFLAGS += -DWIFI_MODULE_NAME=\"$(BOARD_USR_WIFI)\"
else
ifdef WIFI_DRIVER_MODULE_NAME
LOCAL_CFLAGS += -DWIFI_MODULE_NAME=\"$(WIFI_DRIVER_MODULE_NAME)\"
endif
endif
endif

ifdef WIFI_DRIVER_NAME
LOCAL_CFLAGS += -DWIFI_DRIVER_NAME=\"$(WIFI_DRIVER_NAME)\"
else
ifdef WIFI_DRIVER_MODULE_PATH
drivername=$(shell echo $(WIFI_DRIVER_MODULE_PATH) | awk -F '/' '{print $$NF}' | sed 's/.ko//g')
LOCAL_CFLAGS += -DWIFI_DRIVER_NAME=\"$(drivername)\"
endif
endif

ifdef WIFI_DRIVER_MODULE_NAME
LOCAL_CFLAGS += -DWIFI_DRIVER_MODULE_NAME=\"$(WIFI_DRIVER_MODULE_NAME)\"
endif

ifdef WIFI_DRIVER_FW_PATH_STA
LOCAL_CFLAGS += -DWIFI_DRIVER_FW_PATH_STA=\"$(WIFI_DRIVER_FW_PATH_STA)\"
endif

ifdef WIFI_DRIVER_FW_PATH_AP
LOCAL_CFLAGS += -DWIFI_DRIVER_FW_PATH_AP=\"$(WIFI_DRIVER_FW_PATH_AP)\"
endif

ifdef WIFI_DRIVER_FW_PATH_P2P
LOCAL_CFLAGS += -DWIFI_DRIVER_FW_PATH_P2P=\"$(WIFI_DRIVER_FW_PATH_P2P)\"
endif

modulestring=$(shell echo $(LOCAL_CFLAGS) | grep -oP 'WIFI_MODULE_NAME\s*=\s*[^\s]+' \
                                          | sed -e 's/WIFI_MODULE_NAME\s*=\s*//g' -e 's/"//g')
ifeq (rtl8723bs,$(modulestring))
LOCAL_CFLAGS += -DWIFI_USE_RTL8723BS
else
ifeq (rtl8723bs_vq0,$(modulestring))
LOCAL_CFLAGS += -DWIFI_USE_RTL8723BS_VQ0
endif
endif

LOCAL_MODULE:= libwifi_hardware_info
LOCAL_SRC_FILES += wifi_hardware_info.c
include $(BUILD_STATIC_LIBRARY)
