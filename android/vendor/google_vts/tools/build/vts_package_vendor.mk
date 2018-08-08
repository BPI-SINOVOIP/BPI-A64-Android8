LOCAL_PATH := $(call my-dir)

include $(LOCAL_PATH)/tasks/list/vts_apk_package_list_vendor.mk

vendor_testcase_files := \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.py" -and -type f,.) \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.config" -and -type f,.) \
  $(call find-files-in-subdirs,vendor/google_vts/testcases,"*.push" -and -type f,.) \

vendor_testcase_copy_pairs := \
  $(foreach f,$(vendor_testcase_files),\
    vendor/google_vts/testcases/$(f):$(VTS_TESTCASES_OUT)/vts/testcases/$(f))

target_prebuilt_vendor_apk_copy_pairs :=

$(foreach m,$(vts_prebuilt_vendor_apk_packages),\
  $(if $(wildcard $(m)),\
    $(eval target_prebuilt_vendor_apk_copy_pairs += $(m):$(VTS_TESTCASES_OUT)/prebuilt-apk/$(m))))

vendor_tf_files :=
ifeq ($(BUILD_GOOGLE_VTS), true)
  vendor_tf_files += \
    $(HOST_OUT_JAVA_LIBRARIES)/google-tradefed-vts-prebuilt.jar
endif

vendor_tf_copy_pairs := \
  $(foreach f,$(vendor_tf_files),\
    $(f):$(VTS_OUT_ROOT)/android-vts/tools/$(notdir $(f)))

$(compatibility_zip): \
  $(call copy-many-files,$(vendor_testcase_copy_pairs)) \
  $(call copy-many-files,$(vendor_tf_copy_pairs)) \
  $(call copy-many-files,$(target_prebuilt_vendor_apk_copy_pairs))
