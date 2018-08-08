# Use bash for additional echo fancyness
SHELL = /bin/bash

####################################################################################################
## defines

ANDROID_ROOT = $(ANDROID_BUILD_TOP)

# Build for Jellybean 
BUILD_ANDROID_JELLYBEAN = $(shell test -d $(ANDROID_ROOT)/frameworks/native && echo 1)

# Build for Lollipop
# ANDROID version check
BUILD_ANDROID_LOLLIPOP = $(shell test -d $(ANDROID_ROOT)/bionic/libc/kernel/uapi && echo 1)
#ANDROID version check END

PRODUCT = hammerhead
TARGET = android

## libraries ##
LIB_PREFIX = lib

STATIC_LIB_EXT = a
SHARED_LIB_EXT = so

# normally, overridden from outside 
# ?= assignment sets it only if not already defined
TARGET ?= android

MLLITE_LIB_NAME     ?= mllite
MPL_LIB_NAME        ?= mplmpu
CAL_LIB_NAME        ?= motion-cal

## applications ##
SHARED_APP_SUFFIX = -shared
STATIC_APP_SUFFIX = -static

####################################################################################################
## compile, includes, and linker

ifeq ($(BUILD_ANDROID_JELLYBEAN),1)
ANDROID_COMPILE = -DANDROID_JELLYBEAN=1
endif

ANDROID_LINK  = -nostdlib
ANDROID_LINK += -fpic
ANDROID_LINK += -Wl,--gc-sections 
ANDROID_LINK += -Wl,--no-whole-archive 
ANDROID_LINK += -L$(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib
ANDROID_LINK += -L$(ANDROID_ROOT)/out/target/product/$(PRODUCT)/system/lib

ANDROID_LINK_EXECUTABLE  = $(ANDROID_LINK)
ANDROID_LINK_EXECUTABLE += -Wl,-dynamic-linker,/system/bin/linker
ANDROID_LINK_EXECUTABLE += $(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib/crtbegin_dynamic.o
ANDROID_LINK_EXECUTABLE += $(ANDROID_ROOT)/out/target/product/$(PRODUCT)/obj/lib/crtend_android.o

ANDROID_INCLUDES  = -I$(ANDROID_ROOT)/system/core/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/hardware/libhardware/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/hardware/ril/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/dalvik/libnativehelper/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/frameworks/base/include   # ICS
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/frameworks/native/include # Jellybean
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/external/skia/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/out/target/product/generic/obj/include
ifeq ($(BUILD_ANDROID_LOLLIPOP),1)
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/uapi #LP
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/uapi/asm-arm #LP
endif
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/arch-arm/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libstdc++/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/common
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libc/kernel/arch-arm
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/include/arch/arm
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libthread_db/include
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm/arm
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/bionic/libm
ANDROID_INCLUDES += -I$(ANDROID_ROOT)/out/target/product/generic/obj/SHARED_LIBRARIES/libm_intermediates
#for Android L
ANDROID_INCLUDES += -DHAVE_SYS_UIO_H

KERNEL_INCLUDES  = -I$(KERNEL_ROOT)/include -I$(KERNEL_ROOT)/arch/arm/include -I$(KERNEL_ROOT)/arch/arm/include/generated 
#--yd KERNEL_INCLUDES  = -I$(KERNEL_ROOT)/include -I$(KERNEL_ROOT)/include/uapi -I$(KERNEL_ROOT)/arch/arm64/include -I$(KERNEL_ROOT)/arch/arm64/include/generated -I$(KERNEL_ROOT)/arch/arm/include/uapi

INV_INCLUDES  = -I$(INV_ROOT)/software/core/driver/include
INV_INCLUDES += -I$(MLLITE_DIR)
INV_INCLUDES += -I$(MLLITE_DIR)/linux

INV_DEFINES += -DINV_CACHE_DMP=1

####################################################################################################
## macros

ifndef echo_in_colors
define echo_in_colors
	echo -ne "\e[1;32m"$(1)"\e[0m"
endef 
endif



