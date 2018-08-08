#!/bin/bash
#
# Script to generate test boiler plate

if [ "$#" != "3" ]; then
  echo -e "Usage:\t$0 name suite type"
  echo -e "  name  - the name of the test"
  echo -e "  suite - the test suite (CTS, GTS, PTS, etc)"
  echo -e "  type  - the type of test (host - runs only on the host, device - runs on the device under test, both - runs on the host with a device-side app)"
  echo -e "\nEg. $0 FooBar CTS host"
  exit 1
fi

# Check build environment has been set up and lunch'd
if [ -z "${ANDROID_BUILD_TOP}" ]; then
  echo -e "Android environment not initialized, have you had lunch?"
  exit 1
fi

self="$0"
self_dir=$(dirname ${self})
name="$1"
_name="$(tr '[:upper:]' '[:lower:]' <<< ${name})"
suite="$2"
_suite="$(tr '[:upper:]' '[:lower:]' <<< ${suite})"
_Suite=${suite:0:1}${_suite:1}
type="$3"

if [ "$suite" == "CTS" ]; then
  if [ "$type" == "device" ]; then
    location=${ANDROID_BUILD_TOP}/cts/tests/${_name}
  else
    location=${ANDROID_BUILD_TOP}/cts/hostsidetests/${_name}
    if [ "$type" == "both" ]; then
      app_src=${location}/app/src/android/${_name}/
      app_package=android.${_name}
    fi
  fi
  copyright=aosp
  src=${location}/src/android/${_name}/cts/
  package=android.${_name}.cts
elif [ "$suite" == "GTS" ]; then
  if [ "$type" == "device" ]; then
    location=${ANDROID_BUILD_TOP}/vendor/xts/gts-tests/tests/${_name}
  else
    location=${ANDROID_BUILD_TOP}/vendor/xts/gts-tests/hostsidetests/${_name}
    if [ "$type" == "both" ]; then
      app_src=${location}/app/src/com/google/android/${_name}/
      app_package=com.google.android.${_name}
    fi
  fi
  copyright=google
  src=${location}/src/com/google/android/${_name}/gts/
  package=com.google.android.${_name}.gts
elif [ "$suite" == "PTS" ]; then
  location=${ANDROID_BUILD_TOP}/vendor/google_testing/pts/tests/${_name}
  if [ "$type" == "both" ]; then
    app_src=${location}/app/src/com/google/android/${_name}/
    app_package=com.google.android.${_name}
  fi
  copyright=google
  src=${location}/src/com/google/android/${_name}/pts/
  package=com.google.android.${_name}.pts
elif [ "$suite" == "ATS" ]; then
  location=${ANDROID_BUILD_TOP}/vendor/google_testing/ats/tests/${_name}
  if [ "$type" == "both" ]; then
    app_src=${location}/app/src/com/google/android/${_name}/
    app_package=com.google.android.${_name}
  fi
  copyright=google
  src=${location}/src/com/google/android/${_name}/ats/
  package=com.google.android.${_name}.ats
elif [ "$suite" == "TVTS" ]; then
  if [ "$type" == "device" ]; then
    location=${ANDROID_BUILD_TOP}/vendor/xts/tvts-tests/tests/${_name}
  else
    location=${ANDROID_BUILD_TOP}/vendor/xts/tvts-tests/hostsidetests/${_name}
    if [ "$type" == "both" ]; then
      app_src=${location}/app/src/com/google/android/${_name}/
      app_package=com.google.android.${_name}
    fi
  fi
  copyright=aosp
  src=${location}/src/com/google/android/${_name}/tvts/
  package=com.google.android.${_name}.tvts
elif [ "$suite" == "WTS" ]; then
  if [ "$type" == "device" ]; then
    location=${ANDROID_BUILD_TOP}/vendor/xts/wts-tests/tests/${_name}
  else
    location=${ANDROID_BUILD_TOP}/vendor/xts/wts-tests/hostsidetests/${_name}
    if [ "$type" == "both" ]; then
      app_src=${location}/app/src/com/google/android/${_name}/
      app_package=com.google.android.${_name}
    fi
  fi
  copyright=google
  src=${location}/src/com/google/android/${_name}/wts/
  package=com.google.android.${_name}.wts
fi

