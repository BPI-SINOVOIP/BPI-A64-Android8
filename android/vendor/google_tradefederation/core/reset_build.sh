#!/bin/bash

# Copyright 2010 Google Inc. All Rights Reserved.

# A simpler helper script that performs a launch control reset build query
#
# required args: --test-tag --branch --build-flavor --build-id

lib_path=$ANDROID_BUILD_TOP/out/host/linux-x86/framework

java -cp $lib_path/ddmlib-prebuilt.jar:$lib_path/tradefed.jar:$lib_path/google-tradefed.jar com.google.android.tradefed.command.ResetBuild "$@"
