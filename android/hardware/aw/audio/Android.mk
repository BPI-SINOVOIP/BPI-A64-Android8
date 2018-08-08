MY_LOCAL_PATH := $(call my-dir)

#include $(MY_LOCAL_PATH)/hal/Android.mk
ifneq ($(filter petrel,$(TARGET_BOARD_PLATFORM)),)
    include $(call all-named-subdir-makefiles,homlet)
else
    include $(call all-named-subdir-makefiles,hal)
endif

include $(MY_LOCAL_PATH)/soundtrigger/Android.mk
