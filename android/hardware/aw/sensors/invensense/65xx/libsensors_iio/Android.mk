# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# Modified 2016 by InvenSense, Inc

LOCAL_PATH := $(call my-dir)
COMPILE_INVENSENSE_SENSOR_ON_PRIMARY_BUS := 1
LIGHT_SENSOR := 1
PROXIMITY := 1

# InvenSense HAL
include $(CLEAR_VARS)

LOCAL_MODULE := libinvensense_hal
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := invensense
LOCAL_CFLAGS := -DLOG_TAG=\"Sensors\"

ifneq ($(TARGET_CPU_VARIANT), generic)
# ANDROID version check
MAJOR_VERSION :=$(shell echo $(PLATFORM_VERSION) | cut -f1 -d.)
MINOR_VERSION :=$(shell echo $(PLATFORM_VERSION) | cut -f2 -d.)
VERSION_KK :=$(shell test $(MAJOR_VERSION) -gt 4 -o $(MAJOR_VERSION) -eq 4 -a $(MINOR_VERSION) -gt 3 && echo true)
$(info ANDROID VERSION=$(MAJOR_VERSION).$(MINOR_VERSION))
#ANDROID version check END
endif

# libmotion
INV_LIBMOTION := true
$(info libmotion enabled = $(INV_LIBMOTION))

ifeq ($(INV_LIBMOTION), true)
LOCAL_CFLAGS += -DINV_LIBMOTION
endif

# libsensors HAL version
INV_LIBSENSORS_VERSION_MAJOR := 6
INV_LIBSENSORS_VERSION_MINOR := 4
INV_LIBSENSORS_VERSION_PATCH := 1
INV_LIBSENSORS_VERSION_SUFFIX := -4
$(info InvenSense libsensors version MA$(INV_LIBSENSORS_VERSION_MAJOR).$(INV_LIBSENSORS_VERSION_MINOR).$(INV_LIBSENSORS_VERSION_PATCH)$(INV_LIBSENSORS_VERSION_SUFFIX))
LOCAL_CFLAGS +=	-DINV_LIBSENSORS_VERSION_MAJOR=$(INV_LIBSENSORS_VERSION_MAJOR)
LOCAL_CFLAGS +=	-DINV_LIBSENSORS_VERSION_MINOR=$(INV_LIBSENSORS_VERSION_MINOR)
LOCAL_CFLAGS += -DINV_LIBSENSORS_VERSION_PATCH=$(INV_LIBSENSORS_VERSION_PATCH)
LOCAL_CFLAGS += -DINV_LIBSENSORS_VERSION_SUFFIX=\"$(INV_LIBSENSORS_VERSION_SUFFIX)\"

ifeq ($(VERSION_KK),true)
LOCAL_CFLAGS += -DANDROID_KITKAT
else
LOCAL_CFLAGS += -DANDROID_JELLYBEAN
endif

ifeq ($(COMPILE_INVENSENSE_SENSOR_DEBUG_DRIVER), 1)
LOCAL_CFLAGS += -DDEBUG_DRIVER
endif


ifneq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_CFLAGS += -DINVENSENSE_COMPASS_CAL
endif
ifeq ($(COMPILE_THIRD_PARTY_ACCEL),1)
LOCAL_CFLAGS += -DTHIRD_PARTY_ACCEL
endif


LOCAL_SRC_FILES += SensorBase.cpp
LOCAL_SRC_FILES += MPLSensor.cpp
LOCAL_SRC_FILES += MPLSupport.cpp
LOCAL_SRC_FILES += MPLAdapter.cpp
LOCAL_SRC_FILES += InputEventReader.cpp
#LOCAL_SRC_FILES += PressureSensor.IIO.secondary.cpp
#LOCAL_SRC_FILES += LightSensor.IIO.secondary.cpp
ifeq ($(INV_LIBMOTION),true)
LOCAL_SRC_FILES += MPLBuilder.cpp
endif

ifeq ($(LIGHT_SENSOR),1)
LOCAL_SRC_FILES += LightSensor.cpp
LOCAL_SRC_FILES += sensorDetect.cpp
LOCAL_CFLAGS += -DALL_WINNER_LIGHT_SENSOR
endif

ifeq ($(PROXIMITY),1)
LOCAL_SRC_FILES += ProximitySensor.cpp
LOCAL_CFLAGS += -DALL_WINNER_PROXIMITY_SENSOR
endif

