LOCAL_PATH:= $(call my-dir)

#
# libmediaplayerservice
#

include $(CLEAR_VARS)
include $(LOCAL_PATH)/../libcedarx/config.mk

# LOCAL_PROPRIETARY_MODULE is set true in cedarx/config.mk,
# so we should set false here
LOCAL_PROPRIETARY_MODULE := false

LOCAL_SRC_FILES:=               \
    ActivityManager.cpp         \
    HDCP.cpp                    \
    MediaPlayerFactory.cpp      \
    MediaPlayerService.cpp      \
    MediaRecorderClient.cpp     \
    MetadataRetrieverClient.cpp \
    RemoteDisplay.cpp           \
    StagefrightRecorder.cpp     \
    TestPlayerStub.cpp          \

LOCAL_SHARED_LIBRARIES :=       \
    libbinder                   \
    libcrypto                   \
    libcutils                   \
    libdrmframework             \
    liblog                      \
    libdl                       \
    libgui                      \
    libaudioclient              \
    libmedia                    \
    libmediametrics             \
    libmediadrm                 \
    libmediautils               \
    libmemunreachable           \
    libstagefright              \
    libstagefright_foundation   \
    libstagefright_httplive     \
    libstagefright_omx          \
    libstagefright_wfd          \
    libutils                    \
    libnativewindow             \
    libhidlbase                 \
    android.hardware.media.omx@1.0 \
    libawplayer                 \
    libxplayer                  \
    libaw_output                \
    libawmetadataretriever      \

LOCAL_STATIC_LIBRARIES :=       \
    libstagefright_nuplayer     \
    libstagefright_rtsp         \
    libstagefright_timedtext    \

LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := libmedia

LOCAL_C_INCLUDES :=                                                 \
    frameworks/av/media/libstagefright/include               \
    frameworks/av/media/libstagefright/rtsp                  \
    frameworks/av/media/libstagefright/wifi-display          \
    frameworks/av/media/libstagefright/webm                  \
    $(LOCAL_PATH)/include/media                              \
    frameworks/av/include/camera                             \
    frameworks/native/include/media/openmax                  \
    frameworks/native/include/media/hardware                 \
    external/tremolo/Tremolo                                 \

LOCAL_C_INCLUDES +=          \
    $(TOP)/frameworks/av/media/libcedarx/android_adapter/awplayer/   \
    $(TOP)/frameworks/av/media/libcedarx/android_adapter/output/   \
    $(TOP)/frameworks/av/media/libcedarx/android_adapter/metadataretriever/       \
    $(TOP)/frameworks/av/media/libcedarc/include

LOCAL_CFLAGS += -Werror -Wno-error=deprecated-declarations -Wall

LOCAL_MODULE:= libmediaplayerservice

LOCAL_32_BIT_ONLY := true

LOCAL_SANITIZE := cfi
LOCAL_SANITIZE_DIAG := cfi

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
