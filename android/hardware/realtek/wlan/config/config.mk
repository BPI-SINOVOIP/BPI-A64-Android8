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

PRODUCT_COPY_FILES += \
    hardware/realtek/wlan/config/wpa_supplicant_overlay.conf:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/wpa_supplicant_overlay.conf \
    hardware/realtek/wlan/config/p2p_supplicant_overlay.conf:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/p2p_supplicant_overlay.conf \
    hardware/realtek/wlan/config/macprog.sh:$(TARGET_COPY_OUT_VENDOR)/xbin/macprog.sh \
    device/softwinner/common/init.wireless.realtek.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.wireless.rc

PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,"wifi_efuse_*.map",$(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/,$(TARGET_COPY_OUT_VENDOR)/etc/wifi)
