#!/bin/bash

# Copyright (C) 2013 Google Inc. All Rights Reserved.

# A simple helper script that runs Notifilter, both in the build tree and externally

source ./script_help.sh

echo "Running with arguments: $GOOGLENF_SETTINGS"

run_with_class_path google-nf-be.jar:hsqldb-2.2.9.jar $GOOGLENF_SETTINGS com.google.android.notifilter.GoogleNotifilter "$@"

