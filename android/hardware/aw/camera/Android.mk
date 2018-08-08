CAMERA_HAL_LOCAL_PATH := $(call my-dir)
ifneq ($(filter venus%,$(TARGET_BOARD_PLATFORM)),)
include $(call all-subdir-makefiles)
endif
LOCAL_PATH := $(CAMERA_HAL_LOCAL_PATH)

$(warning $(TARGET_BOARD_PLATFORM))
############################################################################
#####---A64---
ifneq ($(filter tulip%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---A50---
ifneq ($(filter venus%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---A83---
ifneq ($(filter octopus%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---A33---
ifneq ($(filter astar%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---A80---
ifneq ($(filter kylin%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---A63---
ifneq ($(filter uranus%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

############################################################################
#####---VR9---
ifneq ($(filter neptune%,$(TARGET_BOARD_PLATFORM)),)
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libfacedetection.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libfacedetection.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libSmileEyeBlink
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libSmileEyeBlink.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libSmileEyeBlink.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/facedetection/libapperceivepeople.so
LOCAL_SRC_FILES_64 := lib64/facedetection/libapperceivepeople.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
endif
############################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := libproc
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/proc/libproc.so
LOCAL_SRC_FILES_64 := lib64/proc/libproc.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)
include $(CLEAR_VARS)
LOCAL_MODULE := libhdr
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_SRC_FILES_32 := lib32/hdr/libhdr.so
LOCAL_SRC_FILES_64 := lib64/hdr/libhdr.so
LOCAL_MULTILIB:= both
LOCAL_MODULE_TAGS := optional
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
# LOCAL_MODULE_RELATIVE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw
LOCAL_MODULE_RELATIVE_PATH := hw
LOCAL_PROPRIETARY_MODULE := true

LOCAL_SHARED_LIBRARIES:= \
    libbinder \
    libutils \
    libcutils \
    libui \
    liblog \
    libexpat

LOCAL_SHARED_LIBRARIES += \
    libhdr \
    libproc

LOCAL_C_INCLUDES +=                                 \
    frameworks/base/core/jni/android/graphics         \
    frameworks/native/include/media/openmax            \
    hardware/libhardware/include/hardware            \
    frameworks/native/include                        \
	frameworks/native/libs/nativewindow/include		\
    frameworks/native/include/media/hardware         \
    system/media/camera/include/                \
    hardware/aw/camera/include        \
    hardware/aw/camera        \
    system/core/libion/include \
    $(TARGET_HARDWARE_INCLUDE)

LOCAL_SRC_FILES := \
    memory/memoryAdapter.c \
    memory/ionMemory/ionAlloc.c \
    HALCameraFactory.cpp \
    PreviewWindow.cpp \
	CameraParameters.cpp \
	CallbackNotifier.cpp \
    CCameraConfig.cpp \
    BufferListManager.cpp \
    OSAL_Mutex.c \
    OSAL_Queue.c \
    scaler.c \
    CameraDebug.cpp \
    SceneFactory/HDRSceneMode.cpp \
    SceneFactory/NightSceneMode.cpp \
    SceneFactory/SceneModeFactory.cpp

ifeq ($(USE_IOMMU),true)
    LOCAL_CFLAGS += -DUSE_IOMMU
endif

# choose hal for new driver or old
SUPPORT_NEW_DRIVER := Y

ifeq ($(SUPPORT_NEW_DRIVER),Y)
LOCAL_CFLAGS += -DSUPPORT_NEW_DRIVER -DTARGET_BOARD_PLATFORM=$(TARGET_BOARD_PLATFORM) -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)
LOCAL_SRC_FILES += \
    CameraHardware2.cpp \
    V4L2CameraDevice2.cpp
else
LOCAL_SRC_FILES += \
    CameraHardware.cpp \
    V4L2CameraDevice.cpp
endif

############################################################################
#####---A64---
ifneq ($(filter tulip%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A64__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

############################################################################

############################################################################
#####---A50---
ifneq ($(filter venus%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A50__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory \
    hardware/aw/camera/allwinnertech/libAWIspApi

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople \
    libAWIspApi
endif

############################################################################

############################################################################
#####---A33---
ifneq ($(filter astar%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A33__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

############################################################################
#####---A83---
ifneq ($(filter octopus%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A83__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

############################################################################
#####---A80---
ifneq ($(filter kylin%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A80__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

###########################################################################
#####---A63---
ifneq ($(filter uranus%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A63__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

###########################################################################
#####---VR9---
ifneq ($(filter neptune%,$(TARGET_BOARD_PLATFORM)),)
LOCAL_CFLAGS += -D__A63__
LOCAL_C_INCLUDES += \
    frameworks/av/media/libcedarc/include \
    hardware/aw/camera/libfacedetection \
    hardware/aw/camera/SceneFactory

LOCAL_SHARED_LIBRARIES += \
    libvencoder \
    libfacedetection \
    libSmileEyeBlink \
    libapperceivepeople
endif

############################################################################

LOCAL_MODULE := camera.$(TARGET_BOARD_PLATFORM)
$(warning $(LOCAL_MODULE))

LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
