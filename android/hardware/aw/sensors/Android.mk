LOCAL_PATH := $(call my-dir)
ifeq ($(SW_BOARD_USES_SENSORS_TYPE),invensense)
include $(call all-named-subdir-makefiles,invensense)
else ifeq ($(SW_BOARD_USES_SENSORS_TYPE),aw)
include $(call all-named-subdir-makefiles,aw_sensors)
endif
