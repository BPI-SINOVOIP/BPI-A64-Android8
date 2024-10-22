bootanimation_CommonCFlags = -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES
bootanimation_CommonCFlags += -Wall -Werror -Wunused -Wunreachable-code


# bootanimation executable
# =========================================================

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS += ${bootanimation_CommonCFlags}

LOCAL_SHARED_LIBRARIES := \
    libOpenSLES \
    libandroidfw \
    libbase \
    libbinder \
    libbootanimation \
    libcutils \
    liblog \
    libutils \

LOCAL_SRC_FILES:= \
    BootAnimationUtil.cpp \

ifeq ($(PRODUCT_IOT),true)
LOCAL_SRC_FILES += \
    iot/iotbootanimation_main.cpp \
    iot/BootAction.cpp

LOCAL_SHARED_LIBRARIES += \
    libandroidthings \
    libbase \
    libbinder

LOCAL_STATIC_LIBRARIES += cpufeatures

else

LOCAL_SRC_FILES += \
    bootanimation_main.cpp \
    audioplay.cpp \

endif  # PRODUCT_IOT

LOCAL_MODULE:= bootanimation

LOCAL_INIT_RC := bootanim.rc

ifdef TARGET_32_BIT_SURFACEFLINGER
LOCAL_32_BIT_ONLY := true
endif

include $(BUILD_EXECUTABLE)


# libbootanimation
# ===========================================================

include $(CLEAR_VARS)
LOCAL_MODULE := libbootanimation
LOCAL_CFLAGS += ${bootanimation_CommonCFlags}

LOCAL_SRC_FILES:= \
    BootAnimation.cpp \
    AudioPlayer.cpp

LOCAL_CFLAGS += ${bootanimation_CommonCFlags}

LOCAL_C_INCLUDES += \
    external/tinyalsa/include \
    frameworks/wilhelm/include

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    liblog \
    libandroidfw \
    libutils \
    libbinder \
    libui \
    libskia \
    libEGL \
    libGLESv1_CM \
    libgui \
    libtinyalsa \
    libbase

ifdef TARGET_32_BIT_SURFACEFLINGER
LOCAL_32_BIT_ONLY := true
endif

include ${BUILD_SHARED_LIBRARY}
