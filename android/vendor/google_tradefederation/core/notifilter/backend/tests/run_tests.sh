#!/bin/bash

# Copyright 2013 Google Inc. All Rights Reserved.

# A simple helper script that runs Google-Notifilter's backend unit tests

shdir=`dirname $0`/../..
source "${shdir}/script_help.sh"

echo "Running with arguments: $GOOGLENF_SETTINGS"

if [ -z "$*" ]; then
    run_with_class_path '*' -DTemplateOutputPath=/tmp/templates $GOOGLENF_SETTINGS junit.textui.TestRunner com.google.android.notifilter.Tests
else
    run_with_class_path '*' $GOOGLENF_SETTINGS junit.textui.TestRunner "$@"
fi
