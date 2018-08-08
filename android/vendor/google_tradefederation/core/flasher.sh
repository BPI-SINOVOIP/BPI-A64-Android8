#!/bin/bash

# Copyright 2011 Google Inc. All Rights Reserved.

# A helper script that launches TradeFederation from the current build
# environment.

checkPath() {
    if ! type -P $1 &> /dev/null; then
        echo "Unable to find $1 in path."
        exit
    fi;
}

checkFile() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

checkPath adb
checkPath java

# check java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | grep '[ "]1\.6[\. "$$]')
if [ "${JAVA_VERSION}" == "" ]; then
    echo "Wrong java version. 1.6 is required."
    exit
fi

# check if in Android build env
if [ ! -z ${ANDROID_BUILD_TOP} ]; then
    HOST=`uname`
    if [ "$HOST" == "Linux" ]; then
        OS="linux-x86"
    elif [ "$HOST" == "Darwin" ]; then
        OS="darwin-x86"
    else
        echo "Unrecognized OS"
        exit
    fi;
    # ddmlib-prebuilt is still in standard dir
    ddmlib_path=$ANDROID_BUILD_TOP/out/host/$OS/framework/ddmlib-prebuilt.jar
    checkFile $ddmlib_path
    # add all jars in tradefed out dir
    tf_path=$ANDROID_BUILD_TOP/out/host/$OS/tradefed/\*
else
    TF_ROOT_PATH=$(dirname $0)
    # assume downloaded TF, look for jars in same directory as script
    # sanity check - ensure ddmlib-prebuilt is there
    checkFile $TF_ROOT_PATH/ddmlib-prebuilt.jar
    tf_path=$TF_ROOT_PATH/\*
fi;

java -cp $ddmlib_path:$tf_path com.google.android.tradefed.command.Flasher "$@"
