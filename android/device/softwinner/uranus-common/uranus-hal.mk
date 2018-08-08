# Sensor                                                                        
PRODUCT_PACKAGES += \
    sensors.exdroid \
    android.hardware.sensors@1.0-impl \
    android.hardware.sensors@1.0-service

# Gralloc
PRODUCT_PACKAGES += \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.graphics.mapper@2.0-impl

# HW Composer
PRODUCT_PACKAGES += \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.composer@2.1-service \
    hwcomposer.uranus

# Audio
PRODUCT_PACKAGES += \
    audio.primary.uranus \
    sound_trigger.primary.uranus \
    radio.fm.default \
    android.hardware.audio@2.0-service \
    android.hardware.audio@2.0-impl \
    android.hardware.audio.effect@2.0-impl \
    android.hardware.soundtrigger@2.0-impl \
    android.hardware.broadcastradio@1.0-impl
# keymaster HAL
PRODUCT_PACKAGES += \
    android.hardware.keymaster@3.0-impl \
    android.hardware.keymaster@3.0-service
# Power
PRODUCT_PACKGES += \
    android.hardware.power@1.0-service \
    android.hardware.power@1.0-impl \
    power.uranus
# CAMERA
PRODUCT_PACKAGES += \
    camera.device@3.2-impl \
    android.hardware.camera.provider@2.4-service \
    android.hardware.camera.provider@2.4-impl \
    libcamera \
    camera.uranus

# Memtrack
PRODUCT_PACKAGES += \
    android.hardware.memtrack@1.0-impl \
    android.hardware.memtrack@1.0-service \
    memtrack.uranus \
    memtrack.default

#gatekeeper
PRODUCT_PACKAGES += \
    android.hardware.gatekeeper@1.0-impl \
    android.hardware.gatekeeper@1.0-service \
    libgatekeeper \
    gatekeeper.uranus


# drm
PRODUCT_PACKAGES += \
    android.hardware.drm@1.0-impl \
    android.hardware.drm@1.0-service \
    android.hardware.drm@1.0-service.widevine

# ION
PRODUCT_PACKAGES += \
    libion

# Wi-Fi Pakages
PRODUCT_PACKAGES += \
    android.hardware.wifi@1.0-service \
    libwpa_client \
    wpa_supplicant \
    hostapd \
    wificond \
    wifilogd \
    wpa_supplicant.conf

# Bluetooth
PRODUCT_PACKAGES += \
    android.hardware.bluetooth@1.0-impl \
    android.hardware.bluetooth@1.0-service \
    android.hardware.bluetooth@1.0-service.rc \
    android.hidl.memory@1.0-impl \
    Bluetooth \
    libbt-vendor \
    audio.a2dp.default
# Light Hal
PRODUCT_PACKAGES += \
    android.hardware.light@2.0-service \
    android.hardware.light@2.0-impl \
    lights.uranus 