year=$(date +"%Y")
mkdir -p ${location}
mkdir -p ${src}
if [ "$type" == "device" ]; then
  module=${_Suite}${name}DeviceTestCases
  activity=${_Suite}${name}DeviceActivity
  test=${_Suite}${name}DeviceTest
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.mk > ${location}/Android.mk
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.xml > ${location}/AndroidManifest.xml
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.xml > ${location}/AndroidTest.xml
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.java > ${src}/${activity}.java
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.java > ${src}/${test}.java
  sed -e "s/:suite:/${_suite}/g"\
    -e "s/:module:/${module}/g"\
    ${self_dir}/device.Android.mk >> ${location}/Android.mk
  sed -e "s/:package:/${package}/g"\
    -e "s/:activity:/${activity}/g"\
    ${self_dir}/device.AndroidManifest.xml >> ${location}/AndroidManifest.xml
  sed -e "s/:package:/${package}/g"\
    -e "s/:module:/${module}/g"\
    ${self_dir}/device.AndroidTest.xml >> ${location}/AndroidTest.xml
  sed -e "s/:package:/${package}/g"\
    -e "s/:activity:/${activity}/g"\
    ${self_dir}/device.Activity.java >> ${src}/${activity}.java
  sed -e "s/:package:/${package}/g"\
    -e "s/:activity:/${activity}/g"\
    -e "s/:test:/${test}/g"\
    ${self_dir}/device.Test.java >> ${src}/${test}.java
else
  module=${_Suite}${name}HostTestCases
  test=${_Suite}${name}HostTest
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.mk > ${location}/Android.mk
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.xml > ${location}/AndroidTest.xml
  sed -e "s/:year:/${year}/g"\
    ${self_dir}/${copyright}.copyright.java > ${src}/${test}.java
  if [ "$type" == "both" ]; then
    app_module=${_Suite}${name}DeviceApp
    app_activity=${name}DeviceActivity
    mkdir -p ${app_src}
    sed -e "s/:year:/${year}/g"\
      ${self_dir}/${copyright}.copyright.mk > ${location}/app/Android.mk
    sed -e "s/:year:/${year}/g"\
      ${self_dir}/${copyright}.copyright.xml > ${location}/app/AndroidManifest.xml
    sed -e "s/:year:/${year}/g"\
      ${self_dir}/${copyright}.copyright.java > ${app_src}/${app_activity}.java
    sed -e "s/:suite:/${_suite}/g"\
      -e "s/:module:/${module}/g"\
      ${self_dir}/both.Android.mk >> ${location}/Android.mk
    sed -e "s/:module:/${module}/g"\
      -e "s/:app-module:/${app_module}/g"\
      ${self_dir}/both.AndroidTest.xml >> ${location}/AndroidTest.xml
    sed -e "s/:package:/${package}/g"\
      -e "s/:app-package:/${app_package}/g"\
      -e "s/:app-activity:/${app_activity}/g"\
      -e "s/:test:/${test}/g"\
      ${self_dir}/both.Test.java >> ${src}/${test}.java
    sed -e "s/:suite:/${_suite}/g"\
      -e "s/:module:/${app_module}/g"\
      ${self_dir}/device.Android.mk >> ${location}/app/Android.mk
    sed -e "s/:package:/${app_package}/g"\
      -e "s/:activity:/${app_activity}/g"\
      ${self_dir}/device.AndroidManifest.xml >> ${location}/app/AndroidManifest.xml
    sed -e "s/:package:/${app_package}/g"\
      -e "s/:activity:/${app_activity}/g"\
      ${self_dir}/device.Activity.java >> ${app_src}/${app_activity}.java
  else
    sed -e "s/:suite:/${_suite}/g"\
      -e "s/:module:/${module}/g"\
      ${self_dir}/host.Android.mk >> ${location}/Android.mk
    sed -e "s/:module:/${module}/g"\
      ${self_dir}/host.AndroidTest.xml >> ${location}/AndroidTest.xml
    sed -e "s/:package:/${package}/g"\
      -e "s/:test:/${test}/g"\
      ${self_dir}/host.Test.java >> ${src}/${test}.java
  fi
fi

echo "Created ${module} under ${location}/"
echo "Build with 'make ${_suite} -j32'"
echo "Run with '${_suite}-tradefed run singleCommand ${_suite} -m ${module}'"
out=${ANDROID_HOST_OUT}/${_suite}/android-${_suite}
echo "Results will be under ${out}/results/"
echo "Logs will be under ${out}/logs/"
