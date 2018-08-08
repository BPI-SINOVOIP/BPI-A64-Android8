#
# Copyright (C) 2017 Allwinner Technology Limited. All rights reserved.
#
# This program is free software and is provided to you under the terms of the
# GNU General Public License version 2 as published by the Free Software
# Foundation, and any use by you of this program is subject to the terms
# of such GNU licence.
#

ifneq ($(filter $(TARGET_GPU_TYPE), mali400 mali450),)
GPU_ARCH_NAME = mali-utgard
endif

ifneq ($(filter $(TARGET_GPU_TYPE), mali-t720 mali-t760),)
GPU_ARCH_NAME = mali-midgard
endif

ifneq ($(filter $(TARGET_GPU_TYPE), sgx544),)
GPU_ARCH_NAME = sgx544
endif

GPU_LIB_ROOT_DIR = hardware/aw/gpu/$(GPU_ARCH_NAME)/$(TARGET_GPU_TYPE)/$(TARGET_ARCH)

ifeq ($(wildcard $(GPU_LIB_ROOT_DIR)/lib/*.so),)
$(error invalid GPU_LIB_ROOT_DIR "$(GPU_LIB_ROOT_DIR)"!)
endif

ifneq ($(filter $(TARGET_GPU_TYPE), mali400 mali450 mali-t720 mali-t760),)
PRODUCT_PACKAGES += \
	gralloc.$(TARGET_BOARD_PLATFORM)

PRODUCT_COPY_FILES += \
	$(GPU_LIB_ROOT_DIR)/lib/libGLES_mali.so:vendor/lib/egl/libGLES_mali.so
ifneq ($(filter $(TARGET_ARCH), arm64),)
PRODUCT_COPY_FILES += \
	$(GPU_LIB_ROOT_DIR)/lib64/libGLES_mali.so:vendor/lib64/egl/libGLES_mali.so
endif
endif

ifneq ($(filter $(TARGET_GPU_TYPE), sgx544),)
PRODUCT_COPY_FILES += \
	$(GPU_LIB_ROOT_DIR)/lib/libusc.so:vendor/lib/libusc.so \
	$(GPU_LIB_ROOT_DIR)/lib/libglslcompiler.so:vendor/lib/libglslcompiler.so \
	$(GPU_LIB_ROOT_DIR)/lib/libIMGegl.so:vendor/lib/libIMGegl.so \
	$(GPU_LIB_ROOT_DIR)/lib/libpvr2d.so:vendor/lib/libpvr2d.so \
	$(GPU_LIB_ROOT_DIR)/lib/libpvrANDROID_WSEGL.so:vendor/lib/libpvrANDROID_WSEGL.so \
	$(GPU_LIB_ROOT_DIR)/lib/libPVRScopeServices.so:vendor/lib/libPVRScopeServices.so \
	$(GPU_LIB_ROOT_DIR)/lib/libsrv_init.so:vendor/lib/libsrv_init.so \
	$(GPU_LIB_ROOT_DIR)/lib/libsrv_um.so:vendor/lib/libsrv_um.so \
	$(GPU_LIB_ROOT_DIR)/lib/libEGL_POWERVR_SGX544_115.so:vendor/lib/egl/libEGL_POWERVR_SGX544_115.so \
	$(GPU_LIB_ROOT_DIR)/lib/libGLESv1_CM_POWERVR_SGX544_115.so:vendor/lib/egl/libGLESv1_CM_POWERVR_SGX544_115.so \
	$(GPU_LIB_ROOT_DIR)/lib/libGLESv2_POWERVR_SGX544_115.so:vendor/lib/egl/libGLESv2_POWERVR_SGX544_115.so \
	$(GPU_LIB_ROOT_DIR)/lib/gralloc.sunxi.so:vendor/lib/hw/gralloc.$(TARGET_BOARD_PLATFORM).so \
	$(GPU_LIB_ROOT_DIR)/lib/powervr.ini:etc/powervr.ini \
	$(GPU_LIB_ROOT_DIR)/lib/pvrsrvctl:vendor/bin/pvrsrvctl \
	$(GPU_LIB_ROOT_DIR)/egl.cfg:system/lib/egl/egl.cfg
endif

# System prop for opengles version
# 131072 is decimal for 0x20000 to report version 2.0
# 196608 is decimal for 0x30000 to report version 3.0
# 196609 is decimal for 0x30001 to report version 3.1
# 196610 is decimal for 0x30001 to report version 3.2
ifneq ($(filter $(TARGET_GPU_TYPE), mali400 mali450 sgx544),)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.opengles.version=131072
endif

ifneq ($(filter $(TARGET_GPU_TYPE), mali-t720 mali-t760),)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.opengles.version=196610
endif
