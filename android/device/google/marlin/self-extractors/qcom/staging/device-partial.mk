# Copyright 2016 The Android Open Source Project
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

#  blob(s) necessary for Marlin hardware
PRODUCT_COPY_FILES := \
    vendor/qcom/marlin/proprietary/pktlogconf:system/bin/pktlogconf:qcom \
    vendor/qcom/marlin/proprietary/ATT_profiles.xml:system/etc/cne/Nexus/ATT/ATT_profiles.xml:qcom \
    vendor/qcom/marlin/proprietary/ROW_profiles.xml:system/etc/cne/Nexus/ROW/ROW_profiles.xml:qcom \
    vendor/qcom/marlin/proprietary/VZW_profiles.xml:system/etc/cne/Nexus/VZW/VZW_profiles.xml:qcom \
    vendor/qcom/marlin/proprietary/com.android.ims.rcsmanager.xml:system/etc/permissions/com.android.ims.rcsmanager.xml:qcom \
    vendor/qcom/marlin/proprietary/com.android.ims.rcsmanager.jar:system/framework/com.android.ims.rcsmanager.jar:qcom \
    vendor/qcom/marlin/proprietary/lib64/libaptX_encoder.so:system/lib64/libaptX_encoder.so:qcom \
    vendor/qcom/marlin/proprietary/lib64/libaptXHD_encoder.so:system/lib64/libaptXHD_encoder.so:qcom \
    vendor/qcom/marlin/proprietary/libclcore_neon.bc:system/lib/libclcore_neon.bc:qcom \
    vendor/qcom/marlin/proprietary/lib64/libiperf.so:system/lib64/libiperf.so:qcom \
    vendor/qcom/marlin/proprietary/lib64/libminui.so:system/lib64/libminui.so:qcom \
    vendor/qcom/marlin/proprietary/libaptX_encoder.so:system/lib/libaptX_encoder.so:qcom \
    vendor/qcom/marlin/proprietary/libaptXHD_encoder.so:system/lib/libaptXHD_encoder.so:qcom \
    vendor/qcom/marlin/proprietary/lib64/libbcc.so:system/lib64/libbcc.so:qcom \
    vendor/qcom/marlin/proprietary/libion.so:system/lib/libion.so:qcom \
    vendor/qcom/marlin/proprietary/libiperf.so:system/lib/libiperf.so:qcom \
    vendor/qcom/marlin/proprietary/lib64/libLLVM.so:system/lib64/libLLVM.so:qcom \
    vendor/qcom/marlin/proprietary/libminui.so:system/lib/libminui.so:qcom \
    vendor/qcom/marlin/proprietary/iperf3:system/xbin/iperf3:qcom \
    vendor/qcom/marlin/proprietary/sanitizer-status:system/xbin/sanitizer-status:qcom \