LOCAL_SRC_FILES += software/core/mllite/start_manager.c
LOCAL_SRC_FILES += software/core/mllite/data_builder.c
LOCAL_SRC_FILES += software/core/mllite/mlmath.c
LOCAL_SRC_FILES += software/core/mllite/mpl.c
LOCAL_SRC_FILES += software/core/mllite/ml_math_func.c
LOCAL_SRC_FILES += software/core/mllite/hal_outputs.c
LOCAL_SRC_FILES += software/core/mllite/message_layer.c
LOCAL_SRC_FILES += software/core/mllite/ml_math_func.h
LOCAL_SRC_FILES += software/core/mllite/results_holder.c
LOCAL_SRC_FILES += software/core/mllite/storage_manager.c
LOCAL_SRC_FILES += software/core/mllite/linux/inv_sysfs_utils.c
LOCAL_SRC_FILES += software/core/mllite/linux/ml_android_to_imu.c
LOCAL_SRC_FILES += software/core/mllite/linux/ml_sysfs_helper.c
LOCAL_SRC_FILES += software/core/mllite/linux/ml_load_dmp.c
LOCAL_SRC_FILES += software/core/mllite/linux/ml_stored_data.c
LOCAL_SRC_FILES += software/core/mllite/linux/ml_sensor_parsing.c

ifeq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_SRC_FILES += AkmSensor.cpp
LOCAL_SRC_FILES += CompassSensor.AKM.cpp
else ifeq ($(COMPILE_INVENSENSE_SENSOR_ON_PRIMARY_BUS), 1)
LOCAL_SRC_FILES += CompassSensor.IIO.primary.cpp
LOCAL_CFLAGS += -DSENSOR_ON_PRIMARY_BUS
else
LOCAL_SRC_FILES += CompassSensor.IIO.secondary.cpp
endif



LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite/linux
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/cal-lib
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include

LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SHARED_LIBRARIES += libcutils
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libdl
ifeq ($(INV_LIBMOTION),true)
LOCAL_SHARED_LIBRARIES += libmotion
endif

LOCAL_CPPFLAGS += -DLINUX=1
LOCAL_PRELINK_MODULE := true

include $(BUILD_SHARED_LIBRARY)

# Build a temporary HAL that links the InvenSense .so
include $(CLEAR_VARS)

LOCAL_MODULE := sensors.exdroid
LOCAL_MODULE_OWNER := invensense

ifdef TARGET_2ND_ARCH
#LOCAL_MODULE_RELATIVE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_MODULE_RELATIVE_PATH := hw
else
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
endif

LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/mllite/linux
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/cal-lib
LOCAL_C_INCLUDES += $(LOCAL_PATH)/software/core/driver/include

LOCAL_PRELINK_MODULE := true
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -DLOG_TAG=\"Sensors\"

ifeq ($(VERSION_KK),true)
LOCAL_CFLAGS += -DANDROID_KITKAT
else
LOCAL_CFLAGS += -DANDROID_JELLYBEAN
endif
ifeq ($(LIGHT_SENSOR),1)
LOCAL_CFLAGS += -DALL_WINNER_LIGHT_SENSOR
endif

ifeq ($(PROXIMITY),1)
LOCAL_CFLAGS += -DALL_WINNER_PROXIMITY_SENSOR
endif

ifeq ($(COMPILE_INVENSENSE_SENSOR_DEBUG_DRIVER), 1)
LOCAL_CFLAGS += -DDEBUG_DRIVER
endif

ifneq ($(COMPILE_INVENSENSE_COMPASS_CAL),0)
LOCAL_CFLAGS += -DINVENSENSE_COMPASS_CAL
endif
ifeq ($(COMPILE_THIRD_PARTY_ACCEL),1)
LOCAL_CFLAGS += -DTHIRD_PARTY_ACCEL
endif
ifeq ($(COMPILE_INVENSENSE_SENSOR_ON_PRIMARY_BUS), 1)
LOCAL_SRC_FILES += CompassSensor.IIO.primary.cpp
LOCAL_CFLAGS += -DSENSOR_ON_PRIMARY_BUS
else
LOCAL_SRC_FILES += CompassSensor.IIO.secondary.cpp
endif

LOCAL_SRC_FILES := sensors_mpl.cpp

LOCAL_SHARED_LIBRARIES := libinvensense_hal
LOCAL_SHARED_LIBRARIES += libcutils
LOCAL_SHARED_LIBRARIES += libutils
LOCAL_SHARED_LIBRARIES += libdl
LOCAL_SHARED_LIBRARIES += liblog
ifeq ($(INV_LIBMOTION),true)
LOCAL_SHARED_LIBRARIES += libmotion
endif

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libmotion
LOCAL_SRC_FILES_arm := obj/libmotion.so
LOCAL_SRC_FILES_arm64 := obj_64/libmotion.so
LOCAL_SRC_FILES_x86 := obj_x86/libmotion.so
LOCAL_SRC_FILES_x86_64 := obj_x86_64/libmotion.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := invensense
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TARGET_ARCH := arm arm64 x86 x86_64
LOCAL_MULTILIB := both

include $(BUILD_PREBUILT)
