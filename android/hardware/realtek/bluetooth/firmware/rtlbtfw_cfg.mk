#
# Copyright (C) 2008 The Android Open Source Project
#

LOCAL_PATH = hardware/realtek/bluetooth/firmware

PRODUCT_COPY_FILES += \
    $(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/bluetooth/rtkbt.conf:$(TARGET_COPY_OUT_VENDOR)/etc/bluetooth/rtkbt.conf

PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,"rtl*_config",$(LOCAL_PATH),$(TARGET_COPY_OUT_VENDOR)/etc/firmware) \
    $(call find-copy-subdir-files,"rtl*_fw",$(LOCAL_PATH),$(TARGET_COPY_OUT_VENDOR)/etc/firmware)
