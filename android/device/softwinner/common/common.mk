DEVICE_PACKAGE_OVERLAYS := \
    device/softwinner/common/overlay

PRODUCT_COPY_FILES += \
    device/softwinner/common/init.common.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.common.rc

#media
include frameworks/av/media/libcedarc/libcdclist.mk
include frameworks/av/media/libcedarx/libcdxlist.mk

# ota tools
PRODUCT_PACKAGES += bro

# tools
PRODUCT_PACKAGES += \
    mtop \
    preinstall \

# build file_contexts.bin for dragonface
PRODUCT_PACKAGES += file_contexts.bin

# Audio
PRODUCT_PACKAGES += \
    audio.a2dp.default \
    audio.usb.default \
    audio.r_submix.default

# f2fs format tool for recovery
PRODUCT_PACKAGES += mkfs.f2fs

USE_XML_AUDIO_POLICY_CONF := 1

PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/a2dp_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/usb_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml

# scense_control
PRODUCT_PROPERTY_OVERRIDES += \
    sys.p_bootcomplete= true \
    sys.p_debug= false \
    sys.p_benchmark= true \
    sys.p_music= true

# sf control
PRODUCT_PROPERTY_OVERRIDES += \
    debug.sf.disable_backpressure= 1

#default logd buffer is too small
PRODUCT_PROPERTY_OVERRIDES += \
    ro.logd.size= 524288

# OEM Unlock reporting
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.oem_unlock_supported= 1 

# not support boots directly in VR mode
PRODUCT_PROPERTY_OVERRIDES += \
    ro.boot.vr= false

# bin: busybox and cpu_monitor
$(call inherit-product, vendor/aw/public/tool.mk)

#display service
$(call inherit-product, vendor/aw/public/package/display/display.mk)
