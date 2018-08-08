#!/bin/bash

# Copyright 2013 Google Inc. All Rights Reserved.

# A simple helper script that runs Notifilter's unit tests

shdir=`dirname $0`

run_tests() {
    echo -n "${1}: "
    "${shdir}/${1}/tests/run_tests.sh" 2>&1 | tail -n3 | grep -i tests
}

run_tests "common"
run_tests "frontend"
run_tests "backend"

