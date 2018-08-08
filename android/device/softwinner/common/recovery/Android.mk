LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CLANG := true
LOCAL_CFLAGS += -Wall
ifeq ($(TARGET_OTA_RESTORE_BOOT_STORAGE_DATA), true)
    LOCAL_CPPFLAGS += -DRESTORE_BOOT_STORAGE_DATA=true
else
    LOCAL_CPPFLAGS += -DRESTORE_BOOT_STORAGE_DATA=false
endif
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := librecovery_updater_common
LOCAL_SRC_FILES := \
    recovery_updater.cpp \
    BurnNandBoot.cpp \
    BurnSdBoot.cpp \
    Utils.cpp \

LOCAL_STATIC_LIBRARIES := \
    libbase \
    libziparchive \

LOCAL_C_INCLUDES += bootable/recovery
LOCAL_C_INCLUDES += bootable/recovery/updater/include

include $(BUILD_STATIC_LIBRARY)

############################################################
# recovery ui

include $(CLEAR_VARS)
LOCAL_CLANG := true
LOCAL_MODULE_TAGS := optional
LOCAL_C_INCLUDES += bootable/recovery
LOCAL_SRC_FILES := aw_device.cpp

ifneq ($(RECOVERY_KEY_UP),)
    LOCAL_CFLAGS += -DRECOVERY_KEY_UP=$(RECOVERY_KEY_UP)
else
    LOCAL_CFLAGS += -DRECOVERY_KEY_UP=115
endif
ifneq ($(RECOVERY_KEY_DOWN),)
    LOCAL_CFLAGS += -DRECOVERY_KEY_DOWN=$(RECOVERY_KEY_DOWN)
else
    LOCAL_CFLAGS += -DRECOVERY_KEY_DOWN=114
endif
ifneq ($(RECOVERY_KEY_POWER),)
    LOCAL_CFLAGS += -DRECOVERY_KEY_POWER=$(RECOVERY_KEY_POWER)
else
    LOCAL_CFLAGS += -DRECOVERY_KEY_POWER=116
endif

# should match TARGET_RECOVERY_UI_LIB set in BoardConfig.mk
LOCAL_MODULE := librecovery_ui_common

include $(BUILD_STATIC_LIBRARY)
