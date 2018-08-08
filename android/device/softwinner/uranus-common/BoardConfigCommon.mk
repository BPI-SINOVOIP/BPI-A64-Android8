include device/softwinner/common/BoardConfigCommon.mk

TARGET_CPU_SMP := true

TARGET_BOARD_PLATFORM := uranus

TARGET_BOARD_CHIP := sun50iw3p1
TARGET_BOOTLOADER_BOARD_NAME := exdroid
TARGET_BOOTLOADER_NAME := exdroid
TARGET_OTA_RESTORE_BOOT_STORAGE_DATA := true

BOARD_KERNEL_BASE := 0x40000000
BOARD_MKBOOTIMG_ARGS := --kernel_offset 0x80000 --ramdisk_offset 0x02000000
BOARD_CHARGER_ENABLE_SUSPEND := true

NUM_FRAMEBUFFER_SURFACE_BUFFERS := 3
TARGET_RUNNING_WITHOUT_SYNC_FRAMEWORK := true

DEVICE_MANIFEST_FILE := device/softwinner/uranus-common/configs/manifest.xml
DEVICE_MATRIX_FILE := device/softwinner/uranus-common/configs/compatibility_matrix.xml

USE_OPENGL_RENDERER := true

TARGET_USES_HWC2 := true
TARGET_USES_DE3 := true
