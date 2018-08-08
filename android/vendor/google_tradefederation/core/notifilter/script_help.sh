#!/bin/sh

# Copyright (C) 2013 Google Inc. All Rights Reserved.

# A simple library for run-java-app scripts

check_path() {
    if ! type -P $1 &> /dev/null; then
        echo "Unable to find $1 in path."
        exit
    fi;
}

check_file() {
    if [ ! -f "$1" ]; then
        echo "Unable to locate $1"
        exit
    fi;
}

run_generic() {
    op="$1"
    classes="$2"
    shift; shift  # to recover our original "$@"

    check_path java

    # check java version
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | grep '[ "]1\.7[\. "$$]')
    if [ "${JAVA_VERSION}" == "" ]; then
        echo "Wrong java version. 1.7 is required."
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
        # look for jar in framework out dir
        jar_path="$ANDROID_BUILD_TOP/out/host/$OS/framework"
    else
        # assume downloaded case, look for jars in same directory as script
        jar_path=`dirname $0`
    fi;

    OIFS="$IFS"
    IFS=":"
    classpath=""
    read -a class_ary <<<"$classes"
    for entry in "${class_ary[@]}"
    do
         entry_path="${jar_path}/${entry}"
         classpath="${classpath}${IFS}/usr/share/java/postgresql-jdbc4.jar"
         classpath="${classpath}${IFS}${entry_path}"
    done
    IFS="$OIFS"

    java "$op" "$classpath" "$@"
}

run_jar_file() {
    run_generic "-jar" "$@"
}

run_with_class_path() {
    run_generic "-cp" "$@"
}
