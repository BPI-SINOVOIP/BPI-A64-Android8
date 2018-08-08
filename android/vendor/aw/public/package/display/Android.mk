LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := displayservice
LOCAL_SRC_FILES := \
    DisplayService.cpp \
    aidl/com/softwinner/IDisplayService.aidl
LOCAL_CFLAGS := -Wall -Werror
LOCAL_SHARED_LIBRARIES := \
    android.hardware.graphics.composer@2.1 \
    libbase \
    libbinder \
    libhidlbase \
    libhidltransport \
    libutils \

LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := \
    android.hardware.graphics.composer@2.1 \
    libhidlbase \
    libhidltransport \

LOCAL_INIT_RC := displayservice.rc
include $(BUILD_EXECUTABLE)

# AwDisplay
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libdisplay
LOCAL_SRC_FILES := \
    $(call all-java-files-under, java)
LOCAL_SRC_FILES += \
    aidl/com/softwinner/IDisplayService.aidl
LOCAL_PROGUARD_ENABLED := disabled
#LOCAL_JACK_ENABLED := disabled

include $(BUILD_STATIC_JAVA_LIBRARY)
