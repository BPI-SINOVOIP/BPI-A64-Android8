# Copyright 2013 Google Inc. All Rights Reserved.

LOCAL_PATH := $(call my-dir)

########################################################

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_MODULE := google-nf-be
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := hsqldb-sqltool-2.2.9
LOCAL_STATIC_JAVA_LIBRARIES := notifilter-be

include $(BUILD_HOST_JAVA_LIBRARY)

# makefile rules to copy jars to HOST_OUT/notifilter
# so run_notifilter.sh can automatically add to classpath

DEST_JAR := $(HOST_OUT)/notifilter/$(LOCAL_MODULE).jar
$(DEST_JAR): $(LOCAL_BUILT_MODULE)
	$(copy-file-to-new-target)

# this dependency ensures the above rule will be executed if jar is built
$(LOCAL_INSTALLED_MODULE) : $(DEST_JAR)

########################################################
# Zip up the built files and dist it as notifilter-be.zip
ifneq (,$(filter google-nf google-nf-be google-nf-all, $(TARGET_BUILD_APPS)))

goog_nf_be_dist_host_jars := google-nf-be hsqldb-2.2.9 hsqldb-sqltool-2.2.9
goog_nf_be_dist_host_jar_files := $(foreach m, $(goog_nf_be_dist_host_jars), $(HOST_OUT_JAVA_LIBRARIES)/$(m).jar)

goog_nf_be_dist_host_exes := run_dbserver.sh run_googlenf.sh sqltool.sh script_help.sh
goog_nf_be_dist_host_exe_files := $(foreach m, $(goog_nf_be_dist_host_exes), $(BUILD_OUT_EXECUTABLES)/$(m))

goog_nf_be_dist_files := \
	$(goog_nf_be_dist_host_jar_files) \
	$(goog_nf_be_dist_host_exe_files)

goog_nf_be_dist_intermediates := $(call intermediates-dir-for,PACKAGING,notifilter_be_dist,HOST,COMMON)
goog_nf_be_dist_zip := $(goog_nf_be_dist_intermediates)/google-nf-be.zip
$(goog_nf_be_dist_zip) : $(goog_nf_be_dist_files)
	@echo "Package: $@"
	$(hide) rm -rf $(dir $@) && mkdir -p $(dir $@)
	$(hide) cp -f $^ $(dir $@)
	$(hide) cd $(dir $@) && zip -q $(notdir $@) $(notdir $^)

$(call dist-for-goals, apps_only, $(goog_nf_be_dist_zip))

endif  # google-nf-be in $(TARGET_BUILD_APPS)

########################################################
# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
