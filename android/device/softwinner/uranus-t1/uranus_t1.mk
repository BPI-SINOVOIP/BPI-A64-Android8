$(call inherit-product, device/softwinner/uranus-common/uranus_64_bit.mk)
$(call inherit-product, build/target/product/full_base.mk)
$(call inherit-product, device/softwinner/uranus-common/uranus-common.mk)
$(call inherit-product-if-exists, device/softwinner/uranus-t1/modules/modules.mk)
$(call inherit-product, device/softwinner/common/pad.mk)

DEVICE_PACKAGE_OVERLAYS := device/softwinner/uranus-t1/overlay \
                           $(DEVICE_PACKAGE_OVERLAYS)

# secure config
BOARD_HAS_SECURE_OS := false

# drm config
BOARD_WIDEVINE_OEMCRYPTO_LEVEL := 3

# dm-verity relative
$(call inherit-product, build/target/product/verity.mk)
# PRODUCT_SUPPORTS_BOOT_SIGNER must be false,otherwise error will be find when boota check boot partition
PRODUCT_SUPPORTS_BOOT_SIGNER := false
#PRODUCT_SUPPORTS_VERITY_FEC := false
PRODUCT_SYSTEM_VERITY_PARTITION := /dev/block/by-name/system
PRODUCT_VENDOR_VERITY_PARTITION := /dev/block/by-name/vendor

PRODUCT_PACKAGES += \
    Launcher3 \
    SoundRecorder

#File Explorer
PRODUCT_PACKAGES += \
    ESFileExplorer

PRODUCT_PACKAGES += \
    android.hardware.power@1.0-service \
    android.hardware.power@1.0-impl \
    power.uranus

# vndk
PRODUCT_PACKAGES += \
    uranus-t1-vndk

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

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-t1/kernel:kernel \
    device/softwinner/uranus-t1/fstab.sun50iw3p1:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.sun50iw3p1 \
    device/softwinner/uranus-t1/init.device.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.device.rc \
    device/softwinner/uranus-t1/init.recovery.sun50iw3p1.rc:root/init.recovery.sun50iw3p1.rc \
    device/softwinner/uranus-t1/modules/modules/gslX680new.ko:recovery/root/gslX680new.ko \

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-t1/configs/camera.cfg:$(TARGET_COPY_OUT_VENDOR)/etc/camera.cfg \
    device/softwinner/uranus-t1/configs/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    device/softwinner/common/config/awbms_config:$(TARGET_COPY_OUT_VENDOR)/etc/awbms_config \


PRODUCT_COPY_FILES += \
    device/softwinner/common/config/tablet_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/tablet_core_hardware.xml \
    frameworks/native/data/etc/android.software.managed_users.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.managed_users.xml \
    frameworks/native/data/etc/android.software.app_widgets.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.app_widgets.xml \
    frameworks/native/data/etc/android.software.picture_in_picture.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.picture_in_picture.xml

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-t1/media/bootanimation.zip:system/media/bootanimation.zip

PRODUCT_PROPERTY_OVERRIDES += \
    ro.radio.noril=true

PRODUCT_PROPERTY_OVERRIDES += \
    ro.frp.pst=/dev/block/by-name/frp

# usb
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usb.config=mtp,adb \
    ro.adb.secure=0

#scense control
PRODUCT_PROPERTY_OVERRIDES += \
    sys.p_home=false \
    sys.p_music=true

PRODUCT_PROPERTY_OVERRIDES += \
    ro.product.first_api_level=27

PRODUCT_PROPERTY_OVERRIDES += \
    ro.sf.lcd_density=320

PRODUCT_PROPERTY_OVERRIDES += \
    pm.dexopt.boot=verify-at-runtime \
    dalvik.vm.heapsize=256m \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heapgrowthlimit=192m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=2m \
    dalvik.vm.heapmaxfree=8m \

PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.timezone=Asia/Shanghai \
    persist.sys.country=CN \
    persist.sys.language=zh

PRODUCT_AAPT_CONFIG := xlarge large
PRODUCT_AAPT_PREF_CONFIG := xhdpi
# A list of dpis to select prebuilt apk, in precedence order.
PRODUCT_AAPT_PREBUILT_DPI := xhdpi hdpi tvdpi mdpi
PRODUCT_CHARACTERISTICS := tablet

# stoarge
PRODUCT_PROPERTY_OVERRIDES += \
    persist.fw.force_adoptable=true

# for ota
PRODUCT_PROPERTY_OVERRIDES += \
    ro.build.version.ota=8.0.0 \
    ro.sys.ota.license=1605226a74fff91167b3df50b1f1bca4c8a924be52cd946ef2ef25c67d8bf886f21832bc66511c8f

$(call inherit-product-if-exists, vendor/google/products/gms-mandatory.mk)

PRODUCT_BRAND := Allwinner
PRODUCT_NAME := uranus_t1
PRODUCT_DEVICE := uranus-t1
# PRODUCT_BOARD must equals the board name in kernel
PRODUCT_BOARD := t1
PRODUCT_MODEL := Uranus A63
PRODUCT_MANUFACTURER := Allwinner

PRODUCT_COPY_FILES += \
    device/softwinner/uranus-t1/configs/gsensor.cfg:$(TARGET_COPY_OUT_VENDOR)/etc/gsensor.cfg
