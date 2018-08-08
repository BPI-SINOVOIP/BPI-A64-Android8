LOCAL_PATH := vendor/aw/public/prebuild/lib/librild

# 3G Data Card Packages
PRODUCT_PACKAGES += \
	android.hardware.radio@1.0 \
	pppd \
	rild \
	chat \
	radio_monitor \
#	libsoftwinner-ril-8.0

# 3G Data Card Configuration Flie
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/config/data_call/call-pppd:$(TARGET_COPY_OUT_VENDOR)/xbin/call-pppd \
	$(LOCAL_PATH)/config/data_call/ip-down:system/etc/ppp/ip-down \
	$(LOCAL_PATH)/config/data_call/ip-up:system/etc/ppp/ip-up \
	$(LOCAL_PATH)/config/data_call/3g_dongle.cfg:$(TARGET_COPY_OUT_VENDOR)/etc/3g_dongle.cfg \
	$(LOCAL_PATH)/config/data_call/apns-conf_sdk.xml:system/etc/apns-conf.xml \

# Radio Monitor Configuration Flie
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/config/radio_monitor/usb_modeswitch:$(TARGET_COPY_OUT_VENDOR)/bin/usb_modeswitch \
	$(LOCAL_PATH)/config/radio_monitor/usb_modeswitch.sh:$(TARGET_COPY_OUT_VENDOR)/xbin/usb_modeswitch.sh \
	$(call find-copy-subdir-files,*,$(LOCAL_PATH)/config/radio_monitor/usb_modeswitch.d,$(TARGET_COPY_OUT_VENDOR)/etc/usb_modeswitch.d)

# libsoftwinner-ril
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/lib/libsoftwinner-ril-8.0.so:$(TARGET_COPY_OUT_VENDOR)/lib/libsoftwinner-ril-8.0.so \
	$(LOCAL_PATH)/lib64/libsoftwinner-ril-8.0.so:$(TARGET_COPY_OUT_VENDOR)/lib64/libsoftwinner-ril-8.0.so

# Radio parameter
PRODUCT_PROPERTY_OVERRIDES += \
	rild.libargs=-d/dev/ttyUSB2 \
	rild.libpath=libsoftwinner-ril-8.0.so \
	ro.sw.embeded.telephony=false
