# inherit common.mk
$(call inherit-product, device/softwinner/common/common.mk)
$(call inherit-product, device/softwinner/uranus-common/uranus-hal.mk)

DEVICE_PACKAGE_OVERLAYS := \
    device/softwinner/uranus-common/overlay \
    $(DEVICE_PACKAGE_OVERLAYS)
# Allows healthd to boot directly from charger mode rather than initiating a reboot.
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.enable_boot_charger_mode=1

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-common/init.sun50iw3p1.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.sun50iw3p1.rc \
    device/softwinner/uranus-common/init.sun50iw3p1.usb.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.sun50iw3p1.usb.rc \
    device/softwinner/uranus-common/ueventd.sun50iw3p1.rc:root/ueventd.sun50iw3p1.rc

# video
PRODUCT_COPY_FILES += \
    device/softwinner/uranus-common/configs/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    device/softwinner/uranus-common/configs/media_codecs_google_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_audio.xml \
    device/softwinner/uranus-common/configs/media_codecs_google_video.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_video.xml \
    device/softwinner/uranus-common/configs/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    device/softwinner/uranus-common/configs/mediacodec-arm.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    device/softwinner/uranus-common/configs/cfg-videoplayer.xml:system/etc/cfg-videoplayer.xml

# Audio
USE_XML_AUDIO_POLICY_CONF := 1

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-common/configs/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    device/softwinner/uranus-common/configs/audio_policy_volumes_drc.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes_drc.xml \
    device/softwinner/uranus-common/configs/audio_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info.xml \
    device/softwinner/uranus-common/configs/audio_mixer_paths.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_mixer_paths.xml \
    hardware/libhardware_legacy/audio/audio_policy.conf:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy.conf

#FEATURE_OPENGLES_EXTENSION_PACK support string config file
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.opengles.aep.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.opengles.aep.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.hardware.usb.accessory.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.accessory.xml \
    frameworks/native/data/etc/android.software.backup.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.backup.xml

PRODUCT_PROPERTY_OVERRIDES += \
    ro.sys.cputype=UltraOcta-A63

PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.firmware=v0.1

# build number
DISPLAY_BUILD_NUMBER := true
BUILD_NUMBER := $(shell date +%Y%m%d)

PRODUCT_PROPERTY_OVERRIDES += \
    drm.service.enabled=true

PRODUCT_PACKAGES += \
    libwvhidl \
    libwvdrmengine \
    libvtswidevine

ifeq ($(BOARD_HAS_SECURE_OS), true)
SECURE_OS_OPTEE := yes
PRODUCT_PACKAGES += \
    libteec \
    tee_supplicant

# keymaster version (0 or 2)
BOARD_KEYMASTER_VERSION := 2

# hardware keymaster hal
PRODUCT_PACKAGES += \
    keystore.uranus

# keymaster ta
ifeq ($(BOARD_KEYMASTER_VERSION), 0)
PRODUCT_COPY_FILES += \
    device/softwinner/common/optee_ta/d6bebe60-be3e-4046-b239891e0a594860.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/d6bebe60-be3e-4046-b239891e0a594860.ta
else
PRODUCT_COPY_FILES += \
    device/softwinner/common/optee_ta/f5f7b549-ba64-44fe-9b74f3fc357c7c61.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/f5f7b549-ba64-44fe-9b74f3fc357c7c61.ta
endif

# gatekeeper ta
PRODUCT_COPY_FILES += \
    device/softwinner/common/optee_ta/2233b43b-cec6-449a-9509469f5023e425.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/2233b43b-cec6-449a-9509469f5023e425.ta

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)
PRODUCT_PACKAGES += \
    liboemcrypto
PRODUCT_COPY_FILES += \
    device/softwinner/common/optee_ta/a98befed-d679-ce4a-a3c827dcd51d21ed.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/a98befed-d679-ce4a-a3c827dcd51d21ed.ta \
    device/softwinner/common/optee_ta/4d78d2ea-a631-70fb-aaa787c2b5773052.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/4d78d2ea-a631-70fb-aaa787c2b5773052.ta \
    device/softwinner/common/optee_ta/e41f7029-c73c-344a-8c5bae90c7439a47.ta:$(TARGET_COPY_OUT_VENDOR)/lib/optee_armtz/e41f7029-c73c-344a-8c5bae90c7439a47.ta
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sys.widevine_oemcrypto_level=1
else # ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sys.widevine_oemcrypto_level=3
endif # ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)

else # ifeq ($(BOARD_HAS_SECURE_OS), true)
SECURE_OS_OPTEE := no
# if has no secure os, widevine level must set to 3
BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 3
PRODUCT_PROPERTY_OVERRIDES += \
    ro.sys.widevine_oemcrypto_level=3
endif # ifeq ($(BOARD_HAS_SECURE_OS), true)

# Wi-Fi XML
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml

# Bluetooth XML
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml

# Light XML
PRODUCT_COPY_FILES += \
	frameworks/native/data/etc/android.hardware.sensor.light.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.light.xml \
	frameworks/native/data/etc/android.hardware.sensor.proximity.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.proximity.xml

# Camera XML
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.camera.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.autofocus.xml

# Touch XML
    PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.touchscreen.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.xml

########################################################################
# GPU relevant configuration
# As we could not get the variable in BoardConfigCommon.mk,
# TARGET_GPU_TYPE, TARGET_ARCH and TARGET_BOARD_PLATFORM
# should be assigned here for hardware/aw/gpu/product_config.mk.
# If the platform does not own a GPU, the GPU configuration
# should not added here.
TARGET_GPU_TYPE := mali-t760
TARGET_ARCH := arm64
TARGET_BOARD_PLATFORM := uranus
$(call inherit-product, hardware/aw/gpu/product_config.mk)
########################################################################
