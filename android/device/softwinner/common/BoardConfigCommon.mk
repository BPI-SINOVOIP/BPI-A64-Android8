# image related
TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := false
TARGET_NO_KERNEL := false
INSTALLED_KERNEL_TARGET := kernel
TARGET_USERIMAGES_USE_EXT4 := true

# recovery related
TARGET_RECOVERY_UPDATER_LIBS := librecovery_updater_common
TARGET_RECOVERY_UI_LIB := librecovery_ui_common
TARGET_RECOVERY_FSTAB := device/softwinner/common/recovery.fstab

# sepolicy
BOARD_SEPOLICY_DIRS += device/softwinner/common/sepolicy/vendor
BOARD_PLAT_PUBLIC_SEPOLICY_DIR := device/softwinner/common/sepolicy/public
BOARD_PLAT_PRIVATE_SEPOLICY_DIR := device/softwinner/common/sepolicy/private

# wifi and bt configuration
# 1. Wifi Configuration
BOARD_WIFI_VENDOR           := common
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
BOARD_HOSTAPD_DRIVER        := NL80211
WPA_SUPPLICANT_VERSION      := VER_0_8_X
BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_common
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_common
include hardware/aw/wlan/firmware/firmware.mk

