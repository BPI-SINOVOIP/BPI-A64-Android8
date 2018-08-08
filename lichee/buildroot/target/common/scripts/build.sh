#!/bin/bash

OUT_PATH=""
BR_ROOT=`(cd ../../..; pwd)`
export PATH=$PATH:$BR_ROOT/target/tools/host/usr/bin
export PATH=$PATH:$BR_ROOT/output/external-toolchain/bin

# build adb
cd ${LICHEE_TOP_DIR}/buildroot/target/common/adb;
make
make install
if [ $? -ne 0 ]; then
    exit 1
fi

# sysroot exist?
cd ${LICHEE_TOP_DIR}/buildroot/target/${LICHEE_PLATFORM};
if [ ! -d "./sysroot" ]; then
    echo "extract sysroot.tar.gz"
    tar zxf ../common/rootfs/sysroot.tar.gz
fi

if [ ! -d "./output/bin" ]; then
    mkdir -p ./output/bin
fi

cd src
make
if [ $? -ne 0 ]; then
    exit 1
fi
cd ..

if [ ! -d "rootfs/dragonboard" ]; then
    mkdir -p rootfs/dragonboard
fi
cp -rf ${LICHEE_TOP_DIR}/buildroot/target/common/scripts/autorun.sh rootfs/
rm -rf rootfs/dragonboard/*
cp -rf output/* rootfs/dragonboard/

echo "generating rootfs..."

NR_SIZE=`du -sm rootfs | awk '{print $1}'`
NEW_NR_SIZE=$(((($NR_SIZE+32)/16)*16))
#NEW_NR_SIZE=360
TARGET_IMAGE=rootfs.ext4

echo "blocks: $NR_SIZE"M" -> $NEW_NR_SIZE"M""
make_ext4fs -l $NEW_NR_SIZE"M" $TARGET_IMAGE rootfs/
fsck.ext4 -y $TARGET_IMAGE > /dev/null
echo "success in generating rootfs"

if [ -n "$OUT_PATH" ]; then
    cp -v rootfs.ext4 $OUT_PATH/
fi

echo "Build at: `date`"
