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
