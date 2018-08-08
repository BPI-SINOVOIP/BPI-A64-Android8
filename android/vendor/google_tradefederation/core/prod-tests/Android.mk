# Copyright 2010 Google Inc. All Rights Reserved.

LOCAL_PATH := $(call my-dir)

########################################################

include $(CLEAR_VARS)

# Only compile source java files in this lib.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_RESOURCE_DIRS := res

LOCAL_JAVACFLAGS += -g -Xlint
-include tools/tradefederation/core/error_prone_rules.mk

LOCAL_MODULE := google-tf-prod-tests
LOCAL_MODULE_TAGS := optional
LOCAL_JAVA_LIBRARIES := tradefed tradefed-tests google-tradefed google-tradefed-tests tf-prod-tests loganalysis commons-compress-prebuilt
LOCAL_STATIC_JAVA_LIBRARIES := google-oauth-client google-api-client google-http-client google-http-client-jackson2 jackson-core

LOCAL_JAR_MANIFEST := MANIFEST.mf

include $(BUILD_HOST_JAVA_LIBRARY)

# makefile rules to copy jars to HOST_OUT/tradefed
# so tradefed.sh can automatically add to classpath

DEST_JAR := $(HOST_OUT)/tradefed/$(LOCAL_MODULE).jar
$(DEST_JAR): $(LOCAL_BUILT_MODULE)
	$(copy-file-to-new-target)

# this dependency ensure the above rule will be executed if jar is built
$(LOCAL_INSTALLED_MODULE) : $(DEST_JAR)

########################################################
# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
