# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

include device/softwinner/tulip-common/BoardConfigCommon.mk

# Enable dex-preoptimization to speed up first boot sequence
WITH_DEXPREOPT := true
DONT_DEXPREOPT_PREBUILTS := false

BOARD_KERNEL_CMDLINE := selinux=1 androidboot.selinux=enforcing
BOARD_FLASH_BLOCK_SIZE := 4096
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 1610612736
BOARD_CACHEIMAGE_PARTITION_SIZE := 536870912

ifeq ($(PRODUCT_NOT_USES_VENDORIMAGE), true)
    TARGET_COPY_OUT_VENDOR := system/vendor
else
    TARGET_COPY_OUT_VENDOR := vendor
    BOARD_VENDORIMAGE_FILE_SYSTEM_TYPE := ext4
    BOARD_VENDORIMAGE_PARTITION_SIZE := 314572800
    # build & split configs
    PRODUCT_ENFORCE_RRO_TARGETS := framework-res
    BOARD_PROPERTY_OVERRIDES_SPLIT_ENABLED := true
    PRODUCT_FULL_TREBLE_OVERRIDE := true
    #BOARD_VNDK_VERSION := current
endif
#time for health alarm
BOARD_PERIODIC_CHORES_INTERVAL_FAST := 86400
BOARD_PERIODIC_CHORES_INTERVAL_SLOW := 86400
# USE Android Go GMS package
BOARD_USE_ANDROID_GO := true

# Enable SVELTE malloc
MALLOC_SVELTE := true

# recovery touch high threshold
TARGET_RECOVERY_UI_TOUCH_HIGH_THRESHOLD := 200
# recovery fs table
TARGET_RECOVERY_FSTAB := device/softwinner/tulip-m64/recovery.fstab

DEVICE_MANIFEST_FILE := device/softwinner/tulip-m64/configs/manifest.xml
DEVICE_MATRIX_FILE := device/softwinner/tulip-m64/configs/compatibility_matrix.xml

# wifi and bt configuration
# 1. Wifi Configuration

BOARD_WIFI_VENDOR := broadcom

# 1.1 broadcom wifi configuration
# BOARD_USR_WIFI: ap6181/ap6210/ap6212/ap6330/ap6335
ifeq ($(BOARD_WIFI_VENDOR), broadcom)
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    WPA_SUPPLICANT_VERSION      := VER_0_8_X
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_bcmdhd
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_bcmdhd
    BOARD_WLAN_DEVICE           := bcmdhd
    WIFI_DRIVER_MODULE_NAME     := "bcmdhd"
    WIFI_DRIVER_FW_PATH_PARAM   := "/sys/module/bcmdhd/parameters/firmware_path"

    BOARD_USR_WIFI := ap6212
    include hardware/aw/wlan/firmware/firmware.mk
endif

# 1.2 realtek wifi configuration
ifeq ($(BOARD_WIFI_VENDOR), realtek)
    WPA_SUPPLICANT_VERSION := VER_0_8_X
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_rtl
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_rtl
    include hardware/realtek/wlan/config/config.mk
    BOARD_WLAN_DEVICE           := realtek
    WIFI_DRIVER_MODULE_NAME     := "8723bu"
    WIFI_DRIVER_MODULE_PATH     := "/vendor/modules/8723bu.ko"
    WIFI_DRIVER_MODULE_ARG      := "ifname=wlan0 if2name=p2p0"
endif

# 1.3 eagle wifi configuration
ifeq ($(BOARD_WIFI_VENDOR), eagle)
    WPA_SUPPLICANT_VERSION := VER_0_8_X
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_eagle
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_eagle

    BOARD_USR_WIFI := esp8089
    BOARD_WLAN_DEVICE := esp8089
    include hardware/espressif/wlan/firmware/esp8089/device-esp.mk
endif

# 2. Bluetooth Configuration
# make sure BOARD_HAVE_BLUETOOTH is true for every bt vendor

BOARD_BLUETOOTH_VENDOR := broadcom

# 2.1 broadcom bt configuration
# BOARD_HAVE_BLUETOOTH_NAME: ap6210/ap6212/ap6330/ap6335
ifeq ($(BOARD_BLUETOOTH_VENDOR), broadcom)
    BOARD_HAVE_BLUETOOTH := true
    BOARD_HAVE_BLUETOOTH_BCM := true
    BOARD_HAVE_BLUETOOTH_NAME := ap6212
    BOARD_CUSTOM_BT_CONFIG := $(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/bluetooth/vnd_$(basename $(TARGET_DEVICE)).txt
    BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := $(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/bluetooth/
endif

# 2.2 realtek bt configuration
ifeq ($(BOARD_BLUETOOTH_VENDOR), realtek)
    BOARD_HAVE_BLUETOOTH := true
    BOARD_HAVE_BLUETOOTH_RTK := true
    BOARD_HAVE_BLUETOOTH_RTK_COEX := true
    BOARD_HAVE_BLUETOOTH_NAME := rtl8723bu
    BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := $(TOP_DIR)device/softwinner/$(basename $(TARGET_DEVICE))/configs/bluetooth/
    include hardware/realtek/bluetooth/firmware/rtlbtfw_cfg.mk
endif

# sensor
SW_BOARD_USES_SENSORS_TYPE := aw
