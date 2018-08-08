#!/bin/bash
# Copyright 2016 Google Inc. All Rights Reserved.

# helper to run tf presubmit locally
tfdir="${ANDROID_HOST_OUT}/bin"

source "${tfdir}/script_help.sh"
checkPath adb

$tfdir/tradefed.sh run singleCommand google/tf/local-unit-launcher --folder-path ${TF_JAR_DIR} "$@"
