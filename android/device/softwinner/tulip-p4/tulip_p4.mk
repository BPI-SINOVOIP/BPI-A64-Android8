$(call inherit-product, device/softwinner/tulip-common/tulip_64_bit.mk)
$(call inherit-product, device/softwinner/tulip-p4/configs/go/go_base.mk)
$(call inherit-product, device/softwinner/tulip-common/tulip-common.mk)
$(call inherit-product-if-exists, device/softwinner/tulip-p4/modules/modules.mk)
$(call inherit-product, device/softwinner/tulip-p4/hal.mk)
$(call inherit-product, device/softwinner/common/pad.mk)
$(call inherit-product, build/target/product/go_defaults.mk)

DEVICE_PACKAGE_OVERLAYS := device/softwinner/tulip-p4/overlay \
                           $(DEVICE_PACKAGE_OVERLAYS)

# Strip the local variable table and the local variable type table to reduce
# the size of the system image. This has no bearing on stack traces, but will
# leave less information available via JDWP.
PRODUCT_MINIMIZE_JAVA_DEBUG_INFO := true

# Do not generate libartd.
PRODUCT_ART_TARGET_INCLUDE_DEBUG_BUILD := false

# Reduces GC frequency of foreground apps by 50%
PRODUCT_PROPERTY_OVERRIDES += dalvik.vm.foreground-heap-growth-multiplier=2.0

PRODUCT_DEX_PREOPT_PROFILE_DIR := device/softwinner/tulip-p4/configs/profiles

# secure config
BOARD_HAS_SECURE_OS := true

# drm config
BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 3

# dm-verity relative
$(call inherit-product, build/target/product/verity.mk)
# PRODUCT_SUPPORTS_BOOT_SIGNER must be false,otherwise error will be find when boota check boot partition
PRODUCT_SUPPORTS_BOOT_SIGNER := false
#PRODUCT_SUPPORTS_VERITY_FEC := false
PRODUCT_SYSTEM_VERITY_PARTITION := /dev/block/by-name/system
PRODUCT_VENDOR_VERITY_PARTITION := /dev/block/by-name/vendor

PRODUCT_PACKAGES += Launcher3Go

# Sound Recorder
PRODUCT_PACKAGES += SoundRecorder

#PRODUCT_NOT_USES_VENDORIMAGE := true
ifneq ($(PRODUCT_NOT_USES_VENDORIMAGE), true)
# vndk
PRODUCT_PACKAGES += tulip-p4-vndk
endif

############################### 3G Dongle Support ###############################
# Radio Packages and Configuration Flie
$(call inherit-product, vendor/aw/public/prebuild/lib/librild/radio_common.mk)

##################### Realtek WiFi & Bluetooth Config start #####################
# WiFi Property for Realtek modules
PRODUCT_PROPERTY_OVERRIDES += \
    wifi.interface=wlan0 \
    wifi.direct.interface=p2p0

# Bluetooth Property for Realtek module
PRODUCT_PROPERTY_OVERRIDES += \
    persist.bluetooth.btsnoopenable=false \
    persist.bluetooth.btsnooppath=/data/misc/bluedroid/btsnoop_hci.cfa \
    persist.bluetooth.btsnoopsize=0xffff \
    persist.bluetooth.rtkcoex=true \
    bluetooth.enable_timeout_ms=11000
###################### Realtek WiFi & Bluetooth Config end ######################

# Disable the task snapshots feature
PRODUCT_PROPERTY_OVERRIDES += \
    persist.enable_task_snapshots = false

PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p4/kernel:kernel \
    device/softwinner/tulip-p4/fstab.sun50iw1p1:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.sun50iw1p1 \
    device/softwinner/tulip-p4/init.device.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.device.rc \
    device/softwinner/tulip-p4/init.recovery.sun50iw1p1.rc:root/init.recovery.sun50iw1p1.rc \
	device/softwinner/tulip-p4/modules/modules/ft5x16_ts.ko:recovery/root/ft5x16_ts.ko \

PRODUCT_COPY_FILES += \
    device/softwinner/common/config/tablet_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/tablet_core_hardware.xml \
    frameworks/native/data/etc/android.hardware.camera.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.software.verified_boot.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.verified_boot.xml \
    frameworks/native/data/etc/android.hardware.ethernet.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.ethernet.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.xml

PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p4/configs/camera.cfg:$(TARGET_COPY_OUT_VENDOR)/etc/camera.cfg \
    device/softwinner/tulip-p4/configs/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    device/softwinner/tulip-p4/configs/tp.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/ft5x_ts.idc \
    device/softwinner/tulip-p4/configs/gsensor.cfg:$(TARGET_COPY_OUT_VENDOR)/etc/gsensor.cfg \
    device/softwinner/common/config/awbms_config:$(TARGET_COPY_OUT_VENDOR)/etc/awbms_config \

# bootanimation
PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p4/media/bootanimation.zip:system/media/bootanimation.zip

# audio
PRODUCT_COPY_FILES += \
    device/softwinner/tulip-p4/configs/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml

PRODUCT_PROPERTY_OVERRIDES += \
    ro.radio.noril=true

PRODUCT_PROPERTY_OVERRIDES += \
    ro.frp.pst=/dev/block/by-name/frp

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mtp \
    ro.adb.secure=1 \

PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.first_api_level=27

PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=213

# limit dex2oat threads to improve thermals
PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.boot-dex2oat-threads=4 \
    dalvik.vm.dex2oat-threads=3 \
    dalvik.vm.image-dex2oat-threads=4

PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.dex2oat-flags=--no-watch-dog \
    dalvik.vm.jit.codecachesize=0

PRODUCT_PROPERTY_OVERRIDES += \
    pm.dexopt.boot=verify-at-runtime \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=512k \
    dalvik.vm.heapmaxfree=8m

PRODUCT_PROPERTY_OVERRIDES += \
    ro.lmk.downgrade_pressure=95

# Reduces GC frequency of foreground apps by 50% (not recommanded for 512M devices)
PRODUCT_PROPERTY_OVERRIDES += dalvik.vm.foreground-heap-growth-multiplier=2.0

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.timezone=Asia/Shanghai \
    persist.sys.country=US \
    persist.sys.language=en

# stoarge
PRODUCT_PROPERTY_OVERRIDES += \
    persist.fw.force_adoptable=true

# for ota
PRODUCT_PROPERTY_OVERRIDES += \
    ro.build.version.ota=8.0.1 \
    ro.sys.ota.license=2c04a55870c751f74412cff2e58f2f1e1adf202d6ea88436e1de783d7f37e741c4d953e21a03a073

PRODUCT_CHARACTERISTICS := tablet

PRODUCT_AAPT_CONFIG := tvdpi xlarge hdpi xhdpi large
PRODUCT_AAPT_PREF_CONFIG := tvdpi

$(call inherit-product-if-exists, vendor/google/products/gms_go-mandatory.mk)

PRODUCT_BRAND := Allwinner
PRODUCT_NAME := tulip_p4
PRODUCT_DEVICE := tulip-p4
# PRODUCT_BOARD must equals the board name in kernel
PRODUCT_BOARD := p4
PRODUCT_MODEL := QUAD-CORE A64 p4
PRODUCT_MANUFACTURER := Allwinner

# sensor XML
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.sensor.accelerometer.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.sensor.accelerometer.xml

$(call inherit-product, vendor/aw/public/tool.mk)
