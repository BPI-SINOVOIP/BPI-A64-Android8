#!/bin/bash
set -ueo pipefail

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 BUILD_ID TARGET_PRODUCT [TARGET_BUILD_TYPE [TARGET_ARCH]]"
    echo "Example: $0 4292972 sailfish user arm64"
    echo "Intermediate files will be created in $PWD,"
    echo "it is recommended to cd in an empty directory"
    echo "For extra configurability, the build targets can be provided by environment variables"
    exit 1
fi

set -x
BUILD_ID=$1
TARGET_PRODUCT=$2
TARGET_BUILD_TYPE=${3:-user}
TARGET_ARCH=${4:-arm64}
set +x

echo "The following three variables can be overridden" \
     "by setting the corresponding "VTS_" prefixed environment variable"
set -x
TEST_SUITES_TARGET=${VTS_TEST_SUITES_TARGET:-test_suites_${TARGET_ARCH}}
BASE_TARGET=${VTS_BASE_TARGET:-${TARGET_PRODUCT}-${TARGET_BUILD_TYPE}}
AOSP_TARGET=${VTS_AOSP_TARGET:-aosp_${TARGET_ARCH}_ab-userdebug}
set +x

download() {
    local target=$1
    local file=$2
    test -f $file ||
        /google/data/ro/projects/android/fetch_artifact --bid $BUILD_ID --target $target $file ||
        { local r=$?; rm $file; return $r; }
}

echo "Downloading"
download $TEST_SUITES_TARGET 'android-vts.zip'
download $BASE_TARGET "${TARGET_PRODUCT}-img-${BUILD_ID}.zip"
download $AOSP_TARGET "aosp_${TARGET_ARCH}_ab-img-${BUILD_ID}.zip"

echo "Unzipping"
rm -rf system.img android-vts
unzip aosp_${TARGET_ARCH}_ab-img-$BUILD_ID.zip system.img
unzip android-vts.zip

echo "Building vbmeta without verity"
avbtool make_vbmeta_image --flag 2 --output vbmeta.img

set -x
adb reboot bootloader
fastboot update ${TARGET_PRODUCT}-img-$BUILD_ID.zip --skip-reboot
fastboot flash vbmeta vbmeta.img || echo "Warning: Device does not support vbmeta"
fastboot erase system
fastboot flash system system.img
fastboot erase metadata
fastboot -w
fastboot reboot
set +x

echo "Board setup"
echo "You may now start vts-tradefed with:"
echo '$ ANDROID_BUILD_TOP= PATH="$PWD:$PATH" vts-tradefed'
echo "For example: "
echo '$ ANDROID_BUILD_TOP= PATH="$PWD/android-vts/tools:$PATH" vts-tradefed run commandAndExit vts --skip-all-system-status-check --primary-abi-only --skip-preconditions --module VtsHalAudioV2_0Target -t CheckConfig.audioPolicyConfigurationValidation'
echo '$ ANDROID_BUILD_TOP= PATH="$PWD/android-vts/tools:$PATH" vts-tradefed run commandAndExit cts --skip-all-system-status-check --skip-preconditions --module CtsInputMethodServiceHostTestCases'
