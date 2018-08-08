TARGET_GPU_TYPE_VALID := 0

ifneq ($(filter $(TARGET_GPU_TYPE), mali400 mali450),)
include $(call all-named-subdir-makefiles, mali-utgard)
TARGET_GPU_TYPE_VALID := 1
endif

ifneq ($(filter $(TARGET_GPU_TYPE), mali-t720 mali-t760),)
include $(call all-named-subdir-makefiles, mali-midgard)
TARGET_GPU_TYPE_VALID := 1
endif

ifneq ($(filter $(TARGET_GPU_TYPE), sgx544),)
include $(call all-named-subdir-makefiles, sgx544)
TARGET_GPU_TYPE_VALID := 1
endif

ifneq ($(TARGET_GPU_TYPE),)
ifneq ($(TARGET_GPU_TYPE_VALID), 1)
$(error Invalid TARGET_GPU_TYPE "$(TARGET_GPU_TYPE)"!)
endif
endif
