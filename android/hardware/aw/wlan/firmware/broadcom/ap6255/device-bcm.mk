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


-include hardware/broadcom/wlan/bcmdhd/config/config-bcm.mk

PRODUCT_COPY_FILES += \
    hardware/aw/wlan/firmware/broadcom/ap6255/fw_bcm43455c0_ag.bin:vendor/modules/fw_bcm43455c0_ag.bin \
    hardware/aw/wlan/firmware/broadcom/ap6255/fw_bcm43455c0_ag_apsta.bin:vendor/modules/fw_bcm43455c0_ag_apsta.bin \
    hardware/aw/wlan/firmware/broadcom/ap6255/nvram_ap6255.txt:vendor/modules/nvram_ap6255.txt \
    hardware/aw/wlan/firmware/broadcom/ap6255/bcm4345c0.hcd:vendor/modules/ap6255.hcd \
    hardware/aw/wlan/firmware/broadcom/ap6255/config.txt:vendor/modules/config_ap6255.txt
