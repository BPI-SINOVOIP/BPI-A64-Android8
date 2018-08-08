# Copyright 2010 Google Inc. All Rights Reserved.

LOCAL_PATH := $(call my-dir)

########################################################

include $(CLEAR_VARS)
# Module to compile protos for google-tradefed
LOCAL_MODULE := google-tradefed-protos
LOCAL_SRC_FILES := $(call all-proto-files-under, proto)
LOCAL_JAVA_LIBRARIES := host-libprotobuf-java-full
LOCAL_SOURCE_FILES_ALL_GENERATED := true
LOCAL_MODULE_TAGS := optional

include $(BUILD_HOST_JAVA_LIBRARY)

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_JAVACFLAGS += -g -Xlint
-include tools/tradefederation/core/error_prone_rules.mk

LOCAL_MODULE := google-tradefed
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := tradefed loganalysis host-libprotobuf-java-full
LOCAL_STATIC_JAVA_LIBRARIES := google-oauth-client google-api-client google-http-client google-http-client-jackson2 jackson-core libandroid_build_v2beta1_java google-tradefed-protos

LOCAL_JAR_MANIFEST := MANIFEST.mf

include $(BUILD_HOST_JAVA_LIBRARY)

# makefile rules to copy jars to HOST_OUT/tradefed
# so tradefed.sh can automatically add to classpath

DEST_JAR := $(HOST_OUT)/tradefed/$(LOCAL_MODULE).jar
$(DEST_JAR): $(LOCAL_BUILT_MODULE)
	$(copy-file-to-new-target)

# this dependency ensure the above rule will be executed if jar is built
$(LOCAL_INSTALLED_MODULE) : $(DEST_JAR)

#######################################################
include $(CLEAR_VARS)

# Create a simple alias to build all the google-tf build targets
# Note that this is incompatible with `make dist`.  If you want to make
# the distribution, you must run `tapas` with the individual target names.
.PHONY: google-tradefed-all
google-tradefed-all: tradefed-all google-tradefed google-tradefed-tests google-tf-prod-tests google-tf-prod-metatests google-tradefed-contrib tradefed-ds-docs

########################################################

gtradefed_dist_host_jars := tradefed tradefed-tests tradefed-contrib google-tradefed google-tradefed-tests tf-prod-tests google-tf-prod-tests google-tradefed-contrib emmalib jack-jacoco-reporter loganalysis loganalysis-tests tools-common-prebuilt
gtradefed_dist_host_jar_files := $(foreach m, $(gtradefed_dist_host_jars), $(HOST_OUT_JAVA_LIBRARIES)/$(m).jar)

gtradefed_dist_host_exes := tradefed.sh tradefed_win.bat script_help.sh
gtradefed_dist_host_exe_files := $(foreach m, $(gtradefed_dist_host_exes), $(BUILD_OUT_EXECUTABLES)/$(m))

gtradefed_dist_test_apks := TradeFedUiTestApp TradeFedTestApp
gtradefed_dist_test_apk_files := $(foreach m, $(gtradefed_dist_test_apks), $(TARGET_OUT_DATA_APPS)/$(m)/$(m).apk)

gtradefed_dist_files := \
    $(gtradefed_dist_host_jar_files) \
    $(gtradefed_dist_test_apk_files) \
    $(gtradefed_dist_host_exe_files)

gtradefed_dist_intermediates := $(call intermediates-dir-for,PACKAGING,gtradefed_dist,HOST,COMMON)
gtradefed_dist_zip := $(gtradefed_dist_intermediates)/google-tradefed.zip
$(gtradefed_dist_zip) : $(gtradefed_dist_files)
	@echo "Package: $@"
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) cp -f $^ $(dir $@)
	$(hide) cd $(dir $@) && zip -q $(notdir $@) $(notdir $^)

$(call dist-for-goals, google-tradefed, $(gtradefed_dist_zip))

########################################################
# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
