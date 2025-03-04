ifneq ($(filter tulip-m64,$(TARGET_DEVICE)),)


VNDK_SP_LIBRARIES := \
    android.hardware.graphics.allocator@2.0 \
    android.hardware.graphics.common@1.0 \
    android.hardware.graphics.mapper@2.0 \
    libbacktrace \
    libbase \
    libc++ \
    libcutils \
    libhardware \
    libhidlbase \
    libhidltransport \
    libhwbinder \
    libion \
    liblzma \
    libunwind \
    libutils

VNDK_SP_EXT_LIBRARIES := \

EXTRA_VENDOR_LIBRARIES := \
    android.hardware.radio.deprecated@1.0 \
    android.hardware.radio@1.0 \
    android.hardware.radio@1.1 \
    android.hardware.wifi.supplicant@1.0 \
    android.hardware.wifi@1.0 \
    android.hardware.wifi@1.1 \
    libSmileEyeBlink \
    libapperceivepeople \
    libaudioroute \
    libcap \
    libfacedetection \
    libhdr \
    libkeymaster_portable \
    libkeymaster_staging \
    libproc \
    libprotobuf-cpp-full \
    libsoftkeymasterdevice \
    libtinyalsa \
	libcdc_base \
	libawavs \
        libawh264 \
        libawh265 \
        libawh265soft \
        libawmjpeg \
        libawmjpegplus \
        libawmpeg2 \
        libawmpeg4base \
        libawmpeg4dx \
        libawmpeg4h263 \
        libawmpeg4normal \
        libawmpeg4vp6 \
        libawvp6soft \
        libawvp8 \
        libawvp9soft \
        libawvp9Hw    \
        libawwmv12soft \
        libawwmv3 \
        libMemAdapter \
        libvdecoder \
        libvideoengine \
        libVE \
        libvencoder \

# If a product include IllegalLib.mk, that product is not well implemented.
# We should remove all dependencies listed in that file, and eliminate it.

#-------------------------------------------------------------------------------
# VNDK Modules
#-------------------------------------------------------------------------------
LOCAL_PATH := $(call my-dir)

define define-vndk-lib
include $$(CLEAR_VARS)
LOCAL_MODULE := $1.$2
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PREBUILT_MODULE_FILE := $$(TARGET_OUT_INTERMEDIATE_LIBRARIES)/$1.so
LOCAL_STRIP_MODULE := false
LOCAL_MULTILIB := first
LOCAL_MODULE_TAGS := optional
LOCAL_INSTALLED_MODULE_STEM := $1.so
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_RELATIVE_PATH := $3
LOCAL_VENDOR_MODULE := $4
include $$(BUILD_PREBUILT)

ifneq ($$(TARGET_2ND_ARCH),)
ifneq ($$(TARGET_TRANSLATE_2ND_ARCH),true)
include $$(CLEAR_VARS)
LOCAL_MODULE := $1.$2
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_PREBUILT_MODULE_FILE := $$($$(TARGET_2ND_ARCH_VAR_PREFIX)TARGET_OUT_INTERMEDIATE_LIBRARIES)/$1.so
LOCAL_STRIP_MODULE := false
LOCAL_MULTILIB := 32
LOCAL_MODULE_TAGS := optional
LOCAL_INSTALLED_MODULE_STEM := $1.so
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_RELATIVE_PATH := $3
LOCAL_VENDOR_MODULE := $4
include $$(BUILD_PREBUILT)
endif  # TARGET_TRANSLATE_2ND_ARCH is not true
endif  # TARGET_2ND_ARCH is not empty
endef

$(foreach lib,$(VNDK_SP_LIBRARIES),\
    $(eval $(call define-vndk-lib,$(lib),vndk-sp-gen,vndk-sp,)))
$(foreach lib,$(VNDK_SP_EXT_LIBRARIES),\
    $(eval $(call define-vndk-lib,$(lib),vndk-sp-ext-gen,vndk-sp,true)))
$(foreach lib,$(EXTRA_VENDOR_LIBRARIES),\
    $(eval $(call define-vndk-lib,$(lib),vndk-ext-gen,,true)))


#-------------------------------------------------------------------------------
# Phony Package
#-------------------------------------------------------------------------------

include $(CLEAR_VARS)
LOCAL_MODULE := tulip-m64-vndk
LOCAL_MODULE_TAGS := optional
LOCAL_REQUIRED_MODULES := \
    $(addsuffix .vndk-sp-gen,$(VNDK_SP_LIBRARIES)) \
    $(addsuffix .vndk-sp-ext-gen,$(VNDK_SP_EXT_LIBRARIES)) \
    $(addsuffix .vndk-ext-gen,$(EXTRA_VENDOR_LIBRARIES))
include $(BUILD_PHONY_PACKAGE)

endif  # ifneq ($(filter tulip-m64,$(TARGET_DEVICE)),)
